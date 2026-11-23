package dev.thaumiclegacy.logistics;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

abstract class EssentiaMachineBlock extends BaseEntityBlock {
	EssentiaMachineBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		ItemStack drop = new ItemStack(this);
		var entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (entity != null) {
			// A single retained machine carries its inventory, timing and ledger back through standard placement.
			entity.saveToItem(drop, params.getLevel().registryAccess());
		}
		return List.of(drop);
	}
}
