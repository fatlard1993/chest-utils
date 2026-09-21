package justfatlard.chest_utils.mixin;

import justfatlard.chest_utils.screen.ChestScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serves the buttoned screen instead of the plain one.
 *
 * <p>Injected on the block rather than on Fabric's UseBlockCallback for the same reason
 * loot-ender is: the event stops at the first handler that answers, so taking it would decide by
 * mod load order whether anything else watching a chest being opened ever hears about it.
 *
 * <p><b>Yields to whoever got there first.</b> loot-ender replaces this same screen with a
 * player's own copy of a loot chest, and two mods both insisting would come down to which mixin
 * happened to sort first. Checking whether the call was already answered means the other mod
 * wins its own chests and this one keeps the rest - and loot-ender can hand its copy to
 * {@link ChestScreens#open} to get the buttons anyway.
 */
// Priority above the default, which in mixin's reckoning means later: whoever wants a chest
// more specifically than "it is a chest" gets to answer before this does.
@Mixin(value = {ChestBlock.class, BarrelBlock.class, ShulkerBoxBlock.class}, priority = 1500)
public class ChestOpenMixin {

	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 1)
	private void chestUtils$openWithButtons(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		// Already answered - loot-ender serving somebody their own copy, most likely. Opening a
		// second screen over the top of it would be this mod's idea of help.
		if (cir.isCancelled()) return;
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
		if (player.isSpectator()) return;

		// The lock gate: somebody else's locked chest refuses the way vanilla's own
		// keyed containers do, a knock and a word, and never shows a screen at all
		var locks = justfatlard.chest_utils.block.ChestLocks.get((net.minecraft.server.level.ServerLevel) level);
		if (locks.refuses(serverPlayer, state, pos)) {
			serverPlayer.sendOverlayMessage(Component.literal(
				"Locked by " + locks.lockedBy(state, pos)));
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_LOCKED,
				net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		// A client that cannot draw Pandorical's screens gets vanilla's: Pandorical declines to
		// open one for it, and answering the click here as well left the chest shut.
		if (!ChestScreens.canShow(serverPlayer)) return;

		// A shulker box is not simply a chest that travels. Vanilla refuses to open one with no
		// room for its lid, counts the opening as a statistic, and takes it as provocation if a
		// piglin is watching. Answering the click here instead of letting vanilla do it means
		// carrying all three, or quietly dropping them.
		if (state.getBlock() instanceof ShulkerBoxBlock) {
			if (!(level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box)) return;
			if (!chestUtils$lidHasRoom(state, level, pos, box)) {
				cir.setReturnValue(InteractionResult.SUCCESS);
				return;
			}
			int shulkerRows = box.getContainerSize() / 9;
			if (shulkerRows <= 0) return;
			serverPlayer.awardStat(net.minecraft.stats.Stats.OPEN_SHULKER_BOX);
			net.minecraft.world.entity.monster.piglin.PiglinAi.angerNearbyPiglins(
				(net.minecraft.server.level.ServerLevel) level, serverPlayer, true);
			ChestScreens.open(serverPlayer, box, state.getBlock().getName(), shulkerRows,
				(net.minecraft.server.level.ServerLevel) level, pos);
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}

		Container container;
		int rows;

		if (state.getBlock() instanceof ChestBlock chest) {
			container = ChestBlock.getContainer(chest, state, level, pos, false);
			if (container == null) return;
			rows = container.getContainerSize() / 9;
		} else {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (!(blockEntity instanceof Container plain)) return;
			container = plain;
			rows = plain.getContainerSize() / 9;
		}
		if (rows <= 0) return;

		ChestScreens.open(serverPlayer, container, state.getBlock().getName(), rows,
			(net.minecraft.server.level.ServerLevel) level, pos);
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	/**
	 * Whether the lid can swing, which is vanilla's own {@code canOpen} and is private.
	 *
	 * <p>A box already opening or open is allowed through; a shut one needs the space its lid
	 * sweeps to be clear, which is what stops a shulker box being opened flush against a ceiling.
	 * Copied rather than called because it is private static, and worth copying rather than
	 * skipping: it is the difference between a shulker box and a chest that happens to move.
	 */
	@Unique
	private static boolean chestUtils$lidHasRoom(BlockState state, Level level, BlockPos pos,
			ShulkerBoxBlockEntity box) {
		if (box.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) return true;
		net.minecraft.world.phys.AABB swept = net.minecraft.world.entity.monster.Shulker
			.getProgressDeltaAabb(1.0F, state.getValue(ShulkerBoxBlock.FACING), 0.0F, 0.5F,
				net.minecraft.world.phys.Vec3.atBottomCenterOf(pos))
			.deflate(1.0E-6);
		return level.noCollision(swept);
	}
}
