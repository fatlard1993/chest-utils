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
		ChestShareCommand.register();

		// Guarded class load: the tip registration names block-tip types, and block-tip is a
		// suggestion rather than a requirement.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("block-tip")) {
			justfatlard.chest_utils.integration.ChestLockTipRegistration.register();
		}

		// The creative tabs are only assembled once the registries are loaded.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
			justfatlard.chest_utils.action.ItemOrder::build);

		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
			justfatlard.chest_utils.block.DyeInteraction::onUse);

		// A client remembers no overlays across a join, so every painted chest is stated again.
		// On Pandorical's ready moment, not Fabric's JOIN: JOIN fires before the capability
		// handshake, and everything sent then was silently dropped - painted chests came back
		// vanilla on every relog.
		justfatlard.pandorical.api.PandoricalApi.onPlayerReady(player -> {
			justfatlard.chest_utils.block.DyedChests
				.get((net.minecraft.server.level.ServerLevel) player.level()).restate(player);

			// And no client remembers which way the hotbar switch was thrown either.
			ChestScreens.showLock(player,
				justfatlard.chest_utils.action.HotbarLocks.get(player).isLocked(player.getUUID()));
		});

		// Paint is per-level data, so a player who arrives in a dimension is told about that
		// dimension's chests - the ready replay above only ever spoke for the level they joined in.
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL
			.register((player, origin, destination) ->
				justfatlard.chest_utils.block.DyedChests.get(destination).restate(player));

		// A locked chest does not break for anyone but its owner or an op, however public its
		// lid. Checked BEFORE the block goes, because a cancelled break is the only kind that
		// keeps the contents inside.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
			(level, player, pos, state, blockEntity) -> {
				if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return true;
				if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return true;

				var locks = justfatlard.chest_utils.block.ChestLocks.get(serverLevel);
				if (!locks.refuses(serverPlayer, state, pos,
						justfatlard.chest_utils.block.ChestLocks.Use.ALTER)) return true;

				serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
					"Locked by " + locks.lockedBy(state, pos)));
				serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_LOCKED,
					net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
				return false;
			});

		// Paint belongs to the chest, not to the hole it left - and so does its lock: this only
		// runs for a break the guard above allowed, so the entry is the owner's to lose.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
			(level, player, pos, state, blockEntity) -> {
				if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
					String was = justfatlard.chest_utils.block.DyedChests.get(serverLevel)
						.strip(serverLevel, pos);
					if (was != null) justfatlard.chest_utils.block.DyeInteraction.giveBack(serverLevel, pos, was);
					justfatlard.chest_utils.block.ChestLocks.get(serverLevel).forget(pos);
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

		// The hotbar lock in the mod menu, beside the star that toggles it in a chest: the same
		// per-player fact, reachable without opening a chest first.
		if (justfatlard.pandorical.api.PandoricalApi.isAvailable()) {
			justfatlard.pandorical.api.PandoricalApi.settings().group(MOD_ID, "Chest Utils")
				.toggle("hotbarLock", "Hotbar lock", false)
				.describe("Sorting keeps your hotbar's layout and only refills it")
				.backedBy(
					player -> justfatlard.chest_utils.action.HotbarLocks.get(player).isLocked(player.getUUID()),
					(player, locked) -> {
						var locks = justfatlard.chest_utils.action.HotbarLocks.get(player);
						if (locked) locks.lock(player); else locks.unlock(player.getUUID());
					});
			// Every chest the player has locked, wherever it is, with the lock's undo beside it:
			// the one you locked in a base three dimensions ago no longer needs walking back to.
			justfatlard.pandorical.api.PandoricalApi.settings().group(MOD_ID, "Chest Utils")
				.list("locked", "Locked chests", LockedChests::of, LockedChests::unlock)
				.describe("Lock a chest from its own screen; removing one here unlocks it");
		}

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
