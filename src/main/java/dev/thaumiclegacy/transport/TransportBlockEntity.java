package dev.thaumiclegacy.transport;

import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TransportBlockEntity extends BlockEntity {
	private String filter = "";
	private EssentiaTransport.Activity activity;

	TransportBlockEntity(BlockPos pos, BlockState state) {
		super(EssentiaTransport.TRANSPORT_ENTITY, pos, state);
	}

	String filter() {
		return filter;
	}

	EssentiaTransport.Activity activity() {
		return activity;
	}

	void note(EssentiaTransport.Activity activity) {
		this.activity = activity;
	}

	boolean configure(String aspect) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || isRemoved()
				|| !getBlockState().is(EssentiaTransport.FILTER) || filter.equals(aspect)
				|| !aspect.isEmpty() && !AspectCatalog.current().definitions().containsKey(aspect)) {
			return false;
		}
		filter = aspect;
		activity = null;
		setChanged();
		BlockState state = getBlockState();
		boolean configured = !filter.isEmpty();
		if (state.getValue(TransportBlock.CONFIGURED) != configured) {
			server.setBlock(worldPosition, state.setValue(TransportBlock.CONFIGURED, configured), Block.UPDATE_CLIENTS);
		}
		return true;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		String id = tag.getString("filter");
		filter = getBlockState().is(EssentiaTransport.FILTER) && id.equals(AspectCatalog.normalizeId(id)) ? id : "";
		activity = null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (!filter.isEmpty()) {
			tag.putString("filter", filter);
		}
	}
}
