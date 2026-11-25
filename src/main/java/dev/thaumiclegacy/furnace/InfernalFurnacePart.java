package dev.thaumiclegacy.furnace;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

final class InfernalFurnacePart extends BaseEntityBlock {
	static final IntegerProperty MATERIAL = IntegerProperty.create("material", 0, 2);
	static final BooleanProperty INLET = BooleanProperty.create("inlet");
	private static final MapCodec<InfernalFurnacePart> CODEC = simpleCodec(InfernalFurnacePart::new);

	InfernalFurnacePart(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(MATERIAL, 0).setValue(INLET, false)
				.setValue(InfernalFurnaceBlock.FACING, Direction.NORTH));
	}

	static BlockState original(BlockState state) {
		return switch (state.getValue(MATERIAL)) {
			case 1 -> Blocks.OBSIDIAN.defaultBlockState();
			case 2 -> Blocks.LAVA.defaultBlockState();
			default -> Blocks.NETHER_BRICKS.defaultBlockState();
		};
	}

	@Override
	protected MapCodec<InfernalFurnacePart> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MATERIAL, INLET, InfernalFurnaceBlock.FACING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InfernalFurnacePartEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel
				? createTickerHelper(type, InfernalFurnace.PART_ENTITY, InfernalFurnacePartEntity::tick) : null;
	}
}
