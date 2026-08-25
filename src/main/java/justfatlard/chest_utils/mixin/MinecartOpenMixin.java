package justfatlard.chest_utils.mixin;

import justfatlard.chest_utils.screen.ChestScreens;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A chest on wheels is still a chest, and gets the same buttons.
 *
 * <p>The block mixin next door cannot reach these. A minecart is an entity: it answers
 * {@code interact} rather than {@code useWithoutItem}, it has no block state and no block entity,
 * and its inventory hangs off the vehicle itself. Everything downstream is the same, though - it
 * is a {@link net.minecraft.world.Container} of nine columns, which is all the screen ever needed.
 *
 * <p><b>Yields to whoever got there first</b>, exactly as the block mixin does: loot-ender serves
 * a player their own copy of a loot cart from this same method, and a second screen opened over
 * the top of that would be this mod's idea of help. It hands its copy to the take-only screen and
 * gets the buttons that suit it, so nothing is lost by standing aside.
 */
// Above the default, which in mixin's reckoning means later: whoever wants a particular cart
// answers before the mod that just wants every cart.
@Mixin(value = AbstractMinecartContainer.class, priority = 1500)
public abstract class MinecartOpenMixin {

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true, require = 1)
	private void chestUtils$openWithButtons(Player player, InteractionHand hand, Vec3 hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.isCancelled()) return;

		AbstractMinecartContainer self = (AbstractMinecartContainer) (Object) this;
		if (self.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
		if (player.isSpectator()) return;

		int rows = self.getContainerSize() / 9;
		if (rows <= 0) return;

		// Vanilla unpacks a loot table on the first look. Left to itself that is fine, but it must
		// happen before the screen reads the container, or the player is shown an empty cart and
		// the loot arrives behind the screen they are already looking at.
		self.unpackChestVehicleLootTable(player);

		ChestScreens.open(serverPlayer, self, self.getDisplayName(), rows);
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
