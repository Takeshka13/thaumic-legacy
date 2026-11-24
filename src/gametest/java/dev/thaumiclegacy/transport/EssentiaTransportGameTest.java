package dev.thaumiclegacy.transport;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaPhial;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.workbench.ArcaneWorkbenchRecipe;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class EssentiaTransportGameTest implements FabricGameTest {
    private static final String ORDO = "thaumiclegacy:ordo";
    private static final String TERRA = "thaumiclegacy:terra";
    private static final String AER = "thaumiclegacy:aer";

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void movesOneUnitAtATimeAcrossMultipleTubesAndDownIntoJar(GameTestHelper test) {
        var line = new Line(test);
        var player = newPlayer(test);
        deposit(player, line.source(), ORDO, 3);
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 3), "The real phial seeds the alembic");
        test.startSequence()
            .thenWaitUntil(() -> test.assertValueEqual(line.stock(line.jar()), Map.of(ORDO, 1),
                "The actual alembic ticker moves only one unit through multiple tubes"))
            .thenExecute(() -> {
                test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 2), "One arrival debits exactly one source unit");
                var reading = EssentiaTransport.read(line.level(), line.tube(2)).orElseThrow();
                test.assertValueEqual(reading.aspect(), ORDO, "Transit is exposed by the tube readout");
                test.assertValueEqual(reading.amount(), 1, "A recent readout represents one timed transfer");
            })
            .thenWaitUntil(() -> test.assertValueEqual(line.stock(line.jar()), Map.of(ORDO, 3),
                "The subsequent ticker polls complete delivery"))
            .thenExecute(() -> test.assertTrue(line.stock(line.source()).isEmpty(), "The depleted source cannot duplicate stock"))
            .thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void refusesClosedOrMisalignedControlsAndHonorsTheConfiguredAspect(GameTestHelper test) {
        var line = new Line(test);
        var player = newPlayer(test);
        var valvePos = line.tube(2);
        var filterPos = line.tube(3);
        var valve = EssentiaTransport.VALVE.defaultBlockState().setValue(TransportBlock.AXIS, Direction.Axis.X);
        line.place(valvePos, valve.setValue(TransportBlock.OPEN, false));
        line.place(filterPos, EssentiaTransport.FILTER.defaultBlockState().setValue(TransportBlock.AXIS, Direction.Axis.X));
        var filter = (TransportBlockEntity) line.level().getBlockEntity(filterPos);
        deposit(player, line.source(), ORDO, 3);
        deposit(player, line.source(), TERRA, 4);
        filter.configure(TERRA);

        EssentiaTransport.tryTransfer(line.level(), line.source());
        test.assertTrue(line.stock(line.jar()).isEmpty(), "Closed valve blocks all aspects");
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 3, TERRA, 4), "Closed route cannot debit stock");

        line.place(valvePos, valve.setValue(TransportBlock.AXIS, Direction.Axis.Z));
        EssentiaTransport.tryTransfer(line.level(), line.source());
        test.assertTrue(line.stock(line.jar()).isEmpty(), "An open valve on the wrong axis cannot fork a route");
        line.place(valvePos, valve.setValue(TransportBlock.OPEN, true));
        EssentiaTransport.tryTransfer(line.level(), line.source());
        test.assertValueEqual(line.stock(line.jar()), Map.of(TERRA, 1), "Only the allowed aspect traverses the aligned filter");
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 3, TERRA, 3), "A rejected aspect remains at source");

        var tag = filter.saveWithFullMetadata(line.level().registryAccess());
        var decoded = (TransportBlockEntity) BlockEntity.loadStatic(filterPos, filter.getBlockState(), tag,
            line.level().registryAccess());
        test.assertTrue(decoded != null && decoded != filter, "Settings load into a new transport block entity");
        line.level().setBlockEntity(decoded);
        test.assertValueEqual(EssentiaTransport.read(line.level(), filterPos).orElseThrow().filter(), TERRA,
            "The filter aspect survives roundtrip through block-entity NBT");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void cyclesAndBlockedReceiversDoNotDebitOrDuplicateEssentia(GameTestHelper test) {
        var line = new Line(test);
        var player = newPlayer(test);
        for (int x = 1; x <= 3; x++) {
            line.place(line.tube(x).south(), EssentiaTransport.TUBE.defaultBlockState());
        }
        deposit(player, line.source(), ORDO, 4);
        deposit(player, line.jar(), TERRA, 1);
        for (int i = 0; i < 4; i++) {
            EssentiaTransport.tryTransfer(line.level(), line.source());
        }
        test.assertValueEqual(line.stock(line.jar()), Map.of(TERRA, 1),
            "A cyclic route cannot change the single aspect in an incompatible jar");
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 4), "An incompatible sink spends nothing");
        use(player, line.jar(), phial(), false);
        for (int i = 0; i < 8; i++) {
            deposit(player, line.jar(), ORDO, 8);
        }
        EssentiaTransport.tryTransfer(line.level(), line.source());
        test.assertValueEqual(line.stock(line.jar()), Map.of(ORDO, 64), "A full jar never overfills");
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 4), "Full output preserves every source unit");
        use(player, line.jar(), phial(), false);
        EssentiaTransport.tryTransfer(line.level(), line.source());
        test.assertValueEqual(line.stock(line.jar()), Map.of(ORDO, 57), "One freed path commits just one transfer");
        test.assertValueEqual(line.stock(line.source()), Map.of(ORDO, 3), "Multiple cyclic routes do not multiply the transfer");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void respectsTubeHopLimitsAndDoesNotLoadAnUnloadedSourceChunk(GameTestHelper test) {
        var level = test.getLevel();
        var start = test.absolutePos(new BlockPos(0, 2, 0));
        level.setBlockAndUpdate(start, EssentiaLogistics.ARCANE_ALEMBIC.defaultBlockState());
        for (int height = 1; height <= 36; height++) {
            level.setBlockAndUpdate(start.above(height), EssentiaTransport.TUBE.defaultBlockState());
        }
        var top = start.above(36).east();
        level.setBlockAndUpdate(top, EssentiaTransport.TUBE.defaultBlockState());
        level.setBlockAndUpdate(top.below(), EssentiaLogistics.WARDED_JAR.defaultBlockState());
        var player = newPlayer(test);
        deposit(player, start, ORDO, 4);
        EssentiaTransport.tryTransfer(level, start);
        test.assertValueEqual(stock(level, start), Map.of(ORDO, 4), "Sink beyond the hop budget cannot spend source stock");
        test.assertTrue(stock(level, top.below()).isEmpty(), "Out-of-range sink stays empty");

        var absent = new BlockPos(1_510_400, start.getY(), -1_510_400);
        int x = absent.getX() >> 4;
        int z = absent.getZ() >> 4;
        test.assertFalse(level.hasChunk(x, z), "Far fixture starts unloaded");
        EssentiaTransport.tryTransfer(level, absent);
        test.assertTrue(EssentiaTransport.read(level, absent).isEmpty(), "Readout declines an unloaded route source");
        test.assertFalse(level.hasChunk(x, z), "Transport operations do not load a missing source chunk");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void controlsUnlockForThePlayerWhoFillsAndPlacesAndRequireKnownAspects(GameTestHelper test) {
        var level = test.getLevel();
        var owner = newPlayer(test);
        var other = newPlayer(test);
        var jarResearch = ThaumicLegacy.id("warded_jars");
        var transportResearch = ThaumicLegacy.id("essentia_transport");
        var controlsResearch = ThaumicLegacy.id("essentia_controls");
        var recipes = Map.of("arcane_essentia_tube", transportResearch,
            "arcane_essentia_valve", controlsResearch, "arcane_essentia_filter", controlsResearch);
        for (var recipe : recipes.entrySet()) {
            var loaded = (ArcaneWorkbenchRecipe) level.getRecipeManager().byKey(ThaumicLegacy.id(recipe.getKey()))
                .orElseThrow().value();
            test.assertValueEqual(loaded.research(), Optional.of(recipe.getValue()), "Recipe limits: " + recipe.getKey());
        }

        var stone = test.absolutePos(new BlockPos(0, 1, 0));
        var torch = stone.east();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        for (var player : List.of(owner, other)) {
            var lens = new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens")));
            player.setItemInHand(InteractionHand.MAIN_HAND, lens);
            scanBlock(player, stone);
            scanBlock(player, torch);
            scanOffhand(player, new ItemStack(Items.FEATHER));
            scanOffhand(player, new ItemStack(Items.WATER_BUCKET));
            test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ALCHEMY_FOUNDATIONS),
                "Observation evidence qualifies each fixture player for alchemy");
            ResearchProgress.recordLumenBrew(player);
            test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ESSENTIA_DISTILLATION),
                "Distillation is the initial prerequisite");
            test.assertFalse(ResearchProgress.isUnlocked(player, jarResearch), "Distillation alone does not complete jar study");
        }

        var jar = stone.south(2);
        level.setBlockAndUpdate(jar, EssentiaLogistics.WARDED_JAR.defaultBlockState());
        use(owner, jar, phial(), false);
        test.assertFalse(ResearchProgress.isUnlocked(owner, jarResearch), "An empty phial into an empty jar awards no evidence");
        deposit(owner, jar, TERRA, 1);
        test.assertTrue(ResearchProgress.isUnlocked(owner, jarResearch), "Manual deposit into a jar completes its study");
        test.assertFalse(ResearchProgress.isUnlocked(other, jarResearch), "A nearby player cannot get credit for someone's transfer");
        test.assertFalse(ResearchProgress.isUnlocked(owner, transportResearch), "Vitreus must be studied before basic tubing");

        var earlyTube = jar.east(2);
        placeTube(owner, earlyTube);
        test.assertTrue(level.getBlockState(earlyTube).is(EssentiaTransport.TUBE), "A fixture-supplied tube can be placed physically");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens"))));
        scanOffhand(owner, new ItemStack(Items.GLASS_BOTTLE));
        test.assertTrue(ResearchProgress.isUnlocked(owner, transportResearch), "Vitreus and Warded Jars unlock the tube recipe");
        test.assertFalse(ResearchProgress.isUnlocked(owner, controlsResearch), "An early block placement cannot retroactively grant controls");

        var qualifyingTube = earlyTube.south();
        placeTube(owner, qualifyingTube);
        test.assertTrue(ResearchProgress.isUnlocked(owner, controlsResearch), "Owner's first qualified tube placement unlocks controls");
        test.assertFalse(ResearchProgress.isUnlocked(other, controlsResearch), "The other observer remains locked");
        var filterPos = qualifyingTube.east();
        level.setBlockAndUpdate(filterPos, EssentiaTransport.FILTER.defaultBlockState().setValue(TransportBlock.AXIS, Direction.Axis.X));
        use(other, filterPos, ItemStack.EMPTY, false);
        test.assertTrue(EssentiaTransport.read(level, filterPos).orElseThrow().filter().isEmpty(),
            "An unqualified player cannot configure the filter");
        use(owner, filterPos, ItemStack.EMPTY, false);
        var configured = EssentiaTransport.read(level, filterPos).orElseThrow().filter();
        test.assertFalse(configured.isEmpty(), "Qualified player can assign a known aspect by hand");
        test.assertTrue(ResearchProgress.knownAspects(owner).contains(configured), "Filter uses a learned aspect");
        use(owner, filterPos, ItemStack.EMPTY, true);
        test.assertTrue(EssentiaTransport.read(level, filterPos).orElseThrow().filter().isEmpty(),
            "Crouch-use clears the filter");
        test.succeed();
    }

    private static void placeTube(ServerPlayer player, BlockPos pos) {
        var ground = pos.below();
        var center = ground.getCenter();
        var level = player.serverLevel();
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        player.moveTo(center.x, ground.getY() + 1.0, center.z + 1.5, 180, 0);
        var tube = new ItemStack(EssentiaTransport.TUBE);
        player.setItemInHand(InteractionHand.MAIN_HAND, tube);
        player.gameMode.useItemOn(player, level, tube, InteractionHand.MAIN_HAND,
            new BlockHitResult(center.add(0, 0.5, 0), Direction.UP, ground, false));
    }

    private static void scanBlock(ServerPlayer player, BlockPos pos) {
        var center = pos.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
        player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
            new BlockHitResult(center, Direction.UP, pos, false));
    }

    private static void scanOffhand(ServerPlayer player, ItemStack specimen) {
        player.setItemInHand(InteractionHand.OFF_HAND, specimen);
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static ItemStack phial() {
        return new ItemStack(EssentiaLogistics.ESSENTIA_PHIAL);
    }

    private static void deposit(ServerPlayer player, BlockPos pos, String aspect, int amount) {
        var stack = phial();
        stack.set(EssentiaPhial.CONTENTS, new EssentiaPhial.Contents(aspect, amount));
        use(player, pos, stack, false);
    }

    private static Map<String, Integer> stock(ServerLevel level, BlockPos pos) {
        return ((EssentiaStorageBlockEntity) level.getBlockEntity(pos)).snapshot().aspects();
    }

    @SuppressWarnings("removal")
    private static ServerPlayer newPlayer(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void use(ServerPlayer player, BlockPos pos, ItemStack stack, boolean crouch) {
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5, 180, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(crouch);
        UseBlockCallback.EVENT.invoker().interact(player, player.serverLevel(), InteractionHand.MAIN_HAND,
            new BlockHitResult(pos.getCenter(), Direction.SOUTH, pos, false));
        player.setShiftKeyDown(false);
    }

    private record Line(ServerLevel level, BlockPos source) {
        private Line(GameTestHelper test) {
            this(test.getLevel(), test.absolutePos(new BlockPos(0, 2, 0)));
            place(source, EssentiaLogistics.ARCANE_ALEMBIC.defaultBlockState());
            for (int x = 1; x <= 4; x++) {
                place(tube(x), EssentiaTransport.TUBE.defaultBlockState());
            }
            place(jar(), EssentiaLogistics.WARDED_JAR.defaultBlockState());
        }

        private BlockPos tube(int x) {
            return source.east(x);
        }

        private BlockPos jar() {
            return tube(4).below();
        }

        private void place(BlockPos pos, BlockState state) {
            level.setBlockAndUpdate(pos, state);
        }

        private Map<String, Integer> stock(BlockPos pos) {
            return EssentiaTransportGameTest.stock(level, pos);
        }
    }
}
