package dev.thaumiclegacy.logistics;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class EssentiaStorageBlock extends EssentiaMachineBlock {
	private static final MapCodec<EssentiaStorageBlock> CODEC = simpleCodec(EssentiaStorageBlock::new);
	private static final VoxelShape JAR = Block.box(3, 0, 3, 13, 14, 13);
	private static final VoxelShape ALEMBIC = Block.box(2, 0, 2, 14, 16, 14);

	EssentiaStorageBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<EssentiaStorageBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.is(EssentiaLogistics.WARDED_JAR) ? JAR : ALEMBIC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EssentiaStorageBlockEntity(pos, state);
	}
}
