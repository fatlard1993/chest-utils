package justfatlard.chest_utils.block;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Dye on a chest paints it. Water bucket on one strips it back.
 *
 * <p>A held dye is the obvious gesture and it costs no new recipe, no new item and no crafting
 * grid: you find a chest you have already placed and the dye you already have, and the two meet
 * where the chest is. Sixteen craftable chest variants would mean sixteen items to carry, and a
 * chest you had to empty and replace to recolour.
 *
 * <p>One dye per coat. Painting a chest the colour it already is does nothing and keeps the dye.
 */
public final class DyeInteraction {
	private DyeInteraction() {}

	/** The colour a stack of dye stands for, or null for anything that is not one. */
	private static String colourOf(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.endsWith("_dye") ? id.substring(0, id.length() - 4).toLowerCase(Locale.ROOT) : null;
	}

	public static InteractionResult onUse(Player player, Level level, InteractionHand hand,
			BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

		BlockPos pos = hit.getBlockPos();
		if (!(level.getBlockState(pos).getBlock() instanceof ChestBlock)) return InteractionResult.PASS;

		ItemStack held = player.getItemInHand(hand);
		String colour = colourOf(held);
		if (colour == null) return InteractionResult.PASS;

		DyedChests painted = DyedChests.get(serverLevel);
		if (colour.equals(painted.colourAt(pos))) return InteractionResult.PASS;

		painted.paint(serverLevel, pos, colour);
		if (!player.isCreative()) held.shrink(1);

		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.DYE_USE,
			net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

		return InteractionResult.SUCCESS;
	}
}
