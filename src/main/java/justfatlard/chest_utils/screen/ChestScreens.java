package justfatlard.chest_utils.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import justfatlard.chest_utils.action.ChestActions;
import justfatlard.chest_utils.action.HotbarLocks;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A chest, with the four buttons it should always have had.
 *
 * <p>Built as a Pandorical container screen rather than by bolting widgets onto the vanilla one:
 * the slots and the buttons then arrive in the same definition, the presses come back as ordinary
 * screen actions, and a client running nothing but Pandorical gets all of it. Nothing here is
 * client code and no packet is this mod's own.
 *
 * <p>{@link #open} is public and takes any {@link Container} on purpose. A loot chest is somebody
 * else's copy of a chest, and loot-ender should be able to hand it here and get the buttons for
 * free rather than either mod having to know how the other builds a screen.
 */
public final class ChestScreens {
	private ChestScreens() {}

	public static final String SCREEN_ID = "chest-utils:chest";

	/**
	 * A chest you can only take from, which is what a loot chest is.
	 *
	 * <p>Its own screen because the buttons point the other way. Nothing can be put into a loot
	 * copy, so Dump and Sort would be two controls that visibly do nothing; what is wanted there
	 * is the reverse pair - take the lot, or top up the stacks you already carry from it.
	 */
	public static final String SCREEN_TAKE_ONLY = "chest-utils:loot";

	/** Rows of nine the vanilla chest texture is drawn for. */
	private static final int COLS = 9;
	private static final int SLOT = 18;
	private static final int MARGIN = 8;

	/**
	 * Just past the right edge of the vanilla panel, level with the hotbar row.
	 *
	 * <p>The hotbar starts at y=142 and is one slot tall, so this centres a twelve on eighteen.
	 * Outside the panel because nine columns of slots leave nothing beside them, and the recipe
	 * book opens to the left, so the right edge is the one side that stays clear.
	 */
	private static final int HOTBAR_BUTTON_X = 178;
	private static final int HOTBAR_BUTTON_Y = 145;

	/** Where a header's text sits, and how tall a line of it is. */
	private static final int TITLE_Y = 6;
	/**
	 * How far above its grid a header's text sits.
	 *
	 * <p>Must be at least {@link #BUTTON_SIZE}, because the buttons on that line are centred on
	 * text shorter than they are and hang below it: at ten, the row beside "Inventory" ended one
	 * pixel inside the top of the pack grid and scraped along it. The rule is
	 * {@code LABEL_GAP >= BUTTON_SIZE}, which puts the lowest pixel of a button one clear of the
	 * first slot; keep them moving together.
	 */
	private static final int LABEL_GAP = 12;
	private static final int TEXT_HEIGHT = 9;

	/** What each open screen is looking at, so a press knows what to act on. */
	private static final Map<ServerPlayer, Container> looking = new WeakHashMap<>();

	/**
	 * Which block the open screen belongs to, for the screens that have one to lock - and the id
	 * of that screen, which is NOT {@link #SCREEN_ID}.
	 *
	 * <p>{@code SCREEN_ID} is the screen TYPE; ScreenBuilder mints a fresh id per opening, and
	 * the client matches updates on the id. Addressing an update by the type is dropped on
	 * arrival without a word, which is how the lock button spent an evening toggling the lock
	 * perfectly and never once changing its own face.
	 */
	private record LockTarget(net.minecraft.server.level.ServerLevel level,
			net.minecraft.core.BlockPos pos, String screenId) {}

	private static final Map<ServerPlayer, LockTarget> lockable = new WeakHashMap<>();

	/**
	 * The header row's two faces: the buttons, and the search field that stands in for them.
	 *
	 * <p>Both are built when the screen opens and one is hidden; a press flips them. Rebuilding
	 * the screen instead would drop whatever the player was carrying on the cursor and put the
	 * scroll back to the top, for a change that is one row of one line. {@code row} is what to
	 * hide, {@code query} what the field last said, kept so a slot changing under a live search
	 * re-answers it.
	 */
	private static final class Search {
		final String screenId;
		final List<String> row;
		String query = "";
		boolean open;

		Search(String screenId, List<String> row) {
			this.screenId = screenId;
			this.row = row;
		}
	}

	private static final Map<ServerPlayer, Search> searches = new WeakHashMap<>();

	public static void register() {
		// On the player's own screen too. Tidying your pack is wanted standing in a field, not
		// only while looking into somebody's chest, and the button that does it should not be
		// somewhere you have to open a chest to reach.
		var slots = PandoricalApi.playerInventory();
		Identifier me = Identifier.fromNamespaceAndPath("chest-utils", "chest-utils");
		// The vanilla panel has a header row too - "Crafting", ending around x=137 - and the
		// same rule applies here as on our own screens: small buttons laid back from the right
		// margin, centred on that line. At twelve they clear the label and stop well above the
		// crafting result slot below them.
		slots.registerButton(me, "sort_inv", 156, 4, BUTTON_SIZE, ICON_SORT);
		slots.onButton(me, "sort_inv", justfatlard.chest_utils.action.PackSort::sort);

		// Beside the hotbar, not beside the sort button. It is the hotbar's switch, and a control
		// sitting against the row it governs needs less explaining than one in a corner: the row
		// is the label. There is no room for it inside the panel - the hotbar spans the full nine
		// columns - so it sits just off the right edge, level with the row.
		slots.registerButton(me, "lock_hotbar", HOTBAR_BUTTON_X, HOTBAR_BUTTON_Y,
			BUTTON_SIZE, ICON_HOTBAR_UNLOCKED);
		slots.onButton(me, "lock_hotbar", ChestScreens::toggleLock);

		var screens = PandoricalApi.screens();

		screens.onAction(SCREEN_ID, "sort", (player, data) -> act(player, Action.SORT));
		screens.onAction(SCREEN_ID, "dump", (player, data) -> act(player, Action.DUMP));
		screens.onAction(SCREEN_ID, "topup", (player, data) -> act(player, Action.TOP_UP));
		screens.onAction(SCREEN_ID, "empty", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_ID, "top_off", (player, data) -> act(player, Action.TOP_OFF));
		screens.onAction(SCREEN_ID, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));
		screens.onAction(SCREEN_ID, "lock", (player, data) -> toggleChestLock(player));

		screens.onAction(SCREEN_TAKE_ONLY, "take_all", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_TAKE_ONLY, "top_off", (player, data) -> act(player, Action.TOP_OFF));
		screens.onAction(SCREEN_TAKE_ONLY, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));

		for (String type : List.of(SCREEN_ID, SCREEN_TAKE_ONLY)) {
			screens.onAction(type, SEARCH, (player, data) -> showSearch(player, true));
			screens.onAction(type, SEARCH_CLOSE, (player, data) -> showSearch(player, false));
			screens.onAction(type, SEARCH_BOX, (player, data) -> search(player, data.get("text")));
			// Moving a stack changes which slots answer; the veil follows the items, not the click
			screens.onSlotChange(type, (player, slot, stack) -> {
				Search search = searches.get(player);
				if (search != null && search.open) search(player, search.query);
			});
		}

		screens.onClose(SCREEN_ID, player -> {
			looking.remove(player);
			lockable.remove(player);
			searches.remove(player);
		});
		screens.onClose(SCREEN_TAKE_ONLY, player -> {
			looking.remove(player);
			searches.remove(player);
		});
	}

	/**
	 * Swap the header row for the search field, or back.
	 *
	 * <p>Opening hands the field the keyboard as it appears, so the first letter typed lands
	 * without a click to find the box first. Closing empties it and lifts the veil, so the next
	 * opening starts clean rather than showing whatever was last looked for.
	 */
	private static void showSearch(ServerPlayer player, boolean open) {
		Search search = searches.get(player);
		if (search == null) return;
		search.open = open;

		List<ComponentUpdate> updates = new ArrayList<>();
		for (String id : search.row) {
			updates.add(new ComponentUpdateBuilder(id)
				.prop(ComponentType.PROP_VISIBLE, String.valueOf(!open)).build());
		}
		updates.add(new ComponentUpdateBuilder(SEARCH_BOX)
			.prop(ComponentType.PROP_VISIBLE, String.valueOf(open))
			.prop(ComponentType.PROP_FOCUSED, String.valueOf(open))
			.prop(ComponentType.PROP_VALUE, "").build());
		updates.add(new ComponentUpdateBuilder(SEARCH_CLOSE)
			.prop(ComponentType.PROP_VISIBLE, String.valueOf(open)).build());
		if (!open) {
			search.query = "";
			updates.addAll(veil(""));
		}
		PandoricalApi.screens().update(player, search.screenId, updates);
	}

	/** Answer the field: veil every slot on the screen that does not match what it says. */
	private static void search(ServerPlayer player, String text) {
		Search search = searches.get(player);
		if (search == null || !search.open) return;
		search.query = text == null ? "" : text;

		String[] words = search.query.trim().toLowerCase(Locale.ROOT).split("\\s+");
		StringBuilder dim = new StringBuilder();
		if (!search.query.isBlank()) {
			for (Slot slot : player.containerMenu.slots) {
				if (matches(slot.getItem(), words)) continue;
				if (dim.length() > 0) dim.append(',');
				dim.append(slot.index);
			}
		}
		PandoricalApi.screens().update(player, search.screenId, veil(dim.toString()));
	}

	/**
	 * Every word typed has to appear in the item's name or its registry id. The name is what
	 * the player reads on the tooltip, renames included; the id catches "log" against Oak Log
	 * as readily as it catches "planks", and "iron" against a sword whose name says so.
	 */
	private static boolean matches(ItemStack stack, String[] words) {
		if (stack.isEmpty()) return false;
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String word : words) {
			if (word.isEmpty()) continue;
			if (!name.contains(word) && !id.contains(word)) return false;
		}
		return true;
	}

	/** The same veil to all three grids; each one dims only the slots it draws. */
	private static List<ComponentUpdate> veil(String dimSlots) {
		List<ComponentUpdate> updates = new ArrayList<>();
		for (String grid : List.of("chest", "pack", "hotbar")) {
			updates.add(new ComponentUpdateBuilder(grid)
				.prop(ComponentType.PROP_DIM_SLOTS, dimSlots).build());
		}
		return updates;
	}

	/** Throw the switch, and say in words what the star on it now means. */
	private static void toggleLock(ServerPlayer player) {
		HotbarLocks locks = HotbarLocks.get(player);
		boolean locking = !locks.isLocked(player.getUUID());

		if (locking) {
			locks.lock(player);
		} else {
			locks.unlock(player.getUUID());
		}

		showLock(player, locking);
		player.sendSystemMessage(Component.literal(locking
			? "Hotbar locked - sorting keeps this arrangement and refills it"
			: "Hotbar unlocked - sorting lays it out by the default rules"));
	}

	/** Put the right face on the switch. Called on a press, and again on every join. */
	public static void showLock(ServerPlayer player, boolean locked) {
		PandoricalApi.playerInventory().setButtonGlyph(player,
			Identifier.fromNamespaceAndPath("chest-utils", "chest-utils"), "lock_hotbar",
			locked ? ICON_HOTBAR_LOCKED : ICON_HOTBAR_UNLOCKED);
	}

	private enum Action { SORT, DUMP, TOP_UP, TOP_OFF, EMPTY, SORT_INVENTORY }

	private static void act(ServerPlayer player, Action action) {
		Container chest = looking.get(player);
		if (chest == null) return;

		Container pack = player.getInventory();
		// Everything a player owns lives in one container, worn gear included. PACK_SLOTS is
		// where the part they can rummage through ends.
		int packEnd = net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE;

		switch (action) {
			case SORT -> ChestActions.sort(chest);
			case DUMP -> ChestActions.dump(pack, packEnd, chest);
			case TOP_UP -> ChestActions.topUp(pack, packEnd, chest);
			case TOP_OFF -> ChestActions.topUp(chest, chest.getContainerSize(), pack);
			case EMPTY -> ChestActions.dump(chest, chest.getContainerSize(), pack);
			case SORT_INVENTORY -> justfatlard.chest_utils.action.PackSort.sort(player);
		}
	}

	/**
	 * Vanilla's own width. The screen used to be widened to 220 to carry a column of buttons
	 * down the side, which bought the buttons a home at the cost of every chest on the server
	 * being visibly the wrong shape. The room was there all along: a header row is one short
	 * word and eighty pixels of empty panel, and buttons small enough to sit on that line cost
	 * no width at all.
	 */
	private static final int WIDTH = MARGIN + COLS * SLOT + MARGIN;

	/** Right edge the button rows are laid back from. */
	private static final int BUTTON_RIGHT = WIDTH - MARGIN;

	/**
	 * Square, and small enough to sit on a line of text.
	 *
	 * <p>A row of words was four labels wide and still clipped every one of them; an arrow says
	 * the same thing in a fraction of the room, and the pair of directions is the whole of what
	 * these do. Twelve pixels is a shade taller than the nine a line of text stands in, so a
	 * button reads as belonging to the header beside it rather than floating near it.
	 *
	 * <p>Solid arrows move everything, hollow ones move only what the far side already has. The
	 * two shapes read apart at a glance, which a pair of words the same length never did.
	 */
	private static final int BUTTON_SIZE = 12;

	/** Enough that two glyphs do not read as one control. */
	private static final int BUTTON_GAP = 2;

	/**
	 * GUI atlas sprites, not font characters. Every one of these was a unicode arrow or star
	 * once, and at a size that fits a header row they all render as one-pixel hairlines - fine
	 * in a paragraph, wrong beside vanilla's chunky widget art, which is what the buttons
	 * themselves are drawn from. See generate_gui_icons.py, which draws them.
	 *
	 * <p>Solid moves everything, outline moves only what the far side already has: the pair
	 * reads apart at a glance, which two words the same length never did.
	 */
	private static final String ICON_SORT = "chest-utils:icon_sort";

	/**
	 * Solid means committed, hollow means not - the same reading the four transfer arrows already
	 * ask for, so the pair does not need learning twice.
	 */
	private static final String ICON_HOTBAR_LOCKED = "chest-utils:icon_star";
	private static final String ICON_HOTBAR_UNLOCKED = "chest-utils:icon_star_open";
	private static final String ICON_ALL_IN = "chest-utils:icon_in";
	private static final String ICON_MATCH_IN = "chest-utils:icon_in_match";
	private static final String ICON_ALL_OUT = "chest-utils:icon_out";
	private static final String ICON_MATCH_OUT = "chest-utils:icon_out_match";

	/**
	 * A shut padlock against an open one - drawn, so it can actually look like a padlock. The
	 * public one is shut but hollow, the same hollow the arrows use for "only partly": the
	 * chest is still claimed, the lid just is not.
	 */
	private static final String ICON_CHEST_LOCKED = "chest-utils:icon_lock";
	private static final String ICON_CHEST_UNLOCKED = "chest-utils:icon_unlock";
	private static final String ICON_CHEST_PUBLIC = "chest-utils:icon_public";

	/** A magnifier to open the search, and the cross that closes it. */
	private static final String ICON_SEARCH = "chest-utils:icon_search";
	private static final String ICON_CLOSE = "chest-utils:icon_close";

	private static final String SEARCH = "search";
	private static final String SEARCH_BOX = "search_box";
	private static final String SEARCH_CLOSE = "search_close";

	/**
	 * The field is as wide as the fullest row it stands in for - seven buttons and their gaps -
	 * so opening it changes nothing about where the header's right edge is, whichever screen
	 * it is on. Wider would run into a long chest name; narrower would not show a word.
	 */
	private static final int SEARCH_WIDTH = 7 * BUTTON_SIZE + 6 * BUTTON_GAP;

	/**
	 * Lay out a chest of this many rows and hand back where the pack starts.
	 *
	 * <p>Nine columns of slots is 162 pixels of a 176-wide panel, so there is no room beside the
	 * grid for anything to press - but there is a whole header row above each grid carrying one
	 * short word, and the rest of that line is empty panel. The buttons go there, laid back from
	 * the right margin, which is why this screen is exactly the width a chest has always been.
	 */
	private static int layout(ScreenBuilder screen, Component title, int rows) {
		int chestSlots = rows * COLS;
		// 16, not vanilla's 14: the pack label now sits LABEL_GAP above its grid rather than 10,
		// and the two extra pixels are given back here so the label keeps its distance from the
		// chest grid above instead of taking it out of that gap
		int packY = 18 + rows * SLOT + 16;
		int height = packY + 3 * SLOT + 4 + SLOT + 8;

		screen.size(WIDTH, height);
		screen.panel("bg", 0, 0, WIDTH, height, Map.of("border", "beveled"));
		screen.text("title", MARGIN, TITLE_Y, Map.of("text", title.getString(), "color", "#404040"));

		screen.inventoryGrid("chest", MARGIN, 18, rows, COLS, 0);
		screen.text("pack_label", MARGIN, packY - LABEL_GAP, Map.of("text", "Inventory", "color", "#404040"));
		screen.inventoryGrid("pack", MARGIN, packY, 3, COLS, chestSlots);
		screen.inventoryGrid("hotbar", MARGIN, packY + 3 * SLOT + 4, 1, COLS, chestSlots + 27);

		return packY;
	}

	/**
	 * A row of buttons laid back from the right margin, centred on a line of text.
	 *
	 * <p>Right to left in the order given, so the first one named sits furthest out and the row
	 * grows inward towards the header it belongs to. Laid back from the edge rather than forward
	 * from the label because the label is the part whose width nobody controls: a chest somebody
	 * named gets a longer title, and a row anchored to the margin simply meets it later.
	 *
	 * @param textY the y the header text was drawn at
	 */
	private static List<String> buttonRow(ScreenBuilder screen, int textY, String... idsAndIcons) {
		// Twelve pixels against nine of text: half the difference puts one on the other's centre.
		int y = textY - (BUTTON_SIZE - TEXT_HEIGHT) / 2;
		int x = BUTTON_RIGHT - BUTTON_SIZE;
		List<String> ids = new ArrayList<>();

		for (int i = 0; i < idsAndIcons.length; i += 2) {
			Map<String, String> props = new LinkedHashMap<>();
			props.put(ComponentType.PROP_ICON, idsAndIcons[i + 1]);
			screen.button(idsAndIcons[i], x, y, BUTTON_SIZE, BUTTON_SIZE, props);
			ids.add(idsAndIcons[i]);
			x -= BUTTON_SIZE + BUTTON_GAP;
		}
		return ids;
	}

	/**
	 * The header row's other face, built hidden in the same place the buttons stand.
	 *
	 * <p>The cross takes the outermost button's spot and the field runs inward from it, so the
	 * two together cover exactly the ground the row did. Hidden rather than absent because a
	 * screen cannot grow a component after it has opened; it can only be shown one it already
	 * has.
	 */
	private static void searchRow(ScreenBuilder screen, int textY) {
		int y = textY - (BUTTON_SIZE - TEXT_HEIGHT) / 2;
		int closeX = BUTTON_RIGHT - BUTTON_SIZE;

		Map<String, String> close = new LinkedHashMap<>();
		close.put(ComponentType.PROP_ICON, ICON_CLOSE);
		close.put(ComponentType.PROP_VISIBLE, "false");
		screen.button(SEARCH_CLOSE, closeX, y, BUTTON_SIZE, BUTTON_SIZE, close);

		screen.component(new ComponentBuilder(SEARCH_BOX, ComponentType.TEXT_INPUT)
			.bounds(closeX - BUTTON_GAP - SEARCH_WIDTH, y, SEARCH_WIDTH, BUTTON_SIZE)
			.prop(ComponentType.PROP_PLACEHOLDER, "Search")
			.prop(ComponentType.PROP_MAX_LENGTH, "32")
			.prop(ComponentType.PROP_VISIBLE, "false"));
	}

	/**
	 * Show this container with the buttons attached.
	 *
	 * <p>This shape is for containers with no block behind them (loot-ender's copies); a real
	 * chest comes through the overload below and gets the lock button too.
	 *
	 * @param rows how many rows of nine the container holds
	 */
	public static void open(ServerPlayer player, Container container, Component title, int rows) {
		open(player, container, title, rows, null, null);
	}

	/**
	 * Show a block-backed container, with the lock switch beside its name.
	 *
	 * <p>Anybody may see the switch; whether the press does anything is the lock's call, since
	 * a public chest opens for people who have no say over it.
	 */
	public static void open(ServerPlayer player, Container container, Component title, int rows,
			net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		looking.put(player, container);

		ScreenBuilder screen = new ScreenBuilder(SCREEN_ID).container(rows * COLS, true);
		int packY = layout(screen, title, rows);

		// Beside the chest's own name: what can be done to the chest. Both directions of both
		// moves - all of it, or only what the far side already has - so the row reads as two
		// pairs rather than three things and an odd one out. The lock sits innermost, against
		// the name, because it is about the chest itself rather than what is in it.
		// Search sits outermost: it is the one press that takes the whole row with it, and
		// the cross that brings the row back appears in exactly its place.
		List<String> row;
		if (level != null && pos != null) {
			lockable.put(player, new LockTarget(level, pos, screen.screenId()));
			row = buttonRow(screen, TITLE_Y,
				SEARCH, ICON_SEARCH,
				"top_off", ICON_MATCH_OUT,
				"empty", ICON_ALL_OUT,
				"topup", ICON_MATCH_IN,
				"dump", ICON_ALL_IN,
				"sort", ICON_SORT,
				"lock", lockIcon(justfatlard.chest_utils.block.ChestLocks.get(level).lockAt(pos)));
		} else {
			lockable.remove(player);
			row = buttonRow(screen, TITLE_Y,
				SEARCH, ICON_SEARCH,
				"top_off", ICON_MATCH_OUT,
				"empty", ICON_ALL_OUT,
				"topup", ICON_MATCH_IN,
				"dump", ICON_ALL_IN,
				"sort", ICON_SORT);
		}
		searchRow(screen, TITLE_Y);
		searches.put(player, new Search(screen.screenId(), row));

		// Beside "Inventory": the one thing that acts on the pack.
		buttonRow(screen, packY - LABEL_GAP, "sort_inv", ICON_SORT);

		PandoricalApi.screens().openContainer(player, screen.build(), container, Set.of());
	}

	/**
	 * Throw the chest's lock one notch round - unlocked, locked, public, unlocked - and repaint
	 * the switch so the screen says what just happened.
	 */
	private static void toggleChestLock(ServerPlayer player) {
		LockTarget target = lockable.get(player);
		if (target == null) return;

		var state = target.level().getBlockState(target.pos());
		var locks = justfatlard.chest_utils.block.ChestLocks.get(target.level());

		// The screen having opened proves nothing about the lock: a public chest opens for
		// everyone, and somebody else may have locked the chest since. The lock is asked fresh.
		if (locks.refuses(player, state, target.pos(), justfatlard.chest_utils.block.ChestLocks.Use.LOCK)) {
			player.sendOverlayMessage(Component.literal(
				"Locked by " + locks.lockedBy(state, target.pos())));
			return;
		}

		var was = locks.lockAt(target.pos());
		String said;
		if (was == null) {
			locks.lock(player, state, target.pos());
			said = "Locked - only you and ops can open or break this chest";
		} else if (!was.isPublic()) {
			locks.publish(state, target.pos(), true);
			said = "Public - anyone can use this chest, only you and ops can break or change it";
		} else {
			locks.unlock(state, target.pos());
			said = "Unlocked - anyone can use this chest again";
		}

		PandoricalApi.screens().update(player, target.screenId(), java.util.List.of(
			new justfatlard.pandorical.api.ComponentUpdateBuilder("lock")
				.prop(ComponentType.PROP_ICON, lockIcon(locks.lockAt(target.pos())))
				.build()));
		player.sendOverlayMessage(Component.literal(said));
	}

	private static String lockIcon(justfatlard.chest_utils.block.ChestLocks.Lock lock) {
		if (lock == null) return ICON_CHEST_UNLOCKED;
		return lock.isPublic() ? ICON_CHEST_PUBLIC : ICON_CHEST_LOCKED;
	}

	/**
	 * The same chest, for something nobody can put anything into.
	 *
	 * <p>Public and taking a plain {@link Container} so loot-ender can hand over a player's own
	 * copy and get the buttons without either mod knowing how the other builds a screen.
	 */
	public static void openTakeOnly(ServerPlayer player, Container container, Component title,
			int rows) {
		looking.put(player, container);

		ScreenBuilder screen = new ScreenBuilder(SCREEN_TAKE_ONLY).container(rows * COLS, true);
		int packY = layout(screen, title, rows);

		List<String> row = buttonRow(screen, TITLE_Y,
			SEARCH, ICON_SEARCH,
			"top_off", ICON_MATCH_OUT,
			"take_all", ICON_ALL_OUT);
		searchRow(screen, TITLE_Y);
		searches.put(player, new Search(screen.screenId(), row));

		buttonRow(screen, packY - LABEL_GAP, "sort_inv", ICON_SORT);

		PandoricalApi.screens().openContainer(player, screen.build(), container, Set.of());
	}

}
