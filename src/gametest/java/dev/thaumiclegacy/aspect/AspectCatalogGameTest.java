package dev.thaumiclegacy.aspect;

import com.google.gson.JsonSyntaxException;
import dev.thaumiclegacy.ThaumicLegacy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

public class AspectCatalogGameTest implements FabricGameTest {
    private static final String ROOT = "thaumiclegacy/";
    private static final ResourceLocation DEFINITIONS = ThaumicLegacy.id(ROOT + "aspects/test.json");
    private static final ResourceLocation ASSIGNMENTS = ThaumicLegacy.id(ROOT + "assignments/test.json");

    @GameTest(template = EMPTY_STRUCTURE)
    public void loadsBundledAspectCatalog(GameTestHelper test) {
        var catalog = AspectCatalog.current();
        var definitions = catalog.definitions();
        test.assertValueEqual(definitions.size(), 48, "Aspect count");
        var primals = definitions.values().stream().filter(aspect -> aspect.parents().isEmpty())
            .map(AspectDefinition::id).collect(Collectors.toSet());
        test.assertValueEqual(primals, Set.of("grain", "ember", "current", "vapor", "pulse", "lattice"), "Primal aspects");
        var glyphs = definitions.values().stream().map(AspectDefinition::glyph).collect(Collectors.toSet());
        test.assertValueEqual(glyphs, IntStream.range(0, 48).boxed().collect(Collectors.toSet()), "Aspect glyph indices");
        test.assertValueEqual(definitions.get("alloy").parents(), List.of("flint", "ember"), "Compound parents");

        checkAssignmentCount(test, catalog, "block", BuiltInRegistries.BLOCK, 44);
        checkAssignmentCount(test, catalog, "item", BuiltInRegistries.ITEM, 33);
        checkAssignmentCount(test, catalog, "entity", BuiltInRegistries.ENTITY_TYPE, 21);
        test.assertValueEqual(catalog.aspects("block", vanilla("stone")), Map.of("grain", 3, "flint", 1), "Stone profile");
        test.assertValueEqual(catalog.aspects("item", vanilla("copper_ingot")), Map.of("alloy", 2, "temper", 1),
            "Copper ingot profile");
        test.assertValueEqual(catalog.aspects("entity", vanilla("cow")), Map.of("pulse", 2, "current", 1, "mantle", 2),
            "Cow profile");
        test.assertTrue(catalog.aspects("item", vanilla("cow")).isEmpty(), "Assignments distinguish registry kinds");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rejectsInvalidAspectCatalogs(GameTestHelper test) {
        String primal = definition("primal", "[]");
        assertInvalid(test, "unknown parent 'missing'", aspects(primal, definition("first", "[\"primal\",\"missing\"]")));
        assertInvalid(test, "parent cycle", aspects(primal, definition("first", "[\"primal\",\"second\"]"),
            definition("second", "[\"primal\",\"first\"]")));
        assertInvalid(test, "Duplicate entry 'primal'", aspects(primal, primal));
        assertInvalid(test, "zero or two parents", aspects(primal, definition("first", "[\"primal\"]")));
        assertInvalid(test, "two distinct parents", aspects(primal, definition("first", "[\"primal\",\"primal\"]")));

        String zero = """
            {"assignments":[{"kind":"block","target":"minecraft:stone","aspects":{"primal":0}}]}
            """;
        assertInvalid(test, "amount for 'primal'", Map.of(DEFINITIONS, aspects(primal), ASSIGNMENTS, zero));
        String empty = """
            {"assignments":[{"kind":"block","target":"minecraft:stone","aspects":{}}]}
            """;
        assertInvalid(test, "aspects in assignment", Map.of(DEFINITIONS, aspects(primal), ASSIGNMENTS, empty));
        String unknownAspect = """
            {"assignments":[{"kind":"item","target":"minecraft:stick","aspects":{"missing":1}}]}
            """;
        assertInvalid(test, "unknown aspect 'missing'", Map.of(DEFINITIONS, aspects(primal), ASSIGNMENTS, unknownAspect));
        String unknownTarget = """
            {"assignments":[{"kind":"entity","target":"minecraft:stick","aspects":{"primal":1}}]}
            """;
        assertInvalid(test, "Unknown entity target 'minecraft:stick'", Map.of(DEFINITIONS, aspects(primal), ASSIGNMENTS, unknownTarget));
        test.succeed();
    }

    private static void checkAssignmentCount(GameTestHelper test, AspectCatalog catalog, String kind, Registry<?> registry,
                                             long expected) {
        var count = registry.keySet().stream().filter(id -> !catalog.aspects(kind, id).isEmpty()).count();
        test.assertValueEqual(count, expected, kind + " assignment count");
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static String definition(String id, String parents) {
        return """
            {"id":"%s","parents":%s,"color":"#123456","glyph":0}
            """.formatted(id, parents);
    }

    private static String aspects(String... definitions) {
        return "{\"aspects\":[" + String.join(",", definitions) + "]}";
    }

    private static void assertInvalid(GameTestHelper test, String message, String aspects) {
        assertInvalid(test, message, Map.of(DEFINITIONS, aspects));
    }

    private static void assertInvalid(GameTestHelper test, String message, Map<ResourceLocation, String> files) {
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(new TestPack(files)))) {
            try {
                AspectCatalog.load(resources);
            } catch (JsonSyntaxException exception) {
                test.assertTrue(exception.getMessage().contains(message), "Unexpected catalog error: " + exception.getMessage());
                return;
            }
            test.fail("Invalid catalog was accepted. Expected: " + message);
        }
    }

    private record TestPack(Map<ResourceLocation, String> files) implements PackResources {
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            String json = files.get(id);
            return json == null ? null : () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            files.keySet().stream().filter(id -> id.getNamespace().equals(namespace) && id.getPath().startsWith(path + "/"))
                .forEach(id -> output.accept(id, getResource(type, id)));
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of(DEFINITIONS.getNamespace());
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return new PackLocationInfo("aspect_catalog_test", Component.literal("Aspect catalog test"), PackSource.DEFAULT, Optional.empty());
        }

        @Override
        public void close() {
        }
    }
}
