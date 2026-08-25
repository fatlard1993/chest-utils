package justfatlard.chest_utils.mixin;

import justfatlard.chest_utils.screen.ChestScreens;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A chest on wheels is still a chest, and gets the same buttons.
 *
 * <p>The block mixin next door cannot reach these. A vehicle is an entity: it has no block state
 * and no block entity, and its inventory hangs off the vehicle itself. Everything downstream is
 * the same, though - it is a {@link net.minecraft.world.Container} of nine columns, which is all
 * the screen ever needed.
 *
 * <p><b>On the interface, where they converge.</b> The obvious target is the minecart's own
 * {@code interact}, and it is the wrong one: {@code MinecartChest} overrides it without calling
 * {@code super}, so an injection on the parent applies cleanly, satisfies {@code require = 1} by
 * finding the method, and then never runs. This method is the one every container vehicle
 * actually goes through to open itself.
 *
 * <p>Anything too small to be a chest excludes itself: a hopper minecart's five slots are not a
 * row of nine, and the row count comes out zero.
 *
 * <p><b>Yields to whoever got there first</b>, exactly as the block mixin does: loot-ender serves
 * a player their own copy of a loot cart from this same method, and a second screen opened over
 * the top of that would be this mod's idea of help.
 */
// Above the default, which in mixin's reckoning means later: whoever wants a particular vehicle
// answers before the mod that just wants every one of them.
@Mixin(value = ContainerEntity.class, priority = 1500)
public interface MinecartOpenMixin {

	@Inject(method = "interactWithContainerVehicle", at = @At("HEAD"), cancellable = true, require = 1)
	private void chestUtils$openWithButtons(Player player,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.isCancelled()) return;

		ContainerEntity self = (ContainerEntity) this;
		Entity entity = (Entity) this;
		if (entity.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
		if (player.isSpectator()) return;

		int rows = self.getContainerSize() / 9;
		if (rows <= 0) return;

		// Vanilla unpacks a loot table when the menu reads the container. That has to happen
		// before the screen does, or the player is shown an empty cart and the loot arrives
		// behind the screen they are already looking at.
		self.unpackChestVehicleLootTable(player);

		ChestScreens.open(serverPlayer, self, entity.getDisplayName(), rows);
		cir.setReturnValue(InteractionResult.SUCCESS);
	}
}
