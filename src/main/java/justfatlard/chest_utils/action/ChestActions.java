package justfatlard.chest_utils.action;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The four things a chest should always have been able to do.
 *
 * <p>Plain operations over {@link Container}s, holding no state and knowing nothing about screens
 * or players, so the same code answers a button, a keybind, or a test. Each returns whether it
 * changed anything, which is what decides if the screen needs redrawing.
 *
 * <p><b>Everything takes a range.</b> A player's inventory is not thirty-six slots, it is
 * thirty-six plus what they are wearing and holding, all in one container. Working over the whole
 * of it sorts a helmet off somebody's head and files it under m, and dumps their boots into a
 * chest. The caller says which part is fair game.
 *
 * <p>Stack limits are read from the container rather than the item. A chest on this server holds
 * more than sixty-four of a thing, and merging to the item's own limit would quietly split every
 * pile back down to vanilla sizes - a sort that costs you slots is not a tidy-up.
 */
public final class ChestActions {
	private ChestActions() {}

	/** Merge and order a whole container. */
	public static boolean sort(Container container) {
		return sort(container, 0, container.getContainerSize());
	}

	/**
	 * Merge and order part of a container, in place.
	 *
	 * <p>By registry id, so the order is the same every time and two chests of the same things
	 * read the same way. Sorting by display name would put the order at the mercy of whichever
	 * language the server happens to be running in.
	 */
	public static boolean sort(Container container, int from, int to) {
		List<ItemStack> gathered = new ArrayList<>();

		for (int slot = from; slot < to; slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) gathered.add(stack.copy());
		}
		if (gathered.isEmpty()) return false;

		List<ItemStack> merged = merge(container, gathered);
		merged.sort(Comparator
			.comparingInt((ItemStack s) -> ItemOrder.of(s.getItem()))
			.thenComparing(s -> ItemOrder.nameOf(s.getItem()))
			.thenComparing(ItemStack::getCount, Comparator.reverseOrder()));

