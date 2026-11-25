package dev.thaumiclegacy.aspect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.thaumiclegacy.aspect.AspectCatalog.Target;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

final class AspectCatalogLoader {
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final int MAX_JSON_DEPTH = 16;

	private AspectCatalogLoader() {
	}

	static AspectCatalog load(ResourceManager resources) {
		Map<String, AspectDefinition> definitions = new LinkedHashMap<>();
		Map<String, String> definitionSources = new HashMap<>();
		readEntries(resources, "aspects", (entry, source) -> {
			AspectDefinition definition = readDefinition(entry);
			requireUnique(definitionSources, definition.id(), source);
			definitions.put(definition.id(), definition);
		});
		validateParents(definitions, definitionSources);

		Map<Target, Map<String, Integer>> assignments = new HashMap<>();
		Map<Target, String> assignmentSources = new HashMap<>();
		readEntries(resources, "assignments", (entry, source) -> {
			Target target = readTarget(entry);
			requireUnique(assignmentSources, target, source);
			assignments.put(target, readAspects(entry, definitions));
		});
		return new AspectCatalog(definitions, assignments);
	}

	private static void readEntries(ResourceManager resources, String table, BiConsumer<JsonObject, String> consumer) {
		// Pack priority may replace an entire file. Distinct effective files may only add entries.
		Map<ResourceLocation, Resource> files = resources.listResources("thaumiclegacy/" + table,
				id -> id.getPath().endsWith(".json"));
		new TreeMap<>(files).forEach((id, resource) -> {
			String source = id + " from pack '" + resource.sourcePackId() + "'";
			try (JsonReader reader = new JsonReader(resource.openAsReader())) {
				JsonObject root = GsonHelper.convertToJsonObject(readJson(reader, 0), "root object");
				if (reader.peek() != JsonToken.END_DOCUMENT) {
					throw new JsonSyntaxException("Unexpected trailing JSON data");
				}
				JsonArray entries = GsonHelper.getAsJsonArray(root, table);
				for (int i = 0; i < entries.size(); i++) {
					String entry = table + "[" + i + "]";
					try {
						consumer.accept(GsonHelper.convertToJsonObject(entries.get(i), entry), source + " " + entry);
					} catch (JsonSyntaxException exception) {
						throw new JsonSyntaxException(entry + ": " + exception.getMessage(), exception);
					}
				}
			} catch (IOException | JsonSyntaxException exception) {
				throw new JsonSyntaxException("Invalid aspect catalog file " + source + ": " + exception.getMessage(),
						exception);
			}
		});
	}

	private static AspectDefinition readDefinition(JsonObject entry) {
		String id = readAspectId(readString(entry.get("id"), "id"));
		JsonArray parentIds = GsonHelper.getAsJsonArray(entry, "parents");
		if (parentIds.size() != 0 && parentIds.size() != 2) {
			throw new JsonSyntaxException("Aspect '" + id + "' must have zero or two parents");
		}
		List<String> parents = new ArrayList<>(parentIds.size());
		for (JsonElement parent : parentIds) {
			parents.add(readAspectId(readString(parent, "parent ID")));
		}
		if (parents.size() == 2 && parents.get(0).equals(parents.get(1))) {
			throw new JsonSyntaxException("Aspect '" + id + "' must have two distinct parents");
		}

		String color = readString(entry.get("color"), "color");
		if (!COLOR.matcher(color).matches()) {
			throw new JsonSyntaxException("Expected color in #RRGGBB format for aspect '" + id + "'");
		}
		int glyph = readInt(entry.get("glyph"), "glyph", 0, AspectCatalog.MAX_GLYPH_INDEX);
		return new AspectDefinition(id, parents, Integer.parseInt(color.substring(1), 16), glyph);
	}

	private static Target readTarget(JsonObject entry) {
		String kind = readString(entry.get("kind"), "kind");
		Registry<?> registry;
		switch (kind) {
			case "block" -> registry = BuiltInRegistries.BLOCK;
			case "item" -> registry = BuiltInRegistries.ITEM;
			case "entity" -> registry = BuiltInRegistries.ENTITY_TYPE;
			case "fluid" -> registry = BuiltInRegistries.FLUID;
			default -> throw new JsonSyntaxException("Unknown assignment kind '" + kind + "'");
		}
		String targetId = readString(entry.get("target"), "target");
		ResourceLocation target = ResourceLocation.tryParse(targetId);
		if (target == null || !registry.containsKey(target)) {
			throw new JsonSyntaxException("Unknown " + kind + " target '" + targetId + "'");
		}
		return new Target(kind, target);
	}

