package justfatlard.chest_utils.gametest;

import justfatlard.pandorical.gametest.Pictures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The pictures for the readme and the mod page: a chest open on this mod's screen, its row of
 * buttons along the top, holding the jumble a chest holds after a day's mining.
 */
public final class Showcase implements FabricClientGameTest {
	private static final long SEED = 20261005L;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = Pictures.world(context, SEED)) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			Pictures.stage(context, server, Pictures.MORNING);

			BlockPos chest = server.computeOnServer(s -> {
				BlockPos spawn = connection.getServerPlayer().blockPosition();
				return Pictures.dryGround(s.overworld(), spawn.getX(), spawn.getZ(), 16);
			});
			check(chest != null, "no dry ground near spawn");
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
				ChestBlockEntity box = (ChestBlockEntity) level.getBlockEntity(chest);
				ItemStack[] haul = {
					new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.RAW_IRON, 23), new ItemStack(Items.COAL, 41),
					new ItemStack(Items.TORCH, 17), new ItemStack(Items.COBBLESTONE, 37), new ItemStack(Items.DEEPSLATE, 64),
					new ItemStack(Items.REDSTONE, 12), new ItemStack(Items.OAK_LOG, 9), new ItemStack(Items.RAW_COPPER, 30),
					new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.BREAD, 6), new ItemStack(Items.GRAVEL, 19),
					new ItemStack(Items.FLINT, 4), new ItemStack(Items.DEEPSLATE, 28), new ItemStack(Items.LAPIS_LAZULI, 15),
					new ItemStack(Items.STRING, 5), new ItemStack(Items.BONE, 8), new ItemStack(Items.RAW_GOLD, 7)};
				int[] slots = {0, 2, 3, 5, 8, 10, 11, 13, 14, 16, 18, 19, 21, 22, 24, 25, 26, 7};
				for (int i = 0; i < haul.length; i++) box.setItem(slots[i], haul[i]);
				box.setChanged();
			});

			// The player stands at the chest to open it, which a spectator cannot do, carrying a
			// miner's kit so their half of the screen is not empty either.
			server.runCommand("gamemode creative @a");
			server.runOnServer(s -> {
				var pack = connection.getServerPlayer().getInventory();
				ItemStack[] kit = {new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.TORCH, 48),
					new ItemStack(Items.COOKED_BEEF, 12), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.COBBLESTONE, 64),
					new ItemStack(Items.LADDER, 22), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.STONE_SWORD)};
				for (int i = 0; i < kit.length; i++) pack.setItem(i, kit[i]);
				pack.setItem(12, new ItemStack(Items.RAW_IRON, 14));
				pack.setItem(20, new ItemStack(Items.ANDESITE, 33));
			});
			Pictures.look(server, Vec3.atBottomCenterOf(chest).add(0, 0, 2.5), Vec3.atCenterOf(chest));
			Pictures.settle(context, connection);
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				var state = s.overworld().getBlockState(chest);
				state.useWithoutItem(s.overworld(), player,
					new BlockHitResult(Vec3.atCenterOf(chest), Direction.SOUTH, chest, false));
			});
			context.waitFor(client -> client.gui.screen() != null);
			context.waitTicks(10);
			Pictures.shootScreen(context, connection, "chest-buttons");
		}
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
