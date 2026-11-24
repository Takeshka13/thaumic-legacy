package dev.thaumiclegacy.research;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.JournalSnapshotPayload.CategoryView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.EntryView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.ResearchView;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Validated server-owned presentation and prerequisite graph. */
final class ResearchCatalog {
	private static final int MAX_ENTRIES = 256;
	private static final Set<String> GATES = Set.of("always", "comparing_specimens", "arcane_foundations",
			"alchemy_foundations", "node_basics", "node_study", "wand_charging", "lumen_brew",
			"jar_use", "vitreus_discovered", "tube_placed");
	private static volatile ResearchCatalog current = new ResearchCatalog(List.of(), List.of());
	private final List<Category> categories;
	private final List<Entry> entries;

	private ResearchCatalog(List<Category> categories, List<Entry> entries) {
		this.categories = List.copyOf(categories);
		this.entries = List.copyOf(entries);
	}

	static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ReloadListener());
	}

	static ResearchCatalog current() {
		return current;
	}

	Set<ResourceLocation> unlocks(Predicate<String> evidence) {
		Set<ResourceLocation> unlocked = new HashSet<>();
		for (Entry entry : entries) {
			if (unlocked.containsAll(entry.parents) && evidence.test(entry.gate)) {
				unlocked.add(entry.id);
			}
		}
		return unlocked;
	}

	ResearchView view(Predicate<String> evidence) {
		Set<ResourceLocation> unlocked = unlocks(evidence);
		Set<ResourceLocation> visible = new HashSet<>();
		Map<ResourceLocation, List<EntryView>> categorized = new HashMap<>();
		for (Entry entry : entries) {
			if (entry.hidden && !unlocked.containsAll(entry.parents)) {
				continue;
			}
			visible.add(entry.id);
			boolean complete = unlocked.contains(entry.id);
			String prefix = entry.translation + (complete ? ".unlocked" : ".locked");
			var parents = entry.parents.stream().filter(visible::contains).toList();
			categorized.computeIfAbsent(entry.category, ignored -> new ArrayList<>())
					.add(new EntryView(entry.id, prefix + ".title", prefix + ".body", complete, parents));
		}
		return new ResearchView(categories.stream().map(category -> new CategoryView(category.id, category.title,
				categorized.getOrDefault(category.id, List.of()))).toList());
	}

	private static ResearchCatalog load(ResourceManager resources) {
		Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
		Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
		var files = resources.listResources("thaumiclegacy/research", id -> id.getPath().endsWith(".json"));
		for (var file : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			try (var reader = file.getValue().openAsReader()) {
				var bundle = Bundle.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				for (Category category : bundle.categories) {
					require(categories.putIfAbsent(category.id, category) == null, "Duplicate category " + category.id);
					require(category.title.length() <= 160, "Category title is too long " + category.id);
				}
				for (Entry entry : bundle.entries) {
					require(entries.putIfAbsent(entry.id, entry) == null, "Duplicate research " + entry.id);
					require(validGate(entry.gate), "Unknown research trigger " + entry.gate);
					require(entry.parents.size() <= 16 && Set.copyOf(entry.parents).size() == entry.parents.size()
							&& entry.translation.length() <= 140, "Invalid research entry " + entry.id);
				}
			} catch (IOException | RuntimeException exception) {
				throw new IllegalArgumentException("Cannot load research data " + file.getKey(), exception);
			}
		}
		require(categories.size() <= 32 && entries.size() <= MAX_ENTRIES, "Research catalog exceeds its limits");
		var ordered = new ArrayList<Entry>();
		var visited = new HashSet<ResourceLocation>();
		var pending = new HashSet<ResourceLocation>();
		for (Entry entry : entries.values()) {
			require(categories.containsKey(entry.category), "Missing category " + entry.category);
			visit(entry, entries, pending, visited, ordered);
		}
		return new ResearchCatalog(categories.values().stream()
				.sorted(Comparator.comparingInt(Category::order).thenComparing(Category::id)).toList(), ordered);
	}

	private static void visit(Entry entry, Map<ResourceLocation, Entry> entries, Set<ResourceLocation> pending,
			Set<ResourceLocation> visited, List<Entry> ordered) {
		if (visited.contains(entry.id)) {
			return;
		}
		require(pending.add(entry.id), "Research dependency cycle at " + entry.id);
		for (var id : entry.parents) {
			require(entries.containsKey(id), "Missing prerequisite " + id + " for " + entry.id);
			visit(entries.get(id), entries, pending, visited, ordered);
		}
		pending.remove(entry.id);
		visited.add(entry.id);
		ordered.add(entry);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	private static boolean validGate(String gate) {
		if (GATES.contains(gate)) {
			return true;
		}
		if (!gate.startsWith("aspect:")) {
			return false;
		}
		String aspect = gate.substring("aspect:".length());
		return aspect.equals(AspectCatalog.normalizeId(aspect));
	}

	private record Category(ResourceLocation id, String title, int order) {
		private static final Codec<Category> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("id").forGetter(Category::id),
				Codec.STRING.fieldOf("title").forGetter(Category::title),
				Codec.INT.fieldOf("order").forGetter(Category::order)).apply(instance, Category::new));
	}

	private record Entry(ResourceLocation id, ResourceLocation category, String translation, String gate,
			boolean hidden, List<ResourceLocation> parents) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
				ResourceLocation.CODEC.fieldOf("category").forGetter(Entry::category),
				Codec.STRING.fieldOf("translation").forGetter(Entry::translation),
				Codec.STRING.fieldOf("gate").forGetter(Entry::gate),
				Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Entry::hidden),
				ResourceLocation.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(Entry::parents)
		).apply(instance, Entry::new));
	}

	private record Bundle(List<Category> categories, List<Entry> entries) {
		private static final Codec<Bundle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Category.CODEC.listOf().optionalFieldOf("categories", List.of()).forGetter(Bundle::categories),
				Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Bundle::entries)
		).apply(instance, Bundle::new));
	}

	private static final class ReloadListener extends SimplePreparableReloadListener<ResearchCatalog>
			implements IdentifiableResourceReloadListener {
		@Override
		public ResourceLocation getFabricId() {
			return ThaumicLegacy.id("research_catalog");
		}

		@Override
		protected ResearchCatalog prepare(ResourceManager resources, ProfilerFiller profiler) {
			return load(resources);
		}

		@Override
		protected void apply(ResearchCatalog validated, ResourceManager resources, ProfilerFiller profiler) {
			current = validated;
		}
	}
}
