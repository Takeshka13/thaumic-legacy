package dev.thaumiclegacy.node;

import dev.thaumiclegacy.node.AuraNodeBlock.Variant;
import dev.thaumiclegacy.wand.WandVis;
import dev.thaumiclegacy.wand.WandVis.Primal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuraNodeBlockEntity extends BlockEntity {
	private static final int VERSION = 1;
	private static final Primal[] PRIMALS = Primal.values();
	private final int[] capacities = new int[PRIMALS.length];
	private final int[] reserves = new int[PRIMALS.length];
	private final long[] recoveryTicks = new long[PRIMALS.length];
	private UUID nodeId = UUID.randomUUID();

	AuraNodeBlockEntity(BlockPos pos, BlockState state) {
		super(AuraNodes.BLOCK_ENTITY_TYPE, pos, state);
		Variant variant = variant();
		int capacity = (variant.minCapacity() + variant.maxCapacity()) / 2;
		Arrays.fill(capacities, capacity);
		Arrays.fill(reserves, capacity);
	}

	void initialize(UUID id, Map<Primal, Integer> generatedCapacities, long now) {
		nodeId = id;
		for (Primal primal : PRIMALS) {
			int index = primal.ordinal();
			capacities[index] = Mth.clamp(generatedCapacities.get(primal), 1, WandVis.CAPACITY);
			reserves[index] = capacities[index];
			recoveryTicks[index] = Math.max(0, now);
		}
		if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
			setChanged();
		}
	}

	public Snapshot snapshot() {
		return snapshotAt(level == null ? 0 : level.getGameTime());
	}

	Snapshot snapshotAt(long now) {
		Variant variant = variant();
		Map<Primal, Integer> projected = new EnumMap<>(Primal.class);
		Map<Primal, Integer> limits = new EnumMap<>(Primal.class);
		for (Primal primal : PRIMALS) {
			int index = primal.ordinal();
			projected.put(primal, projectedReserve(index, now, variant.recoveryTicks()));
			limits.put(primal, capacities[index]);
		}
		return new Snapshot(nodeId, variant, level == null ? Level.OVERWORLD : level.dimension(),
				worldPosition, Map.copyOf(projected), Map.copyOf(limits));
	}

	public int transferTo(ItemStack wand, Primal primal, int requested) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()
				|| requested <= 0 || isRemoved() || !server.hasChunkAt(worldPosition)
				|| server.getBlockEntity(worldPosition) != this || !server.getBlockState(worldPosition).is(AuraNodes.BLOCK)) {
			return 0;
		}
		int index = primal.ordinal();
		long now = server.getGameTime();
		int interval = variant().recoveryTicks();
		int available = projectedReserve(index, now, interval);
		int accepted = WandVis.restore(wand, primal, Math.min(requested, available));
		if (accepted == 0) {
			return 0;
		}

		// Time spent full cannot be banked. Partial progress survives debits while below capacity.
		recoveryTicks[index] = available == capacities[index] ? Math.max(now, recoveryTicks[index])
				: recoveryTicks[index] + (long) (available - reserves[index]) * interval;
		reserves[index] = available - accepted;
		setChanged();
		return accepted;
	}

	private int projectedReserve(int index, long now, int interval) {
		if (now <= recoveryTicks[index]) {
			return reserves[index];
		}
		long elapsed = now - recoveryTicks[index];
		return reserves[index] + (int) Math.min(capacities[index] - reserves[index], elapsed / interval);
	}

	private Variant variant() {
		return getBlockState().getValue(AuraNodeBlock.VARIANT);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.hasUUID("node_id")) {
			nodeId = tag.getUUID("node_id");
		}
		var savedCapacities = tag.getCompound("capacities");
		var savedReserves = tag.getCompound("reserves");
		var savedRecoveryTicks = tag.getCompound("recovery_ticks");
		for (Primal primal : PRIMALS) {
			String key = saveKey(primal);
			int index = primal.ordinal();
			if (savedCapacities.contains(key, Tag.TAG_INT)) {
				capacities[index] = Mth.clamp(savedCapacities.getInt(key), 1, WandVis.CAPACITY);
			}
			reserves[index] = savedReserves.contains(key, Tag.TAG_INT)
					? Mth.clamp(savedReserves.getInt(key), 0, capacities[index]) : capacities[index];
			recoveryTicks[index] = Math.max(0, savedRecoveryTicks.getLong(key));
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", VERSION);
		tag.putUUID("node_id", nodeId);
		var savedCapacities = new CompoundTag();
		var savedReserves = new CompoundTag();
		var savedRecoveryTicks = new CompoundTag();
		for (Primal primal : PRIMALS) {
			String key = saveKey(primal);
			int index = primal.ordinal();
			savedCapacities.putInt(key, capacities[index]);
			savedReserves.putInt(key, reserves[index]);
			savedRecoveryTicks.putLong(key, recoveryTicks[index]);
		}
		tag.put("capacities", savedCapacities);
		tag.put("reserves", savedReserves);
		tag.put("recovery_ticks", savedRecoveryTicks);
	}

	private static String saveKey(Primal primal) {
		return primal.name().toLowerCase(Locale.ROOT);
	}

	public record Snapshot(UUID id, Variant variant, ResourceKey<Level> dimension, BlockPos position,
							Map<Primal, Integer> reserves, Map<Primal, Integer> capacities) {
		public int reserve(Primal primal) {
			return reserves.get(primal);
		}

		public int capacity(Primal primal) {
			return capacities.get(primal);
		}
	}
}
