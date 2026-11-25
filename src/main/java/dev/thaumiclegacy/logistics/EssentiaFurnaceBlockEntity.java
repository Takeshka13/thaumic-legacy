package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.TreeMap;

public final class EssentiaFurnaceBlockEntity extends BlockEntity {
	public static final int DURATION = 100;
	public static final int MAX_ALEMBICS = 4;
	public static final int CAPACITY = MAX_ALEMBICS * EssentiaStorageBlockEntity.CAPACITY;
	static final int INPUT = 0, FUEL = 1, INPUT_REMAINDER = 2, FUEL_REMAINDER = 3;
	private static final String[] ITEM_KEYS = {"input", "fuel", "input_remainder", "fuel_remainder"};
	private final SimpleContainer inventory = new SimpleContainer(ITEM_KEYS.length);
	private final TreeMap<String, Integer> buffer = new TreeMap<>();
	private ItemStack lastInput = ItemStack.EMPTY;
	private AspectCatalog cachedCatalog;
	private Map<String, Integer> cachedProfile = Map.of();
	private int buffered;
	private int progress;
	private int burnTicks;
	private int burnCapacity;

	EssentiaFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(EssentiaLogistics.FURNACE_ENTITY, pos, state);
		inventory.addListener(container -> {
			trackInput();
			setChanged();
		});
	}

	SimpleMenuProvider menuProvider() {
		return new SimpleMenuProvider((id, playerInventory, player) ->
				new EssentiaFurnaceMenu(id, playerInventory, this), getBlockState().getBlock().getName());
	}

	SimpleContainer inventory() {
		return inventory;
	}

	public FurnaceView snapshot() {
		return new FurnaceView(inventory.getItem(INPUT).copy(), inventory.getItem(FUEL).copy(),
				inventory.getItem(INPUT_REMAINDER).copy(), inventory.getItem(FUEL_REMAINDER).copy(),
				progress, DURATION, burnTicks, state(), burnCapacity, Map.copyOf(buffer), buffered);
	}

	static void tick(Level level, BlockPos pos, BlockState state, EssentiaFurnaceBlockEntity furnace) {
		if (level instanceof ServerLevel server) {
			furnace.tick(server, state);
		}
	}

	private void tick(ServerLevel server, BlockState blockState) {
		trackInput();
		drainIntoAlembics(server);
		boolean running = state() == FurnaceState.DISTILLING;
		if (running) {
			distill();
		}
		if (blockState.getValue(EssentiaFurnaceBlock.LIT) != running) {
			server.setBlock(worldPosition, blockState.setValue(EssentiaFurnaceBlock.LIT, running), Block.UPDATE_CLIENTS);
		}
	}

	private void trackInput() {
		ItemStack current = inventory.getItem(INPUT);
		if (!ItemStack.isSameItemSameComponents(lastInput, current)) {
			lastInput = current.copyWithCount(1);
			progress = 0;
			cachedCatalog = null;
		}
	}

	private void distill() {
		if (burnTicks == 0) {
			burnCapacity = burnDuration(inventory.getItem(FUEL));
			burnTicks = burnCapacity;
			consume(FUEL, FUEL_REMAINDER);
		}
		burnTicks--;
		if (++progress == DURATION) {
			var profile = currentProfile();
			profile.forEach((id, amount) -> buffer.merge(id, amount, Integer::sum));
			buffered += (int) units(profile);
			consume(INPUT, INPUT_REMAINDER);
			progress = 0;
		}
		setChanged();
	}

	FurnaceState state() {
		if (inventory.getItem(INPUT).isEmpty()) {
			return FurnaceState.IDLE;
		}
		var profile = currentProfile();
		long units = units(profile);
		if (units == 0 || units > CAPACITY) {
			return FurnaceState.INVALID_INPUT;
		}
		if (units > CAPACITY - buffered) {
			return FurnaceState.BUFFER_FULL;
		}
		if (!fits(INPUT, INPUT_REMAINDER)) {
			return FurnaceState.REMAINDER_FULL;
		}
		if (burnTicks == 0) {
			if (burnDuration(inventory.getItem(FUEL)) == 0) {
				return FurnaceState.NEEDS_FUEL;
			}
			if (!fits(FUEL, FUEL_REMAINDER)) {
				return FurnaceState.REMAINDER_FULL;
			}
		}
		return FurnaceState.DISTILLING;
	}

	int progress() {
		return progress;
	}

	int burnTicks() {
		return burnTicks;
	}

	int burnCapacity() {
		return burnCapacity;
	}

	Map<String, Integer> buffer() {
		return buffer;
	}

	private void drainIntoAlembics(ServerLevel server) {
		if (buffer.isEmpty() || server.getGameTime() % 5 != 0) {
			return;
		}
		for (int height = 1; height <= MAX_ALEMBICS && !buffer.isEmpty(); height++) {
			if (!(server.getBlockEntity(worldPosition.above(height)) instanceof EssentiaStorageBlockEntity storage)
					|| storage.isRemoved() || !storage.getBlockState().is(EssentiaLogistics.ARCANE_ALEMBIC)) {
				break;
			}
			var entries = buffer.entrySet().iterator();
			while (entries.hasNext()) {
				var entry = entries.next();
				int moved = Math.min(storage.freeCapacity(), entry.getValue());
				if (moved == 0 || !storage.canAccept(Map.of(entry.getKey(), moved))) {
					continue;
				}
				storage.applyBatch(Map.of(entry.getKey(), moved));
				buffered -= moved;
				if (moved == entry.getValue()) {
					entries.remove();
				} else {
					entry.setValue(entry.getValue() - moved);
				}
				storage.setChanged();
				setChanged();
				break;
			}
		}
	}

	private Map<String, Integer> currentProfile() {
		var catalog = AspectCatalog.current();
		if (cachedCatalog != catalog) {
			cachedCatalog = catalog;
			cachedProfile = profileFor(catalog, inventory.getItem(INPUT));
		}
		return cachedProfile;
	}

	static boolean acceptsInput(ItemStack stack) {
		long units = units(profileFor(AspectCatalog.current(), stack));
		return units > 0 && units <= CAPACITY;
	}

	private static Map<String, Integer> profileFor(AspectCatalog catalog, ItemStack stack) {
		var profile = catalog.aspects("item", BuiltInRegistries.ITEM.getKey(stack.getItem()));
		if (profile.isEmpty() && stack.getItem() instanceof BlockItem block) {
			return catalog.aspects("block", BuiltInRegistries.BLOCK.getKey(block.getBlock()));
		}
		return profile;
	}

	private static long units(Map<String, Integer> profile) {
		return profile.values().stream().mapToLong(Integer::longValue).sum();
	}

	static int burnDuration(ItemStack stack) {
		return stack.isEmpty() ? 0 : AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
	}

	private static ItemStack remainder(ItemStack stack) {
		var item = stack.getItem().getCraftingRemainingItem();
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private boolean fits(int source, int byproduct) {
		var stored = inventory.getItem(byproduct);
		var incoming = remainder(inventory.getItem(source));
		return incoming.isEmpty() || stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, incoming)
				&& stored.getCount() < stored.getMaxStackSize();
	}

	private void consume(int source, int byproduct) {
		var incoming = remainder(inventory.getItem(source));
		inventory.removeItem(source, 1);
		if (incoming.isEmpty()) {
			return;
		}
		var stored = inventory.getItem(byproduct);
		inventory.setItem(byproduct, stored.isEmpty() ? incoming : stored.copyWithCount(stored.getCount() + 1));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		for (int slot = 0; slot < ITEM_KEYS.length; slot++) {
			inventory.setItem(slot, ItemStack.parseOptional(registries, tag.getCompound(ITEM_KEYS[slot])));
		}
		progress = inventory.getItem(INPUT).isEmpty() ? 0 : Mth.clamp(tag.getInt("progress"), 0, DURATION - 1);
		burnTicks = Mth.clamp(tag.getInt("burn_ticks"), 0, 32767);
		burnCapacity = Mth.clamp(tag.getInt("burn_capacity"), burnTicks, 32767);
		buffer.clear();
		buffered = 0;
		var saved = tag.getCompound("buffer");
		for (String id : saved.getAllKeys().stream().sorted().toList()) {
			if (id.equals(AspectCatalog.normalizeId(id)) && saved.contains(id, Tag.TAG_INT)) {
				int amount = Mth.clamp(saved.getInt(id), 0, CAPACITY - buffered);
				if (amount > 0) {
					buffer.put(id, amount);
					buffered += amount;
				}
			}
		}
		cachedCatalog = null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 2);
		for (int slot = 0; slot < ITEM_KEYS.length; slot++) {
			tag.put(ITEM_KEYS[slot], inventory.getItem(slot).saveOptional(registries));
		}
		tag.putInt("progress", progress);
		tag.putInt("burn_ticks", burnTicks);
		tag.putInt("burn_capacity", burnCapacity);
		var saved = new CompoundTag();
		buffer.forEach(saved::putInt);
		tag.put("buffer", saved);
	}

	public enum FurnaceState {
		IDLE("idle"), INVALID_INPUT("invalid_input"), NEEDS_FUEL("needs_fuel"), BUFFER_FULL("buffer_full"),
		REMAINDER_FULL("remainder_full"), DISTILLING("distilling");

		private final String key;

		FurnaceState(String key) {
			this.key = "state.thaumiclegacy.logistics." + key;
		}

		public String translationKey() {
			return key;
		}
	}

	public record FurnaceView(ItemStack input, ItemStack fuel, ItemStack inputRemainder, ItemStack fuelRemainder,
							int progress, int duration, int burnTicks, FurnaceState state, int burnCapacity,
							Map<String, Integer> buffer, int buffered) { }
}
