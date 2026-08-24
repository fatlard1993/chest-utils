package justfatlard.chest_utils.action;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

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

		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			// Only the real category tabs. Search holds everything again, and the inventory and
			// hotbar tabs are the player's own, not an ordering.
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;

			for (var stack : tab.getDisplayItems()) {
				built.putIfAbsent(stack.getItem(), position++);
			}
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
