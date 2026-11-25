package dev.thaumiclegacy.infusion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class PedestalEntity extends BlockEntity {
	private ItemStack stack = ItemStack.EMPTY;
	private UUID revision = UUID.randomUUID();

	PedestalEntity(BlockPos pos, BlockState state) {
		super(Infusion.PEDESTAL_ENTITY, pos, state);
	}

	public ItemStack stack() {
		return stack.copy();
	}

	UUID revision() {
		return revision;
	}

	void setStack(ItemStack replacement) {
		stack = replacement.copy();
		revision = UUID.randomUUID();
		setChanged();
		if (level != null) {
			var state = getBlockState();
			level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		stack = ItemStack.parseOptional(registries, tag.getCompound("stack"));
		if (tag.hasUUID("revision")) {
			revision = tag.getUUID("revision");
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("stack", stack.saveOptional(registries));
		tag.putUUID("revision", revision);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		var tag = new CompoundTag();
		tag.put("stack", stack.saveOptional(registries));
		return tag;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
