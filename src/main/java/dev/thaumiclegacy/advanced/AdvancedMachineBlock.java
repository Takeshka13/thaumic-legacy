package dev.thaumiclegacy.advanced;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public abstract class AdvancedMachineBlock extends BaseEntityBlock {
	public static final BooleanProperty WORKING = BooleanProperty.create("working");

	private AdvancedMachineBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(WORKING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WORKING);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		var stack = new ItemStack(this);
		var entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (entity != null) {
			entity.saveToItem(stack, params.getLevel().registryAccess());
		}
		return List.of(stack);
	}

	static final class Centrifuge extends AdvancedMachineBlock {
		private static final MapCodec<Centrifuge> CODEC = simpleCodec(Centrifuge::new);

		Centrifuge(Properties properties) {
			super(properties);
		}

		@Override
		protected MapCodec<Centrifuge> codec() {
			return CODEC;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new AlchemicalCentrifugeBlockEntity(pos, state);
		}

		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level instanceof ServerLevel
					? createTickerHelper(type, AdvancedAlchemy.CENTRIFUGE_ENTITY, AlchemicalCentrifugeBlockEntity::tick) : null;
		}
	}

	static final class Thaumatorium extends AdvancedMachineBlock {
		private static final MapCodec<Thaumatorium> CODEC = simpleCodec(Thaumatorium::new);

		Thaumatorium(Properties properties) {
			super(properties);
		}

		@Override
		protected MapCodec<Thaumatorium> codec() {
			return CODEC;
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new ThaumatoriumBlockEntity(pos, state);
		}

		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level instanceof ServerLevel
					? createTickerHelper(type, AdvancedAlchemy.THAUMATORIUM_ENTITY, ThaumatoriumBlockEntity::tick) : null;
		}
	}
}
