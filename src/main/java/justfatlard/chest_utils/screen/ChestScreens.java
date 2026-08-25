package justfatlard.chest_utils.screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import justfatlard.chest_utils.action.ChestActions;
import justfatlard.chest_utils.action.HotbarLocks;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

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
	private static final int LABEL_GAP = 10;
	private static final int TEXT_HEIGHT = 9;

	/** What each open screen is looking at, so a press knows what to act on. */
	private static final Map<ServerPlayer, Container> looking = new WeakHashMap<>();

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
		slots.registerButton(me, "sort_inv", 156, 4, BUTTON_SIZE, GLYPH_SORT);
		slots.onButton(me, "sort_inv", justfatlard.chest_utils.action.PackSort::sort);

		// Beside the hotbar, not beside the sort button. It is the hotbar's switch, and a control
		// sitting against the row it governs needs less explaining than one in a corner: the row
		// is the label. There is no room for it inside the panel - the hotbar spans the full nine
		// columns - so it sits just off the right edge, level with the row.
		slots.registerButton(me, "lock_hotbar", HOTBAR_BUTTON_X, HOTBAR_BUTTON_Y,
			BUTTON_SIZE, GLYPH_UNLOCKED);
		slots.onButton(me, "lock_hotbar", ChestScreens::toggleLock);

		var screens = PandoricalApi.screens();

		screens.onAction(SCREEN_ID, "sort", (player, data) -> act(player, Action.SORT));
		screens.onAction(SCREEN_ID, "dump", (player, data) -> act(player, Action.DUMP));
		screens.onAction(SCREEN_ID, "topup", (player, data) -> act(player, Action.TOP_UP));
		screens.onAction(SCREEN_ID, "empty", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_ID, "top_off", (player, data) -> act(player, Action.TOP_OFF));
		screens.onAction(SCREEN_ID, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));

		screens.onAction(SCREEN_TAKE_ONLY, "take_all", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_TAKE_ONLY, "top_off", (player, data) -> act(player, Action.TOP_OFF));
		screens.onAction(SCREEN_TAKE_ONLY, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));

		screens.onClose(SCREEN_ID, looking::remove);
		screens.onClose(SCREEN_TAKE_ONLY, looking::remove);
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
			locked ? GLYPH_LOCKED : GLYPH_UNLOCKED);
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

	private static final String GLYPH_SORT = "\u21C5";

	/**
	 * Solid means committed, hollow means not - the same reading the four transfer arrows already
	 * ask for, so the pair does not need learning twice.
	 */
	private static final String GLYPH_LOCKED = "\u2605";
	private static final String GLYPH_UNLOCKED = "\u2606";
	private static final String GLYPH_ALL_IN = "\u2191";
	private static final String GLYPH_MATCH_IN = "\u21E7";
	private static final String GLYPH_ALL_OUT = "\u2193";
	private static final String GLYPH_MATCH_OUT = "\u21E9";

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
		int packY = 18 + rows * SLOT + 14;
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
	private static void buttonRow(ScreenBuilder screen, int textY, String... idsAndGlyphs) {
		// Twelve pixels against nine of text: half the difference puts one on the other's centre.
		int y = textY - (BUTTON_SIZE - TEXT_HEIGHT) / 2;
		int x = BUTTON_RIGHT - BUTTON_SIZE;

		for (int i = 0; i < idsAndGlyphs.length; i += 2) {
			Map<String, String> props = new LinkedHashMap<>();
			props.put(ComponentType.PROP_LABEL, idsAndGlyphs[i + 1]);
			screen.button(idsAndGlyphs[i], x, y, BUTTON_SIZE, BUTTON_SIZE, props);
			x -= BUTTON_SIZE + BUTTON_GAP;
		}
	}

	/**
	 * Show this container with the buttons attached.
	 *
	 * @param rows how many rows of nine the container holds
	 */
	public static void open(ServerPlayer player, Container container, Component title, int rows) {
		looking.put(player, container);

		ScreenBuilder screen = new ScreenBuilder(SCREEN_ID).container(rows * COLS, true);
		int packY = layout(screen, title, rows);

		// Beside the chest's own name: what can be done to the chest. Both directions of both
		// moves - all of it, or only what the far side already has - so the row reads as two
		// pairs rather than three things and an odd one out.
		buttonRow(screen, TITLE_Y,
			"top_off", GLYPH_MATCH_OUT,
			"empty", GLYPH_ALL_OUT,
			"topup", GLYPH_MATCH_IN,
			"dump", GLYPH_ALL_IN,
			"sort", GLYPH_SORT);

		// Beside "Inventory": the one thing that acts on the pack.
		buttonRow(screen, packY - LABEL_GAP, "sort_inv", GLYPH_SORT);

		PandoricalApi.screens().openContainer(player, screen.build(), container, Set.of());
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

		buttonRow(screen, TITLE_Y,
			"top_off", GLYPH_MATCH_OUT,
			"take_all", GLYPH_ALL_OUT);

		buttonRow(screen, packY - LABEL_GAP, "sort_inv", GLYPH_SORT);

		PandoricalApi.screens().openContainer(player, screen.build(), container, Set.of());
	}

}
