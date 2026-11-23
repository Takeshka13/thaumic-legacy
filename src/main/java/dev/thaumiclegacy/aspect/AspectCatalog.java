package dev.thaumiclegacy.aspect;

import dev.thaumiclegacy.ThaumicLegacy;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** The server-side snapshot used by Thaumic Legacy's observation system. */
public final class AspectCatalog {
	public static final int MAX_ID_LENGTH = 128;
	public static final int MAX_PROFILE_SIZE = 16;
	static final int MAX_GLYPH_INDEX = 51;
	private static volatile AspectCatalog current = new AspectCatalog(Map.of(), Map.of());

	private final Map<String, AspectDefinition> definitions;
	private final Map<Target, Map<String, Integer>> assignments;

	AspectCatalog(Map<String, AspectDefinition> definitions, Map<Target, Map<String, Integer>> assignments) {
		this.definitions = Map.copyOf(definitions);
		this.assignments = Map.copyOf(assignments);
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new ReloadListener());
	}

	public static AspectCatalog current() {
		return current;
	}

	public static AspectCatalog load(ResourceManager resources) {
		return AspectCatalogLoader.load(resources);
	}

	public Map<String, Integer> aspects(String kind, ResourceLocation target) {
		return assignments.getOrDefault(new Target(kind, target), Map.of());
	}

	public Map<String, AspectDefinition> definitions() {
		return definitions;
	}

	/** Legacy shorthand uses the mod namespace; qualified identifiers retain their namespace. */
	public static String normalizeId(String id) {
		String qualified = id.indexOf(':') < 0 ? ThaumicLegacy.MOD_ID + ":" + id : id;
		ResourceLocation location = ResourceLocation.tryParse(qualified);
		if (qualified.length() > MAX_ID_LENGTH || location == null || location.getPath().isEmpty()
				|| !qualified.equals(location.toString())) {
			return null;
		}
		return qualified;
	}

	record Target(String kind, ResourceLocation id) {
	}

	private static final class ReloadListener extends SimplePreparableReloadListener<AspectCatalog>
			implements IdentifiableResourceReloadListener {
		@Override
		public ResourceLocation getFabricId() {
			return ThaumicLegacy.id("aspect_catalog");
		}

		@Override
		protected AspectCatalog prepare(ResourceManager resources, ProfilerFiller profiler) {
			return load(resources);
		}

		@Override
		protected void apply(AspectCatalog catalog, ResourceManager resources, ProfilerFiller profiler) {
			current = catalog;
		}
	}
}