	private static Map<String, Integer> readAspects(JsonObject entry, Map<String, AspectDefinition> definitions) {
		var profile = GsonHelper.getAsJsonObject(entry, "aspects");
		if (profile.size() == 0 || profile.size() > AspectCatalog.MAX_PROFILE_SIZE) {
			throw new JsonSyntaxException("Expected 1 to " + AspectCatalog.MAX_PROFILE_SIZE + " aspects in assignment");
		}
		Map<String, Integer> aspects = new HashMap<>();
		profile.entrySet().forEach(amount -> {
			String id = readAspectId(amount.getKey());
			if (!definitions.containsKey(id)) {
				throw new JsonSyntaxException("Assignment references unknown aspect '" + id + "'");
			}
			int value = readInt(amount.getValue(), "amount for '" + id + "'", 1, Integer.MAX_VALUE);
			if (aspects.putIfAbsent(id, value) != null) {
				throw new JsonSyntaxException("Assignment lists aspect '" + id + "' more than once");
			}
		});
		return Map.copyOf(aspects);
	}

	private static void validateParents(Map<String, AspectDefinition> definitions, Map<String, String> sources) {
		Map<String, Integer> unresolvedParents = new LinkedHashMap<>();
		Map<String, List<String>> children = new HashMap<>();
		ArrayDeque<String> resolved = new ArrayDeque<>();
		for (AspectDefinition definition : definitions.values()) {
			String id = definition.id();
			if (definition.category() == AspectDefinition.Category.PRIMARY) {
				resolved.addLast(id);
				continue;
			}
			unresolvedParents.put(id, definition.parents().size());
			for (String parent : definition.parents()) {
				if (!definitions.containsKey(parent)) {
					throw new JsonSyntaxException(sources.get(id) + ": Aspect '" + id + "' has unknown parent '" + parent + "'");
				}
				children.computeIfAbsent(parent, key -> new ArrayList<>()).add(id);
			}
		}
		while (!resolved.isEmpty()) {
			String parent = resolved.removeFirst();
			for (String child : children.getOrDefault(parent, List.of())) {
				int remaining = unresolvedParents.get(child) - 1;
				if (remaining == 0) {
					unresolvedParents.remove(child);
					resolved.addLast(child);
				} else {
					unresolvedParents.put(child, remaining);
				}
			}
		}
		if (!unresolvedParents.isEmpty()) {
			String id = unresolvedParents.keySet().iterator().next();
			throw new JsonSyntaxException(sources.get(id) + ": A parent cycle prevents resolving aspect '" + id + "'");
		}
	}

	private static <T> void requireUnique(Map<T, String> sources, T key, String source) {
		String previous = sources.putIfAbsent(key, source);
		if (previous != null) {
			throw new JsonSyntaxException("Duplicate entry '" + key + "', first defined in " + previous);
		}
	}

	private static String readAspectId(String id) {
		String normalized = AspectCatalog.normalizeId(id);
		if (normalized == null) {
			throw new JsonSyntaxException("Invalid aspect ID '" + id + "'");
		}
		return normalized;
	}

	private static String readString(JsonElement value, String field) {
		if (!GsonHelper.isStringValue(value)) {
			throw new JsonSyntaxException("Expected a string for " + field);
		}
		return value.getAsString();
	}

	private static int readInt(JsonElement value, String field, int min, int max) {
		String message = "Expected an integer from " + min + " to " + max + " for " + field;
		if (GsonHelper.isNumberValue(value)) {
			try {
				int integer = value.getAsBigDecimal().intValueExact();
				if (integer >= min && integer <= max) {
					return integer;
				}
			} catch (ArithmeticException exception) {
				throw new JsonSyntaxException(message, exception);
			}
		}
		throw new JsonSyntaxException(message);
	}

	// Read with Gson's strict reader and preserve duplicate-key errors that its tree parser discards.
	private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
		if (depth > MAX_JSON_DEPTH) {
			throw new JsonSyntaxException("JSON nesting is too deep at " + reader.getPath());
		}
		switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				JsonObject object = new JsonObject();
				reader.beginObject();
				while (reader.hasNext()) {
					String key = reader.nextName();
					if (object.has(key)) {
						throw new JsonSyntaxException("Duplicate JSON field at " + reader.getPath());
					}
					object.add(key, readJson(reader, depth + 1));
				}
				reader.endObject();
				return object;
			}
			case BEGIN_ARRAY -> {
				JsonArray array = new JsonArray();
				reader.beginArray();
				while (reader.hasNext()) {
					array.add(readJson(reader, depth + 1));
				}
				reader.endArray();
				return array;
			}
			case STRING -> {
				return new JsonPrimitive(reader.nextString());
			}
			case NUMBER -> {
				try {
					return new JsonPrimitive(new BigDecimal(reader.nextString()));
				} catch (NumberFormatException exception) {
					throw new JsonSyntaxException("Invalid number near " + reader.getPath(), exception);
				}
			}
			default -> throw new JsonSyntaxException("Unexpected JSON value at " + reader.getPath());
		}
	}
}
