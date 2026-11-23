package dev.thaumiclegacy.alchemy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A drop-free exchange of one held item for all results and remainders. */
record AlchemyInventory(List<ItemStack> main, ItemStack offhand) {
	static AlchemyInventory plan(ServerPlayer player, InteractionHand hand, ItemStack... outputs) {
		Inventory inventory = player.getInventory();
		List<ItemStack> main = new ArrayList<>(inventory.items.size());
		for (ItemStack stack : inventory.items) {
			main.add(stack.copy());
		}
		ItemStack offhand = inventory.offhand.getFirst().copy();
		boolean usingOffhand = hand == InteractionHand.OFF_HAND;
		ItemStack spent = usingOffhand ? offhand : main.get(inventory.selected);
		if (spent.isEmpty()) {
			return null;
		}
		spent.shrink(1);

		for (ItemStack output : outputs) {
			ItemStack pending = output.copy();
			if (!pending.isEmpty()) {
				int limit = Math.min(pending.getMaxStackSize(), inventory.getMaxStackSize());
				if (usingOffhand && offhand.isEmpty()) {
					offhand = pending.split(limit);
				} else if (!usingOffhand && main.get(inventory.selected).isEmpty()) {
					main.set(inventory.selected, pending.split(limit));
				}
			}
			if (!insert(main, pending, inventory.getMaxStackSize())) {
				return null;
			}
		}
		return new AlchemyInventory(main, offhand);
	}

	void apply(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < main.size(); i++) {
			if (!ItemStack.matches(inventory.items.get(i), main.get(i))) {
				inventory.setItem(i, main.get(i));
			}
		}
		if (!ItemStack.matches(inventory.offhand.getFirst(), offhand)) {
			player.setItemInHand(InteractionHand.OFF_HAND, offhand);
		}
		inventory.setChanged();
	}

	static ItemStack remainder(ItemStack held) {
		var remainder = held.getItem().getCraftingRemainingItem();
		return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
	}

	private static boolean insert(List<ItemStack> inventory, ItemStack moving, int capacity) {
		if (moving.isEmpty()) {
			return true;
		}
		int limit = Math.min(capacity, moving.getMaxStackSize());
		for (ItemStack stack : inventory) {
			if (ItemStack.isSameItemSameComponents(stack, moving)) {
				int transferred = Math.min(moving.getCount(), limit - stack.getCount());
				if (transferred > 0) {
					stack.grow(transferred);
					moving.shrink(transferred);
				}
			}
			if (moving.isEmpty()) {
				return true;
			}
		}
		for (int i = 0; i < inventory.size(); i++) {
			if (inventory.get(i).isEmpty()) {
				inventory.set(i, moving.split(limit));
				if (moving.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}
}
