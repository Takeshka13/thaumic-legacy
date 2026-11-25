package dev.thaumiclegacy.infusion;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.research.NodeResearch;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.wand.WandVis;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

public class InfusionGameTest implements FabricGameTest {
    private static final String TERRA = "thaumiclegacy:terra";
    private static final String ORDO = "thaumiclegacy:ordo";

    @GameTest(template = EMPTY_STRUCTURE)
    public void formationChecksResearchLayoutAndCharge(GameTestHelper test) {
        var altar = build(test);
        var player = player(test);
        var wand = wand();
        wand.set(WandVis.COMPONENT, RunicMatrixEntity.FORMATION_COST);
        click(player, altar.pos, wand);
        test.assertTrue(!altar.formed(), "The formation requires research");
        test.assertValueEqual(WandVis.get(wand), RunicMatrixEntity.FORMATION_COST, "Denied activation preserves Vis");
        train(test, player);

        var pillar = altar.pos.offset(1, -1, 1);
        altar.level.setBlockAndUpdate(pillar, Blocks.AIR.defaultBlockState());
        click(player, altar.pos, wand);
        test.assertTrue(!altar.formed(), "Missing pillar blocks formation");
        test.assertValueEqual(WandVis.get(wand), RunicMatrixEntity.FORMATION_COST, "Invalid structure preserves Vis");
        altar.level.setBlockAndUpdate(pillar, Infusion.STONE.defaultBlockState());

        click(player, altar.pos, wand());
        test.assertTrue(!altar.formed(), "An empty wand cannot form the altar");
        click(player, altar.pos, wand);
        test.assertTrue(altar.formed(), "Valid layout forms with exactly enough Wand Vis");
        test.assertValueEqual(WandVis.get(wand), WandVis.EMPTY, "Formation spends exactly the declared Vis");
        altar.center().setStack(new ItemStack(Items.CHISELED_STONE_BRICKS));
        altar.level.setBlockAndUpdate(pillar, Blocks.AIR.defaultBlockState());
        altar.matrix.advance(altar.level);
        test.assertTrue(!altar.formed(), "Breaking a pillar deactivates the formed matrix");
        test.assertTrue(altar.center().stack().is(Items.CHISELED_STONE_BRICKS), "An intact pedestal keeps its item");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ritualResumesFromNbtAndPaysOnlyOnce(GameTestHelper test) {
        var altar = build(test);
        var owner = player(test);
        train(test, owner);
        var wand = chargedWand();
        click(owner, altar.pos, wand);

        click(owner, altar.pos, wand);
        test.assertValueEqual(altar.matrix.reading().status(), "infusion_recipe", "Missing items reject the recipe");
        setLodestoneInputs(altar, 2);
        click(owner, altar.pos, wand);
        test.assertValueEqual(altar.matrix.reading().status(), "infusion_essentia", "Missing essentia rejects the ritual");

        var terra = jar(altar, altar.pos.offset(1, -2, 2), TERRA, 2);
        var ordo = jar(altar, altar.pos.offset(-1, -2, 2), ORDO, 2);
        var visitor = player(test);
        click(visitor, altar.pos, wand());
        test.assertTrue(altar.matrix.reading().total() == 0, "Research is per player");
        click(owner, altar.pos, wand);
        test.assertTrue(altar.matrix.instability(altar.level) == 0, "Opposite components stabilize the first recipe");
        test.assertTrue(altar.matrix.reading().total() == 4, "Correct recipe reserves four withdrawals");
        altar.matrix.advance(altar.level);
        test.assertTrue(altar.matrix.reading().absorbed() == 1, "First step takes exactly one unit");

        visitor.setShiftKeyDown(true);
        click(visitor, altar.pos, wand());
        test.assertTrue(altar.matrix.reading().absorbed() == 1, "A second player cannot cancel or restart an active ritual");
        var registry = altar.level.registryAccess();
        var savedRuntime = altar.matrix.saveWithoutMetadata(registry);
        var savedPedestal = altar.center().saveWithoutMetadata(registry);
        altar.center().loadWithComponents(savedPedestal, registry);
        altar.matrix.loadWithComponents(savedRuntime, registry);
        test.assertTrue(altar.matrix.reading().absorbed() == 1, "NBT preserves the amount already paid");

        for (int i = 0; i < 5; i++) {
            altar.matrix.advance(altar.level);
        }
        var output = altar.center().stack();
        test.assertTrue(output.is(Items.LODESTONE) && output.getCount() == 1, "One lodestone appears on the central pedestal");
        test.assertTrue(altar.ring(0).stack().isEmpty() && altar.ring(1).stack().isEmpty(),
                "Both ingredients are consumed once");
        test.assertTrue(terra.transportContents(Direction.UP).isEmpty() && ordo.transportContents(Direction.UP).isEmpty(),
                "The two jars paid their exact costs");
        click(owner, altar.pos, wand);
        altar.matrix.advance(altar.level);
        test.assertTrue(altar.center().stack().is(Items.LODESTONE) && altar.center().stack().getCount() == 1,
                "Retrying cannot produce another output");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void transportObeysConnectionAndCancellationKeepsInputs(GameTestHelper test) {
        var altar = build(test);
        var owner = player(test);
        train(test, owner);
        var wand = chargedWand();
        click(owner, altar.pos, wand);
        setLodestoneInputs(altar, 2);

        var inlet = altar.pos.east();
        var source = jar(altar, inlet.below(), ORDO, 2);
        altar.level.setBlockAndUpdate(inlet, Blocks.STONE.defaultBlockState());
        jar(altar, altar.pos.offset(-1, -2, 2), TERRA, 2);
        click(owner, altar.pos, wand);
        test.assertTrue(altar.matrix.reading().total() == 0, "A covered jar is unavailable without a pipe path");

        altar.level.setBlockAndUpdate(inlet, EssentiaTransport.TUBE.defaultBlockState());
        click(owner, altar.pos, wand);
        test.assertTrue(altar.matrix.reading().total() == 4, "Side inlet finds the jar through existing transport");
        altar.matrix.advance(altar.level);
        test.assertTrue(source.transportContents(Direction.UP).getOrDefault(ORDO, 0) == 1, "One unit passed through the tube");

        altar.level.setBlockAndUpdate(inlet, Blocks.STONE.defaultBlockState());
        altar.matrix.advance(altar.level);
        test.assertValueEqual(altar.matrix.reading().status(), "infusion_essentia", "A blocked path pauses the ritual");
        test.assertTrue(altar.matrix.reading().absorbed() == 1, "Blocking a path neither refunds nor consumes essentia");
        owner.setShiftKeyDown(true);
        click(owner, altar.pos, wand);
        test.assertValueEqual(altar.matrix.reading().status(), "infusion_cancelled", "Sneak-click with the wand cancels as owner");
        test.assertTrue(altar.center().stack().is(Items.CHISELED_STONE_BRICKS) && altar.ring(0).stack().is(Items.IRON_INGOT),
                "Cancellation preserves the item inputs");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void instabilityPicksOneComponentAndAddsFlux(GameTestHelper test) {
        var altar = build(test);
        var player = player(test);
        train(test, player);
        var wand = chargedWand();
        click(player, altar.pos, wand);
        setLodestoneInputs(altar, 3);
        jar(altar, altar.pos.offset(1, -2, 2), TERRA, 2);
        jar(altar, altar.pos.offset(-1, -2, 2), ORDO, 2);
        click(player, altar.pos, wand);
        test.assertTrue(altar.matrix.instability(altar.level) == 5, "Separated unpaired components add instability");

        for (int x : new int[]{-4, 4}) {
            var candle = altar.pos.offset(x, -2, 0);
            altar.level.setBlockAndUpdate(candle.below(), Blocks.STONE.defaultBlockState());
            altar.level.setBlockAndUpdate(candle, Blocks.CANDLE.defaultBlockState());
        }
        test.assertTrue(altar.matrix.instability(altar.level) == 4, "An opposed candle pair lowers instability");
        var chunk = new net.minecraft.world.level.ChunkPos(altar.pos);
        float baseline = dev.thaumiclegacy.aura.AuraService.read(altar.level, chunk).flux();
        test.assertTrue(altar.matrix.accident(altar.level, 0), "Minimum chance roll produces a controlled accident");
        test.assertTrue(altar.ring(0).stack().isEmpty() != altar.ring(2).stack().isEmpty(),
                "Exactly one peripheral component is ejected");
        test.assertTrue(altar.center().stack().is(Items.CHISELED_STONE_BRICKS), "The central input survives");
        test.assertTrue(dev.thaumiclegacy.aura.AuraService.read(altar.level, chunk).flux() == baseline + 1,
                "An accident adds the configured Flux");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enderChestRitualCompletesOnServerTicks(GameTestHelper test) {
        var altar = build(test);
        var player = player(test);
        train(test, player);
        var wand = chargedWand();
        click(player, altar.pos, wand);
        altar.center().setStack(new ItemStack(Items.OBSIDIAN));
        for (int index = 0; index < 4; index++) {
            altar.ring(index).setStack(new ItemStack(Items.OBSIDIAN));
        }
        altar.ring(4).setStack(new ItemStack(Items.ENDER_PEARL));
        altar.ring(5).setStack(new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("lumen_glass"))));
        var alienis = jar(altar, altar.pos.offset(1, -2, 2), "thaumiclegacy:alienis", 3);
        var vacuos = jar(altar, altar.pos.offset(-1, -2, 2), "thaumiclegacy:vacuos", 3);
        click(player, altar.pos, wand);
        test.assertTrue(altar.matrix.instability(altar.level) == 0, "Three opposed pairs stabilize this recipe");
        test.runAfterDelay(55, () -> {
            test.assertTrue(altar.center().stack().is(Items.ENDER_CHEST), "The normal server ticker completes the recipe");
            for (int index = 0; index < 6; index++) {
                test.assertTrue(altar.ring(index).stack().isEmpty(), "All six components must be consumed");
            }
            test.assertValueEqual(alienis.transportContents(Direction.UP).get("thaumiclegacy:alienis"), 1, "Alienis remainder");
            test.assertValueEqual(vacuos.transportContents(Direction.UP).get("thaumiclegacy:vacuos"), 1, "Vacuos remainder");
            test.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void missingChunksPauseSavedRitualWithoutLoadingThem(GameTestHelper test) {
        var altar = build(test);
        var player = player(test);
        train(test, player);
        var wand = chargedWand();
        click(player, altar.pos, wand);
        setLodestoneInputs(altar, 2);
        jar(altar, altar.pos.offset(1, -2, 2), TERRA, 2);
        jar(altar, altar.pos.offset(-1, -2, 2), ORDO, 2);
        click(player, altar.pos, wand);
        altar.matrix.advance(altar.level);

        var remote = new BlockPos(100_000, altar.pos.getY(), 100_000);
        test.assertFalse(altar.level.hasChunkAt(remote), "Remote chunk starts unloaded");
        var registry = altar.level.registryAccess();
        var saved = altar.matrix.saveWithoutMetadata(registry);
        var restored = new RunicMatrixEntity(remote, altar.matrix.getBlockState());
        restored.loadWithComponents(saved, registry);
        restored.advance(altar.level);

        test.assertValueEqual(restored.reading().status(), "infusion_paused", "Missing structure chunks pause the ritual");
        test.assertValueEqual(restored.saveWithoutMetadata(registry), saved, "Pausing leaves the saved transaction intact");
        test.assertFalse(altar.level.hasChunkAt(remote), "The ritual never loads the missing chunk");
        test.succeed();
    }

    private static Altar build(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(5, 4, 5));
        level.setBlockAndUpdate(pos.below(2), Infusion.PEDESTAL.defaultBlockState());
        for (var offset : Infusion.RING) {
            level.setBlockAndUpdate(pos.offset(offset), Infusion.PEDESTAL.defaultBlockState());
        }
        for (int x : new int[]{-1, 1}) {
            for (int z : new int[]{-1, 1}) {
                level.setBlockAndUpdate(pos.offset(x, -2, z), Infusion.BRICKS.defaultBlockState());
                level.setBlockAndUpdate(pos.offset(x, -1, z), Infusion.STONE.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(pos, Infusion.MATRIX.defaultBlockState());
        return new Altar(level, pos, (RunicMatrixEntity) level.getBlockEntity(pos));
    }

    private static void setLodestoneInputs(Altar altar, int secondSlot) {
        altar.center().setStack(new ItemStack(Items.CHISELED_STONE_BRICKS));
        altar.ring(0).setStack(new ItemStack(Items.IRON_INGOT));
        altar.ring(secondSlot - 1).setStack(new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("resonant_copper"))));
    }

    private static EssentiaStorageBlockEntity jar(Altar altar, BlockPos pos, String aspect, int count) {
        altar.level.setBlockAndUpdate(pos, EssentiaLogistics.WARDED_JAR.defaultBlockState());
        var jar = (EssentiaStorageBlockEntity) altar.level.getBlockEntity(pos);
        for (int unit = 0; unit < count; unit++) {
            jar.receiveOne(Direction.UP, aspect, () -> true);
        }
        return jar;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void train(GameTestHelper test, ServerPlayer player) {
        var level = test.getLevel();
        var stone = test.absolutePos(new BlockPos(0, 1, 2));
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var meter = new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("thaumometer")));
        player.setItemInHand(InteractionHand.MAIN_HAND, meter);
        var center = stone.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
        player.gameMode.useItemOn(player, level, meter, InteractionHand.MAIN_HAND,
                new BlockHitResult(center, Direction.UP, stone, false));
        player.setShiftKeyDown(true);
        for (var item : new net.minecraft.world.item.Item[]{Items.TORCH, Items.FEATHER, Items.WATER_BUCKET, Items.GLASS_BOTTLE}) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
            player.gameMode.useItem(player, level, meter, InteractionHand.MAIN_HAND);
        }
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(false);
        ResearchProgress.recordLumenBrew(player);
        ResearchProgress.recordJarUse(player);
        NodeResearch.observe(player, UUID.randomUUID());
        NodeResearch.recordCharge(player);
        test.assertTrue(ResearchProgress.isUnlocked(player, Infusion.RESEARCH), "Existing research path unlocks Infusion Foundations");
    }

    private static ItemStack wand() {
        return new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("wand")));
    }

    private static ItemStack chargedWand() {
        var wand = wand();
        wand.set(WandVis.COMPONENT, RunicMatrixEntity.FORMATION_COST);
        return wand;
    }

    private static void click(ServerPlayer player, BlockPos pos, ItemStack wand) {
        var center = pos.getCenter();
        player.moveTo(center.x, center.y, center.z + 2, 180, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        player.gameMode.useItemOn(player, player.serverLevel(), wand, InteractionHand.MAIN_HAND,
                new BlockHitResult(center, Direction.SOUTH, pos, false));
    }

    private record Altar(ServerLevel level, BlockPos pos, RunicMatrixEntity matrix) {
        boolean formed() {
            return level.getBlockState(pos).getValue(RunicMatrixBlock.FORMED);
        }

        PedestalEntity center() {
            return (PedestalEntity) level.getBlockEntity(pos.below(2));
        }

        PedestalEntity ring(int index) {
            return (PedestalEntity) level.getBlockEntity(pos.offset(Infusion.RING.get(index)));
        }
    }
}
