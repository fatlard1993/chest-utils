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

	/**
	 * Drop the dye a broken chest was painted with.
	 *
	 * <p>Popped where the chest was, beside the chest itself, so the paint behaves like every
	 * other thing a block is made of: you get back what you put in and the colour lives only on
	 * placed chests.
	 */
	public static void giveBack(ServerLevel level, BlockPos pos, String colour) {
		var dye = BuiltInRegistries.ITEM.getOptional(
			net.minecraft.resources.Identifier.withDefaultNamespace(colour + "_dye")).orElse(null);
		if (dye == null) return;

		net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(dye));
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

		// Painting is the sneaking gesture, so an ordinary click still opens the chest.
		//
		// Holding dye is not an intention to paint. A chest is opened far more often than it is
		// recoloured, and dye is exactly the sort of thing that is in your hand because you were
		// doing something else with it - so a plain right-click has to keep meaning "open", or
		// carrying dye quietly turns every chest into a paint pot. Sneaking already means "act on
		// the block rather than use it" everywhere else in the game, and vanilla passes a sneaking
		// click straight through to the held item, which is where this is standing.
		if (!player.isShiftKeyDown()) return InteractionResult.PASS;

		// A lock covers the paint as well as the lid. It always guarded opening and breaking, and
		// left this third way in open: anyone could walk up to a locked chest and recolour it,
		// which is not theft but is somebody else's chest changed by a hand that has no claim on
		// it. Checked before the dye is spent, so a refusal costs the dye nothing.
		ChestLocks locks = ChestLocks.get(serverLevel);
		if (locks.refuses(serverPlayer, level.getBlockState(pos), pos, ChestLocks.Use.ALTER)) {
			serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
				"Locked by " + locks.lockedBy(level.getBlockState(pos), pos)));
			level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_LOCKED,
				net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
			return InteractionResult.FAIL;
		}

		DyedChests painted = DyedChests.get(serverLevel);
		String previous = painted.colourAt(pos);
		if (colour.equals(previous)) return InteractionResult.PASS;

		painted.paint(serverLevel, pos, colour);
		if (!player.isCreative()) held.shrink(1);
		// Repainting scrapes the old coat off whole: the previous dye pops back out, so a
		// recolour costs one dye and not two
		if (previous != null) giveBack(serverLevel, pos, previous);

		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.DYE_USE,
			net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);

		return InteractionResult.SUCCESS;
	}
}
