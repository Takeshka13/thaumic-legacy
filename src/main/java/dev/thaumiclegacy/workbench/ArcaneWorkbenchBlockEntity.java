package dev.thaumiclegacy.workbench;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ArcaneWorkbenchBlockEntity extends BlockEntity implements MenuProvider {
	static final int GRID_WIDTH = 3;
	static final int GRID_SIZE = GRID_WIDTH * GRID_WIDTH;
	private long revision;
	private final SimpleContainer grid = new SimpleContainer(GRID_SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			revision++;
			ArcaneWorkbenchBlockEntity.this.setChanged();
		}
	};

	ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) {
		super(ArcaneWorkbench.BLOCK_ENTITY_TYPE, pos, state);
	}

	SimpleContainer grid() {
		return grid;
	}

	long revision() {
		return revision;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		grid.getItems().clear();
		ContainerHelper.loadAllItems(tag, grid.getItems(), registries);
		revision++;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, grid.getItems(), registries);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(ArcaneWorkbench.BLOCK.getDescriptionId());
	}

	@Override
	public ArcaneWorkbenchMenu createMenu(int id, Inventory inventory, Player player) {
		return new ArcaneWorkbenchMenu(id, inventory, this);
	}
}
