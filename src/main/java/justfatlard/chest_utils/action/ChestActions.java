package justfatlard.chest_utils.action;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * <p>Stack limits are read from the container rather than the item. A chest on this server holds
 * more than sixty-four of a thing, and merging to the item's own limit would quietly split every
 * pile back down to vanilla sizes - a sort that costs you slots is not a tidy-up.
 */
public final class ChestActions {
	private ChestActions() {}

	/**
	 * Merge and order a container in place.
	 *
	 * <p>By registry id, so the order is the same every time and two chests of the same things
	 * read the same way. Sorting by display name would put the order at the mercy of the language
	 * the server happens to be running in.
	 */
	public static boolean sort(Container container) {
		List<ItemStack> gathered = new ArrayList<>();

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) gathered.add(stack.copy());
		}
		if (gathered.isEmpty()) return false;

		List<ItemStack> merged = merge(container, gathered);
		merged.sort(Comparator
			.comparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
			.thenComparing(ItemStack::getCount, Comparator.reverseOrder()));

		boolean changed = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack next = slot < merged.size() ? merged.get(slot) : ItemStack.EMPTY;
			if (!ItemStack.matches(container.getItem(slot), next)) changed = true;
			container.setItem(slot, next);
		}
		if (changed) container.setChanged();
		return changed;
	}

	/**
	 * Tip the chest into the player's pack.
	 *
	 * <p>The same move as {@link #dump} with the ends swapped, named separately because that is
	 * what the button says and a reader should not have to work out which way round the arguments
	 * went.
	 */
	public static boolean empty(Container chest, Container pack) {
		return move(chest, pack, null);
	}

	/** Everything that will fit, from one container into another. */
	public static boolean dump(Container from, Container into) {
		return move(from, into, null);
	}

	/**
	 * Only the kinds already in there, from one container into another.
	 *
	 * <p>The one that gets used most: back from a trip, drop off what belongs in this chest and
	 * keep the rest. Deciding what belongs by what is already there needs no configuring and no
	 * filters to maintain.
	 */
	public static boolean topUp(Container from, Container into) {
		Set<Item> wanted = new HashSet<>();
		for (int slot = 0; slot < into.getContainerSize(); slot++) {
			ItemStack stack = into.getItem(slot);
			if (!stack.isEmpty()) wanted.add(stack.getItem());
		}
		return wanted.isEmpty() ? false : move(from, into, wanted);
	}

	private static boolean move(Container from, Container into, Set<Item> only) {
		boolean moved = false;

		for (int slot = 0; slot < from.getContainerSize(); slot++) {
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
