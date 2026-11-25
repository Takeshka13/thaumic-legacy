package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.revealing.RevealingPayload;

import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaPhial;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

abstract class AdvancedMachineEntity extends BlockEntity implements AdvancedEssentiaPort {
	AdvancedMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	abstract boolean interact(ServerPlayer player, InteractionHand hand);

	final ServerLevel attachedServer() {
		if (level instanceof ServerLevel server && server.getServer().isSameThread() && !isRemoved()
				&& server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this) {
			return server;
		}
		return null;
	}

	final boolean canUse(ServerPlayer player) {
		ServerLevel server = attachedServer();
		return server != null && player.serverLevel() == server && player.isAlive() && !player.isSpectator()
				&& player.canInteractWithBlock(worldPosition, 0) && server.mayInteract(player, worldPosition);
	}

	@Override
	public final boolean inputPort(Direction face) {
		return AdvancedAlchemy.inputPort(getBlockState(), face);
	}

	@Override
	public final boolean outputPort(Direction face) {
		return AdvancedAlchemy.outputPort(getBlockState(), face);
	}

	@Override
	public Map<String, Integer> transportContents(Direction face) {
		return Map.of();
	}

	@Override
	public boolean extractOne(Direction face, String aspect) {
		return false;
	}

	final void showWorking(ServerLevel server, boolean working) {
		var state = getBlockState();
		if (state.getValue(AdvancedMachineBlock.WORKING) != working) {
			server.setBlock(worldPosition, state.setValue(AdvancedMachineBlock.WORKING, working), Block.UPDATE_CLIENTS);
		}
	}

	static boolean isPhial(ItemStack stack) {
		return stack.is(EssentiaLogistics.ESSENTIA_PHIAL) && stack.getCount() == 1;
	}

	final boolean extractPhial(ServerPlayer player, ItemStack phial, EssentiaBuffer buffer) {
		var first = buffer.first();
		if (first == null) {
			return AdvancedAlchemy.feedback(player, false, "phial_empty");
		}
		String id = first.getKey();
		int quantity = Math.min(first.getValue(), EssentiaPhial.CAPACITY);
		buffer.remove(id, quantity);
		phial.set(EssentiaPhial.CONTENTS, new EssentiaPhial.Contents(id, quantity));
		player.getInventory().setChanged();
		setChanged();
		return AdvancedAlchemy.feedback(player, true, "essentia_out", RevealingPayload.AspectAmount.nameFor(player, id), quantity);
	}

	final boolean depositPhial(ServerPlayer player, ItemStack phial, EssentiaBuffer buffer, int limit) {
		var contents = EssentiaPhial.contents(phial);
		int quantity = Math.min(contents.amount(), Math.min(buffer.room(), limit));
		if (quantity <= 0) {
			return AdvancedAlchemy.feedback(player, false, "phial_full");
		}
		buffer.add(contents.aspect(), quantity);
		if (quantity == contents.amount()) {
			phial.remove(EssentiaPhial.CONTENTS);
		} else {
			phial.set(EssentiaPhial.CONTENTS, new EssentiaPhial.Contents(contents.aspect(), contents.amount() - quantity));
		}
		player.getInventory().setChanged();
		setChanged();
		return AdvancedAlchemy.feedback(player, true, "essentia_in", RevealingPayload.AspectAmount.nameFor(player, contents.aspect()), quantity);
	}
}
