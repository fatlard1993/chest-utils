package justfatlard.chest_utils.mixin;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The two container habits worth having: sweep the wheel to move a stack, drag to move a row.
 *
 * <p>Both are ordinary quick-moves - the same thing shift-clicking sends - so nothing here needs
 * the server's cooperation or invents a packet. That is the whole reason it can be done at all: the
 * server sees a player shift-clicking, quickly, and has no way to tell the difference.
 *
 * <p>Every container screen gets them, not only chests, because a screen is a screen and the habit
 * should not stop working at a furnace.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MouseTweaksMixin {

	@Shadow protected Slot hoveredSlot;

	@Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

	/**
	 * Read through the accessor rather than shadowing the field.
	 *
	 * <p>{@code menu} is a protected final of the screen's own generic type. Shadowing it means
	 * restating an access level and an erasure that both have to keep matching the game, and
	 * getting either wrong is a mixin that fails to apply - which for a client mixin is not a
	 * missing feature, it is a client that will not start.
	 */
	private AbstractContainerMenu chestutils$menu() {
		return ((AbstractContainerScreen<?>) (Object) this).getMenu();
	}

	/**
	 * Slots already swept during the current drag.
	 *
	 * <p>A drag reports a position every frame, so without this a slow hand over one slot sends a
	 * quick-move a dozen times - which is not merely wasteful, it empties the slot and then starts
	 * on whatever the container shuffles into its place.
	 */
	private final Set<Integer> chestutils$swept = new HashSet<>();

	/**
	 * The wheel pushes items the way it turns: up into the container, down into your own pockets.
	 *
	 * <p>Direction is read as a place on the screen rather than as a mode. The container is the top
	 * half and your inventory is the bottom, so scrolling up over something of yours sends it up,
	 * and scrolling down over something in the chest brings it down. Scrolling the way a stack has
	 * already gone does nothing, which is what makes the gesture safe to lean on: the wheel never
	 * reverses on you.
	 *
	 * <p>One item at a time, because that is the thing no other input can do. Whole stacks already
	 * have two gestures - shift-click for one, a left-drag for a row - so the wheel is left to be
	 * the precise tool rather than a third way to do the same bulk move.
	 */
	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void chestutils$wheelMoves(double mouseX, double mouseY, double scrollX, double scrollY,
			CallbackInfoReturnable<Boolean> cir) {
		if (scrollY == 0 || this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;
		// Not while something is on the cursor: the hand is mid-gesture, and taking a slot out from
		// under it would be a surprise.
		if (!chestutils$menu().getCarried().isEmpty()) return;

		// Scrolling a stack further in the direction it already lives has nowhere to go.
		if ((scrollY > 0) != chestutils$isPlayerSide(this.hoveredSlot)) return;

		chestutils$moveOne(this.hoveredSlot);
		cir.setReturnValue(true);
	}

	/**
	 * Dragging with an empty hand moves every slot the pointer crosses: stacks on the left button,
	 * one item each on the right.
	 *
	 * <p>The empty hand is what keeps this out of vanilla's way. Vanilla's own drag distributes the
	 * stack you are holding across the slots you touch, and it needs something held to do it - with
	 * nothing on the cursor that gesture means nothing at all today, so it is free to mean this.
	 * The two buttons then split the same way they do everywhere else in the game: left takes the
	 * lot, right takes one.
	 */
	@Inject(method = "mouseDragged", at = @At("HEAD"))
	private void chestutils$dragMoves(MouseButtonEvent event, double dragX, double dragY,
			CallbackInfoReturnable<Boolean> cir) {
		if (!chestutils$menu().getCarried().isEmpty()) return;
		if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

		boolean rightButton = event.button() == 1;
		if (!rightButton && event.button() != 0) return;
		if (!chestutils$swept.add(this.hoveredSlot.index)) return;

		if (rightButton) {
			chestutils$moveOne(this.hoveredSlot);
		} else {
			slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
		}
	}

	/**
	 * Move a single item out of this slot and across to the other side.
	 *
	 * <p>Three ordinary clicks, because there is no single one that means it: take the stack up,
	 * put one down where it is going, put the rest back. Exactly what a player would do by hand,
	 * which is the point - the server is being told a story it already knows how to read, and no
	 * step of it is anything a player could not have done themselves.
	 *
	 * <p>A stack of one still works: the third click lands on an empty slot with an empty hand and
	 * is simply nothing.
	 */
	private void chestutils$moveOne(Slot from) {
		Slot target = chestutils$landingSlot(from);
		if (target == null) return;

		slotClicked(from, from.index, 0, ContainerInput.PICKUP);
		slotClicked(target, target.index, 1, ContainerInput.PICKUP);
		slotClicked(from, from.index, 0, ContainerInput.PICKUP);
	}

	/**
	 * Where one item from this slot should land: the other side of the screen.
	 *
	 * <p>A slot already holding the same item with room to spare is preferred over an empty one, so
	 * a repeated gesture piles items up rather than scattering them across the grid.
	 */
	private Slot chestutils$landingSlot(Slot from) {
		boolean fromPlayer = chestutils$isPlayerSide(from);
		ItemStack moving = from.getItem();
		Slot empty = null;

		for (Slot candidate : chestutils$menu().slots) {
			if (chestutils$isPlayerSide(candidate) == fromPlayer) continue;
			if (!candidate.mayPlace(moving)) continue;

			ItemStack held = candidate.getItem();
			if (held.isEmpty()) {
				if (empty == null) empty = candidate;
				continue;
			}
			if (ItemStack.isSameItemSameComponents(held, moving)
					&& held.getCount() < candidate.getMaxStackSize(held)) {
				return candidate;
			}
		}
		return empty;
	}

	/** Which half of the screen a slot belongs to: the player's own bag, or whatever is open. */
	private static boolean chestutils$isPlayerSide(Slot slot) {
		return slot.container instanceof Inventory;
	}

	/** A new drag sweeps a clean set; otherwise the last one's slots stay immune to this one. */
	@Inject(method = "mouseReleased", at = @At("HEAD"))
	private void chestutils$endSweep(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		chestutils$swept.clear();
	}
}
