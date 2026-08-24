package justfatlard.chest_utils;

import justfatlard.chest_utils.screen.ChestScreens;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "chest-utils";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ChestScreens.register();
		HotbarCommand.register();

		// The creative tabs are only assembled once the registries are loaded.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
			justfatlard.chest_utils.action.ItemOrder::build);

		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
			justfatlard.chest_utils.block.DyeInteraction::onUse);

		// A client remembers no overlays across a join, so every painted chest is stated again.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
			(handler, sender, server) -> {
				var player = handler.getPlayer();
				justfatlard.chest_utils.block.DyedChests.get(player.level()).restate(player);

				// And no client remembers which way the hotbar switch was thrown either.
				ChestScreens.showLock(player,
					justfatlard.chest_utils.action.HotbarLocks.get(player).isLocked(player.getUUID()));
			});

		// Paint belongs to the chest, not to the hole it left.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
			(level, player, pos, state, blockEntity) -> {
				if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
					String was = justfatlard.chest_utils.block.DyedChests.get(serverLevel)
						.strip(serverLevel, pos);
					if (was != null) justfatlard.chest_utils.block.DyeInteraction.giveBack(serverLevel, pos, was);
				}
			});

		// A locked hotbar is read back four times a second, so an arrangement changed by hand is
		// the arrangement the lock means. Nine reference comparisons per locked player.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 5 != 0) return;
			for (var player : server.getPlayerList().getPlayers()) {
				justfatlard.chest_utils.action.HotbarLocks.get(player).refresh(player);
			}
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> justfatlard.chest_utils.action.HotbarLocks
				.get(handler.getPlayer()).forgetSeen(handler.getPlayer().getUUID()));

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
