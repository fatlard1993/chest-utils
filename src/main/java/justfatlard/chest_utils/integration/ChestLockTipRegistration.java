package justfatlard.chest_utils.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.chest_utils.block.ChestLocks;

/**
 * Whose chest this is, before you try the lid.
 *
 * <p>A lock only ever announced itself by refusing you: you walked up, clicked, and got a clunk
 * and a name. That is the answer arriving after the question, and on a shared server it means
 * every locked chest gets tried once by everybody. Looking at it is the natural moment to learn
 * it is somebody's.
 *
 * <p>The line says what you can do with it as well as who owns it, because "locked" and "locked
 * to you" are the same word for two opposite situations - one is a wall and the other is a chest
 * you can just open.
 */
public final class ChestLockTipRegistration {
	private ChestLockTipRegistration() {}

	public static void register() {
		BlockTipApi.describe((level, pos, state, player) -> {
			ChestLocks.Lock lock = ChestLocks.get(level).lockAt(pos);
			if (lock == null) return null;

			// A public chest is everybody's to open, so the line only has to say whose it is.
			if (lock.isPublic()) {
				return lock.owner().equals(player.getUUID())
					? "Public - yours"
					: "Public - " + lock.ownerName() + "'s";
			}

			if (lock.owner().equals(player.getUUID())) {
				return lock.shared().isEmpty()
					? "Locked - yours"
					: "Locked - yours, shared with " + names(lock);
			}

			// Named either way: the point of showing an owner is knowing who to go and ask.
			return lock.allows(player.getUUID())
				? "Locked by " + lock.ownerName() + " - shared with you"
				: "Locked by " + lock.ownerName();
		});
	}

	private static String names(ChestLocks.Lock lock) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < lock.shared().size(); i++) {
			if (i > 0) out.append(", ");
			out.append(lock.shared().get(i).name());
		}
		return out.toString();
	}
}
