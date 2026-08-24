package justfatlard.chest_utils.screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import justfatlard.chest_utils.action.ChestActions;
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

	/** What each open screen is looking at, so a press knows what to act on. */
	private static final Map<ServerPlayer, Container> looking = new WeakHashMap<>();

	public static void register() {
		// On the player's own screen too. Tidying your pack is wanted standing in a field, not
		// only while looking into somebody's chest, and the button that does it should not be
		// somewhere you have to open a chest to reach.
		var slots = PandoricalApi.playerInventory();
		Identifier me = Identifier.fromNamespaceAndPath("chest-utils", "chest-utils");
		// Top right, the one corner of the vanilla panel nothing already claims: the armour
		// runs down the left, the recipe-book toggle and map-plus-plus's two slots fill the row
		// at y=62, and the crafting label stops well short of here.
		slots.registerButton(me, "sort_inv", 155, 6, BUTTON_SIZE, GLYPH_SORT);
		slots.onButton(me, "sort_inv", player -> ChestActions.sort(player.getInventory(),
			0, net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE));

		var screens = PandoricalApi.screens();

		screens.onAction(SCREEN_ID, "sort", (player, data) -> act(player, Action.SORT));
		screens.onAction(SCREEN_ID, "dump", (player, data) -> act(player, Action.DUMP));
		screens.onAction(SCREEN_ID, "topup", (player, data) -> act(player, Action.TOP_UP));
		screens.onAction(SCREEN_ID, "empty", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_ID, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));

		screens.onAction(SCREEN_TAKE_ONLY, "take_all", (player, data) -> act(player, Action.EMPTY));
		screens.onAction(SCREEN_TAKE_ONLY, "top_off", (player, data) -> act(player, Action.TOP_OFF));
		screens.onAction(SCREEN_TAKE_ONLY, "sort_inv", (player, data) -> act(player, Action.SORT_INVENTORY));

		screens.onClose(SCREEN_ID, looking::remove);
		screens.onClose(SCREEN_TAKE_ONLY, looking::remove);
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
			case SORT_INVENTORY -> ChestActions.sort(pack, 0, packEnd);
		}
	}

	/** Wide enough for nine columns and a column of buttons beside them. */
	private static final int WIDTH = 220;
	private static final int BUTTON_X = 178;

	/**
	 * Square, and small. A row of words was four labels wide and still clipped every one of them;
	 * an arrow says the same thing in a sixteenth of the room, and the pair of directions is the
	 * whole of what these do.
	 *
	 * <p>Solid arrows move everything, hollow ones move only what the far side already has. The
	 * two shapes read apart at a glance, which a pair of words the same length never did.
	 */
	private static final int BUTTON_SIZE = 16;

	private static final String GLYPH_SORT = "\u21C5";
	private static final String GLYPH_ALL_IN = "\u2191";
	private static final String GLYPH_MATCH_IN = "\u21E7";
	private static final String GLYPH_ALL_OUT = "\u2193";
	private static final String GLYPH_MATCH_OUT = "\u21E9";

	/**
	 * Lay out a chest of this many rows and hand back where the pack starts.
	 *
	 * <p>Nine columns of slots is 162 pixels and the vanilla chest is 176 wide, which leaves no
	 * room beside them for anything to press. So the screen is widened rather than the buttons
	 * squeezed: four labels crammed into the title row is how they came out as "Sor..." and
	 * "Emp...", which says nothing at all.
	 */
	private static int layout(ScreenBuilder screen, Component title, int rows) {
		int chestSlots = rows * COLS;
		int packY = 18 + rows * SLOT + 14;
		int height = packY + 3 * SLOT + 4 + SLOT + 8;

		screen.size(WIDTH, height);
		screen.panel("bg", 0, 0, WIDTH, height, Map.of("border", "beveled"));
		screen.text("title", MARGIN, 6, Map.of("text", title.getString(), "color", "#404040"));

		screen.inventoryGrid("chest", MARGIN, 18, rows, COLS, 0);
		screen.text("pack_label", MARGIN, packY - 10, Map.of("text", "Inventory", "color", "#404040"));
		screen.inventoryGrid("pack", MARGIN, packY, 3, COLS, chestSlots);
		screen.inventoryGrid("hotbar", MARGIN, packY + 3 * SLOT + 4, 1, COLS, chestSlots + 27);

		return packY;
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

		button(screen, "sort", 18, GLYPH_SORT);
		button(screen, "dump", 38, GLYPH_ALL_IN);
		button(screen, "topup", 56, GLYPH_MATCH_IN);
		button(screen, "empty", 76, GLYPH_ALL_OUT);
		button(screen, "sort_inv", packY, GLYPH_SORT);

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

		button(screen, "take_all", 18, GLYPH_ALL_OUT);
		button(screen, "top_off", 38, GLYPH_MATCH_OUT);
		button(screen, "sort_inv", packY, GLYPH_SORT);

		PandoricalApi.screens().openContainer(player, screen.build(), container, Set.of());
	}

	private static void button(ScreenBuilder screen, String id, int y, String label) {
		Map<String, String> props = new LinkedHashMap<>();
		props.put(ComponentType.PROP_LABEL, label);
		screen.button(id, BUTTON_X, y, BUTTON_SIZE, BUTTON_SIZE, props);
	}
}
