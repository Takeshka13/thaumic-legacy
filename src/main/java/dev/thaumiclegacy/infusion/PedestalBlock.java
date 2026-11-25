package dev.thaumiclegacy.infusion;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class PedestalBlock extends BaseEntityBlock {
	private static final MapCodec<PedestalBlock> CODEC = simpleCodec(PedestalBlock::new);
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15),
			Block.box(4, 3, 4, 12, 12, 12), Block.box(1, 12, 1, 15, 16, 15));

	PedestalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<PedestalBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PedestalEntity(pos, state);
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty()) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (level instanceof ServerLevel && level.mayInteract(player, pos)
				&& level.getBlockEntity(pos) instanceof PedestalEntity pedestal && pedestal.stack().isEmpty()) {
			pedestal.setStack(stack.copyWithCount(1));
			stack.consume(1, player);
		}
		return ItemInteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && level.mayInteract(player, pos)
				&& level.getBlockEntity(pos) instanceof PedestalEntity pedestal) {
			var item = pedestal.stack();
			int count = item.getCount();
			if (!item.isEmpty()) {
				player.getInventory().add(item);
				if (item.getCount() != count) {
					pedestal.setStack(item);
				}
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
		if (!state.is(replacement.getBlock()) && level instanceof ServerLevel
				&& level.getBlockEntity(pos) instanceof PedestalEntity pedestal) {
			var item = pedestal.stack();
			pedestal.setStack(ItemStack.EMPTY);
			popResource(level, pos, item);
		}
		super.onRemove(state, level, pos, replacement, moving);
	}
}
