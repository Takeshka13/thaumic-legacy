package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaPhial;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.workbench.ArcaneWorkbenchRecipe;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

public class AdvancedAlchemyGameTest implements FabricGameTest {
    private static final String AER = "thaumiclegacy:aer";
    private static final String AQUA = "thaumiclegacy:aqua";
    private static final String IGNIS = "thaumiclegacy:ignis";
    private static final String PERDITIO = "thaumiclegacy:perditio";
    private static final String MORTUUS = "thaumiclegacy:mortuus";
    private static final String VENENUM = "thaumiclegacy:venenum";

    @GameTest(template = EMPTY_STRUCTURE)
    public void loadsAdvancedEquipmentRecipesWithTheirResearchAndVisCosts(GameTestHelper test) {
        checkArcaneRecipe(test, "alchemical_centrifuge", ResearchProgress.ALCHEMICAL_CENTRIFUGE, 24, 1);
        checkArcaneRecipe(test, "thaumatorium", ResearchProgress.AUTOMATED_ALCHEMY, 32, 1);
        checkArcaneRecipe(test, "warded_jar_label", ResearchProgress.WARDED_JAR_LABELS, 4, 4);
        test.succeed();
    }

    private static void checkArcaneRecipe(GameTestHelper test, String path, ResourceLocation research, int vis, int count) {
        var level = test.getLevel();
        var recipe = (ArcaneWorkbenchRecipe) level.getRecipeManager().byKey(ThaumicLegacy.id("arcane_" + path))
            .orElseThrow().value();
        test.assertValueEqual(recipe.research(), Optional.of(research), "Recipe uses expected server-side research " + path);
        test.assertValueEqual(recipe.visCost(), vis, "Recipe uses expected scalar Vis for crafting " + path);
        var result = recipe.getResultItem(level.registryAccess());
        test.assertTrue(result.is(item(path)) && result.getCount() == count, "Recipe returns the real item and count for " + path);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
    public void separatesBothExactAspectIdsAndRejectsPrimals(GameTestHelper test) {
        var line = new Line(test, AdvancedAlchemy.ALCHEMICAL_CENTRIFUGE);
        var centrifuge = line.centrifuge();
        var player = newPlayer(test);
        line.deposit(player, AER, 1);
        line.deposit(player, MORTUUS, 1);
        test.assertTrue(line.transfer(), "Mortuus crosses the side input tube");
        test.assertFalse(line.transfer(), "A primal cannot enter the centrifuge");
        test.assertValueEqual(line.sourceStock(), Map.of(AER, 1), "Rejected primal is retained at the source");
        test.assertValueEqual(centrifuge.snapshot().input(), Map.of(MORTUUS, 1), "Mortuus keeps its own ID");
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(centrifuge.snapshot().output(), Map.of(AQUA, 1, PERDITIO, 1),
            "Mortuus separates into both of its registered parents")).thenExecute(() -> {
                test.assertTrue(centrifuge.snapshot().input().isEmpty(), "One committed cycle consumes exactly one compound");
                line.deposit(player, VENENUM, 1);
                test.assertTrue(line.transfer(), "Venenum remains independently routable");
                test.assertValueEqual(centrifuge.snapshot().input(), Map.of(VENENUM, 1),
                    "Venenum is stored as its exact ID despite its shared formula");
            }).thenWaitUntil(() -> test.assertValueEqual(centrifuge.snapshot().output(), Map.of(AQUA, 2, PERDITIO, 2),
                "Venenum separates according to its independent registered definition")).thenExecute(() -> {
                    test.assertTrue(centrifuge.snapshot().input().isEmpty(), "Second committed cycle consumes its compound");
                    test.assertValueEqual(line.sourceStock(), Map.of(AER, 1), "Both batches leave the rejected primal intact");
                    var outlet = line.target().above();
                    var jar = line.target().east();
                    line.level().setBlockAndUpdate(outlet, EssentiaTransport.TUBE.defaultBlockState());
                    line.level().setBlockAndUpdate(outlet.east(), EssentiaTransport.TUBE.defaultBlockState());
                    line.level().setBlockAndUpdate(jar, EssentiaLogistics.WARDED_JAR.defaultBlockState());
                    test.assertTrue(EssentiaTransport.tryTransfer(line.level(), line.target()), "The real upper outlet can feed a jar");
                    var sink = (EssentiaStorageBlockEntity) line.level().getBlockEntity(jar);
                    test.assertValueEqual(sink.snapshot().amount(), 1, "Exactly one parent reaches the jar on each hop cycle");
                    test.assertValueEqual(centrifuge.snapshot().output().values().stream().mapToInt(Integer::intValue).sum(), 3,
                        "Upper delivery debits only the sent unit");
                }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 145)
    public void centrifugeRequiresRoomForBothParentsBeforeSpendingInput(GameTestHelper test) {
        var line = new Line(test, AdvancedAlchemy.ALCHEMICAL_CENTRIFUGE);
        var level = line.level();
        var registries = level.registryAccess();
        var tagged = line.centrifuge().saveWithFullMetadata(registries);
        var output = new CompoundTag();
        output.putInt(AER, 63);
        tagged.put("output", output);
        var centrifuge = (AlchemicalCentrifugeBlockEntity) BlockEntity.loadStatic(line.target(),
            level.getBlockState(line.target()), tagged, registries);
        test.assertTrue(centrifuge != null, "Centrifuge accepts a legitimate saved output ledger");
        level.setBlockEntity(centrifuge);
        var player = newPlayer(test);
        line.deposit(player, MORTUUS, 1);
        test.assertTrue(line.transfer(), "Compound queues even when output is nearly saturated");
        test.runAfterDelay(15, () -> {
            var waiting = centrifuge.snapshot();
            test.assertValueEqual(waiting.progress(), 0, "One free unit cannot start a two-parent process");
            test.assertValueEqual(waiting.input(), Map.of(MORTUUS, 1), "Output blockage preserves Mortuus");
            test.assertValueEqual(waiting.output(), Map.of(AER, 63), "A refused batch produces no partial parent");
            centrifuge.extractOne(Direction.UP, AER);
            test.assertValueEqual(centrifuge.snapshot().output(), Map.of(AER, 62), "The real output port recovers one unit");
        });
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(centrifuge.snapshot().output(),
            Map.of(AER, 62, AQUA, 1, PERDITIO, 1), "Two free units permit precisely one full decomposition"))
            .thenExecute(() -> test.assertTrue(centrifuge.snapshot().input().isEmpty(), "Successful batch spends Mortuus once"))
            .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 185)
    public void appliesOneJsonRecipeOnlyAfterTheOwnerAndAllInputsAreReady(GameTestHelper test) {
        var line = new Line(test, AdvancedAlchemy.THAUMATORIUM);
        var machine = line.thaumatorium();
        var owner = newPlayer(test);
        var outsider = newPlayer(test);
        var recipe = ThaumicLegacy.id("ember_salt");
        trainOperator(test, owner);
        standNear(owner, line.target());
        standNear(outsider, line.target());
        test.assertFalse(machine.select(outsider, recipe), "A player missing Automated Alchemy cannot select the recipe");
        test.assertTrue(machine.select(owner, recipe), "Qualified player selects the bundled Crucible recipe directly");
        test.assertValueEqual(machine.snapshot().recipe(), Optional.of(recipe), "Selection retains the exact data-driven recipe ID");
        test.assertValueEqual(machine.snapshot().owner(), Optional.of(owner.getUUID()), "The server associates the selection with the owner");
        test.assertValueEqual(machine.snapshot().requirements(), Map.of(IGNIS, 4, PERDITIO, 2),
            "The selected JSON requirements are read without a second hard-coded recipe");
        var wrong = use(owner, line.target(), new ItemStack(Items.POISONOUS_POTATO), false);
        test.assertTrue(wrong.is(Items.POISONOUS_POTATO), "A mismatched catalyst stays with the user");
        var held = use(owner, line.target(), new ItemStack(Items.REDSTONE, 2), false);
        test.assertTrue(held.is(Items.REDSTONE) && held.getCount() == 1, "Exactly one matching catalyst is queued");
        line.deposit(owner, AER, 2);
        line.deposit(owner, IGNIS, 4);
        line.deposit(owner, PERDITIO, 1);
        for (int i = 0; i < 5; i++) {
            test.assertTrue(line.transfer(), "A matching unit enters through the side transport port");
        }
        test.assertFalse(line.transfer(), "Essentia the recipe does not request stays at the source");
        test.assertValueEqual(line.sourceStock(), Map.of(AER, 2), "Wrong-aspect source units are never silently consumed");
        test.runAfterDelay(16, () -> {
            var waiting = machine.snapshot();
            test.assertTrue(waiting.progress() == 0 && waiting.result().isEmpty() && waiting.catalyst().is(Items.REDSTONE),
                "Missing one unit preserves both the catalyst and the stored mixture");
            test.assertValueEqual(waiting.essentia(), Map.of(IGNIS, 4, PERDITIO, 1), "Stock remains intact after waiting");
            line.deposit(owner, PERDITIO, 1);
            test.assertTrue(line.transfer(), "The last matching unit enters through the tube");
        });
        test.startSequence().thenWaitUntil(() -> test.assertTrue(machine.snapshot().result().is(item("ember_salt")),
            "All JSON inputs permit the selected transaction")).thenExecute(() -> {
                var crafted = machine.snapshot();
                test.assertTrue(crafted.catalyst().isEmpty() && crafted.essentia().isEmpty(),
                    "The catalyst and exact recipe quantities are spent once");
                test.assertValueEqual(crafted.result().getCount(), 1, "The recipe produces precisely one Ember Salt");
                var result = use(owner, line.target(), ItemStack.EMPTY, false);
                test.assertTrue(result.is(item("ember_salt")) && result.getCount() == 1, "The result can be recovered through normal use");
                test.assertFalse(use(owner, line.target(), ItemStack.EMPTY, false).is(item("ember_salt")),
                    "Replaying collection cannot issue another product");
            }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void retainsAllInputsWhenTheSavedOwnerIsUnavailable(GameTestHelper test) {
        var line = new Line(test, AdvancedAlchemy.THAUMATORIUM);
        var level = line.level();
        var registries = level.registryAccess();
        var owner = UUID.randomUUID();
        var serialized = line.thaumatorium().saveWithFullMetadata(registries);
        serialized.putString("recipe", "thaumiclegacy:ember_salt");
        serialized.putUUID("owner", owner);
        var mixture = new CompoundTag();
        mixture.putInt(IGNIS, 4);
        mixture.putInt(PERDITIO, 2);
        serialized.put("essentia", mixture);
        serialized.put("catalyst", new ItemStack(Items.REDSTONE).save(registries));
        var reloaded = (ThaumatoriumBlockEntity) BlockEntity.loadStatic(line.target(), level.getBlockState(line.target()),
            serialized, registries);
        test.assertTrue(reloaded != null, "Selected recipe and queued resources survive a block-entity load");
        level.setBlockEntity(reloaded);
        test.runAfterDelay(15, () -> {
            var waiting = reloaded.snapshot();
            test.assertValueEqual(waiting.owner(), Optional.of(owner), "The saved owner is not replaced by a nearby player");
            test.assertTrue(waiting.progress() == 0 && waiting.result().isEmpty() && waiting.catalyst().is(Items.REDSTONE),
                "An offline owner cannot authorize the transaction");
            test.assertValueEqual(waiting.status(), AdvancedAlchemy.Status.OWNER_UNAVAILABLE, "Offline owner has an explicit wait state");
            test.assertValueEqual(waiting.essentia(), Map.of(IGNIS, 4, PERDITIO, 2),
                "All otherwise sufficient loaded resources stay unspent");
            test.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 145)
    public void resumesInFlightThaumatoriumAfterReloadAndIssuesOneResult(GameTestHelper test) {
        var line = new Line(test, AdvancedAlchemy.THAUMATORIUM);
        var owner = newPlayer(test);
        trainOperator(test, owner);
        standNear(owner, line.target());
        test.assertTrue(line.thaumatorium().select(owner, ThaumicLegacy.id("ember_salt")), "The owner starts a known recipe");
        use(owner, line.target(), new ItemStack(Items.REDSTONE), false);
        line.deposit(owner, IGNIS, 4);
        line.deposit(owner, PERDITIO, 2);
        for (int i = 0; i < 6; i++) {
            test.assertTrue(line.transfer(), "The alembic supplies each required unit");
        }

        test.startSequence().thenWaitUntil(() -> test.assertTrue(line.thaumatorium().snapshot().progress() >= 40,
            "The operation has advanced before saving")).thenExecute(() -> {
                var level = line.level();
                var old = line.thaumatorium();
                var saved = old.saveWithFullMetadata(level.registryAccess());
                var loaded = (ThaumatoriumBlockEntity) BlockEntity.loadStatic(line.target(), old.getBlockState(),
                    saved, level.registryAccess());
                test.assertTrue(loaded != null && loaded != old, "The saved machine loads into a fresh entity");
                level.setBlockEntity(loaded);
                var state = loaded.snapshot();
                test.assertValueEqual(state.progress(), old.snapshot().progress(), "Elapsed work survives serialization");
                test.assertValueEqual(state.essentia(), Map.of(IGNIS, 4, PERDITIO, 2), "The saved inputs remain unspent");
                test.assertTrue(state.catalyst().is(Items.REDSTONE) && state.result().isEmpty(), "Reload cannot issue a result early");
            }).thenWaitUntil(() -> test.assertTrue(line.thaumatorium().snapshot().result().is(item("ember_salt")),
                "The reloaded machine completes within the original work budget")).thenExecute(() -> {
                    var state = line.thaumatorium().snapshot();
                    test.assertValueEqual(state.result().getCount(), 1, "The resumed transaction produces a single item");
                    test.assertTrue(state.essentia().isEmpty() && state.catalyst().isEmpty(), "The original costs are debited exactly once");
                    test.assertTrue(use(owner, line.target(), ItemStack.EMPTY, false).is(item("ember_salt")), "The owner can collect the result");
                    test.assertFalse(use(owner, line.target(), ItemStack.EMPTY, false).is(item("ember_salt")), "Repeated collection gives no copy");
                }).thenSucceed();
    }

    private static void trainOperator(GameTestHelper test, ServerPlayer player) {
        var level = test.getLevel();
        var stone = test.absolutePos(new BlockPos(0, 1, 2));
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var lens = new ItemStack(item("field_lens"));
        player.setItemInHand(InteractionHand.MAIN_HAND, lens);
        var center = stone.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
        player.gameMode.useItemOn(player, level, lens, InteractionHand.MAIN_HAND,
            new BlockHitResult(center, Direction.UP, stone, false));
        for (Item item : new Item[]{Items.TORCH, Items.FEATHER, Items.WATER_BUCKET, Items.GLASS_BOTTLE,
                Items.ENDER_PEARL, Items.REDSTONE}) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
            player.setShiftKeyDown(true);
            player.gameMode.useItem(player, level, lens, InteractionHand.MAIN_HAND);
        }
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        ResearchProgress.recordLumenBrew(player);
        ResearchProgress.recordJarUse(player);
        ResearchProgress.recordTubePlacement(player);
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.IMPROVED_DISTILLATION),
            "Controls plus discoveries cover the improved distillation prerequisites");
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ALCHEMICAL_CENTRIFUGE),
            "Motus opens the centrifuge after improved distillation");
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.AUTOMATED_ALCHEMY),
            "Label research and Potentia complete Automated Alchemy");
    }

    @SuppressWarnings("removal")
    private static ServerPlayer newPlayer(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack use(ServerPlayer player, BlockPos pos, ItemStack stack, boolean sneak) {
        standNear(player, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(sneak);
        UseBlockCallback.EVENT.invoker().interact(player, player.serverLevel(), InteractionHand.MAIN_HAND,
            new BlockHitResult(pos.getCenter(), Direction.SOUTH, pos, false));
        player.setShiftKeyDown(false);
        return player.getMainHandItem();
    }

    private static void standNear(ServerPlayer player, BlockPos pos) {
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5, 180, 0);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(ThaumicLegacy.id(path));
    }

    private record Line(ServerLevel level, BlockPos source, BlockPos target) {
        private Line(GameTestHelper test, Block machine) {
            this(test.getLevel(), test.absolutePos(new BlockPos(0, 1, 0)),
                test.absolutePos(new BlockPos(3, 1, 0)));
            level.setBlockAndUpdate(source, EssentiaLogistics.ARCANE_ALEMBIC.defaultBlockState());
            level.setBlockAndUpdate(source.east(), EssentiaTransport.TUBE.defaultBlockState());
            level.setBlockAndUpdate(source.east(2), EssentiaTransport.TUBE.defaultBlockState());
            level.setBlockAndUpdate(target, machine.defaultBlockState());
        }

        private AlchemicalCentrifugeBlockEntity centrifuge() {
            return (AlchemicalCentrifugeBlockEntity) level.getBlockEntity(target);
        }

        private ThaumatoriumBlockEntity thaumatorium() {
            return (ThaumatoriumBlockEntity) level.getBlockEntity(target);
        }

        private void deposit(ServerPlayer player, String aspect, int amount) {
            var phial = new ItemStack(EssentiaLogistics.ESSENTIA_PHIAL);
            phial.set(EssentiaPhial.CONTENTS, new EssentiaPhial.Contents(aspect, amount));
            use(player, source, phial, false);
        }

        private boolean transfer() {
            return EssentiaTransport.tryTransfer(level, source);
        }

        private Map<String, Integer> sourceStock() {
            return ((EssentiaStorageBlockEntity) level.getBlockEntity(source)).snapshot().aspects();
        }
    }
}
