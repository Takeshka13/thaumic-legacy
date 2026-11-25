package dev.thaumiclegacy.infusion;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.multiblock.MultiblockPattern;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class Infusion {
	static final GameRules.Key<GameRules.IntegerValue> RISK = GameRuleRegistry.register(
			"infusionRiskPerPoint", GameRules.Category.MISC, GameRuleFactory.createIntRule(2, 0, 10));
	static final GameRules.Key<GameRules.IntegerValue> FLUX = GameRuleRegistry.register(
			"infusionAccidentFlux", GameRules.Category.MISC, GameRuleFactory.createIntRule(1, 0, 10));

	public static final ResourceLocation RESEARCH = ThaumicLegacy.id("infusion_foundations");
	public static final Block STONE = block("arcane_stone", new Block(properties()));
	public static final Block BRICKS = block("arcane_stone_bricks", new Block(properties()));
	public static final Block PEDESTAL = block("arcane_pedestal", new PedestalBlock(properties()));
	public static final Block MATRIX = block("runic_matrix", new RunicMatrixBlock(properties()
			.lightLevel(state -> state.getValue(RunicMatrixBlock.FORMED) ? 12 : 4)));
	public static final BlockEntityType<PedestalEntity> PEDESTAL_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("arcane_pedestal"),
			FabricBlockEntityTypeBuilder.create(PedestalEntity::new, PEDESTAL).build());
	public static final BlockEntityType<RunicMatrixEntity> MATRIX_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("runic_matrix"),
			FabricBlockEntityTypeBuilder.create(RunicMatrixEntity::new, MATRIX).build());
	public static final RecipeType<InfusionRecipe> RECIPE_TYPE = Registry.register(BuiltInRegistries.RECIPE_TYPE,
			ThaumicLegacy.id("infusion"), new RecipeType<>() {});
	public static final RecipeSerializer<InfusionRecipe> RECIPE_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, ThaumicLegacy.id("infusion"), new InfusionRecipe.Serializer());

	// Opposite positions are adjacent in this list. The central pedestal is two blocks below the matrix.
	static final List<BlockPos> RING = List.of(new BlockPos(3, -2, 0), new BlockPos(-3, -2, 0),
			new BlockPos(0, -2, 3), new BlockPos(0, -2, -3), new BlockPos(2, -2, 2),
			new BlockPos(-2, -2, -2), new BlockPos(2, -2, -2), new BlockPos(-2, -2, 2));
	static final MultiblockPattern PATTERN = pattern();

	private Infusion() {}

	public static void register() {}

	public static boolean inputPort(BlockState state, Direction face) {
		return state.is(MATRIX) && face.getAxis().isHorizontal();
	}

	public static InteractionResult useWand(ServerPlayer player, ItemStack wand, BlockPos pos) {
		var level = player.serverLevel();
		if (player.isSpectator() || !level.mayInteract(player, pos)
				|| !(level.getBlockEntity(pos) instanceof RunicMatrixEntity matrix)) {
			return InteractionResult.FAIL;
		}
		matrix.useWand(level, player, wand);
		return InteractionResult.CONSUME;
	}

	private static MultiblockPattern pattern() {
		var cells = new ArrayList<MultiblockPattern.Cell>();
		cells.add(new MultiblockPattern.Cell(0, -2, 0, state -> state.is(PEDESTAL)));
		cells.add(new MultiblockPattern.Cell(0, -1, 0, state -> state.isAir()));
		for (int x : new int[] {-1, 1}) {
			for (int z : new int[] {-1, 1}) {
				cells.add(new MultiblockPattern.Cell(x, -2, z, state -> state.is(BRICKS)));
				cells.add(new MultiblockPattern.Cell(x, -1, z, state -> state.is(STONE)));
			}
		}
		for (BlockPos offset : RING.subList(0, 4)) {
			cells.add(new MultiblockPattern.Cell(offset.getX(), offset.getY(), offset.getZ(), state -> state.is(PEDESTAL)));
		}
		return new MultiblockPattern(cells);
	}

	private static BlockBehaviour.Properties properties() {
		return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS);
	}

	private static Block block(String name, Block block) {
		var id = ThaumicLegacy.id(name);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
		return block;
	}
}
