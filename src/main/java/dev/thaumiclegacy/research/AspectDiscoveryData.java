package dev.thaumiclegacy.research;

import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.SpecimenView;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class AspectDiscoveryData extends SavedData {
	private static final String STORAGE_ID = "thaumiclegacy_aspect_discoveries";
	private static final Factory<AspectDiscoveryData> FACTORY = new Factory<>(
			AspectDiscoveryData::new, AspectDiscoveryData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	private final Map<UUID, Progress> players = new HashMap<>();

	static AspectDiscoveryData get(ServerPlayer player) {
		return player.serverLevel().getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_ID);
	}

	static List<ResourceLocation> legacyObservations(ServerPlayer player) {
		return ObservationData.get(player).getObservations(player.getUUID());
	}

	void importLegacy(UUID player, List<ResourceLocation> observations, AspectCatalog catalog) {
		for (ResourceLocation id : observations) {
			record(player, new Specimen("block", id), catalog);
		}
	}

	ScanResult record(UUID player, Specimen specimen, AspectCatalog catalog) {
		var profile = catalog.aspects(specimen.kind(), specimen.id());
		if (profile.isEmpty()) {
			return ScanResult.UNSUPPORTED;
		}
		Progress progress = players.computeIfAbsent(player, ignored -> new Progress());
		if (!progress.specimens.add(specimen)) {
			return ScanResult.REPEAT;
		}
		String discovered = discover(progress, catalog);
		setDirty();
		return new ScanResult(State.NEW, discovered);
	}

	private static String discover(Progress progress, AspectCatalog catalog) {
		Set<String> candidates = new TreeSet<>();
		for (Specimen specimen : progress.specimens) {
			candidates.addAll(catalog.aspects(specimen.kind(), specimen.id()).keySet());
		}
		candidates.removeAll(progress.known);
		String firstDiscovery = null;
		boolean unlocked;
		do {
			unlocked = false;
			var pending = candidates.iterator();
			while (pending.hasNext()) {
				String id = pending.next();
				if (progress.known.containsAll(catalog.definitions().get(id).parents())) {
					progress.known.add(id);
					pending.remove();
					if (firstDiscovery == null) {
						firstDiscovery = id;
					}
					unlocked = true;
				}
			}
		} while (unlocked && !candidates.isEmpty());
		return firstDiscovery;
	}

	boolean firstResearchUnlocked(UUID player, List<ResourceLocation> legacy) {
		var progress = players.get(player);
		if (progress == null) {
			return ObservationCatalog.firstResearchUnlocked(legacy.size());
		}
		int count = progress.specimens.size();
		if (ObservationCatalog.firstResearchUnlocked(count)) {
			return true;
		}
		for (ResourceLocation id : legacy) {
			if (!progress.specimens.contains(new Specimen("block", id))) {
				count++;
			}
		}
		return ObservationCatalog.firstResearchUnlocked(count);
	}

	JournalSnapshotPayload snapshot(UUID player, List<ResourceLocation> legacy, AspectCatalog catalog) {
		importLegacy(player, legacy, catalog);
		Progress progress = players.get(player);
		List<SpecimenView> specimens = new ArrayList<>();
		List<AspectView> discovered = new ArrayList<>();
		if (progress != null) {
			var recent = List.copyOf(progress.specimens);
			for (Specimen specimen : recent.subList(Math.max(0, recent.size() - JournalSnapshotPayload.MAX_SPECIMENS), recent.size())) {
				var aspects = catalog.aspects(specimen.kind(), specimen.id()).entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.map(entry -> progress.known.contains(entry.getKey())
								? view(catalog, entry.getKey(), entry.getValue(), Set.of())
								: new AspectView("", entry.getValue(), 0xA29179, -1, List.of()))
						.toList();
				specimens.add(new SpecimenView(specimen.id(), specimen.kind(), aspects));
			}
			progress.known.stream().filter(catalog.definitions()::containsKey)
					.limit(JournalSnapshotPayload.MAX_DISCOVERED)
					.map(id -> view(catalog, id, 0, progress.known)).forEach(discovered::add);
		}
		return new JournalSnapshotPayload(legacy.stream().limit(JournalSnapshotPayload.MAX_LEGACY).toList(),
				firstResearchUnlocked(player, legacy), specimens, discovered);
	}

	private static AspectView view(AspectCatalog catalog, String id, int amount, Set<String> disclosed) {
		var definition = catalog.definitions().get(id);
		var parents = disclosed.containsAll(definition.parents()) ? definition.parents() : List.<String>of();
		return new AspectView(id, amount, definition.color(), definition.glyph(), parents);
	}

	private static AspectDiscoveryData load(CompoundTag root, HolderLookup.Provider registries) {
		AspectDiscoveryData data = new AspectDiscoveryData();
		ListTag players = root.getList("players", Tag.TAG_COMPOUND);
		for (int i = 0; i < players.size(); i++) {
			CompoundTag entry = players.getCompound(i);
			if (!entry.hasUUID("uuid")) {
				continue;
			}
			Progress progress = data.players.computeIfAbsent(entry.getUUID("uuid"), ignored -> new Progress());
			ListTag specimens = entry.getList("specimens", Tag.TAG_COMPOUND);
			for (int j = 0; j < specimens.size(); j++) {
				CompoundTag specimen = specimens.getCompound(j);
				String kind = specimen.getString("kind");
				ResourceLocation id = ResourceLocation.tryParse(specimen.getString("id"));
				if (id != null && Set.of("block", "item", "entity").contains(kind)) {
					progress.specimens.add(new Specimen(kind, id));
				}
			}
			ListTag known = entry.getList("known", Tag.TAG_STRING);
			for (int j = 0; j < known.size(); j++) {
				progress.known.add(known.getString(j));
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
		ListTag entries = new ListTag();
		players.forEach((uuid, progress) -> {
			CompoundTag entry = new CompoundTag();
			entry.putUUID("uuid", uuid);
			ListTag specimens = new ListTag();
			for (Specimen specimen : progress.specimens) {
				CompoundTag tag = new CompoundTag();
				tag.putString("kind", specimen.kind());
				tag.putString("id", specimen.id().toString());
				specimens.add(tag);
			}
			entry.put("specimens", specimens);
			ListTag known = new ListTag();
			progress.known.forEach(id -> known.add(StringTag.valueOf(id)));
			entry.put("known", known);
			entries.add(entry);
		});
		root.put("players", entries);
		return root;
	}

	record Specimen(String kind, ResourceLocation id) { }

	enum State { UNSUPPORTED, REPEAT, NEW }

	record ScanResult(State state, String discovery) {
		static final ScanResult UNSUPPORTED = new ScanResult(State.UNSUPPORTED, null);
		static final ScanResult REPEAT = new ScanResult(State.REPEAT, null);
	}

	private static final class Progress {
		private final Set<Specimen> specimens = new LinkedHashSet<>();
		private final Set<String> known = new LinkedHashSet<>();
	}
}
