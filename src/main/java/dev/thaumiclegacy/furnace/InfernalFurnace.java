package dev.thaumiclegacy.furnace;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.multiblock.MultiblockPattern;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.wand.WandVis;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class InfernalFurnace {
	public static final WandVis ACTIVATION_COST = new WandVis(0, 0, 12, 12, 0, 0);
	static final MultiblockPattern PATTERN = createPattern();
	public static final Block BLOCK = Registry.register(BuiltInRegistries.BLOCK, ThaumicLegacy.id("infernal_furnace"),
			new InfernalFurnaceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS)
					.pushReaction(PushReaction.BLOCK).lightLevel(state -> 8)));
	static final BlockEntityType<InfernalFurnaceBlockEntity> ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("infernal_furnace"),
			FabricBlockEntityTypeBuilder.create(InfernalFurnaceBlockEntity::new, BLOCK).build());

	static final int REPLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	static final Block PART = Registry.register(BuiltInRegistries.BLOCK, ThaumicLegacy.id("infernal_furnace_part"),
			new InfernalFurnacePart(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
					.pushReaction(PushReaction.BLOCK).lightLevel(state -> state.getValue(InfernalFurnacePart.MATERIAL) == 2 ? 12 : 4)));
	static final BlockEntityType<InfernalFurnacePartEntity> PART_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("infernal_furnace_part"),
			FabricBlockEntityTypeBuilder.create(InfernalFurnacePartEntity::new, PART).build());
	public static final Block BELLOWS = Registry.register(BuiltInRegistries.BLOCK, ThaumicLegacy.id("arcane_bellows"),
			new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)));

	public static final Item BELLOWS_ITEM = Registry.register(BuiltInRegistries.ITEM, ThaumicLegacy.id("arcane_bellows"),
			new BlockItem(BELLOWS, new Item.Properties()));

	private InfernalFurnace() {
	}

	public static void register() {
	}

	public static InteractionResult activate(ServerPlayer player, ItemStack wand, BlockPos pos, Direction front) {
		var level = player.serverLevel();
		if (!front.getAxis().isHorizontal() || player.isSpectator() || !player.isAlive()
				|| !level.hasChunkAt(pos) || !level.mayInteract(player, pos)
				|| !level.getBlockState(pos).is(Blocks.IRON_BARS)) {
			return InteractionResult.PASS;
		}
		if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) {
			return InteractionResult.PASS;
		}
		var match = PATTERN.check(level, pos, front);
		if (match == MultiblockPattern.Match.INVALID) {
			return reject(player, "invalid");
		}
		if (match == MultiblockPattern.Match.UNLOADED) {
			return reject(player, "unloaded");
		}
		if (!ResearchProgress.isUnlocked(player, ResearchProgress.INFERNAL_FURNACE)) {
			return reject(player, "research");
		}
		if (!WandVis.canSpend(wand, ACTIVATION_COST)) {
			return reject(player, "charge");
		}
		for (var cell : PATTERN.cells()) {
			if (!level.mayInteract(player, cell.position(pos, front))) {
				return reject(player, "invalid");
			}
		}
		var originals = new LinkedHashMap<BlockPos, BlockState>();
		originals.put(pos, level.getBlockState(pos));
		if (!level.setBlock(pos, BLOCK.defaultBlockState().setValue(InfernalFurnaceBlock.FACING, front), REPLACE_FLAGS)
				|| !(level.getBlockEntity(pos) instanceof InfernalFurnaceBlockEntity furnace)
				|| !formParts(level, pos, front, originals) || !WandVis.trySpend(wand, ACTIVATION_COST)) {
			rollback(level, originals);
			return reject(player, "invalid");
		}
		furnace.formed();
		updateNeighbors(level, originals);
		message(player, "formed");
		return InteractionResult.CONSUME;
	}

	static boolean migrate(ServerLevel level, BlockPos origin, Direction front) {
		var originals = new LinkedHashMap<BlockPos, BlockState>();
		if (!formParts(level, origin, front, originals)) {
			rollback(level, originals);
			return false;
		}
		updateNeighbors(level, originals);
		return true;
	}

	private static boolean formParts(ServerLevel level, BlockPos origin, Direction front, Map<BlockPos, BlockState> originals) {
		for (var cell : PATTERN.cells()) {
			BlockPos pos = cell.position(origin, front);
			BlockState old = level.getBlockState(pos);
			if (old.isAir()) {
				continue;
			}
			originals.put(pos, old);
			int material = old.is(Blocks.LAVA) ? 2 : old.is(Blocks.OBSIDIAN) ? 1 : 0;
			BlockState part = PART.defaultBlockState().setValue(InfernalFurnacePart.MATERIAL, material)
					.setValue(InfernalFurnacePart.INLET, pos.equals(origin.above()))
					.setValue(InfernalFurnaceBlock.FACING, front);
			if (!level.setBlock(pos, part, REPLACE_FLAGS)
					|| !(level.getBlockEntity(pos) instanceof InfernalFurnacePartEntity entity)) {
				return false;
			}
			entity.bind(origin);
		}
		return true;
	}

	private static void rollback(ServerLevel level, LinkedHashMap<BlockPos, BlockState> originals) {
		originals.reversed().forEach((pos, state) -> level.setBlock(pos, state, REPLACE_FLAGS));
		updateNeighbors(level, originals);
	}

	private static void updateNeighbors(ServerLevel level, Map<BlockPos, BlockState> originals) {
		originals.keySet().forEach(pos -> level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock()));
	}

	static void restorePart(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, InfernalFurnacePart.original(state), Block.UPDATE_ALL);
	}

	static void restoreParts(ServerLevel level, BlockPos origin, Direction front) {
		for (var cell : PATTERN.cells()) {
			BlockPos pos = cell.position(origin, front);
			if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof InfernalFurnacePartEntity part
					&& part.belongsTo(origin)) {
				restorePart(level, pos, level.getBlockState(pos));
			}
		}
	}

	public static boolean inputPort(BlockState state, Direction face) {
		return state.is(PART) && state.getValue(InfernalFurnacePart.INLET)
				&& state.getValue(InfernalFurnaceBlock.FACING) == face;
	}

	private static InteractionResult reject(ServerPlayer player, String key) {
		message(player, key);
		return InteractionResult.FAIL;
	}

	static void message(ServerPlayer player, String key, Object... arguments) {
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.infernal_furnace." + key, arguments), true);
	}

	private static MultiblockPattern createPattern() {
		var cells = new ArrayList<MultiblockPattern.Cell>();
		for (int back = 0; back < 3; back++) {
			for (int right = -1; right <= 1; right++) {
				boolean center = back == 1 && right == 0;
				cells.add(new MultiblockPattern.Cell(right, -1, back, state -> state.is(Blocks.NETHER_BRICKS)));
				cells.add(new MultiblockPattern.Cell(right, 1, back,
						center ? BlockState::isAir : state -> state.is(Blocks.NETHER_BRICKS)));
				if (back == 0 && right == 0) {
					continue;
				}
				Predicate<BlockState> middle = center
						? state -> state.is(Blocks.LAVA) && state.getFluidState().isSource()
						: state -> state.is(Blocks.OBSIDIAN);
				cells.add(new MultiblockPattern.Cell(right, 0, back, middle));
			}
		}
		return new MultiblockPattern(cells);
	}
}
