package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
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
	private static final String VERSION_TAG = "version";
	private static final int CLASSIC_CATALOG_VERSION = 1;
	private static final String ARCHIVE_TAG = "archived_known";
	// Ember was the old primary for fire sources. The other old IDs have no direct equivalent.
	private static final Map<String, String> LEGACY_EQUIVALENTS = Map.of("ember", "thaumiclegacy:ignis");
	private static final Factory<AspectDiscoveryData> FACTORY = new Factory<>(
			AspectDiscoveryData::new, AspectDiscoveryData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	private final Map<UUID, Progress> players = new HashMap<>();
	private int version = CLASSIC_CATALOG_VERSION;
	private CompoundTag unreadableData;

	static AspectDiscoveryData get(ServerPlayer player) {
		AspectDiscoveryData data = player.serverLevel().getServer().overworld().getDataStorage()
				.computeIfAbsent(FACTORY, STORAGE_ID);
		data.migrate(AspectCatalog.current());
		return data;
	}

	static List<ResourceLocation> legacyObservations(ServerPlayer player) {
		return ObservationData.get(player).getObservations(player.getUUID());
	}

	void importLegacy(UUID player, List<ResourceLocation> observations, AspectCatalog catalog) {
		migrate(catalog);
		if (unreadableData != null || observations.isEmpty()) {
			return;
		}
		Progress progress = players.computeIfAbsent(player, ignored -> new Progress());
		boolean changed = false;
		for (ResourceLocation id : observations) {
			changed |= progress.specimens.add(new Specimen("block", id));
		}
		if (changed) {
			discover(progress, catalog);
			setDirty();
		}
	}

	ScanResult record(UUID player, Specimen specimen, AspectCatalog catalog) {
		migrate(catalog);
		if (unreadableData != null) {
			return ScanResult.UNSUPPORTED;
		}
		Progress existing = players.get(player);
		if (existing != null && existing.specimens.contains(specimen)) {
			return ScanResult.REPEAT;
		}
		var profile = catalog.aspects(specimen.kind(), specimen.id());
		if (profile.isEmpty()) {
			return ScanResult.UNSUPPORTED;
		}
		Progress progress = players.computeIfAbsent(player, ignored -> new Progress());
		progress.specimens.add(specimen);
		String discovered = discover(progress, catalog);
		setDirty();
		return new ScanResult(State.NEW, discovered);
	}

	void migrate(AspectCatalog catalog) {
		if (version != 0 || unreadableData != null || catalog.definitions().isEmpty()) {
			return;
		}
		for (Progress progress : players.values()) {
			progress.archivedKnown.addAll(progress.known);
			progress.known.clear();
			Set<String> carriedEvidence = new LinkedHashSet<>();
			for (String oldId : progress.archivedKnown) {
				String replacement = LEGACY_EQUIVALENTS.get(oldId);
				// Qualified external IDs name the same registered aspect after the upgrade.
				if (replacement == null && oldId.contains(":") && !oldId.startsWith(ThaumicLegacy.MOD_ID + ":")) {
					replacement = AspectCatalog.normalizeId(oldId);
				}
				if (replacement != null && catalog.definitions().containsKey(replacement)) {
					carriedEvidence.add(replacement);
				}
			}
			// Using existing observations as evidence does not produce scan feedback or repeat rewards.
			discover(progress, catalog, carriedEvidence);
		}
		version = CLASSIC_CATALOG_VERSION;
		setDirty();
	}

	private static String discover(Progress progress, AspectCatalog catalog) {
		return discover(progress, catalog, Set.of());
	}

	private static String discover(Progress progress, AspectCatalog catalog, Set<String> carriedEvidence) {
		Set<String> candidates = new TreeSet<>(carriedEvidence);
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

	static AspectDiscoveryData load(CompoundTag root, HolderLookup.Provider registries) {
		AspectDiscoveryData data = new AspectDiscoveryData();
		data.version = root.getInt(VERSION_TAG);
		if ((root.contains(VERSION_TAG) && !root.contains(VERSION_TAG, Tag.TAG_INT))
				|| data.version < 0 || data.version > CLASSIC_CATALOG_VERSION) {
			data.unreadableData = root.copy();
			ThaumicLegacy.LOGGER.warn("Aspect discoveries use an unsupported save version; the data will be preserved.");
			return data;
		}
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
			readIds(entry, "known", progress.known);
			readIds(entry, ARCHIVE_TAG, progress.archivedKnown);
		}
		return data;
	}

	private static void readIds(CompoundTag entry, String key, Set<String> target) {
		ListTag ids = entry.getList(key, Tag.TAG_STRING);
		for (int i = 0; i < ids.size(); i++) {
			target.add(ids.getString(i));
		}
	}

	private static ListTag writeIds(Set<String> ids) {
		ListTag tags = new ListTag();
		ids.forEach(id -> tags.add(StringTag.valueOf(id)));
		return tags;
	}

	@Override
	public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
		if (unreadableData != null) {
			return unreadableData.copy();
		}
		root.putInt(VERSION_TAG, version);
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
			entry.put("known", writeIds(progress.known));
			if (!progress.archivedKnown.isEmpty()) {
				entry.put(ARCHIVE_TAG, writeIds(progress.archivedKnown));
			}
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
		private final Set<String> archivedKnown = new LinkedHashSet<>();
	}
}
