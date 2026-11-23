package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public final class EssentiaFurnaceBlockEntity extends BlockEntity {
	public static final int DURATION = 100;
	private ItemStack input = ItemStack.EMPTY;
	private ItemStack fuel = ItemStack.EMPTY;
	private ItemStack inputRemainder = ItemStack.EMPTY;
	private ItemStack fuelRemainder = ItemStack.EMPTY;
	private int progress;
	private int burnTicks;
	private AspectCatalog cachedCatalog;
	private Map<String, Integer> cachedProfile = Map.of();

	EssentiaFurnaceBlockEntity(BlockPos pos, BlockState state) {
		super(EssentiaLogistics.FURNACE_ENTITY, pos, state);
	}

	public FurnaceView snapshot() {
		FurnaceState state = level instanceof ServerLevel server ? state(output(server)) : FurnaceState.IDLE;
		return new FurnaceView(input.copy(), fuel.copy(), inputRemainder.copy(), fuelRemainder.copy(),
				progress, DURATION, burnTicks, state);
	}

	static void tick(Level level, BlockPos pos, BlockState state, EssentiaFurnaceBlockEntity furnace) {
		if (level instanceof ServerLevel server) {
			furnace.tick(server, state);
		}
	}

	private void tick(ServerLevel server, BlockState blockState) {
		var alembic = output(server);
		boolean running = state(alembic) == FurnaceState.DISTILLING;
		if (running) {
			running = distill(alembic);
		}
		if (blockState.getValue(EssentiaFurnaceBlock.LIT) != running) {
			server.setBlock(worldPosition, blockState.setValue(EssentiaFurnaceBlock.LIT, running), Block.UPDATE_CLIENTS);
		}
	}

	private boolean distill(EssentiaStorageBlockEntity alembic) {
		boolean finished = progress + 1 >= DURATION;
		// The complete batch must fit before anything is spent on the final tick.
		if (finished && !alembic.offer(currentProfile())) {
			return false;
		}
		if (burnTicks == 0) {
			burnTicks = burnDuration(fuel);
			fuelRemainder = combine(fuelRemainder, remainder(fuel));
			fuel = ItemStack.EMPTY;
		}
		burnTicks--;
		if (finished) {
			inputRemainder = combine(inputRemainder, remainder(input));
			input = ItemStack.EMPTY;
			cachedCatalog = null;
			progress = 0;
		} else {
			progress++;
		}
		setChanged();
		return true;
	}

	private FurnaceState state(EssentiaStorageBlockEntity alembic) {
		if (input.isEmpty()) {
			return FurnaceState.IDLE;
		}
		var profile = currentProfile();
		if (profile.isEmpty() || units(profile) > EssentiaStorageBlockEntity.CAPACITY) {
			return FurnaceState.INVALID_INPUT;
		}
		if (alembic == null) {
			return FurnaceState.NO_ALEMBIC;
		}
		if (!alembic.canAccept(profile)) {
			return FurnaceState.ALEMBIC_FULL;
		}
		if (!fits(inputRemainder, remainder(input))) {
			return FurnaceState.REMAINDER_FULL;
		}
		if (burnTicks == 0) {
			if (burnDuration(fuel) == 0) {
				return FurnaceState.NEEDS_FUEL;
			}
			if (!fits(fuelRemainder, remainder(fuel))) {
				return FurnaceState.REMAINDER_FULL;
			}
		}
		return FurnaceState.DISTILLING;
	}

	private EssentiaStorageBlockEntity output(ServerLevel server) {
		if (input.isEmpty()) {
			return null;
		}
		BlockPos above = worldPosition.above();
		// Directly above this attached furnace is always in the same chunk column.
		return server.getBlockState(above).is(EssentiaLogistics.ARCANE_ALEMBIC)
				&& server.getBlockEntity(above) instanceof EssentiaStorageBlockEntity storage && !storage.isRemoved()
				? storage : null;
	}

	boolean interact(ServerPlayer player, InteractionHand hand) {
		if (!EssentiaLogistics.canInteract(this, player)) {
			return false;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			if (player.isShiftKeyDown()) {
				return recover(player, hand);
			}
			describe(player);
			return false;
		}
		if (player.isShiftKeyDown()) {
			if (burnDuration(held) == 0) {
				return EssentiaLogistics.feedback(player, false, "needs_fuel_item");
			}
			if (!fuel.isEmpty()) {
				return EssentiaLogistics.feedback(player, false, "fuel_occupied");
			}
			fuel = takeOne(player, hand, held);
			setChanged();
			return EssentiaLogistics.feedback(player, true, "inserted_fuel", fuel.getHoverName());
		}
		if (!input.isEmpty()) {
			return EssentiaLogistics.feedback(player, false, "input_occupied");
		}
		var catalog = AspectCatalog.current();
		var profile = profileFor(catalog, held);
		if (profile.isEmpty()) {
			return EssentiaLogistics.feedback(player, false, "needs_profile");
		}
		if (units(profile) > EssentiaStorageBlockEntity.CAPACITY) {
			return EssentiaLogistics.feedback(player, false, "profile_too_large");
		}
		input = takeOne(player, hand, held);
		cachedCatalog = catalog;
		cachedProfile = profile;
		progress = 0;
		setChanged();
		return EssentiaLogistics.feedback(player, true, "inserted_input", input.getHoverName());
	}

	private boolean recover(ServerPlayer player, InteractionHand hand) {
		ItemStack recovered;
		if (!inputRemainder.isEmpty()) {
			recovered = inputRemainder;
			inputRemainder = ItemStack.EMPTY;
		} else if (!fuelRemainder.isEmpty()) {
			recovered = fuelRemainder;
			fuelRemainder = ItemStack.EMPTY;
		} else if (!input.isEmpty()) {
			recovered = input;
			input = ItemStack.EMPTY;
			cachedCatalog = null;
			progress = 0;
		} else if (!fuel.isEmpty()) {
			recovered = fuel;
			fuel = ItemStack.EMPTY;
		} else {
			return EssentiaLogistics.feedback(player, false, "nothing_to_recover");
		}
		player.setItemInHand(hand, recovered);
		player.getInventory().setChanged();
		setChanged();
		return EssentiaLogistics.feedback(player, true, "recovered", recovered.getHoverName());
	}

	private void describe(ServerPlayer player) {
		var view = snapshot();
		player.sendSystemMessage(EssentiaLogistics.text("furnace_status", Component.translatable(view.state().translationKey()),
				view.progress(), view.duration(), view.burnTicks()));
		player.sendSystemMessage(EssentiaLogistics.text("furnace_slots", display(input), display(fuel),
				display(inputRemainder), display(fuelRemainder)));
	}

	private Map<String, Integer> currentProfile() {
		var catalog = AspectCatalog.current();
		if (cachedCatalog != catalog) {
			cachedCatalog = catalog;
			cachedProfile = profileFor(catalog, input);
		}
		return cachedProfile;
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

	private static ItemStack takeOne(ServerPlayer player, InteractionHand hand, ItemStack held) {
		ItemStack queued = held.split(1);
		if (held.isEmpty()) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		}
		player.getInventory().setChanged();
		return queued;
	}

	private static Component display(ItemStack stack) {
		return stack.isEmpty() ? EssentiaLogistics.text("empty_slot") : stack.getHoverName();
	}

	private static int burnDuration(ItemStack stack) {
		return stack.isEmpty() ? 0 : Math.max(0, AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0));
	}

	private static ItemStack remainder(ItemStack stack) {
		var remainder = stack.getItem().getCraftingRemainingItem();
		return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
	}

	private static boolean fits(ItemStack stored, ItemStack incoming) {
		return incoming.isEmpty() || stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, incoming)
				&& stored.getCount() + incoming.getCount() <= stored.getMaxStackSize();
	}

	private static ItemStack combine(ItemStack stored, ItemStack incoming) {
		if (stored.isEmpty()) {
			return incoming;
		}
		stored.grow(incoming.getCount());
		return stored;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		input = ItemStack.parseOptional(registries, tag.getCompound("input"));
		fuel = ItemStack.parseOptional(registries, tag.getCompound("fuel"));
		inputRemainder = ItemStack.parseOptional(registries, tag.getCompound("input_remainder"));
		fuelRemainder = ItemStack.parseOptional(registries, tag.getCompound("fuel_remainder"));
		input.limitSize(1);
		fuel.limitSize(1);
		progress = input.isEmpty() ? 0 : Mth.clamp(tag.getInt("progress"), 0, DURATION - 1);
		burnTicks = Math.max(0, tag.getInt("burn_ticks"));
		cachedCatalog = null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 1);
		tag.put("input", input.saveOptional(registries));
		tag.put("fuel", fuel.saveOptional(registries));
		tag.put("input_remainder", inputRemainder.saveOptional(registries));
		tag.put("fuel_remainder", fuelRemainder.saveOptional(registries));
		tag.putInt("progress", progress);
		tag.putInt("burn_ticks", burnTicks);
	}

	public enum FurnaceState {
		IDLE("idle"), INVALID_INPUT("invalid_input"), NEEDS_FUEL("needs_fuel"), NO_ALEMBIC("no_alembic"),
		ALEMBIC_FULL("alembic_full"), REMAINDER_FULL("remainder_full"), DISTILLING("distilling");

		private final String key;

		FurnaceState(String key) {
			this.key = "state.thaumiclegacy.logistics." + key;
		}

		public String translationKey() {
			return key;
		}
	}

	public record FurnaceView(ItemStack input, ItemStack fuel, ItemStack inputRemainder, ItemStack fuelRemainder,
							int progress, int duration, int burnTicks, FurnaceState state) { }
}
