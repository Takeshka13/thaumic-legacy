package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.advanced.AdvancedEssentiaPort;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.transport.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import java.util.function.BooleanSupplier;
import java.util.TreeMap;

public final class EssentiaStorageBlockEntity extends BlockEntity implements AdvancedEssentiaPort {
	public static final int CAPACITY = 64;
	private final TreeMap<String, Integer> aspects = new TreeMap<>();
	private int transportOrder;
	private EssentiaTransport.Activity transportActivity;
	private String label = "";

	EssentiaStorageBlockEntity(BlockPos pos, BlockState state) {
		super(EssentiaLogistics.STORAGE_ENTITY, pos, state);
	}

	public StorageView snapshot() {
		return new StorageView(CAPACITY, Map.copyOf(aspects), singleAspect());
	}

	public boolean hasEssentia() {
		return !aspects.isEmpty();
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

	public String label() {
		return label;
	}

	@Override
	public boolean inputPort(Direction face) {
		return singleAspect() && face == Direction.UP;
	}

	@Override
	public boolean outputPort(Direction face) {
		return singleAspect() ? face == Direction.UP : face != Direction.DOWN;
	}

	@Override
	public Map<String, Integer> transportContents(Direction face) {
		return outputPort(face) ? snapshot().aspects() : Map.of();
	}

	@Override
	public boolean canReceive(Direction face, String aspect) {
		return inputPort(face) && canAccept(Map.of(aspect, 1));
	}

	@Override
	public boolean receiveOne(Direction face, String aspect, BooleanSupplier debit) {
		if (!attached() || !canReceive(face, aspect) || !debit.getAsBoolean()) {
			return false;
		}
		aspects.merge(aspect, 1, Integer::sum);
		setChanged();
		return true;
	}

	@Override
	public boolean extractOne(Direction face, String aspect) {
		if (!attached() || !outputPort(face)) {
			return false;
		}
		int source = aspects.getOrDefault(aspect, 0);
		if (source == 0) {
			return false;
		}
		if (source == 1) {
			aspects.remove(aspect);
		} else {
			aspects.put(aspect, source - 1);
		}
		setChanged();
		return true;
	}

	private boolean attached() {
		return level instanceof ServerLevel server && server.getServer().isSameThread() && !isRemoved()
				&& server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
	}

	int freeCapacity() {
		return CAPACITY - total();
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
		applyBatch(batch);
		setChanged();
		return true;
	}

	void applyBatch(Map<String, Integer> batch) {
		batch.forEach((id, amount) -> aspects.merge(id, amount, Integer::sum));
	}

	boolean interact(ServerPlayer player, InteractionHand hand) {
		if (!EssentiaLogistics.canInteract(this, player)) {
			return false;
		}
		ItemStack held = player.getItemInHand(hand);
		if (singleAspect() && held.is(EssentiaLogistics.WARDED_JAR_LABEL)) {
			return applyLabel(player, hand, held);
		}
		if (held.isEmpty()) {
			if (player.isShiftKeyDown() && !label.isEmpty()) {
				player.setItemInHand(hand, new ItemStack(EssentiaLogistics.WARDED_JAR_LABEL));
				label = "";
				changed(player);
				return EssentiaLogistics.feedback(player, true, "label_removed");
			}
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

	private boolean applyLabel(ServerPlayer player, InteractionHand hand, ItemStack held) {
		if (!ResearchProgress.isUnlocked(player, ResearchProgress.WARDED_JAR_LABELS)) {
			return EssentiaLogistics.feedback(player, false, "label_research");
		}
		if (!label.isEmpty()) {
			return EssentiaLogistics.feedback(player, false, "label_already");
		}
		ItemStack sample = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		var contents = EssentiaPhial.contents(sample);
		String chosen = contents.isEmpty() ? aspects.isEmpty() ? "" : aspects.firstKey() : contents.aspect();
		if (chosen.isEmpty()) {
			return EssentiaLogistics.feedback(player, false, "label_needs_sample");
		}
		if (!matchesContents(chosen)) {
			return EssentiaLogistics.feedback(player, false, "label_mismatch");
		}
		if (!ResearchProgress.knownAspects(player).contains(chosen)) {
			return EssentiaLogistics.feedback(player, false, "label_unknown");
		}
		label = chosen;
		held.shrink(1);
		changed(player);
		return EssentiaLogistics.feedback(player, true, "label_set", EssentiaLogistics.aspectName(label));
	}

	private void changed(ServerPlayer player) {
		setChanged();
		player.getInventory().setChanged();
	}

	private boolean singleAspect() {
		return getBlockState().is(EssentiaLogistics.WARDED_JAR);
	}

	private boolean matchesContents(String id) {
		return (label.isEmpty() || label.equals(id)) && (aspects.isEmpty() || aspects.containsKey(id));
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
		String savedLabel = tag.getString("label");
		label = singleAspect() && savedLabel.equals(AspectCatalog.normalizeId(savedLabel))
				&& (aspects.isEmpty() || aspects.containsKey(savedLabel)) ? savedLabel : "";
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 1);
		CompoundTag stored = new CompoundTag();
		aspects.forEach(stored::putInt);
		tag.put("aspects", stored);
		if (!label.isEmpty()) {
			tag.putString("label", label);
		}
	}

	public record StorageView(int capacity, Map<String, Integer> aspects, boolean singleAspect) {
		public int amount() {
			return aspects.values().stream().mapToInt(Integer::intValue).sum();
		}
	}
}
