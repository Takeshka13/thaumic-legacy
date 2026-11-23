package dev.thaumiclegacy.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Personal node observations, stored separately from existing specimen saves. */
public final class NodeResearch extends SavedData {
	private static final int VERSION = 1;
	private static final Factory<NodeResearch> FACTORY = new Factory<>(
			NodeResearch::new, NodeResearch::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	private final Map<UUID, PlayerProgress> players = new HashMap<>();
	private CompoundTag unsupported;

	private static NodeResearch get(ServerPlayer player) {
		var server = player.serverLevel().getServer();
		if (!server.isSameThread()) {
			throw new IllegalStateException("Node research must run on the server thread");
		}
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "thaumiclegacy_node_research");
	}

	/** Records an authenticated Thaumometer observation. */
	public static boolean observe(ServerPlayer player, UUID node) {
		var data = get(player);
		if (data.unsupported != null || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (data.players.computeIfAbsent(player.getUUID(), ignored -> new PlayerProgress()).observed.add(node)) {
			data.setDirty();
			return true;
		}
		return false;
	}

	public static boolean canCharge(ServerPlayer player) {
		var progress = get(player).players.get(player.getUUID());
		return progress != null && !progress.observed.isEmpty();
	}

	public static void recordCharge(ServerPlayer player) {
		var data = get(player);
		var progress = data.players.get(player.getUUID());
		if (progress != null && !progress.observed.isEmpty() && !progress.charged) {
			progress.charged = true;
			data.setDirty();
		}
	}

	public static JournalSnapshotPayload.NodeProgress progress(ServerPlayer player) {
		var legacy = AspectDiscoveryData.legacyObservations(player);
		var observed = AspectDiscoveryData.get(player).firstResearchUnlocked(player.getUUID(), legacy);
		return progress(player, observed);
	}

	static JournalSnapshotPayload.NodeProgress progress(ServerPlayer player, boolean comparingSpecimens) {
		var progress = get(player).players.get(player.getUUID());
		int observed = progress == null ? 0 : progress.observed.size();
		return new JournalSnapshotPayload.NodeProgress(comparingSpecimens || observed > 0, observed,
				progress != null && progress.charged);
	}

	private static NodeResearch load(CompoundTag tag, HolderLookup.Provider registries) {
		var data = new NodeResearch();
		if (tag.getInt("version") != VERSION) {
			data.unsupported = tag.copy();
			return data;
		}
		for (Tag entry : tag.getList("players", Tag.TAG_COMPOUND)) {
			var saved = (CompoundTag) entry;
			if (!saved.hasUUID("id")) {
				continue;
			}
			var progress = new PlayerProgress();
			for (Tag node : saved.getList("observed", Tag.TAG_INT_ARRAY)) {
				if (((IntArrayTag) node).size() == 4) {
					progress.observed.add(NbtUtils.loadUUID(node));
				}
			}
			if (!progress.observed.isEmpty()) {
				progress.charged = saved.getBoolean("charged");
				data.players.put(saved.getUUID("id"), progress);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		if (unsupported != null) {
			return unsupported.copy();
		}
		var entries = new ListTag();
		players.forEach((id, progress) -> {
			var saved = new CompoundTag();
			var observed = new ListTag();
			progress.observed.stream().sorted().map(NbtUtils::createUUID).forEach(observed::add);
			saved.putUUID("id", id);
			saved.put("observed", observed);
			saved.putBoolean("charged", progress.charged);
			entries.add(saved);
		});
		tag.putInt("version", VERSION);
		tag.put("players", entries);
		return tag;
	}

	private static final class PlayerProgress {
		private final Set<UUID> observed = new HashSet<>();
		private boolean charged;
	}
}
