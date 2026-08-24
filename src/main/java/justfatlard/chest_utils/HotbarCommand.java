package justfatlard.chest_utils;

import java.util.List;
import justfatlard.chest_utils.action.HotbarLocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /hotbar} says what the sort thinks each slot is for, {@code /hotbar reset} makes it
 * forget.
 *
 * <p>The hotbar's slots are held for things without anything on screen saying so, which is
 * tolerable while it behaves and baffling the first time it refills a slot with something you did
 * not expect. This is the answer to "why did it do that", and the way back.
 */
public final class HotbarCommand {
	private HotbarCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			dispatcher.register(Commands.literal("hotbar")
				.executes(context -> list(context.getSource()))
				.then(Commands.literal("reset").executes(context -> reset(context.getSource())))));
	}

	private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<HotbarLocks.Lock> plan = HotbarLocks.get(player).of(player.getUUID());

		if (plan.isEmpty()) {
			return said(player, "Nothing held yet - sort your pack once and the hotbar is laid out");
		}

		StringBuilder line = new StringBuilder();
		for (int slot = 0; slot < plan.size(); slot++) {
			if (slot > 0) line.append("  ");
			line.append(slot + 1).append(':').append(plan.get(slot).describe());
		}
		return said(player, line.toString());
	}

	private static int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean had = HotbarLocks.get(player).forget(player.getUUID());

		return said(player, had
			? "Hotbar forgotten - the next sort lays it out again"
			: "Nothing was held");
	}

	private static int said(ServerPlayer player, String words) {
		player.sendSystemMessage(Component.literal(words).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}
