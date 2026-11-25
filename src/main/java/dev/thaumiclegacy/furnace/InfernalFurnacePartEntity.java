package dev.thaumiclegacy.furnace;

import dev.thaumiclegacy.advanced.AdvancedEssentiaPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.BooleanSupplier;

final class InfernalFurnacePartEntity extends BlockEntity implements AdvancedEssentiaPort {
	private BlockPos controller;

	InfernalFurnacePartEntity(BlockPos pos, BlockState state) {
		super(InfernalFurnace.PART_ENTITY, pos, state);
	}

	void bind(BlockPos pos) {
		controller = pos.immutable();
		setChanged();
	}

	boolean belongsTo(BlockPos pos) {
		return pos.equals(controller);
	}

	static void tick(Level level, BlockPos pos, BlockState state, InfernalFurnacePartEntity part) {
		if (level instanceof ServerLevel server && level.getGameTime() % 20 == 0 && part.controller != null
				&& level.hasChunkAt(part.controller) && part.controller(server) == null) {
			InfernalFurnace.restorePart(server, pos, state);
		}
	}

	private InfernalFurnaceBlockEntity controller(ServerLevel server) {
		return controller != null && server.hasChunkAt(controller)
				&& server.getBlockEntity(controller) instanceof InfernalFurnaceBlockEntity furnace
				? furnace : null;
	}

	@Override
	public boolean inputPort(Direction face) {
		return InfernalFurnace.inputPort(getBlockState(), face);
	}

	@Override
	public boolean outputPort(Direction face) {
		return false;
	}

	@Override
	public Map<String, Integer> transportContents(Direction face) {
		return Map.of();
	}

	@Override
	public boolean canReceive(Direction face, String aspect) {
		if (!inputPort(face) || !(level instanceof ServerLevel server)) {
			return false;
		}
		var furnace = controller(server);
		return furnace != null && furnace.canReceive(server, aspect);
	}

	@Override
	public boolean receiveOne(Direction face, String aspect, BooleanSupplier debit) {
		return canReceive(face, aspect) && controller((ServerLevel) level).receive(debit);
	}

	@Override
	public boolean extractOne(Direction face, String aspect) {
		return false;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (controller != null) {
			tag.putLong("controller", controller.asLong());
		}
	}
}
