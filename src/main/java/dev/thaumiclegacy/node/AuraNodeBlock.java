package dev.thaumiclegacy.node;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AuraNodeBlock extends BaseEntityBlock {
	public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
	private static final MapCodec<AuraNodeBlock> CODEC = simpleCodec(AuraNodeBlock::new);
	private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);

	AuraNodeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(VARIANT, Variant.NORMAL));
	}

	@Override
	protected MapCodec<AuraNodeBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(VARIANT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return CORE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AuraNodeBlockEntity(pos, state);
	}

	public enum Variant implements StringRepresentable {
		NORMAL("normal", 6, 12, 1_200),
		BRIGHT("bright", 12, 20, 600),
		UNSTABLE("unstable", 4, 12, 1_800);

		private final String id;
		private final int minCapacity;
		private final int maxCapacity;
		private final int recoveryTicks;

		Variant(String id, int minCapacity, int maxCapacity, int recoveryTicks) {
			this.id = id;
			this.minCapacity = minCapacity;
			this.maxCapacity = maxCapacity;
			this.recoveryTicks = recoveryTicks;
		}

		@Override
		public String getSerializedName() {
			return id;
		}

		public int minCapacity() {
			return minCapacity;
		}

		public int maxCapacity() {
			return maxCapacity;
		}

		public int recoveryTicks() {
			return recoveryTicks;
		}
	}
}
