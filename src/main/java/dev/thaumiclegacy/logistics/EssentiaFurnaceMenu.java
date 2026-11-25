package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.revealing.RevealingPayload.AspectAmount;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class EssentiaFurnaceMenu extends AbstractContainerMenu {
	private static final int MACHINE_SLOTS = 4, MAIN_END = 31, ALL_END = 40;
	private final EssentiaFurnaceBlockEntity furnace;
	private final Player player;
	private final SimpleContainerData data = new SimpleContainerData(4);
	private List<AspectAmount> aspects = List.of();

	public EssentiaFurnaceMenu(int id, Inventory inventory) {
		this(id, inventory, null);
	}

	EssentiaFurnaceMenu(int id, Inventory inventory, EssentiaFurnaceBlockEntity furnace) {
		super(EssentiaLogistics.FURNACE_MENU, id);
		this.furnace = furnace;
		this.player = inventory.player;
		Container machine = furnace == null ? new SimpleContainer(MACHINE_SLOTS) : furnace.inventory();
		addMachineSlot(machine, 0, 80, 24, stack -> furnace == null || EssentiaFurnaceBlockEntity.acceptsInput(stack));
		addMachineSlot(machine, 1, 80, 60, stack -> EssentiaFurnaceBlockEntity.burnDuration(stack) > 0);
		addMachineSlot(machine, 2, 116, 24, stack -> false);
		addMachineSlot(machine, 3, 116, 60, stack -> false);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 104 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, 162));
		}
		addDataSlots(data);
		updateProperties();
	}

	private void addMachineSlot(Container machine, int index, int x, int y, Predicate<ItemStack> accepts) {
		addSlot(new Slot(machine, index, x, y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return accepts.test(stack);
			}
		});
	}

	@Override
	public boolean stillValid(Player player) {
		return furnace == null || player instanceof ServerPlayer server && EssentiaLogistics.canInteract(furnace, server);
	}

	private EssentiaFurnaceBlockEntity.FurnaceView updateProperties() {
		if (furnace == null) {
			return null;
		}
		var view = furnace.snapshot();
		data.set(0, view.progress());
		data.set(1, view.burnTicks());
		data.set(2, view.burnCapacity());
		data.set(3, view.state().ordinal());
		return view;
	}

	@Override
	public void broadcastChanges() {
		var view = updateProperties();
		super.broadcastChanges();
		if (view != null && player instanceof ServerPlayer server && server.containerMenu == this
				&& ServerPlayNetworking.canSend(server, FurnaceBufferPayload.TYPE)) {
			var known = ResearchProgress.knownAspects(server);
			var visible = view.buffer().entrySet().stream().sorted(Map.Entry.comparingByKey())
					.map(entry -> AspectAmount.visible(entry.getKey(), entry.getValue(), known)).toList();
			if (!aspects.equals(visible)) {
				aspects = visible;
				ServerPlayNetworking.send(server, new FurnaceBufferPayload(containerId, visible));
			}
		}
	}

	public void updateBuffer(List<AspectAmount> replacement) {
		if (furnace == null) {
			aspects = List.copyOf(replacement);
		}
	}

	public List<AspectAmount> aspects() { return aspects; }
	public int progress() { return data.get(0); }
	public int burnTicks() { return data.get(1); }
	public int burnCapacity() { return data.get(2); }
	public EssentiaFurnaceBlockEntity.FurnaceState state() {
		return EssentiaFurnaceBlockEntity.FurnaceState.values()[data.get(3)];
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved;
		if (index < MACHINE_SLOTS) {
			moved = moveItemStackTo(stack, MACHINE_SLOTS, ALL_END, true);
		} else {
			moved = getSlot(1).mayPlace(stack) && moveItemStackTo(stack, 1, 2, false)
					|| getSlot(0).mayPlace(stack) && moveItemStackTo(stack, 0, 1, false);
			if (!moved) {
				moved = index < MAIN_END ? moveItemStackTo(stack, MAIN_END, ALL_END, false)
						: moveItemStackTo(stack, MACHINE_SLOTS, MAIN_END, false);
			}
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, stack);
		return original;
	}
}
