package justfatlard.chest_utils.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What each hotbar slot is for, so that sorting can fill it without rearranging it.
 *
 * <p>The hotbar is the one part of a pack that must not simply be tidied. It is a set of
 * controls: which key the pickaxe is under was learned once and is now used without looking, and
 * a sort that reshuffles it costs more than the mess it cleaned. But "never touch it" throws away
 * the useful half - coming home with a better pickaxe and having to put it back under the same
 * key, by hand, every time.
 *
 * <p>So the hotbar is the one lock-point. Sorting reads it rather than writing it: whatever is in
 * a slot says what that slot is <em>for</em>, and the answer is remembered so the slot can be
 * filled again once it empties. A slot holding a pickaxe is a pickaxe slot and gets the best
 * pickaxe you own; a slot holding torches is a torch slot and gets torches, never something
 * torch-shaped. Nothing is ever moved out of the hotbar and nothing in it is reordered.
 *
 * <p>The exception is the first sort, when there is nothing to read. That one lays the hotbar out
 * - tools from the left in reaching order, torches after them, food at the far end - and the
 * layout it produces is the first thing it learns. From then on the player's own arrangement is
 * the standard, because it is the one their hands know.
 */
public final class HotbarLocks extends SavedData {
	private static final String STORAGE_KEY = "chest-utils:hotbar_locks";

	/** Torches earn a slot by being the next thing a hand wants after the tool that made the dark. */
	private static final String TORCH = BuiltInRegistries.ITEM.getKey(Items.TORCH).toString();

	/** Furthest from the tools, so a mis-scroll mid-fight does not put dinner in your hand. */
	private static final int FOOD_SLOT = Inventory.SELECTION_SIZE - 1;

	/** A slot is held for a role, or for one exact item, or for nothing. */
	public record Lock(HotbarRole role, String item) {
		static final Lock FREE = new Lock(null, null);

