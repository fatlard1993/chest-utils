package justfatlard.chest_utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import justfatlard.chest_utils.block.ChestLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * A player's locked chests as a list they can read and prune from the mod menu.
 *
 * <p>Each entry is keyed by dimension and position, which is the only name a chest has, and shown
 * as its coordinates, its dimension, and how open its lid is. A double chest is two locked blocks;
 * where its chunk is loaded the pair is shown once, and unlocking one half unlocks both, the way
 * the lock itself works. Where it is not loaded, each half stands alone and goes alone, rather
 * than loading a chunk to draw a list.
 */
final class LockedChests {
	private LockedChests() {}

	static Map<String, String> of(ServerPlayer player) {
		Map<String, String> out = new LinkedHashMap<>();
		UUID owner = player.getUUID();
		for (ServerLevel level : player.level().getServer().getAllLevels()) {
			Map<BlockPos, ChestLocks.Lock> owned = ChestLocks.get(level).ownedBy(owner);
			java.util.Set<BlockPos> seen = new java.util.HashSet<>();
			for (Map.Entry<BlockPos, ChestLocks.Lock> entry : owned.entrySet()) {
				BlockPos pos = entry.getKey();
				if (seen.contains(pos)) continue;
				if (level.hasChunkAt(pos)) {
					seen.addAll(ChestLocks.halvesOf(level.getBlockState(pos), pos));
				}
				out.put(key(level, pos), label(level, pos, entry.getValue()));
			}
		}
		return out;
	}

	static void unlock(ServerPlayer player, String key) {
		int at = key.indexOf('@');
		if (at < 0) return;
		ServerLevel level = player.level().getServer().getLevel(
			ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(key.substring(0, at))));
		if (level == null) return;
		String[] xyz = key.substring(at + 1).split(",");
		if (xyz.length != 3) return;
		BlockPos pos;
		try {
			pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
		} catch (NumberFormatException e) {
			return;
		}
		ChestLocks locks = ChestLocks.get(level);
		if (level.hasChunkAt(pos)) {
			locks.unlock(level.getBlockState(pos), pos);
		} else {
			locks.forget(pos);
		}
	}

	private static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String label(ServerLevel level, BlockPos pos, ChestLocks.Lock lock) {
		String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		if (level.dimension() != Level.OVERWORLD) {
			String name = level.dimension().identifier().getPath().replace('_', ' ');
			where += " in " + Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}
		if (lock.isPublic()) return where + "  (public)";
		if (!lock.shared().isEmpty()) return where + "  (shared with " + lock.shared().size() + ")";
		return where;
	}
}
