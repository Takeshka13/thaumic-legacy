package dev.thaumiclegacy.furnace;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.multiblock.MultiblockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.BooleanSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class InfernalFurnaceBlockEntity extends BlockEntity {
	private static final int BONUS_INTERVAL = 4;
	private static final String IGNIS = ThaumicLegacy.id("ignis").toString();
	private static final int WORK_SCALE = 100;
	private static final int BASE_RATE = setting("baseRate", 150, 101, 500);
	private static final int BELLOWS_RATE = setting("bellowsRate", 50, 8, 200);
	private static final int IGNIS_RATE = setting("ignisRate", 50, 1, 200);
	private static final int IGNIS_TICKS = setting("ignisTicks", 40, 1, 400);
	private static final int MAX_RATE = setting("maxRate", 500, BASE_RATE + BELLOWS_RATE * 2 + IGNIS_RATE, 1200);
	private boolean formed;
	private boolean ignisBuffered;
	private int ignisTicks;
	private boolean working;
	private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe>
			recipes = RecipeManager.createCheck(RecipeType.SMELTING);
	private ItemStack input = ItemStack.EMPTY;
	private ItemStack output = ItemStack.EMPTY;
	private ItemStack bonus = ItemStack.EMPTY;
	private ResourceLocation cookingRecipe;
	private int work;
	private int ironBatch;
	private int goldBatch;
	private MultiblockPattern.Match lastMatch;
	private long checkAt;

	InfernalFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(InfernalFurnace.ENTITY, pos, state);
	}

	static void tick(Level level, BlockPos pos, BlockState state, InfernalFurnaceBlockEntity furnace) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		Direction front = furnace.front();
		BlockPos firstCorner = pos.relative(front.getClockWise());
		BlockPos oppositeCorner = pos.relative(front.getOpposite(), 2).relative(front.getCounterClockWise());
		if (!level.hasChunksAt(firstCorner, oppositeCorner)) {
			furnace.working = false;
			furnace.lastMatch = null;
			return;
		}
		long now = level.getGameTime();
		if (furnace.lastMatch == null || now >= furnace.checkAt) {
			furnace.lastMatch = furnace.shell(server);
			furnace.checkAt = now + 10;
			if (furnace.lastMatch == MultiblockPattern.Match.MATCH && !furnace.formed
					&& InfernalFurnace.migrate(server, pos, furnace.front())) {
				furnace.formed();
			} else if (furnace.lastMatch == MultiblockPattern.Match.INVALID && furnace.formed) {
				level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
				return;
			}
		}
		furnace.working = furnace.formed && furnace.lastMatch == MultiblockPattern.Match.MATCH
				&& !furnace.input.isEmpty() && furnace.process(server);
	}

	private Direction front() {
		return getBlockState().getValue(InfernalFurnaceBlock.FACING);
	}

	void formed() {
		formed = true;
		setChanged();
	}

	private MultiblockPattern.Match shell(ServerLevel level) {
		if (!formed) {
			return InfernalFurnace.PATTERN.check(level, worldPosition, front());
		}
		return InfernalFurnace.PATTERN.check(level, worldPosition, front(), (cell, pos) -> {
			BlockState part = level.getBlockState(pos);
			if (cell.right() == 0 && cell.up() == 1 && cell.back() == 1) {
				return part.isAir();
			}
			return part.is(InfernalFurnace.PART) && cell.matches().test(InfernalFurnacePart.original(part))
					&& level.getBlockEntity(pos) instanceof InfernalFurnacePartEntity entity
					&& entity.belongsTo(worldPosition);
		});
	}

	boolean canReceive(ServerLevel level, String aspect) {
		return IGNIS.equals(aspect) && working && !ignisBuffered
				&& shell(level) == MultiblockPattern.Match.MATCH;
	}

	boolean receive(BooleanSupplier debit) {
		if (!debit.getAsBoolean()) {
			return false;
		}
		ignisBuffered = true;
		setChanged();
		return true;
	}

	private int bellows(ServerLevel level) {
		BlockPos center = worldPosition.relative(front().getOpposite());
		int count = 0;
		for (Direction side : new Direction[] {Direction.DOWN, front().getOpposite(),
				front().getClockWise(), front().getCounterClockWise()}) {
			BlockPos pos = center.relative(side, 2);
			if (level.hasChunkAt(pos) && level.getBlockState(pos).is(InfernalFurnace.BELLOWS)) {
				count++;
			}
		}
		return count;
	}

	static int rate(int bellows, boolean ignis) {
		int rate = BASE_RATE;
		for (int index = 0; index < bellows; index++) {
			rate += BELLOWS_RATE >> index;
		}
		return Math.min(MAX_RATE, rate + (ignis ? IGNIS_RATE : 0));
	}

	private static int setting(String key, int fallback, int minimum, int maximum) {
		return Math.clamp(Integer.getInteger("thaumiclegacy.infernalFurnace." + key, fallback), minimum, maximum);
	}

	void insert(ServerLevel level, ServerPlayer player, ItemStack stack) {
		var structure = shell(level);
		if (structure != MultiblockPattern.Match.MATCH) {
			InfernalFurnace.message(player, structure == MultiblockPattern.Match.UNLOADED ? "unloaded" : "damaged");
			return;
		}
		if (recipes.getRecipeFor(new SingleRecipeInput(stack), level).isEmpty()) {
			InfernalFurnace.message(player, "no_recipe");
			return;
		}
		if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(stack, input)) {
			InfernalFurnace.message(player, "occupied");
			return;
		}
		int count = Math.min(stack.getCount(), stack.getMaxStackSize() - input.getCount());
		if (count <= 0) {
			InfernalFurnace.message(player, "occupied");
			return;
		}
		if (input.isEmpty()) {
			input = stack.copyWithCount(count);
		} else {
			input.grow(count);
		}
		if (!player.getAbilities().instabuild) {
			stack.shrink(count);
		}
		setChanged();
		InfernalFurnace.message(player, "inserted", count, input.getHoverName());
	}

	void collect(ServerLevel level, ServerPlayer player) {
		if (player.isShiftKeyDown()) {
			int before = input.getCount();
			give(player.getInventory(), input);
			if (input.getCount() != before) {
				resetWork();
				setChanged();
				InfernalFurnace.message(player, "collected");
				return;
			}
			if (!input.isEmpty()) {
				InfernalFurnace.message(player, "inventory_full");
				return;
			}
		} else {
			int before = output.getCount() + bonus.getCount();
			give(player.getInventory(), output);
			give(player.getInventory(), bonus);
			if (output.getCount() + bonus.getCount() != before) {
				setChanged();
				InfernalFurnace.message(player, "collected");
				return;
			}
			if (!output.isEmpty() || !bonus.isEmpty()) {
				InfernalFurnace.message(player, "inventory_full");
				return;
			}
		}
		var structure = shell(level);
		if (structure == MultiblockPattern.Match.MATCH) {
			String status = input.isEmpty() ? "idle" : working ? "working" : "paused";
			InfernalFurnace.message(player, "status",
					net.minecraft.network.chat.Component.translatable("message.thaumiclegacy.infernal_furnace." + status),
					bellows(level), (ignisBuffered ? 1 : 0), ignisTicks);
		} else {
			InfernalFurnace.message(player, structure == MultiblockPattern.Match.UNLOADED ? "unloaded" : "damaged");
		}
	}

	private static void give(Inventory inventory, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		// Inventory.add erases the remainder in creative mode.
		for (ItemStack slot : inventory.items) {
			if (ItemStack.isSameItemSameComponents(slot, stack)) {
				int space = Math.max(0, slot.getMaxStackSize() - slot.getCount());
				int moved = Math.min(stack.getCount(), space);
				slot.grow(moved);
				stack.shrink(moved);
				if (stack.isEmpty()) {
					break;
				}
			}
		}
		int free = inventory.getFreeSlot();
		if (!stack.isEmpty() && free >= 0) {
			inventory.setItem(free, stack.copy());
			stack.setCount(0);
		}
		inventory.setChanged();
	}

	private boolean process(ServerLevel level) {
		var recipeInput = new SingleRecipeInput(input);
		var match = recipes.getRecipeFor(recipeInput, level);
		if (match.isEmpty()) {
			if (cookingRecipe != null) {
				resetWork();
				setChanged();
			}
			return false;
		}
		var recipe = match.get();
		if (!recipe.id().equals(cookingRecipe)) {
			cookingRecipe = recipe.id();
			work = 0;
			setChanged();
		}
		ItemStack result = recipe.value().assemble(recipeInput, level.registryAccess());
		boolean iron = input.is(Items.RAW_IRON) && result.is(Items.IRON_INGOT);
		boolean gold = input.is(Items.RAW_GOLD) && result.is(Items.GOLD_INGOT);
		ItemStack extra = iron && ironBatch == BONUS_INTERVAL - 1 ? new ItemStack(Items.IRON_NUGGET)
				: gold && goldBatch == BONUS_INTERVAL - 1 ? new ItemStack(Items.GOLD_NUGGET) : ItemStack.EMPTY;
		if (result.isEmpty() || !fits(output, result) || !fits(bonus, extra)) {
			return false;
		}
		int required = (int) Math.clamp((long) recipe.value().getCookingTime() * WORK_SCALE, 1, Integer.MAX_VALUE);
		int bellows = bellows(level);
		int rate = rate(bellows, false);
		boolean benefit = required - (long) work > rate && (ignisTicks > 0 || ignisBuffered);
		if ((benefit || (long) work + rate >= required) && shell(level) != MultiblockPattern.Match.MATCH) {
			return false;
		}
		if (benefit) {
			if (ignisTicks == 0) {
				ignisBuffered = false;
				ignisTicks = IGNIS_TICKS;
			}
			rate = rate(bellows, true);
			ignisTicks--;
		}
		if ((long) work + rate < required) {
			work += rate;
		} else {
			input.shrink(1);
			output = append(output, result);
			bonus = append(bonus, extra);
			if (iron) {
				ironBatch = (ironBatch + 1) % BONUS_INTERVAL;
			}
			if (gold) {
				goldBatch = (goldBatch + 1) % BONUS_INTERVAL;
			}
			resetWork();
		}
		setChanged();
		return true;
	}

	private static boolean fits(ItemStack current, ItemStack addition) {
		return addition.isEmpty() || (addition.getCount() <= addition.getMaxStackSize()
				&& (current.isEmpty() || (ItemStack.isSameItemSameComponents(current, addition)
				&& addition.getCount() <= current.getMaxStackSize() - current.getCount())));
	}

	private static ItemStack append(ItemStack current, ItemStack addition) {
		if (addition.isEmpty()) {
			return current;
		}
		if (current.isEmpty()) {
			return addition.copy();
		}
		current.grow(addition.getCount());
		return current;
	}

	private void resetWork() {
		work = 0;
		cookingRecipe = null;
	}

	void dropContents(ServerLevel level) {
		if (formed) {
			InfernalFurnace.restoreParts(level, worldPosition, front());
		}
		ItemStack pending = input;
		ItemStack finished = output;
		ItemStack byproduct = bonus;
		input = ItemStack.EMPTY;
		output = ItemStack.EMPTY;
		bonus = ItemStack.EMPTY;
		resetWork();
		double x = worldPosition.getX() + 0.5;
		double y = worldPosition.getY() + 0.5;
		double z = worldPosition.getZ() + 0.5;
		Containers.dropItemStack(level, x, y, z, pending);
		Containers.dropItemStack(level, x, y, z, finished);
		Containers.dropItemStack(level, x, y, z, byproduct);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		input = ItemStack.parseOptional(registries, tag.getCompound("input"));
		output = ItemStack.parseOptional(registries, tag.getCompound("output"));
		bonus = ItemStack.parseOptional(registries, tag.getCompound("bonus"));
		cookingRecipe = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
		formed = tag.getBoolean("formed");
		ignisBuffered = tag.getBoolean("ignis_buffered");
		ignisTicks = Math.clamp(tag.getInt("ignis_ticks"), 0, IGNIS_TICKS);
		long savedWork = Math.max(0, tag.getInt("work"));
		work = input.isEmpty() || cookingRecipe == null ? 0 : (int) Math.min(Integer.MAX_VALUE,
				tag.contains("formed") ? savedWork : savedWork * WORK_SCALE / 2);
		ironBatch = Math.clamp(tag.getInt("iron_batch"), 0, BONUS_INTERVAL - 1);
		goldBatch = Math.clamp(tag.getInt("gold_batch"), 0, BONUS_INTERVAL - 1);
		lastMatch = null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("input", input.saveOptional(registries));
		tag.put("output", output.saveOptional(registries));
		tag.put("bonus", bonus.saveOptional(registries));
		if (cookingRecipe != null) {
			tag.putString("recipe", cookingRecipe.toString());
		}
		tag.putInt("work", work);
		tag.putBoolean("formed", formed);
		tag.putBoolean("ignis_buffered", ignisBuffered);
		tag.putInt("ignis_ticks", ignisTicks);
		tag.putInt("iron_batch", ironBatch);
		tag.putInt("gold_batch", goldBatch);
	}
}
