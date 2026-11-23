package dev.thaumiclegacy.workbench;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class ArcaneWorkbenchBlock extends BaseEntityBlock {
	private static final MapCodec<ArcaneWorkbenchBlock> CODEC = simpleCodec(ArcaneWorkbenchBlock::new);

	ArcaneWorkbenchBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<ArcaneWorkbenchBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ArcaneWorkbenchBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity workbench) {
			if (!level.isClientSide()) {
				player.openMenu(workbench);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
		if (!level.isClientSide() && !state.is(replacement.getBlock())
				&& level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity workbench) {
			Containers.dropContents(level, pos, workbench.grid());
			workbench.grid().clearContent();
		}
		super.onRemove(state, level, pos, replacement, moving);
	}
}
