package justfatlard.chest_utils.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which chests are locked, and by whom.
 *
 * <p>A locked chest can only be opened or broken by the player who locked it, or an op. That is
 * the whole rule: no keys to carry, no lock item to craft, nothing for anybody else to configure.
 * Locking is a claim, not a mechanism, which is the right size for a server where the threat model
 * is housemates borrowing diamonds.
 *
 * <p>A lock can also be made public: the lid is anybody's, but the chest itself is still the
 * owner's - nobody else breaks it, paints it or changes the lock. That is the community chest at
 * spawn, which everyone should be able to use and nobody should be able to walk off with.
 *
 * <p>Kept as saved data against the level rather than in the block entity, for the same reason
 * {@link DyedChests} is: the block entity belongs to vanilla, and a chest that stopped being a
 * vanilla chest would give up doubling, hoppers and every other mod's understanding of it to
 * store one name.
 *
 * <p><b>A double chest locks as a whole.</b> Every operation here expands to both halves through
 * {@link #halvesOf}, because half a locked double chest is a door with one hinge protected:
 * breaking the free half spills that half's contents, and a chest placed against a locked single
 * would open the pair through the new half.
 *
 * <p>The owner's name is stored beside the id purely so the refusal can say who to ask, without a
 * lookup that would go blank the moment the owner is offline.
 *
 * <p>Known limits, accepted deliberately: hoppers and droppers still work a locked chest (the lock
 * is against players, and automation is the owner's own plumbing), and a chest destroyed by an
 * explosion keeps its entry, so the spot inherits a stale lock if a new chest is placed there. An
 * op can always unlock either situation away.
 */
public final class ChestLocks extends SavedData {
	private static final String STORAGE_KEY = "chest-utils:locks";


	/** Somebody the owner has let in, with the name to show when listing them. */
	public record Share(UUID id, String name) {
		static final Codec<Share> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Share::id),
			Codec.STRING.fieldOf("name").forGetter(Share::name)
		).apply(instance, Share::new));
	}

	/** What somebody wants with a locked chest, in the order the lock lets go of them. */
	public enum Use {
		/** Open the lid. Anybody's on a public chest; the owner's and their guests' otherwise. */
		OPEN,
		/** Break it or paint it. The owner's and their guests', however public the lid is. */
		ALTER,
		/** Lock it, unlock it, make it public. The owner's alone. */
		LOCK
	}

	public record Lock(UUID owner, String ownerName, List<Share> shared, boolean isPublic) {
		static final Codec<Lock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Lock::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(Lock::ownerName),
			// Optional with an empty default, so every lock saved before sharing existed still
			// loads - a chest locked yesterday must not become unreadable for gaining a feature
			Share.CODEC.listOf().optionalFieldOf("shared", List.of()).forGetter(Lock::shared),
			Codec.BOOL.optionalFieldOf("public", false).forGetter(Lock::isPublic)
		).apply(instance, Lock::new));

		/** Whether this player may open and break the chest: the owner, or somebody let in. */
		public boolean allows(UUID player) {
			if (this.owner.equals(player)) return true;
			for (Share share : this.shared) {
				if (share.id().equals(player)) return true;
			}
			return false;
		}

		public boolean permits(UUID player, Use use) {
			return switch (use) {
				case OPEN -> this.isPublic || allows(player);
				case ALTER -> allows(player);
				case LOCK -> this.owner.equals(player);
			};
		}

		Lock with(Share share) {
			List<Share> next = new ArrayList<>(this.shared);
			next.removeIf(existing -> existing.id().equals(share.id()));
			next.add(share);
			return new Lock(this.owner, this.ownerName, List.copyOf(next), this.isPublic);
		}

		Lock without(UUID player) {
			List<Share> next = new ArrayList<>(this.shared);
			next.removeIf(existing -> existing.id().equals(player));
			return new Lock(this.owner, this.ownerName, List.copyOf(next), this.isPublic);
		}

		Lock published(boolean isPublic) {
			return new Lock(this.owner, this.ownerName, this.shared, isPublic);
		}
	}

	private record Entry(long pos, Lock lock) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
			Lock.CODEC.fieldOf("lock").forGetter(Entry::lock)
		).apply(instance, Entry::new));
	}

	public static final Codec<ChestLocks> CODEC = Entry.CODEC.listOf()
		.xmap(ChestLocks::fromEntries, ChestLocks::toEntries);

	private static final SavedDataType<ChestLocks> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), ChestLocks::new, CODEC, DataFixTypes.LEVEL);

	private final Map<Long, Lock> locks = new HashMap<>();

	public static ChestLocks get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * Both halves of a double chest, or just the one block for anything else. Everything that
	 * locks, unlocks or checks goes through this, so the two halves cannot drift apart.
	 */
	public static List<BlockPos> halvesOf(BlockState state, BlockPos pos) {
		if (state.getBlock() instanceof ChestBlock
				&& state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			Direction toPartner = ChestBlock.getConnectedDirection(state);
			return List.of(pos, pos.relative(toPartner));
		}
		return List.of(pos);
	}

	public Lock lockAt(BlockPos pos) {
		return this.locks.get(pos.asLong());
	}

	/** Whether any half is locked against this player opening it. */
	public boolean refuses(ServerPlayer player, BlockState state, BlockPos pos) {
		return refuses(player, state, pos, Use.OPEN);
	}

	/** Whether any half is locked against this player doing this with it. */
	public boolean refuses(ServerPlayer player, BlockState state, BlockPos pos, Use use) {
		// Gamemaster is the level "somebody else's lock is not your problem" starts at,
		// same bar the suite's admin commands use
		if (player.permissions().hasPermission(
				net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return false;
		for (BlockPos half : halvesOf(state, pos)) {
			Lock lock = lockAt(half);
			if (lock != null && !lock.permits(player.getUUID(), use)) return true;
		}
		return false;
	}

	/** The name on whichever half is locked, for telling the refused player who to ask. */
	public String lockedBy(BlockState state, BlockPos pos) {
		for (BlockPos half : halvesOf(state, pos)) {
			Lock lock = lockAt(half);
			if (lock != null) return lock.ownerName();
		}
		return null;
	}

	public void lock(ServerPlayer owner, BlockState state, BlockPos pos) {
		Lock lock = new Lock(owner.getUUID(), owner.getName().getString(), List.of(), false);
		for (BlockPos half : halvesOf(state, pos)) {
			this.locks.put(half.asLong(), lock);
		}
		this.setDirty();
	}

	/**
	 * Open the lid to everyone, or shut it to the owner and their guests again. The lock, its
	 * owner and its guests stay as they were: this is about the lid, not the claim.
	 *
	 * @return false when there is no lock here to change
	 */
	public boolean publish(BlockState state, BlockPos pos, boolean isPublic) {
		return edit(state, pos, lock -> lock.published(isPublic));
	}

	public void unlock(BlockState state, BlockPos pos) {
		boolean removed = false;
		for (BlockPos half : halvesOf(state, pos)) {
			removed |= this.locks.remove(half.asLong()) != null;
		}
		if (removed) this.setDirty();
	}

	/**
	 * Let somebody else in, or shut them out again. Applied to every half, like the lock itself.
	 *
	 * @return false when there is no lock here to share
	 */
	public boolean share(BlockState state, BlockPos pos, ServerPlayer guest) {
		return edit(state, pos, lock -> lock.with(new Share(guest.getUUID(), guest.getName().getString())));
	}

	public boolean unshare(BlockState state, BlockPos pos, UUID guest) {
		return edit(state, pos, lock -> lock.without(guest));
	}

	private boolean edit(BlockState state, BlockPos pos, java.util.function.UnaryOperator<Lock> change) {
		boolean edited = false;
		for (BlockPos half : halvesOf(state, pos)) {
			Lock lock = lockAt(half);
			if (lock == null) continue;
			this.locks.put(half.asLong(), change.apply(lock));
			edited = true;
		}
		if (edited) this.setDirty();
		return edited;
	}

	/** Every locked block this player owns, half by half; a double chest is two of them. */
	public Map<BlockPos, Lock> ownedBy(UUID owner) {
		Map<BlockPos, Lock> out = new java.util.TreeMap<>();
		this.locks.forEach((pos, lock) -> {
			if (lock.owner().equals(owner)) out.put(BlockPos.of(pos), lock);
		});
		return out;
	}

	/** Forget one block's lock, for when that block stops existing. */
	public void forget(BlockPos pos) {
		if (this.locks.remove(pos.asLong()) != null) this.setDirty();
	}

	private static ChestLocks fromEntries(List<Entry> entries) {
		ChestLocks locks = new ChestLocks();
		for (Entry entry : entries) {
			locks.locks.put(entry.pos(), entry.lock());
		}
		return locks;
	}

	private List<Entry> toEntries() {
		List<Entry> entries = new ArrayList<>(this.locks.size());
		this.locks.forEach((pos, lock) -> entries.add(new Entry(pos, lock)));
		return entries;
	}
}
