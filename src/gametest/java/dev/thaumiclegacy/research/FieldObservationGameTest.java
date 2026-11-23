package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.AspectDiscoveryData.Specimen;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class FieldObservationGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void crafting(GameTestHelper test) {
        checkRecipe(test, "field_lens", CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY,
            new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.GLASS_PANE),
            ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY)));
        checkRecipe(test, "research_journal", CraftingInput.of(2, 1, List.of(
            new ItemStack(Items.BOOK), new ItemStack(Items.COPPER_INGOT))));
        test.succeed();
    }

    private static void checkRecipe(GameTestHelper test, String name, CraftingInput input) {
        ResourceLocation id = ThaumicLegacy.id(name);
        Item item = BuiltInRegistries.ITEM.get(id);
        test.assertTrue(item != Items.AIR, "Item not registered: " + id);
        var recipe = (CraftingRecipe) test.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
        test.assertTrue(recipe.matches(input, test.getLevel()), "The documented ingredients do not match " + id);
        var result = recipe.assemble(input, test.getLevel().registryAccess());
        test.assertTrue(result.is(item) && result.getCount() == 1, "Wrong crafted result for " + id);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void recordsProgressAndSaves(GameTestHelper test) throws Exception {
        BlockPos stone = test.absolutePos(new BlockPos(0, 1, 0));
        BlockPos torch = stone.east(3);
        BlockPos wood = stone.east(6);
        BlockPos unsupported = stone.east(9);
        var level = test.getLevel();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(wood, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(unsupported, Blocks.BEDROCK.defaultBlockState());

        Item lens = BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens"));
        ServerPlayer first = test.makeMockServerPlayerInLevel();
        ServerPlayer second = test.makeMockServerPlayerInLevel();
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));

        var data = ObservationData.get(first);
        checkObservations(test, data, first, false);
        observe(first, stone, 1.1);
        checkObservations(test, data, first, false, "stone");
        observe(first, stone, 1.1);
        observe(first, unsupported, 1.1);
        observe(first, wood, 10.0);
        level.setBlockAndUpdate(wood.above(), Blocks.DIRT.defaultBlockState());
        observe(first, wood, 2.2);
        level.setBlockAndUpdate(wood.above(), Blocks.AIR.defaultBlockState());
        checkObservations(test, data, first, false, "stone");
        checkObservations(test, data, second, false);

        test.assertValueEqual(ObservationCatalog.find(Blocks.WALL_TORCH.defaultBlockState()),
            ResourceLocation.withDefaultNamespace("torch"), "wall torch shares the torch ID");
        observe(first, torch, 1.1);
        checkObservations(test, data, first, true, "stone", "torch");
        observe(first, wood, 1.1);
        checkObservations(test, data, first, true, "stone", "torch", "oak_log");
        observe(second, wood, 1.1);
        checkObservations(test, data, second, false, "oak_log");

        var storage = saveAndOpenStorage(test);
        var reloaded = reload(storage, ObservationData.class, "thaumiclegacy_observations");
        test.assertTrue(reloaded != null && reloaded != data, "SavedData did not reload from disk");
        checkObservations(test, reloaded, first, true, "stone", "torch", "oak_log");
        checkObservations(test, reloaded, second, false, "oak_log");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void scansAspectSpecimensAndSaves(GameTestHelper test) throws Exception {
        var level = test.getLevel();
        BlockPos stone = test.absolutePos(new BlockPos(0, 1, 0));
        BlockPos torch = stone.east(2);
        BlockPos copper = stone.east(4);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(copper, Blocks.COPPER_BLOCK.defaultBlockState());

        Item lens = BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens"));
        ServerPlayer first = test.makeMockServerPlayerInLevel();
        ServerPlayer second = test.makeMockServerPlayerInLevel();
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));

        observe(first, stone, 1.1);
        var afterStone = ResearchJournalItem.snapshot(first);
        test.assertFalse(afterStone.unlocked(), "One specimen cannot unlock research");
        test.assertValueEqual(discovered(afterStone), List.of(id("ordo"), id("terra")), "Stone reveals two primals");
        test.assertValueEqual(afterStone.specimens().getFirst().aspects().stream()
            .map(JournalSnapshotPayload.AspectView::amount).toList(), List.of(1, 4), "Stone aspect quantities");

        observeHeld(first, Items.TORCH);
        var afterHeld = ResearchJournalItem.snapshot(first);
        test.assertTrue(afterHeld.unlocked(), "Two distinct specimens unlock research");
        test.assertValueEqual(discovered(afterHeld), List.of(id("ordo"), id("terra"), id("ignis")), "Held item discovery");
        var hidden = afterHeld.specimens().getLast().aspects().getLast();
        test.assertTrue(hidden.id().isEmpty() && hidden.parents().isEmpty() && hidden.glyph() == -1,
            "Unknown Lux does not disclose its identity, parents, or glyph");
        var droppedTorch = test.spawnItem(Items.TORCH, new Vec3(0.5, 2, 0.5));
        pointAt(first, droppedTorch.position(), 0.4);
        useLensInAir(first);
        test.assertValueEqual(ResearchJournalItem.snapshot(first), afterHeld, "Held and dropped copies are one specimen");
        droppedTorch.discard();

        observe(first, torch, 1.1);
        var droppedCopper = test.spawnItem(Items.COPPER_INGOT, new Vec3(0.5, 2, 0.5));
        pointAt(first, droppedCopper.position(), 0.4);
        useLensInAir(first);
        droppedCopper.discard();
        var afterDropped = ResearchJournalItem.snapshot(first);
        test.assertValueEqual(specimens(afterDropped), List.of("block:stone", "item:torch", "block:torch", "item:copper_ingot"),
            "Dropped item records its item ID");
        observeHeld(first, Items.COPPER_INGOT);
        test.assertValueEqual(ResearchJournalItem.snapshot(first), afterDropped, "Held copy of a dropped specimen is a repeat");
        observe(first, copper, 1.1);

        var cow = test.spawnWithNoFreeWill(EntityType.COW, new Vec3(0.5, 2, 0.5));
        pointAt(first, cow.position(), 0.4);
        var result = UseEntityCallback.EVENT.invoker().interact(first, level, InteractionHand.MAIN_HAND,
            cow, new EntityHitResult(cow));
        test.assertValueEqual(result, InteractionResult.SUCCESS, "Entity observation consumes interaction");
        cow.discard();

        test.assertValueEqual(Set.copyOf(discovered(ResearchJournalItem.snapshot(first))),
            Set.of(id("ordo"), id("terra"), id("ignis"), id("metallum")), "Compound evidence waits for known parents");
        observeHeld(first, Items.FEATHER);
        var firstSnapshot = ResearchJournalItem.snapshot(first);
        test.assertValueEqual(specimens(firstSnapshot), List.of("block:stone", "item:torch", "block:torch",
            "item:copper_ingot", "block:copper_block", "entity:cow", "item:feather"), "Specimen types and scan order");
        test.assertValueEqual(Set.copyOf(discovered(firstSnapshot)),
            Set.of(id("ordo"), id("terra"), id("ignis"), id("metallum"), id("aer"), id("lux")),
            "The feather's Aer completes earlier Lux evidence");
        var revealedLux = firstSnapshot.specimens().get(1).aspects().getLast();
        test.assertValueEqual(revealedLux.id(), id("lux"), "Earlier torch reveals Lux");
        test.assertValueEqual(revealedLux.amount(), 2, "Revealed compound retains the original quantity");
        var lux = firstSnapshot.discovered().stream().filter(aspect -> aspect.id().equals(id("lux"))).findFirst().orElseThrow();
        test.assertValueEqual(Set.copyOf(lux.parents()), Set.of(id("aer"), id("ignis")), "Discovered compound reports its formula");
        test.assertValueEqual(firstSnapshot.observations(), List.of(vanilla("stone"), vanilla("torch")), "Legacy observation IDs");

        var empty = ResearchJournalItem.snapshot(second);
        test.assertValueEqual(specimens(empty), List.of(), "New player specimens");
        test.assertValueEqual(discovered(empty), List.of(), "New player discoveries");
        observeHeld(second, Items.TORCH);
        var secondSnapshot = ResearchJournalItem.snapshot(second);
        test.assertFalse(secondSnapshot.unlocked(), "Research progress is per player");
        test.assertValueEqual(specimens(secondSnapshot), List.of("item:torch"), "Second player's specimen");
        test.assertValueEqual(discovered(secondSnapshot), List.of(id("ignis")), "Second player's discovery");

        var storage = saveAndOpenStorage(test);
        var restored = reload(storage, AspectDiscoveryData.class, "thaumiclegacy_aspect_discoveries");
        test.assertTrue(restored != null && restored != AspectDiscoveryData.get(first), "Aspect SavedData did not reload from disk");
        var catalog = AspectCatalog.current();
        test.assertValueEqual(restored.snapshot(first.getUUID(), firstSnapshot.observations(), catalog), firstSnapshot,
            "First player's saved journal");
        test.assertValueEqual(restored.snapshot(second.getUUID(), secondSnapshot.observations(), catalog), secondSnapshot,
            "Second player's saved journal");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void thaumometerSharesResearchWithoutSpendingAura(GameTestHelper test) {
        var level = test.getLevel();
        var stone = test.absolutePos(new BlockPos(0, 1, 0));
        var torch = stone.east(2);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        var player = test.makeMockServerPlayerInLevel();
        var scanner = BuiltInRegistries.ITEM.get(ThaumicLegacy.id("thaumometer"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scanner));
        var measuredChunk = new ChunkPos(stone);
        var before = AuraService.read(level, measuredChunk);

        observe(player, stone, 1.1);
        test.assertValueEqual(AuraService.read(level, measuredChunk), before, "Scanning a block does not cost aura");
        var initial = ResearchJournalItem.snapshot(player);
        test.assertValueEqual(specimens(initial), List.of("block:stone"), "Thaumometer records a block in the existing journal");
        observe(player, torch, 1.1);
        test.assertValueEqual(ResearchJournalItem.snapshot(player), initial, "Thaumometer cooldown prevents a second immediate scan");

        test.runAfterDelay(9, () -> {
            elapseMockCooldown(player);
            observe(player, torch, 1.1);
            test.assertTrue(ResearchJournalItem.snapshot(player).unlocked(), "Scanning with the Thaumometer opens Comparing Specimens");
        });
        test.runAfterDelay(18, () -> {
            elapseMockCooldown(player);
            var feather = test.spawnItem(Items.FEATHER, new Vec3(0.5, 2, 0.5));
            pointAt(player, feather.position(), 0.4);
            useLensInAir(player);
            feather.discard();
            test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ARCANE_FOUNDATIONS),
                "Thaumometer reads dropped items and reveals Lux");
        });
        test.runAfterDelay(27, () -> {
            elapseMockCooldown(player);
            observeHeld(player, Items.WATER_BUCKET);
            test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ALCHEMY_FOUNDATIONS),
                "Thaumometer reads a held offhand specimen while crouching");
        });
        test.runAfterDelay(36, () -> {
            elapseMockCooldown(player);
            var cow = test.spawnWithNoFreeWill(EntityType.COW, new Vec3(0.5, 2, 0.5));
            pointAt(player, cow.position(), 0.4);
            var result = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
                cow, new EntityHitResult(cow));
            test.assertValueEqual(result, InteractionResult.SUCCESS, "Thaumometer uses the shared creature scan route");
            cow.discard();
            test.assertValueEqual(specimens(ResearchJournalItem.snapshot(player)), List.of(
                "block:stone", "block:torch", "item:feather", "item:water_bucket", "entity:cow"),
                "Exactly the expected specimens are recorded");
        });
        test.runAfterDelay(45, () -> {
            elapseMockCooldown(player);
            var observed = ResearchJournalItem.snapshot(player);
            var above = stone.getCenter();
            player.moveTo(above.x, above.y + 5, above.z, 0, -90);
            var localAura = AuraService.read(level, measuredChunk);
            useLensInAir(player);
            test.assertValueEqual(ResearchJournalItem.snapshot(player), observed, "Measuring empty air adds no new observation");
            test.assertValueEqual(AuraService.read(level, measuredChunk), localAura, "Measuring the local aura does not drain it");
            test.succeed();
        });
    }

    private static void elapseMockCooldown(ServerPlayer player) {
        // The embedded GameTest player does not drive its inventory cooldown clock.
        for (int tick = 0; tick < 9; tick++) {
            player.getCooldowns().tick();
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void discoversAllAspectsInBothScanOrders(GameTestHelper test) {
        var catalog = AspectCatalog.current();
        Map<String, Registry<?>> registries = Map.of("block", BuiltInRegistries.BLOCK, "item", BuiltInRegistries.ITEM,
            "entity", BuiltInRegistries.ENTITY_TYPE);
        var specimens = registries.entrySet().stream().flatMap(entry -> entry.getValue().keySet().stream()
                .filter(id -> !catalog.aspects(entry.getKey(), id).isEmpty())
                .map(id -> new Specimen(entry.getKey(), id)))
            .sorted(Comparator.comparing(Specimen::kind).thenComparing(Specimen::id)).toList();

        var data = new AspectDiscoveryData();
        for (var order : List.of(specimens, specimens.reversed())) {
            UUID player = UUID.randomUUID();
            order.forEach(specimen -> data.record(player, specimen, catalog));
            var discoveries = Set.copyOf(discovered(data.snapshot(player, List.of(), catalog)));
            test.assertValueEqual(discoveries, catalog.definitions().keySet(), "Distinct specimens discover all 52 aspects");
        }
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void distinguishesAspectsWithTheSameFormula(GameTestHelper test) {
        var catalog = AspectCatalog.current();
        var data = new AspectDiscoveryData();
        var mortuus = id("mortuus");
        var venenum = id("venenum");
        var sharedParents = Set.of(id("aqua"), id("perditio"));
        test.assertValueEqual(Set.copyOf(catalog.definitions().get(mortuus).parents()), sharedParents, "Mortuus parents");
        test.assertValueEqual(Set.copyOf(catalog.definitions().get(venenum).parents()), sharedParents, "Venenum parents");

        var bone = new Specimen("item", vanilla("bone"));
        var bee = new Specimen("entity", vanilla("bee"));
        for (var order : List.of(List.of(bone, bee), List.of(bee, bone))) {
            UUID player = UUID.randomUUID();
            data.record(player, order.getFirst(), catalog);
            test.assertTrue(data.snapshot(player, List.of(), catalog).discovered().isEmpty(),
                "Specimen evidence needs both known parents");
            data.record(player, new Specimen("item", vanilla("water_bucket")), catalog);
            data.record(player, new Specimen("item", vanilla("charcoal")), catalog);
            var expected = order.getFirst().equals(bone) ? mortuus : venenum;
            var other = expected.equals(mortuus) ? venenum : mortuus;
            var discoveries = discovered(data.snapshot(player, List.of(), catalog));
            test.assertTrue(discoveries.contains(expected) && !discoveries.contains(other),
                "A shared parent pair reveals only the aspect supported by specimen evidence");
            data.record(player, order.getLast(), catalog);
            discoveries = discovered(data.snapshot(player, List.of(), catalog));
            test.assertTrue(discoveries.containsAll(List.of(mortuus, venenum)), "Independent evidence reveals both aspects");
            test.assertValueEqual(data.record(player, order.getLast(), catalog), AspectDiscoveryData.ScanResult.REPEAT,
                "Repeating either source does not grant progress");
        }
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void importsLegacyObservations(GameTestHelper test) throws Exception {
        var player = test.makeMockServerPlayerInLevel();
        var legacy = ObservationData.get(player);
        var expected = List.of(vanilla("torch"), vanilla("stone"));
        expected.forEach(id -> legacy.recordObservation(player.getUUID(), id));
        var restored = reload(saveAndOpenStorage(test), ObservationData.class, "thaumiclegacy_observations");
        test.assertTrue(restored != null, "Legacy observations did not reload from disk");
        test.assertValueEqual(restored.getObservations(player.getUUID()), expected, "Legacy save retains observation order");

        var migrated = ResearchJournalItem.snapshot(player);
        test.assertTrue(migrated.unlocked(), "Legacy research remains unlocked");
        test.assertValueEqual(migrated.observations(), expected, "Legacy journal observations");
        test.assertValueEqual(specimens(migrated), List.of("block:torch", "block:stone"), "Migrated specimen order");
        test.assertValueEqual(Set.copyOf(discovered(migrated)), Set.of(id("ignis"), id("ordo"), id("terra")),
            "Migrated aspect discoveries");
        var importedFromDisk = new AspectDiscoveryData().snapshot(player.getUUID(), restored.getObservations(player.getUUID()),
            AspectCatalog.current());
        test.assertValueEqual(importedFromDisk, migrated, "Legacy disk data produces the same migrated journal");
        test.assertValueEqual(ResearchJournalItem.snapshot(player), migrated, "Legacy import is idempotent");
        test.assertValueEqual(legacy.getObservations(player.getUUID()), expected, "Import preserves legacy observations");

        var catalogView = migrated.research();
        test.assertValueEqual(catalogView.categories().stream().map(category -> category.id().getPath()).toList(),
            List.of("fundamentals", "exploration", "thaumaturgy", "alchemy", "artifice", "infusion", "golemancy",
                "ancient_knowledge"), "Journal uses the loaded category order for a legacy player");
        test.assertTrue(journalEntry(migrated, "comparing_specimens").orElseThrow().unlocked(),
            "Legacy observations keep their data-driven journal entry unlocked");
        test.assertTrue(journalEntry(migrated, "wand_charging").isEmpty(),
            "A future entry is hidden until its parent is completed");
        test.assertFalse(ResearchCatalog.current().unlocks("lumen_brew"::equals).contains(ResearchProgress.ESSENTIA_DISTILLATION),
            "A distillation trigger without the prerequisite chain cannot unlock the entry");

        NodeResearch.observe(player, UUID.randomUUID());
        var afterNode = journalEntry(ResearchJournalItem.snapshot(player), "wand_charging").orElseThrow();
        test.assertFalse(afterNode.unlocked(), "Observed node reveals the entry while the charge trigger remains locked");
        test.assertValueEqual(afterNode.parents(), List.of(ThaumicLegacy.id("node_study")), "Journal shows the known prerequisite");
        NodeResearch.recordCharge(player);
        test.assertTrue(journalEntry(ResearchJournalItem.snapshot(player), "wand_charging").orElseThrow().unlocked(),
            "Completing the trigger unlocks the visible entry");
        test.succeed();
    }

    private static Optional<JournalSnapshotPayload.EntryView> journalEntry(JournalSnapshotPayload snapshot, String path) {
        var id = ThaumicLegacy.id(path);
        return snapshot.research().categories().stream().flatMap(category -> category.entries().stream())
            .filter(entry -> entry.id().equals(id)).findFirst();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void migratesPrototypeKnowledgeAcrossReloads(GameTestHelper test) throws Exception {
        var level = test.getLevel();
        var registries = level.registryAccess();
        var catalog = AspectCatalog.current();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var stone = new Specimen("block", vanilla("stone"));
        var feather = new Specimen("item", vanilla("feather"));
        var removed = new Specimen("item", ThaumicLegacy.id("removed_specimen"));
        List<String> firstOldIds = List.of("ember", "grain", "lattice", "flint", "other:retired");
        List<String> secondOldIds = List.of("vapor", "pulse");

        var root = new CompoundTag();
        var players = new ListTag();
        players.add(prototypePlayer(first, List.of(stone, feather, removed), firstOldIds));
        players.add(prototypePlayer(second, List.of(), secondOldIds));
        root.put("players", players);

        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("prototype-aspects-fixture.dat");
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(root, path);
        var migrated = AspectDiscoveryData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), registries);
        var firstLegacy = List.of(vanilla("stone"));
        var firstSnapshot = migrated.snapshot(first, firstLegacy, catalog);
        var secondSnapshot = migrated.snapshot(second, List.of(), catalog);
        test.assertTrue(firstSnapshot.unlocked(), "Prototype research stays unlocked");
        test.assertValueEqual(firstSnapshot.observations(), firstLegacy, "Original observations are preserved");
        test.assertValueEqual(specimens(firstSnapshot), List.of("block:stone", "item:feather", "item:removed_specimen"),
            "All typed observations retain their order, including a retired profile");
        test.assertValueEqual(Set.copyOf(discovered(firstSnapshot)), Set.of(id("aer"), id("ignis"), id("ordo"), id("terra")),
            "Prototype fire transfers directly; the other discoveries require current evidence");
        test.assertTrue(!secondSnapshot.unlocked() && secondSnapshot.discovered().isEmpty(),
            "Ambiguous discoveries stay archived and progress remains per player");
        test.assertValueEqual(migrated.record(first, feather, catalog), AspectDiscoveryData.ScanResult.REPEAT,
            "Repeat scanning after migration cannot grant a reward");
        test.assertValueEqual(migrated.record(first, removed, catalog), AspectDiscoveryData.ScanResult.REPEAT,
            "Removed specimen still cannot be recorded twice");

        var afterMigration = migrated.save(new CompoundTag(), registries);
        test.assertValueEqual(afterMigration.getInt("version"), 1, "Save schema version");
        test.assertValueEqual(archivedIds(afterMigration, first), firstOldIds, "First player's prototype archive");
        test.assertValueEqual(archivedIds(afterMigration, second), secondOldIds, "Second player's prototype archive");
        migrated.migrate(catalog);
        test.assertValueEqual(migrated.save(new CompoundTag(), registries), afterMigration, "Reapplying migration is a no-op");

        NbtIo.writeCompressed(afterMigration, path);
        var reloaded = AspectDiscoveryData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), registries);
        test.assertValueEqual(reloaded.snapshot(first, firstLegacy, catalog), firstSnapshot, "Migrated research survives disk reload");
        test.assertValueEqual(reloaded.snapshot(second, List.of(), catalog), secondSnapshot, "Isolated progress survives disk reload");
        var afterReload = reloaded.save(new CompoundTag(), registries);
        test.assertValueEqual(afterReload.getInt("version"), afterMigration.getInt("version"), "Reload preserves the schema version");
        test.assertValueEqual(playersById(afterReload), playersById(afterMigration), "Reload preserves both archives and IDs");
        test.succeed();
    }

    private static CompoundTag prototypePlayer(UUID player, List<Specimen> observations, List<String> discoveries) {
        var entry = new CompoundTag();
        entry.putUUID("uuid", player);
        var specimens = new ListTag();
        for (Specimen observation : observations) {
            var specimen = new CompoundTag();
            specimen.putString("kind", observation.kind());
            specimen.putString("id", observation.id().toString());
            specimens.add(specimen);
        }
        entry.put("specimens", specimens);
        var known = new ListTag();
        discoveries.forEach(discovery -> known.add(StringTag.valueOf(discovery)));
        entry.put("known", known);
        return entry;
    }

    private static List<String> archivedIds(CompoundTag root, UUID player) {
        return playersById(root).get(player).getList("archived_known", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
    }

    private static Map<UUID, CompoundTag> playersById(CompoundTag root) {
        return root.getList("players", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
            .collect(Collectors.toMap(entry -> entry.getUUID("uuid"), entry -> entry));
    }

    private static DimensionDataStorage saveAndOpenStorage(GameTestHelper test) {
        var level = test.getLevel();
        var server = level.getServer();
        server.overworld().getDataStorage().save();
        var dataPath = server.getWorldPath(LevelResource.ROOT).resolve("data");
        return new DimensionDataStorage(dataPath.toFile(), server.getFixerUpper(), level.registryAccess());
    }

    @SuppressWarnings("unchecked")
    private static <T extends SavedData> T reload(DimensionDataStorage storage, Class<T> type, String id)
            throws ReflectiveOperationException {
        var factoryField = type.getDeclaredField("FACTORY");
        factoryField.setAccessible(true);
        return storage.get((SavedData.Factory<T>) factoryField.get(null), id);
    }

    private static List<String> discovered(JournalSnapshotPayload snapshot) {
        return snapshot.discovered().stream().map(JournalSnapshotPayload.AspectView::id).toList();
    }

    private static List<String> specimens(JournalSnapshotPayload snapshot) {
        return snapshot.specimens().stream().map(specimen -> specimen.kind() + ":" + specimen.id().getPath()).toList();
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static String id(String path) {
        return ThaumicLegacy.id(path).toString();
    }

    private static void checkObservations(GameTestHelper test, ObservationData data, ServerPlayer player,
                                          boolean unlocked, String... expected) {
        var ids = Arrays.stream(expected).map(ResourceLocation::withDefaultNamespace).toList();
        var observed = data.getObservations(player.getUUID());
        test.assertValueEqual(observed, ids, "observation IDs");
        test.assertValueEqual(ObservationCatalog.firstResearchUnlocked(observed.size()), unlocked, "research state");
    }

    private static void observe(ServerPlayer player, BlockPos target, double feetAboveCenter) {
        pointAt(player, target, feetAboveCenter);
        useLens(player, target);
    }

    private static void pointAt(ServerPlayer player, BlockPos target, double feetAboveCenter) {
        pointAt(player, target.getCenter(), feetAboveCenter);
    }

    private static void pointAt(ServerPlayer player, Vec3 target, double feetAboveCenter) {
        Vec3 feet = target.add(0, feetAboveCenter, 0);
        player.moveTo(feet.x, feet.y, feet.z, 0, 90);
    }

    private static void useLens(ServerPlayer player, BlockPos target) {
        ItemStack stack = player.getMainHandItem();
        var hit = new BlockHitResult(target.getCenter(), Direction.UP, target, false);
        player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }

    private static void observeHeld(ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
        player.setShiftKeyDown(true);
        useLensInAir(player);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void useLensInAir(ServerPlayer player) {
        player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
    }
}
