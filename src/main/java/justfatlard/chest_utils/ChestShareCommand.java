package justfatlard.chest_utils;

import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import justfatlard.chest_utils.block.ChestLocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /chest-lock} shares the chest you are looking at.
 *
 * <p>A lock is a claim on a chest, and a claim nobody can lend is a wall. The common case on a
 * house server is not "keep everyone out" but "keep everyone out except my brother", and without
 * this the only way to give somebody a thing in a locked chest is to unlock it, which gives it to
 * everybody.
 *
 * <p>The chest is the one under the crosshair rather than a coordinate, because that is how the
 * lock was put on in the first place - a button on the chest's own screen - and asking for x y z
 * to undo something you did by pointing at it is a different mod's idea of consistency.
 */
public final class ChestShareCommand {
	private ChestShareCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			dispatcher.register(Commands.literal("chest-lock")
				.then(Commands.literal("share")
					.then(Commands.argument("player", EntityArgument.player())
						.executes(ChestShareCommand::share)))
				.then(Commands.literal("unshare")
					.then(Commands.argument("player", EntityArgument.player())
						.executes(ChestShareCommand::unshare)))
				.then(Commands.literal("list")
					.executes(ChestShareCommand::list))));
	}

	/** The chest under the crosshair, or null having already told the player why not. */
	private static Target look(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();

		HitResult hit = player.pick(player.blockInteractionRange(), 0F, false);
		if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
			source.sendFailure(Component.literal("Look at a locked chest first"));
			return null;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		ChestLocks locks = ChestLocks.get(level);

		// Only ever a locked chest: with no lock there is no access to share, and testing for a
		// lock rather than for a block type means this covers whatever the lock covers
		ChestLocks.Lock lock = locks.lockAt(pos);
		if (lock == null) {
			source.sendFailure(Component.literal("That is not a locked chest"));
			return null;
		}

		// Somebody let in may open it; only the owner decides who else gets to. An op overrides
		// both, the same as everywhere else a lock is enforced.
		boolean mayShare = lock.owner().equals(player.getUUID())
			|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		return new Target(player, level, pos, state, locks, lock, mayShare);
	}

	private record Target(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
			ChestLocks locks, ChestLocks.Lock lock, boolean mayShare) {}

	private static int share(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Target target = look(context.getSource());
		if (target == null) return 0;
		if (!ownerOnly(context.getSource(), target)) return 0;

		ServerPlayer guest = EntityArgument.getPlayer(context, "player");
		if (guest.getUUID().equals(target.lock().owner())) {
			context.getSource().sendFailure(Component.literal("They already own that chest"));
			return 0;
		}

		target.locks().share(target.state(), target.pos(), guest);
		context.getSource().sendSuccess(() -> Component.literal("Shared with " + guest.getName().getString())
			.withStyle(ChatFormatting.GREEN), false);
		guest.sendSystemMessage(Component.literal(
			target.player().getName().getString() + " shared a locked chest with you")
			.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int unshare(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Target target = look(context.getSource());
		if (target == null) return 0;
		if (!ownerOnly(context.getSource(), target)) return 0;

		ServerPlayer guest = EntityArgument.getPlayer(context, "player");
		UUID id = guest.getUUID();
		if (target.lock().shared().stream().noneMatch(share -> share.id().equals(id))) {
			context.getSource().sendFailure(
				Component.literal(guest.getName().getString() + " does not have access to that chest"));
			return 0;
		}

		target.locks().unshare(target.state(), target.pos(), id);
		context.getSource().sendSuccess(() -> Component.literal("Removed " + guest.getName().getString())
			.withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	/** Anyone who can open it can ask who else can; changing that is the owner's alone. */
	private static int list(CommandContext<CommandSourceStack> context)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Target target = look(context.getSource());
		if (target == null) return 0;

		ChestLocks.Lock lock = target.lock();
		StringBuilder line = new StringBuilder(lock.isPublic() ? "Public, locked by " : "Locked by ")
			.append(lock.ownerName());
		if (lock.shared().isEmpty()) {
			line.append(" - shared with nobody");
		} else {
			line.append(" - shared with ");
			for (int i = 0; i < lock.shared().size(); i++) {
				if (i > 0) line.append(", ");
				line.append(lock.shared().get(i).name());
			}
		}

		context.getSource().sendSuccess(() -> Component.literal(line.toString())
			.withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static boolean ownerOnly(CommandSourceStack source, Target target) {
		if (target.mayShare()) return true;
		source.sendFailure(Component.literal(
			"Only " + target.lock().ownerName() + " can change who may use that chest"));
		return false;
	}
}
