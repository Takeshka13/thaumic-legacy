package dev.thaumiclegacy.infusion;

import com.mojang.serialization.MapCodec;
import dev.thaumiclegacy.wand.WandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class RunicMatrixBlock extends BaseEntityBlock {
	static final BooleanProperty FORMED = BooleanProperty.create("formed");
	private static final MapCodec<RunicMatrixBlock> CODEC = simpleCodec(RunicMatrixBlock::new);
	private static final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 14, 14);

	RunicMatrixBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FORMED, false));
	}

	@Override
	protected MapCodec<RunicMatrixBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FORMED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RunicMatrixEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel && state.getValue(FORMED)
				? createTickerHelper(type, Infusion.MATRIX_ENTITY, RunicMatrixEntity::tick) : null;
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(stack.getItem() instanceof WandItem)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer user && level.mayInteract(user, pos)
				&& level.getBlockEntity(pos) instanceof RunicMatrixEntity matrix) {
			matrix.useWand(server, user, stack);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}
}