		boolean changed = false;
		for (int slot = from; slot < to; slot++) {
			int index = slot - from;
			ItemStack next = index < merged.size() ? merged.get(index) : ItemStack.EMPTY;
			if (!ItemStack.matches(container.getItem(slot), next)) changed = true;
			container.setItem(slot, next);
		}
		if (changed) container.setChanged();
		return changed;
	}

	/**
	 * Sort a player's pack, leaving the hotbar as they arranged it.
	 *
	 * <p>The hotbar is not storage, it is a set of controls. Where somebody keeps their pickaxe
	 * is a decision they made once and now reach for without looking, and a tidy-up that files it
	 * under p has broken something no amount of neatness pays for. Only the three rows above it
	 * are rearranged; the armour and the offhand are outside the range entirely.
	 */
	public static boolean sortPack(Inventory pack) {
		// Top up first, then sort what is left. The other way round would sort the rows, then
		// pull items out of them to fill the hotbar, and leave gaps in a freshly tidied grid.
		boolean topped = refillHotbar(pack);
		boolean sorted = sort(pack, Inventory.SELECTION_SIZE, Inventory.INVENTORY_SIZE);
		return topped || sorted;
	}

	/**
	 * Fill the hotbar's part-used stacks from the rows above, without moving anything.
	 *
	 * <p>Every sorting mod that rearranges the hotbar gets complaints about it, and the reason is
	 * that the hotbar is not storage - it is a set of controls. Which key the pickaxe is under
	 * was decided once and is now reached for without looking, and no amount of tidiness pays for
	 * breaking that.
	 *
	 * <p>But leaving it untouched gives up the thing people liked most about the old Inventory
	 * Tweaks: the refill. So nothing moves and nothing is reordered, and the half-empty stack of
	 * torches in slot three simply becomes a full one. Positions are the player's; quantities
	 * are the chore.
	 */
	private static boolean refillHotbar(Inventory pack) {
		boolean filled = false;

		for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
			ItemStack held = pack.getItem(slot);
			if (held.isEmpty()) continue;

			int cap = Math.min(pack.getMaxStackSize(held), held.getMaxStackSize());
			if (held.getCount() >= cap) continue;

			for (int from = Inventory.SELECTION_SIZE; from < Inventory.INVENTORY_SIZE; from++) {
				ItemStack spare = pack.getItem(from);
				if (spare.isEmpty() || !ItemStack.isSameItemSameComponents(spare, held)) continue;

				int room = cap - held.getCount();
				if (room <= 0) break;

				int taken = Math.min(room, spare.getCount());
				held.grow(taken);
				spare.shrink(taken);
				if (spare.isEmpty()) pack.setItem(from, ItemStack.EMPTY);
				filled = true;
			}
		}
		if (filled) pack.setChanged();
		return filled;
	}

	/** Everything in the range that will fit, into the other container. */
	public static boolean dump(Container from, int fromTo, Container into) {
		return move(from, fromTo, into, null);
	}

	/**
	 * Only the kinds already in the destination.
	 *
	 * <p>The one that gets used most, in both directions: coming back from a trip it drops off
	 * what belongs in this chest and keeps the rest; pointed the other way it refills the stacks
	 * already in your pack and leaves the rest of the loot where it is. Deciding what belongs by
	 * what is already there needs no configuring and no filters to maintain.
	 */
	public static boolean topUp(Container from, int fromTo, Container into) {
		Set<Item> wanted = new HashSet<>();
		for (int slot = 0; slot < into.getContainerSize(); slot++) {
			ItemStack stack = into.getItem(slot);
			if (!stack.isEmpty()) wanted.add(stack.getItem());
		}
		return !wanted.isEmpty() && move(from, fromTo, into, wanted);
	}

	private static boolean move(Container from, int fromTo, Container into, Set<Item> only) {
		boolean moved = false;

		for (int slot = 0; slot < fromTo; slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty()) continue;
			if (only != null && !only.contains(stack.getItem())) continue;

			ItemStack left = insert(into, stack);
			if (left.getCount() != stack.getCount()) {
				moved = true;
				from.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
			}
		}
		if (moved) {
			from.setChanged();
			into.setChanged();
		}
		return moved;
	}

	/** Fill existing piles first, then take empty slots. Whatever is left comes back. */
	private static ItemStack insert(Container into, ItemStack stack) {
		ItemStack remaining = stack.copy();

		for (int slot = 0; slot < into.getContainerSize() && !remaining.isEmpty(); slot++) {
			ItemStack there = into.getItem(slot);
			if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, remaining)) continue;

			int room = Math.min(into.getMaxStackSize(there), there.getMaxStackSize()) - there.getCount();
			if (room <= 0) continue;

			int taken = Math.min(room, remaining.getCount());
			there.grow(taken);
			remaining.shrink(taken);
		}

		for (int slot = 0; slot < into.getContainerSize() && !remaining.isEmpty(); slot++) {
			if (!into.getItem(slot).isEmpty()) continue;
			into.setItem(slot, remaining.copy());
			remaining = ItemStack.EMPTY;
		}

		return remaining;
	}

	private static List<ItemStack> merge(Container container, List<ItemStack> stacks) {
		List<ItemStack> merged = new ArrayList<>();

		for (ItemStack stack : stacks) {
			for (ItemStack into : merged) {
				if (!ItemStack.isSameItemSameComponents(into, stack)) continue;

				int cap = Math.min(container.getMaxStackSize(into), into.getMaxStackSize());
				int room = cap - into.getCount();
				if (room <= 0) continue;

				int taken = Math.min(room, stack.getCount());
				into.grow(taken);
				stack.shrink(taken);
				if (stack.isEmpty()) break;
			}
			if (!stack.isEmpty()) merged.add(stack);
		}
		return merged;
	}
}
