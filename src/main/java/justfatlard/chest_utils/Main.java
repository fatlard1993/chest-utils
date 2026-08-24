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

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
