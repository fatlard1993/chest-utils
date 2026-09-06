package justfatlard.chest_utils.action;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The order the game itself puts items in.
 *
 * <p>Sorting alphabetically is the obvious thing and the wrong one: it files an apple between an
 * anvil and armour, splits oak planks from oak logs, and scatters the ores through the alphabet.
 * Every sorting mod that players actually keep installed ends up offering a "by category" mode,
 * and the category everybody already knows is the creative menu - building blocks, then coloured
 * blocks, then natural, and so on down. A player who has ever opened it has the map in their head
 * already, and nothing has to be taught.
 *
 * <p>Taken from the tabs rather than restated here, so it costs nothing to maintain and a modded
 * item lands wherever its own mod put it instead of at the end under "miscellaneous".
 *
 * <p>Built once, on server start. Rebuilding the tabs is not cheap and their contents do not
 * change while the server is up.
 */
public final class ItemOrder {
	private ItemOrder() {}

	/** Anything the creative menu never shows, sorted after everything it does. */
	private static final int UNPLACED = Integer.MAX_VALUE / 2;

	/**
	 * Items lifted out of their creative tab to sit at the head of the tools, in this order.
	 *
	 * <p>The creative menu is the right map for nearly everything, and this is where it is the
	 * wrong one. A bow is a tool you reach for without looking, and Combat files it behind every
	 * sword, axe and trident there is - so a sorted pack put it in the middle of the weapons,
	 * nowhere the hand expects. Hoisting is deliberately a short list of exceptions and not a
	 * category of its own: the moment it needs a rule, the creative order has stopped being the
	 * answer and something else should be.
	 */
	private static final List<Item> HOISTED_TO_TOOLS = List.of(Items.BOW);

	/**
	 * How the tools tab is recognised: by something that can only be in it.
	 *
	 * <p>Not by name or by index. Tab names are translation keys that have been renamed before
	 * ("Tools" became "Tools &amp; Utilities"), and the order tabs come in has changed more than
	 * once; a wooden pickaxe has never been anywhere else.
	 */
	private static final Item TOOLS_TAB_MARKER = Items.WOODEN_PICKAXE;

	/**
	 * Creative positions are spaced rather than consecutive, so the hoisted items have somewhere
	 * to land: the gap left between neighbours is exactly wide enough to hold all of them ahead
	 * of the first tool without landing on whatever the previous tab ended with. Derived from the
	 * list rather than picked, or a second entry on it would quietly tie with a real item.
	 */
	private static final int STEP = HOISTED_TO_TOOLS.size() + 1;

	private static volatile Map<Item, Integer> order = Map.of();

	public static void build(MinecraftServer server) {
		var overworld = server.overworld();

		try {
			CreativeModeTabs.tryRebuildTabContents(
				overworld.enabledFeatures(), true, server.registryAccess());
		} catch (RuntimeException e) {
			// Not fatal: without the tabs everything falls back to name order, which is worse
			// but still an order.
			justfatlard.chest_utils.Main.LOGGER.warn(
				"[chest-utils] Could not read creative tab order, falling back to names: {}",
				e.toString());
			return;
		}

		Map<Item, Integer> built = new IdentityHashMap<>();
		int position = 0;
		int toolsStart = -1;

		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			// Only the real category tabs. Search holds everything again, and the inventory and
			// hotbar tabs are the player's own, not an ordering.
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;

			int tabStart = position;
			boolean isToolsTab = false;

			for (var stack : tab.getDisplayItems()) {
				if (stack.is(TOOLS_TAB_MARKER)) isToolsTab = true;
				built.putIfAbsent(stack.getItem(), position);
				position += STEP;
			}

			if (isToolsTab && toolsStart < 0) toolsStart = tabStart;
		}

		// put, not putIfAbsent: these already have a place from their own tab, and this is the
		// one that wins. Skipped entirely if the tools tab was not found, which leaves the
		// creative order intact rather than inventing a spot for them at the front of the menu.
		if (toolsStart >= 0) {
			int at = toolsStart - HOISTED_TO_TOOLS.size();
			for (Item item : HOISTED_TO_TOOLS) built.put(item, at++);
		}

		order = built;
		justfatlard.chest_utils.Main.LOGGER.info(
			"[chest-utils] Item order taken from {} creative entries", built.size());
	}

	/** Where this item sits in the creative menu, or past the end if it is not in it. */
	public static int of(Item item) {
		return order.getOrDefault(item, UNPLACED);
	}

	/** A stable tiebreak for anything the menu does not place. */
	public static String nameOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
