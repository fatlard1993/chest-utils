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
 * {@code /hotbar} says what the sort thinks each slot is for.
 *
 * <p>The star beside the sort button says whether the hotbar is locked, but not what it is locked
 * <em>to</em> - and a slot's job is invisible until the sort fills it with something unexpected.
 * This is the answer to "why did it do that".
 */
public final class HotbarCommand {
	private HotbarCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			dispatcher.register(Commands.literal("hotbar")
				.executes(context -> list(context.getSource()))));
	}

	private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<HotbarLocks.Lock> plan = HotbarLocks.get(player).of(player.getUUID());

		if (plan.isEmpty()) {
			return said(player, "Hotbar unlocked - sorting lays it out by the default rules."
				+ " The star beside the sort button locks it to what you have.");
		}

		StringBuilder line = new StringBuilder();
		for (int slot = 0; slot < plan.size(); slot++) {
			if (slot > 0) line.append("  ");
			line.append(slot + 1).append(':').append(plan.get(slot).describe());
		}
		return said(player, line.toString());
	}

	private static int said(ServerPlayer player, String words) {
		player.sendSystemMessage(Component.literal(words).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}
}
