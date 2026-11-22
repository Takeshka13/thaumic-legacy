package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.AspectDiscoveryData.Specimen;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
        BlockPos dirt = stone.east(9);
        var level = test.getLevel();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(wood, Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());

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
        observe(first, dirt, 1.1);
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
        BlockPos bricks = stone.south(2);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(copper, Blocks.COPPER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(bricks, Blocks.STONE_BRICKS.defaultBlockState());

        Item lens = BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens"));
        ServerPlayer first = test.makeMockServerPlayerInLevel();
        ServerPlayer second = test.makeMockServerPlayerInLevel();
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));

        observe(first, stone, 1.1);
        var afterStone = ResearchJournalItem.snapshot(first);
        test.assertFalse(afterStone.unlocked(), "One specimen cannot unlock research");
        test.assertValueEqual(discovered(afterStone), List.of("grain"), "First primal discovery");
        test.assertValueEqual(afterStone.specimens().getFirst().aspects().stream()
            .map(JournalSnapshotPayload.AspectView::id).toList(), List.of("", "grain"), "Unknown aspect remains hidden");

        observeHeld(first, Items.TORCH);
        var afterHeld = ResearchJournalItem.snapshot(first);
        test.assertTrue(afterHeld.unlocked(), "Two distinct specimens unlock research");
        test.assertValueEqual(discovered(afterHeld), List.of("grain", "ember"), "Held item discovery");
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
            Set.of("grain", "ember", "current", "pulse"), "Compound evidence waits for known parents");
        observe(first, bricks, 1.1);
        var firstSnapshot = ResearchJournalItem.snapshot(first);
        test.assertValueEqual(specimens(firstSnapshot), List.of("block:stone", "item:torch", "block:torch",
            "item:copper_ingot", "block:copper_block", "entity:cow", "block:stone_bricks"), "Specimen types and scan order");
        test.assertValueEqual(Set.copyOf(discovered(firstSnapshot)),
            Set.of("grain", "ember", "current", "pulse", "lattice", "flint", "gleam", "alloy", "temper"),
            "A new primal completes multiple earlier compounds");
        test.assertValueEqual(firstSnapshot.specimens().getFirst().aspects().stream()
            .map(JournalSnapshotPayload.AspectView::id).toList(), List.of("flint", "grain"), "Earlier profile reveals its compound");
        var flint = firstSnapshot.discovered().stream().filter(aspect -> aspect.id().equals("flint")).findFirst().orElseThrow();
        test.assertValueEqual(flint.parents(), List.of("grain", "lattice"), "Discovered compound reports its catalog formula");
        test.assertValueEqual(firstSnapshot.observations(), List.of(vanilla("stone"), vanilla("torch")), "Legacy observation IDs");

        var empty = ResearchJournalItem.snapshot(second);
        test.assertValueEqual(specimens(empty), List.of(), "New player specimens");
        test.assertValueEqual(discovered(empty), List.of(), "New player discoveries");
        observeHeld(second, Items.TORCH);
        var secondSnapshot = ResearchJournalItem.snapshot(second);
        test.assertFalse(secondSnapshot.unlocked(), "Research progress is per player");
        test.assertValueEqual(specimens(secondSnapshot), List.of("item:torch"), "Second player's specimen");
        test.assertValueEqual(discovered(secondSnapshot), List.of("ember"), "Second player's discovery");

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
            test.assertValueEqual(discoveries, catalog.definitions().keySet(), "Distinct specimens discover all 48 aspects");
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
        test.assertValueEqual(discovered(migrated), List.of("ember", "grain"), "Migrated aspect discoveries");
        var importedFromDisk = new AspectDiscoveryData().snapshot(player.getUUID(), restored.getObservations(player.getUUID()),
            AspectCatalog.current());
        test.assertValueEqual(importedFromDisk, migrated, "Legacy disk data produces the same migrated journal");
        test.assertValueEqual(ResearchJournalItem.snapshot(player), migrated, "Legacy import is idempotent");
        test.assertValueEqual(legacy.getObservations(player.getUUID()), expected, "Import preserves legacy observations");
        test.succeed();
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
