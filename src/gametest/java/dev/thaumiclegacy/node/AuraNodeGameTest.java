package dev.thaumiclegacy.node;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.NodeResearch;
import dev.thaumiclegacy.wand.WandVis;
import dev.thaumiclegacy.wand.WandVis.Primal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;

public class AuraNodeGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void generatesDeterministicNodePlansAndLoadsWorldgen(GameTestHelper test) {
        var registry = test.getLevel().registryAccess();
        var nodeId = ThaumicLegacy.id("aura_node");
        var configured = registry.registryOrThrow(Registries.CONFIGURED_FEATURE).get(nodeId);
        test.assertTrue(configured != null && configured.feature() instanceof AuraNodeFeature,
            "Aura node uses the bundled configured feature");
        test.assertTrue(registry.registryOrThrow(Registries.PLACED_FEATURE).get(nodeId) != null,
            "Aura node's placement is registered");

        var config = new AuraNodeFeature.Configuration(75, 15, 10);
        var position = new BlockPos(-581, 74, 891);
        var plan = AuraNodeFeature.plan(41L, position, config);
        test.assertValueEqual(AuraNodeFeature.plan(41L, position, config), plan,
            "Variant, ID and primal capacities are deterministic for a world seed and position");
        test.assertFalse(AuraNodeFeature.plan(41L, position.east(), config).id().equals(plan.id()),
            "A different position produces a different stable ID");
        test.assertFalse(AuraNodeFeature.plan(42L, position, config).id().equals(plan.id()),
            "A different world seed produces a different stable ID");
        test.assertTrue(!plan.capacities().isEmpty() && plan.capacities().values().stream().allMatch(capacity -> capacity > 0),
            "Node generation provides positive capacity for its selected primals");
        test.assertValueEqual(AuraNodeFeature.plan(41L, position, new AuraNodeFeature.Configuration(100, 0, 0)).variant(),
            AuraNodeBlock.Variant.NORMAL, "Normal generation weight");
        test.assertValueEqual(AuraNodeFeature.plan(41L, position, new AuraNodeFeature.Configuration(0, 100, 0)).variant(),
            AuraNodeBlock.Variant.BRIGHT, "Bright generation weight");
        test.assertValueEqual(AuraNodeFeature.plan(41L, position, new AuraNodeFeature.Configuration(0, 0, 100)).variant(),
            AuraNodeBlock.Variant.UNSTABLE, "Unstable generation weight");

        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        var feature = (AuraNodeFeature) configured.feature();
        var context = new FeaturePlaceContext<>(Optional.of(configured), level, level.getChunkSource().getGenerator(),
            RandomSource.create(123L), origin, config);
        test.assertTrue(feature.place(context), "Registered feature places a node above open solid ground");
        var generated = (AuraNodeBlockEntity) level.getBlockEntity(origin);
        var expected = AuraNodeFeature.plan(level.getSeed(), origin, config);
        test.assertValueEqual(generated.snapshot().id(), expected.id(), "World placement uses the deterministic ID");
        test.assertValueEqual(generated.snapshot().capacities(), expected.capacities(), "World placement uses planned primal capacities");
        test.assertFalse(feature.place(context), "An existing node cannot be generated twice");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void conservesPrimalVisAndPersistsDrainedReserves(GameTestHelper test) {
        var level = test.getLevel();
        var node = placeNode(test, AuraNodeBlock.Variant.NORMAL, Map.of(Primal.AER, 12, Primal.AQUA, 7));
        var initial = node.snapshot();
        var auraPos = new ChunkPos(initial.position());
        var before = AuraService.read(level, auraPos);
        var first = wand();
        var second = wand();
        WandVis.restore(first, Primal.AER, WandVis.CAPACITY - 1);
        test.assertValueEqual(node.transferTo(first, Primal.AER, 5), 1, "Wand capacity limits the exact debit");
        test.assertValueEqual(node.snapshot().reserve(Primal.AER), 11, "Node loses precisely the accepted unit");
        test.assertValueEqual(WandVis.get(first).amount(Primal.AER), WandVis.CAPACITY, "First wand reaches capacity");
        test.assertValueEqual(node.transferTo(first, Primal.AER, 5), 0, "Replaying against a full wand wastes no node Vis");
        test.assertValueEqual(node.transferTo(second, Primal.AER, 50), 11, "Second wand cannot draw more than the node contains");
        test.assertValueEqual(node.transferTo(second, Primal.AER, 50), 0, "An empty reserve cannot be replayed");
        test.assertValueEqual(WandVis.get(second).amount(Primal.AER), 11, "Second wand independently accepts the remaining units");
        test.assertValueEqual(node.transferTo(new ItemStack(Items.STICK), Primal.IGNIS, 1), 0,
            "A non-wand cannot receive or debit a nearby node");
        test.assertValueEqual(node.transferTo(second, Primal.AQUA, -1), 0, "Invalid amounts do not modify charge");
        var drained = node.snapshot();
        test.assertValueEqual(drained.reserve(Primal.AQUA), 7, "Draining Aer leaves Aqua untouched");
        test.assertValueEqual(drained.reserve(Primal.AER), 0, "Transfer conserves the original twelve Aer");

        var nbt = node.saveWithFullMetadata(level.registryAccess());
        test.assertValueEqual(nbt.getInt("version"), 1, "The node has a versioned persisted representation");
        var decoded = (AuraNodeBlockEntity) BlockEntity.loadStatic(initial.position(), node.getBlockState(), nbt,
            level.registryAccess());
        test.assertTrue(decoded != null && decoded != node, "A new block entity loads the saved node");
        level.setBlockEntity(decoded);
        test.assertValueEqual(decoded.snapshot(), drained, "Node identity, variant, dimension and reserves survive reload");
        test.assertValueEqual(decoded.transferTo(wand(), Primal.AER, 1), 0, "Reloading a drained node cannot refill it");
        test.assertValueEqual(AuraService.read(level, auraPos), before, "Node-to-wand transfer leaves scalar chunk aura unchanged");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recoversEachPrimalOnItsOwnSavedGameTickAnchor(GameTestHelper test) {
        var level = test.getLevel();
        var registries = level.registryAccess();
        var pos = test.absolutePos(new BlockPos(0, 1, 0));
        for (var variant : AuraNodeBlock.Variant.values()) {
            long start = 21_000;
            long interval = variant.recoveryTicks();
            var state = AuraNodes.BLOCK.defaultBlockState().setValue(AuraNodeBlock.VARIANT, variant);
            var node = new AuraNodeBlockEntity(pos, state);
            var tag = node.saveWithFullMetadata(registries);
            tag.getCompound("reserves").putInt("aer", 2);
            tag.getCompound("reserves").putInt("aqua", 1);
            tag.getCompound("recovery_ticks").putLong("aer", start);
            tag.getCompound("recovery_ticks").putLong("aqua", start + 30);
            var reloaded = (AuraNodeBlockEntity) BlockEntity.loadStatic(pos, state, tag, registries);
            test.assertTrue(reloaded != null, "Recovery fixture reloads for " + variant);
            test.assertValueEqual(reloaded.snapshotAt(start + interval - 1).reserve(Primal.AER), 2,
                "A partial interval cannot recover Vis for " + variant);
            var firstBoundary = reloaded.snapshotAt(start + interval);
            test.assertValueEqual(firstBoundary.reserve(Primal.AER), 3, "Aer recovers on its first complete interval");
            test.assertValueEqual(firstBoundary.reserve(Primal.AQUA), 1, "Aqua's later anchor remains independent");
            test.assertValueEqual(reloaded.snapshotAt(start + interval + 30).reserve(Primal.AQUA), 2,
                "Aqua recovers exactly at its own interval");
            test.assertValueEqual(reloaded.snapshotAt(start + interval), firstBoundary, "Reading never banks recovery twice");
            test.assertValueEqual(reloaded.snapshotAt(start - 1).reserve(Primal.AER), 2, "Earlier clock ticks cannot recharge");
            var longUnloaded = reloaded.snapshotAt(Long.MAX_VALUE);
            test.assertValueEqual(longUnloaded.reserves(), longUnloaded.capacities(),
                "Elapsed game ticks recharge every primal only up to capacity");
        }

        var attached = placeNode(test, AuraNodeBlock.Variant.NORMAL, Map.of());
        long now = level.getGameTime();
        test.assertValueEqual(attached.transferTo(wand(), Primal.AER, 1), 1, "The full attached node can be drained");
        var firstDebit = attached.saveWithFullMetadata(registries);
        test.assertValueEqual(firstDebit.getCompound("recovery_ticks").getLong("aer"), now,
            "The first debit of a full node starts a fresh recovery interval");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    @SuppressWarnings("removal")
    public void scansAndChargesForEachPlayerThroughTheirHeldTools(GameTestHelper test) {
        var level = test.getLevel();
        var node = placeNode(test, AuraNodeBlock.Variant.NORMAL, Map.of(Primal.AER, 6, Primal.AQUA, 6));
        var pos = node.getBlockPos();
        var first = test.makeMockServerPlayerInLevel();
        var second = test.makeMockServerPlayerInLevel();
        for (var player : new ServerPlayer[]{first, second}) {
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            aimAt(player, pos);
        }
        var firstWand = wand();
        var secondWand = wand();
        beginWandUse(first, firstWand);
        test.assertFalse(first.isUsingItem(), "An untrained player cannot start charging");
        test.assertValueEqual(node.snapshot().reserve(Primal.AER), 6, "A locked attempt changes no reserve");
        scanNode(first, pos);
        test.assertTrue(NodeResearch.canCharge(first), "Thaumometer reveals the node for its user");
        test.assertFalse(NodeResearch.canCharge(second), "The other player does not inherit the observation");
        test.assertTrue(NodeResearch.progress(first).basics(), "Node measurement shows the research milestone");
        test.assertValueEqual(NodeResearch.progress(first).observed(), 1, "One node is recorded");

        test.runAfterDelay(10, () -> {
            scanNode(first, pos);
            test.assertValueEqual(NodeResearch.progress(first).observed(), 1, "Scanning the same node again cannot inflate progress");
            beginWandUse(first, firstWand);
            test.assertTrue(first.isUsingItem(), "A trained player begins holding the wand on the node");
            beginWandUse(second, secondWand);
            test.assertFalse(second.isUsingItem(), "A second player on the same node remains locked");
        });
        test.runAfterDelay(16, () -> {
            tickEmbeddedPlayer(first);
            test.assertValueEqual(WandVis.get(firstWand).amount(Primal.AER), 1, "Holding for one interval moves one primal unit");
            test.assertValueEqual(node.snapshot().reserve(Primal.AER), 5, "Exactly one Vis leaves the shared node");
            test.assertTrue(NodeResearch.progress(first).charged(), "First real transfer completes the research step");
            test.assertFalse(NodeResearch.progress(second).charged(), "The other journal has no charge milestone");
            first.moveTo(first.getX(), first.getY(), first.getZ(), 0, -90);
        });
        test.runAfterDelay(21, () -> {
            tickEmbeddedPlayer(first);
            test.assertFalse(first.isUsingItem(), "Looking away interrupts the channel");
            test.assertValueEqual(WandVis.get(firstWand).amount(Primal.AER), 1, "An interrupted channel cannot replay a transfer");
            scanNode(second, pos);
            beginWandUse(second, secondWand);
            test.assertTrue(second.isUsingItem(), "The second observer qualifies independently");
        });
        test.runAfterDelay(27, () -> {
            tickEmbeddedPlayer(second);
            second.stopUsingItem();
            test.assertValueEqual(WandVis.get(secondWand).amount(Primal.AER), 1, "Second wand receives its own transferred unit");
            test.assertValueEqual(node.snapshot().reserve(Primal.AER), 4, "Both wands and the shared node conserve the six origin units");
            test.assertTrue(NodeResearch.progress(second).charged(), "Second player's charge progress updates separately");
            level.getDataStorage().save();
            verifyResearchSave(test, first, node.snapshot().id());
            verifyResearchSave(test, second, node.snapshot().id());
            test.succeed();
        });
    }

    private static void tickEmbeddedPlayer(ServerPlayer player) {
        // Embedded GameTest connections do not run the gameplay tick of a real client connection.
        for (int tick = 0; tick < 5; tick++) {
            player.doTick();
        }
    }

    private static void verifyResearchSave(GameTestHelper test, ServerPlayer player, UUID nodeId) {
        var file = test.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("data")
            .resolve("thaumiclegacy_node_research.dat");
        try {
            var tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data");
            var entries = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                if (entry.getUUID("id").equals(player.getUUID())) {
                    var observed = entry.getList("observed", Tag.TAG_INT_ARRAY);
                    test.assertTrue(observed.size() == 1 && NbtUtils.loadUUID(observed.getFirst()).equals(nodeId),
                        "The player's unique observation is persisted");
                    test.assertTrue(entry.getBoolean("charged"), "The player's charge milestone is persisted");
                    return;
                }
            }
            test.fail("Missing player research entry");
        } catch (java.io.IOException exception) {
            test.fail("Could not read persisted node research: " + exception.getMessage());
        }
    }

    private static void scanNode(ServerPlayer player, BlockPos pos) {
        aimAt(player, pos);
        var scanner = new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("thaumometer")));
        player.setItemInHand(InteractionHand.MAIN_HAND, scanner);
        player.gameMode.useItemOn(player, player.serverLevel(), scanner, InteractionHand.MAIN_HAND,
            new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }

    private static void aimAt(ServerPlayer player, BlockPos pos) {
        var center = pos.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
    }

    private static void beginWandUse(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItem(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND);
    }

    private static AuraNodeBlockEntity placeNode(GameTestHelper test, AuraNodeBlock.Variant variant,
                                                 Map<Primal, Integer> capacities) {
        var level = test.getLevel();
        var position = test.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(position, AuraNodes.BLOCK.defaultBlockState().setValue(AuraNodeBlock.VARIANT, variant));
        var node = (AuraNodeBlockEntity) level.getBlockEntity(position);
        var full = new EnumMap<Primal, Integer>(Primal.class);
        for (Primal primal : Primal.values()) {
            full.put(primal, capacities.getOrDefault(primal, variant.minCapacity()));
        }
        node.initialize(UUID.randomUUID(), full, 0);
        return node;
    }

    private static ItemStack wand() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("wand")));
    }
}
