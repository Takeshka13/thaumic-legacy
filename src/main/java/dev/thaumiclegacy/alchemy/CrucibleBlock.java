package dev.thaumiclegacy.alchemy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrucibleBlock extends BaseEntityBlock {
	public static final EnumProperty<Phase> PHASE = EnumProperty.create("phase", Phase.class);
	private static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);
	private static final VoxelShape BASIN = Block.box(1, 0, 1, 15, 14, 15);

	CrucibleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(PHASE, Phase.EMPTY));
	}

	@Override
	protected MapCodec<CrucibleBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return BASIN;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PHASE);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrucibleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, Alchemy.CRUCIBLE_ENTITY, CrucibleBlockEntity::tick) : null;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
		if (level instanceof ServerLevel server && !replacement.is(this)
				&& level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
			crucible.releaseResidue(server);
		}
		super.onRemove(state, level, pos, replacement, moving);
	}

	public enum Phase implements StringRepresentable {
		EMPTY("empty"), FILLED("filled"), HEATING("heating"), READY("ready");

		private final String id;

		Phase(String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return id;
		}
	}
}