		static final Codec<Lock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.optionalFieldOf("role", "").forGetter(l -> l.role == null ? "" : l.role.name()),
			Codec.STRING.optionalFieldOf("item", "").forGetter(l -> l.item == null ? "" : l.item)
		).apply(instance, (role, item) -> new Lock(readRole(role), item.isEmpty() ? null : item)));

		/**
		 * A role this build no longer has a name for reads as an unheld slot.
		 *
		 * <p>{@code valueOf} would throw instead, and this is loaded with the level: one renamed
		 * enum constant would take a world's saved data down with it.
		 */
		private static HotbarRole readRole(String name) {
			if (name.isEmpty()) return null;
			for (HotbarRole role : HotbarRole.values()) {
				if (role.name().equals(name)) return role;
			}
			return null;
		}

		public boolean isFree() {
			return role == null && item == null;
		}

		/** What to call this slot when listing the hotbar back to whoever asked. */
		public String describe() {
			if (role != null) return role.name().toLowerCase();
			if (item != null) return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
			return "-";
		}
	}

	private record Entry(UUID player, List<Lock> locks) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
			Lock.CODEC.listOf().fieldOf("locks").forGetter(Entry::locks)
		).apply(instance, Entry::new));
	}

	public static final Codec<HotbarLocks> CODEC = Entry.CODEC.listOf()
		.xmap(HotbarLocks::fromEntries, HotbarLocks::toEntries);

	private static final SavedDataType<HotbarLocks> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), HotbarLocks::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, List<Lock>> locks = new HashMap<>();

	/**
	 * Always the overworld's copy.
	 *
	 * <p>Saved data is stored per dimension, and a hotbar is not: sorting in the Nether and
	 * sorting at home have to read the same answer.
	 */
	public static HotbarLocks get(ServerPlayer player) {
		return player.level().getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** What the hotbar is currently held for, for anybody who wants to see it written down. */
	public List<Lock> of(UUID player) {
		return locks.getOrDefault(player, List.of());
	}

	/** Forget a hotbar, so the next sort lays it out from scratch. */
	public boolean forget(UUID player) {
		if (locks.remove(player) == null) return false;
		this.setDirty();
		return true;
	}

	/**
	 * Fill the hotbar's held slots, then learn what it now says.
	 *
	 * <p>Called before the rows are sorted, for the same reason the top-up is: pulling stacks out
	 * of a grid that was just put in order undoes the ordering.
	 *
	 * @return true if this was the layout pass, so the caller can say what happened
	 */
	public boolean apply(ServerPlayer player) {
		Inventory pack = player.getInventory();
		List<Lock> plan = locks.get(player.getUUID());

		boolean laidOut = plan == null;
		if (laidOut) {
			arrange(pack);
		} else {
			refill(pack, plan);
		}

		locks.put(player.getUUID(), learn(pack, plan));
		this.setDirty();
		pack.setChanged();
		return laidOut;
	}

	/**
	 * The opening layout, for a hotbar nobody has told us anything about.
	 *
	 * <p>Tools first, from the left, in reaching order, and only the ones actually owned - a
	 * player with no hoe gets their shovel a slot earlier rather than a gap where a hoe would go.
	 */
	private static void arrange(Inventory pack) {
		int next = 0;

		for (HotbarRole role : HotbarRole.values()) {
			if (role == HotbarRole.FOOD) continue;

			// Searched from the first unsettled slot, so a swap can only ever displace something
			// this pass has not placed yet.
			int from = bestFor(pack, role, next);
			if (from < 0) continue;
			swap(pack, next++, from);
		}

		int torch = firstOf(pack, TORCH, next);
		if (torch >= 0 && next < FOOD_SLOT) swap(pack, next, torch);

		int food = bestFor(pack, HotbarRole.FOOD, next);
		if (food >= 0) swap(pack, FOOD_SLOT, food);
	}

	/** Bring the held slots back up to what they are held for, filling only. */
	private static void refill(Inventory pack, List<Lock> plan) {
		for (int slot = 0; slot < Math.min(plan.size(), Inventory.SELECTION_SIZE); slot++) {
			Lock lock = plan.get(slot);
			if (lock.isFree()) continue;

			ItemStack there = pack.getItem(slot);

			if (lock.role() != null) {
				// Only ever into an empty slot. Swapping a worn pick for a fresher one would take
				// the tool out of somebody's hand between two swings.
				if (!there.isEmpty()) continue;

				int from = bestFor(pack, lock.role(), Inventory.SELECTION_SIZE);
				if (from >= 0) swap(pack, slot, from);
				continue;
			}

			// An occupied slot needs nothing here: topping part-used stacks up is the sort's own
			// job, and it does it for every hotbar slot whether it is held for anything or not.
			if (!there.isEmpty()) continue;

			int from = firstOf(pack, lock.item(), Inventory.SELECTION_SIZE);
			if (from >= 0) swap(pack, slot, from);
		}
	}

	/** Read the hotbar back: every slot says what it is for, and an empty one keeps its old word. */
	private static List<Lock> learn(Inventory pack, List<Lock> previous) {
		List<Lock> plan = new ArrayList<>(Inventory.SELECTION_SIZE);

		for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
			ItemStack stack = pack.getItem(slot);

			if (stack.isEmpty()) {
				plan.add(previous != null && slot < previous.size() ? previous.get(slot) : Lock.FREE);
				continue;
			}

			HotbarRole role = HotbarRole.of(stack);
			plan.add(role != null ? new Lock(role, null) : new Lock(null, idOf(stack)));
		}
		return plan;
	}

	/** Whichever of these fills the role best, searching from {@code first} to the end of the pack. */
	private static int bestFor(Inventory pack, HotbarRole role, int first) {
		int best = -1;
		long bestScore = Long.MIN_VALUE;

		for (int slot = first; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = pack.getItem(slot);
			if (!role.matches(stack)) continue;

			long score = role.score(stack);
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}
		return best;
	}

	private static int firstOf(Inventory pack, String id, int from) {
		for (int slot = from; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = pack.getItem(slot);
			if (!stack.isEmpty() && idOf(stack).equals(id)) return slot;
		}
		return -1;
	}

	/** Swap rather than move, so whatever was in the way keeps a home. */
	private static void swap(Inventory pack, int a, int b) {
		if (a == b) return;
		ItemStack held = pack.getItem(a);
		pack.setItem(a, pack.getItem(b));
		pack.setItem(b, held);
	}

	private static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static HotbarLocks fromEntries(List<Entry> entries) {
		HotbarLocks data = new HotbarLocks();
		for (Entry entry : entries) data.locks.put(entry.player(), entry.locks());
		return data;
	}

	private static List<Entry> toEntries(HotbarLocks data) {
		List<Entry> entries = new ArrayList<>(data.locks.size());
		data.locks.forEach((player, plan) -> entries.add(new Entry(player, plan)));
		return entries;
	}
}
