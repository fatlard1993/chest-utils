package justfatlard.chest_utils.action;

import net.minecraft.server.level.ServerPlayer;

/**
 * Tidy a player's pack: the one entry point, for the button and for anybody else with a reason.
 *
 * <p>The hotbar is served first, because it fills from the rows: sorting the rows before it would
 * mean pulling stacks back out of a grid that was just put in order.
 */
public final class PackSort {
	private PackSort() {}

	public static void sort(ServerPlayer player) {
		HotbarLocks.get(player).apply(player);
		ChestActions.sortPack(player.getInventory());
	}
}
