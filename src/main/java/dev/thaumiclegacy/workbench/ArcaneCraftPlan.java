package dev.thaumiclegacy.workbench;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.ArrayList;
import java.util.List;

/** An exact inventory plan prepared before any Vis is charged. */
record ArcaneCraftPlan(List<ItemStack> gridAfter, List<ItemStack> inventoryAfter) {
	static ArcaneCraftPlan prepare(SimpleContainer grid, Inventory inventory, CraftingInput.Positioned positioned,
									ArcaneWorkbenchRecipe recipe, ItemStack output) {
		List<ItemStack> gridAfter = copyStacks(grid.getItems());
		List<ItemStack> inventoryAfter = copyStacks(inventory.items);
		if (!insert(inventoryAfter, output.copy(), inventory.getMaxStackSize())) {
			return null;
		}
		CraftingInput input = positioned.input();
		var remainders = recipe.getRemainingItems(input);
		for (int y = 0; y < input.height(); y++) {
			for (int x = 0; x < input.width(); x++) {
				if (input.getItem(x, y).isEmpty()) {
					continue;
				}
				int slot = (y + positioned.top()) * ArcaneWorkbenchBlockEntity.GRID_WIDTH + x + positioned.left();
				gridAfter.get(slot).shrink(1);
				ItemStack remainder = remainders.get(y * input.width() + x).copy();
				if (!remainder.isEmpty()) {
					insertIntoSlot(gridAfter, slot, remainder, grid.getMaxStackSize());
					if (!insert(inventoryAfter, remainder, inventory.getMaxStackSize())) {
						return null;
					}
				}
			}
		}
		return new ArcaneCraftPlan(gridAfter, inventoryAfter);
	}

	void apply(SimpleContainer grid, Inventory inventory) {
		for (int i = 0; i < gridAfter.size(); i++) {
			if (!ItemStack.matches(grid.getItem(i), gridAfter.get(i))) {
				grid.setItem(i, gridAfter.get(i));
			}
		}
		for (int i = 0; i < inventoryAfter.size(); i++) {
			if (!ItemStack.matches(inventory.getItem(i), inventoryAfter.get(i))) {
				inventory.setItem(i, inventoryAfter.get(i));
			}
		}
		inventory.setChanged();
	}

	private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
		List<ItemStack> copies = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			copies.add(stack.copy());
		}
		return copies;
	}

	private static boolean insert(List<ItemStack> stacks, ItemStack moving, int containerLimit) {
		for (int i = 0; i < stacks.size() && !moving.isEmpty(); i++) {
			if (!stacks.get(i).isEmpty()) {
				insertIntoSlot(stacks, i, moving, containerLimit);
			}
		}
		for (int i = 0; i < stacks.size() && !moving.isEmpty(); i++) {
			if (stacks.get(i).isEmpty()) {
				insertIntoSlot(stacks, i, moving, containerLimit);
			}
		}
		return moving.isEmpty();
	}

	private static void insertIntoSlot(List<ItemStack> stacks, int slot, ItemStack moving, int containerLimit) {
		ItemStack existing = stacks.get(slot);
		int limit = Math.min(moving.getMaxStackSize(), containerLimit);
		if (existing.isEmpty()) {
			stacks.set(slot, moving.split(limit));
		} else if (ItemStack.isSameItemSameComponents(existing, moving)) {
			int transferred = Math.min(moving.getCount(), limit - existing.getCount());
			if (transferred > 0) {
				existing.grow(transferred);
				moving.shrink(transferred);
			}
		}
	}
}
