package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.transport.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.TreeMap;

public final class EssentiaStorageBlockEntity extends BlockEntity {
	public static final int CAPACITY = 64;
	private final TreeMap<String, Integer> aspects = new TreeMap<>();
	private int transportOrder;
	private EssentiaTransport.Activity transportActivity;

	EssentiaStorageBlockEntity(BlockPos pos, BlockState state) {
		super(EssentiaLogistics.STORAGE_ENTITY, pos, state);
	}

	public StorageView snapshot() {
		return new StorageView(CAPACITY, Map.copyOf(aspects), singleAspect());
	}

	public int nextTransportOrder() {
		return transportOrder++;
	}

	public EssentiaTransport.Activity transportActivity() {
		return transportActivity;
	}

	public void noteTransport(EssentiaTransport.Activity activity) {
		transportActivity = activity;
	}

	/** Computes and applies one validated debit and credit on the same server thread. */
	public boolean transferOneTo(EssentiaStorageBlockEntity target, String aspect) {
		if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()
				|| target.getLevel() != server || !attached(server) || !target.attached(server)
				|| !getBlockState().is(EssentiaLogistics.ARCANE_ALEMBIC)
				|| !target.getBlockState().is(EssentiaLogistics.WARDED_JAR)) {
			return false;
		}
		int source = aspects.getOrDefault(aspect, 0);
		if (source == 0 || !target.canAccept(Map.of(aspect, 1))) {
			return false;
		}
		if (source == 1) {
			aspects.remove(aspect);
		} else {
			aspects.put(aspect, source - 1);
		}
		target.aspects.merge(aspect, 1, Integer::sum);
		setChanged();
		target.setChanged();
		return true;
	}

	private boolean attached(ServerLevel server) {
		return !isRemoved() && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
	}

	boolean canAccept(Map<String, Integer> batch) {
		if (batch.isEmpty()) {
			return false;
		}
		int room = CAPACITY - total();
		if (singleAspect() && (batch.size() != 1 || !matchesContents(batch.keySet().iterator().next()))) {
			return false;
		}
		for (var entry : batch.entrySet()) {
			int amount = entry.getValue();
			if (amount <= 0 || amount > room || !entry.getKey().equals(AspectCatalog.normalizeId(entry.getKey()))) {
				return false;
			}
			room -= amount;
		}
		return true;
	}

	boolean offer(Map<String, Integer> batch) {
		if (!canAccept(batch)) {
			return false;
		}
		batch.forEach((id, amount) -> aspects.merge(id, amount, Integer::sum));
		setChanged();
		return true;
	}

	boolean interact(ServerPlayer player, InteractionHand hand) {
		if (!EssentiaLogistics.canInteract(this, player)) {
			return false;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			describe(player);
			return false;
		}
		if (!held.is(EssentiaLogistics.ESSENTIA_PHIAL) || held.getCount() != 1) {
			return EssentiaLogistics.feedback(player, false, "use_phial");
		}
		var contents = EssentiaPhial.contents(held);
		if (contents.isEmpty()) {
			var first = aspects.firstEntry();
			if (first == null) {
				return EssentiaLogistics.feedback(player, false, "storage_empty");
			}
			String id = first.getKey();
			int moved = Math.min(EssentiaPhial.CAPACITY, first.getValue());
			aspects.compute(id, (key, value) -> value == moved ? null : value - moved);
			EssentiaPhial.writeContents(held, id, moved);
			changed(player);
			return EssentiaLogistics.feedback(player, true, "transferred_out", EssentiaLogistics.aspectName(id), moved);
		}
		String id = contents.aspect();
		if (singleAspect() && !matchesContents(id)) {
			return EssentiaLogistics.feedback(player, false, "incompatible");
		}
		int moved = Math.min(contents.amount(), CAPACITY - total());
		if (moved == 0) {
			return EssentiaLogistics.feedback(player, false, "storage_full");
		}
		aspects.merge(id, moved, Integer::sum);
		EssentiaPhial.writeContents(held, id, contents.amount() - moved);
		changed(player);
		if (singleAspect()) {
			ResearchProgress.recordJarUse(player);
		}
		return EssentiaLogistics.feedback(player, true, "transferred_in", EssentiaLogistics.aspectName(id), moved);
	}

	private void changed(ServerPlayer player) {
		setChanged();
		player.getInventory().setChanged();
	}

	private boolean singleAspect() {
		return getBlockState().is(EssentiaLogistics.WARDED_JAR);
	}

	private boolean matchesContents(String id) {
		return aspects.isEmpty() || aspects.containsKey(id);
	}

	private int total() {
		return aspects.values().stream().mapToInt(Integer::intValue).sum();
	}

	private void describe(ServerPlayer player) {
		player.sendSystemMessage(EssentiaLogistics.text("storage_status", total(), CAPACITY));
		for (var entry : aspects.entrySet()) {
			player.sendSystemMessage(EssentiaLogistics.text("aspect",
					EssentiaLogistics.aspectName(entry.getKey()), entry.getValue()));
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspects.clear();
		CompoundTag stored = tag.getCompound("aspects");
		int room = CAPACITY;
		for (String id : stored.getAllKeys().stream().sorted().toList()) {
			if (room == 0 || singleAspect() && !aspects.isEmpty()) {
				break;
			}
			if (id.equals(AspectCatalog.normalizeId(id)) && stored.contains(id, Tag.TAG_INT)) {
				int amount = Mth.clamp(stored.getInt(id), 0, room);
				if (amount > 0) {
					aspects.put(id, amount);
					room -= amount;
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 1);
		CompoundTag stored = new CompoundTag();
		aspects.forEach(stored::putInt);
		tag.put("aspects", stored);
	}

	public record StorageView(int capacity, Map<String, Integer> aspects, boolean singleAspect) {
		public int amount() {
			return aspects.values().stream().mapToInt(Integer::intValue).sum();
		}
	}
}
