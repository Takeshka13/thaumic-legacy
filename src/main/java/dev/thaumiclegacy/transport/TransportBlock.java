package dev.thaumiclegacy.transport;

import com.mojang.serialization.MapCodec;
import dev.thaumiclegacy.research.ResearchProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public abstract class TransportBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final BooleanProperty CONFIGURED = BooleanProperty.create("configured");
	private static final VoxelShape CENTER = Block.box(6, 6, 6, 10, 10, 10);
	private static final VoxelShape CONTROL = Block.box(4, 4, 4, 12, 12, 12);

	TransportBlock(Properties properties) {
		super(properties);
	}

	static boolean hasPort(BlockState state, Direction face) {
		return state.getBlock() instanceof TransportBlock
				&& (!state.hasProperty(AXIS) || state.getValue(AXIS) == face.getAxis());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TransportBlockEntity(pos, state);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		return List.of(new ItemStack(this));
	}

	private static VoxelShape arm(Direction face) {
		return switch (face) {
			case DOWN -> Block.box(6, 0, 6, 10, 6, 10);
			case UP -> Block.box(6, 10, 6, 10, 16, 10);
			case NORTH -> Block.box(6, 6, 0, 10, 10, 6);
			case SOUTH -> Block.box(6, 6, 10, 10, 10, 16);
			case WEST -> Block.box(0, 6, 6, 6, 10, 10);
			case EAST -> Block.box(10, 6, 6, 16, 10, 10);
		};
	}

	static final class Tube extends TransportBlock {
		private static final MapCodec<Tube> CODEC = simpleCodec(Tube::new);

		Tube(Properties properties) {
			super(properties);
			BlockState initial = stateDefinition.any();
			for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
				initial = initial.setValue(property, false);
			}
			registerDefaultState(initial);
		}

		@Override
		protected MapCodec<Tube> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			var level = context.getLevel();
			var pos = context.getClickedPos();
			BlockState state = defaultBlockState();
			for (Direction face : Direction.values()) {
				BlockPos neighbor = pos.relative(face);
				state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face),
						level.hasChunkAt(neighbor) && EssentiaTransport.hasPort(level.getBlockState(neighbor), face.getOpposite()));
			}
			return state;
		}

		@Override
		protected BlockState updateShape(BlockState state, Direction face, BlockState neighbor,
									 LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
			return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face), EssentiaTransport.hasPort(neighbor, face.getOpposite()));
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			VoxelShape shape = CENTER;
			for (Direction face : Direction.values()) {
				if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face))) {
					shape = Shapes.or(shape, arm(face));
				}
			}
			return shape;
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
			if (placer instanceof ServerPlayer player) {
				ResearchProgress.recordTubePlacement(player);
			}
		}
	}

	private abstract static class Inline extends TransportBlock {
		Inline(Properties properties) {
			super(properties);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(AXIS);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(AXIS)) {
				case X -> Shapes.or(CONTROL, arm(Direction.WEST), arm(Direction.EAST));
				case Y -> Shapes.or(CONTROL, arm(Direction.DOWN), arm(Direction.UP));
				case Z -> Shapes.or(CONTROL, arm(Direction.NORTH), arm(Direction.SOUTH));
			};
		}
	}

	static final class Valve extends Inline {
		private static final MapCodec<Valve> CODEC = simpleCodec(Valve::new);

		Valve(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(OPEN, true));
		}

		@Override
		protected MapCodec<Valve> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(OPEN);
		}
	}

	static final class Filter extends Inline {
		private static final MapCodec<Filter> CODEC = simpleCodec(Filter::new);

		Filter(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(CONFIGURED, false));
		}

		@Override
		protected MapCodec<Filter> codec() {
			return CODEC;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(CONFIGURED);
		}
	}
}
