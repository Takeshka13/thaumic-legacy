package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.ThaumicLegacy;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class EssentiaLogisticsGameTest implements FabricGameTest {
    private static final String ORDO = "thaumiclegacy:ordo";
    private static final String TERRA = "thaumiclegacy:terra";
    private static final String AER = "thaumiclegacy:aer";

    @GameTest(template = EMPTY_STRUCTURE)
    public void registersEssentiaEquipmentAndGatedRecipes(GameTestHelper test) {
        var fixture = new Fixture(test);
        fixture.placeAlembic();
        var jar = fixture.placeJar(2);
        test.assertTrue(fixture.furnace().getType() == EssentiaLogistics.FURNACE_ENTITY,
            "The furnace uses its registered block entity");
        test.assertTrue(fixture.alembic().getType() == EssentiaLogistics.STORAGE_ENTITY
                && fixture.storage(jar).getType() == EssentiaLogistics.STORAGE_ENTITY,
            "Both storage blocks use their registered block entity");
        test.assertTrue(item("essentia_phial") == EssentiaLogistics.ESSENTIA_PHIAL, "The phial item is registered");
        checkArcaneRecipe(test, "arcane_alchemical_furnace", "alchemical_furnace", 18);
        checkArcaneRecipe(test, "arcane_alembic", "arcane_alembic", 16);
        checkArcaneRecipe(test, "arcane_warded_jar", "warded_jar", 10);
        var phialRecipe = (CraftingRecipe) fixture.level().getRecipeManager()
            .byKey(ThaumicLegacy.id("essentia_phial")).orElseThrow().value();
        var phialInput = CraftingInput.of(2, 1, List.of(stack(Items.COPPER_INGOT), stack(Items.GLASS_BOTTLE)));
        test.assertTrue(phialRecipe.matches(phialInput, fixture.level()), "An empty phial is made with normal crafting");
        test.assertTrue(phialRecipe.assemble(phialInput, fixture.level().registryAccess()).is(EssentiaLogistics.ESSENTIA_PHIAL),
            "Ordinary phial recipe produces the registered phial");
        test.succeed();
    }

    private static void checkArcaneRecipe(GameTestHelper test, String recipeId, String resultId, int visCost) {
        var level = test.getLevel();
        var recipe = level.getRecipeManager().byKey(ThaumicLegacy.id(recipeId)).orElseThrow().value();
        test.assertTrue(recipe instanceof ArcaneWorkbenchRecipe, "Logistics equipment uses arcane crafting: " + recipeId);
        var arcane = (ArcaneWorkbenchRecipe) recipe;
        test.assertValueEqual(arcane.research(), Optional.of(ResearchProgress.ESSENTIA_DISTILLATION),
            "Equipment recipe is gated behind distillation: " + recipeId);
        test.assertValueEqual(arcane.visCost(), visCost, "Vis cost: " + recipeId);
        test.assertTrue(arcane.getResultItem(level.registryAccess()).is(item(resultId)),
            "Equipment recipe yields the registered item: " + resultId);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
    public void distillsIntoAlembicAndMovesExactEssentiaThroughPhials(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        var jar = fixture.placeJar(2);
        var other = fixture.placeJar(3);
        var unsupported = use(player, fixture.furnacePos(), stack(Items.POISONOUS_POTATO), false);
        test.assertTrue(unsupported.is(Items.POISONOUS_POTATO), "A source without an aspect profile is refused");
        test.assertTrue(use(player, fixture.furnacePos(), stack(Items.STONE), false).isEmpty(), "An assigned source is accepted");
        test.assertTrue(use(player, fixture.furnacePos(), stack(Items.CHARCOAL), true).isEmpty(), "Sneaking queues fuel");
        test.runAfterDelay(10, () -> {
            var stalled = fixture.furnace().snapshot();
            test.assertTrue(stalled.input().is(Items.STONE) && stalled.fuel().is(Items.CHARCOAL),
                "Without an attached alembic the queued source and fuel are retained");
            test.assertValueEqual(stalled.progress(), 0, "A furnace cannot distill without an alembic");
            fixture.placeAlembic();
        });
        test.startSequence().thenWaitUntil(() -> {
            var alembic = fixture.alembic();
            test.assertTrue(alembic != null, "The delayed alembic has been installed");
            test.assertValueEqual(alembic.snapshot().amount(), 5, "One stone has completed distillation");
        }).thenExecute(() -> {
                test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 1, TERRA, 4),
                    "The alembic receives the entire stone profile");
                test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "One completed run spends one source");

                var ordo = use(player, fixture.alembicPos(), phial(), false);
                assertContents(test, ordo, ORDO, 1);
                var empty = use(player, jar, ordo, false);
                test.assertValueEqual(EssentiaPhial.contents(empty), EssentiaPhial.contents(phial()), "An accepted phial is emptied");
                test.assertValueEqual(fixture.storage(jar).snapshot().aspects(), Map.of(ORDO, 1), "Jar stores the extracted aspect");

                var terra = use(player, fixture.alembicPos(), phial(), false);
                assertContents(test, terra, TERRA, 4);
                var rejected = use(player, jar, terra, false);
                assertContents(test, rejected, TERRA, 4);
                test.assertValueEqual(fixture.storage(jar).snapshot().aspects(), Map.of(ORDO, 1),
                    "An incompatible phial does not alter a single-aspect jar");
                use(player, other, rejected, false);
                test.assertValueEqual(fixture.storage(other).snapshot().aspects(), Map.of(TERRA, 4),
                    "The second jar stores exactly the second aspect");
                test.assertValueEqual(fixture.alembic().snapshot().amount(), 0, "No Essentia is duplicated by transfer");
            }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 225)
    public void preservesQueuedInputAndFuelUntilTheEntireProfileFits(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        fixture.placeAlembic();
        for (int i = 0; i < 8; i++) {
            use(player, fixture.alembicPos(), filledPhial(ORDO, 8), false);
        }
        test.assertValueEqual(fixture.alembic().snapshot().amount(), 64, "Alembic capacity is 64 Essentia units");
        var rejected = use(player, fixture.alembicPos(), filledPhial(AER, 8), false);
        assertContents(test, rejected, AER, 8);
        test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 64), "Full storage refuses the entire transfer");
        use(player, fixture.furnacePos(), stack(Items.STONE), false);
        use(player, fixture.furnacePos(), stack(Items.CHARCOAL), true);
        test.runAfterDelay(15, () -> {
            var waiting = fixture.furnace().snapshot();
            test.assertValueEqual(waiting.progress(), 0, "Full output blocks distillation");
            test.assertTrue(waiting.input().is(Items.STONE) && waiting.fuel().is(Items.CHARCOAL),
                "A blocked cycle does not consume queued input or fuel");
            assertContents(test, use(player, fixture.alembicPos(), phial(), false), ORDO, 8);
        });
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(fixture.alembic().snapshot().amount(), 61,
            "Removing eight units lets the complete five-unit batch finish")).thenExecute(() -> {
                test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 57, TERRA, 4),
                    "Resuming a blocked cycle transfers one complete stone profile");
                test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "Resumed work consumes its source once");
                var overflow = use(player, fixture.alembicPos(), filledPhial(AER, 8), false);
                assertContents(test, overflow, AER, 5);
                test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 57, TERRA, 4, AER, 3),
                    "A precise partial deposit fills the remaining space and preserves the phial remainder");
            }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 170)
    public void spillsNoStockWhenMultipleAlembicsCannotAcceptTheFullBatch(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        var bottom = fixture.alembicPos();
        var upper = bottom.above();
        placeWithItem(player, bottom, stack(EssentiaLogistics.ARCANE_ALEMBIC.asItem()));
        placeWithItem(player, upper, stack(EssentiaLogistics.ARCANE_ALEMBIC.asItem()));
        test.assertTrue(fixture.storage(bottom) != null && fixture.storage(upper) != null,
            "The furnace and lower alembic accept ordinary placement from above");
        for (int i = 0; i < 7; i++) {
            use(player, bottom, filledPhial(ORDO, 8), false);
        }
        use(player, bottom, filledPhial(ORDO, 6), false);
        for (int i = 0; i < 8; i++) {
            use(player, upper, filledPhial(ORDO, 8), false);
        }
        use(player, fixture.furnacePos(), stack(Items.STONE), false);
        use(player, fixture.furnacePos(), stack(Items.CHARCOAL), true);
        test.runAfterDelay(12, () -> {
            test.assertValueEqual(fixture.storage(bottom).snapshot().amount(), 62, "No partial output is committed on a full stack");
            test.assertValueEqual(fixture.storage(upper).snapshot().amount(), 64, "A full upper alembic remains unchanged");
            var waiting = fixture.furnace().snapshot();
            test.assertTrue(waiting.input().is(Items.STONE) && waiting.fuel().is(Items.CHARCOAL) && waiting.progress() == 0,
                "There is no source, fuel, or timing loss while the complete batch will not fit");
            assertContents(test, use(player, upper, phial(), false), ORDO, 8);
        });
        test.startSequence().thenWaitUntil(() -> {
            test.assertValueEqual(fixture.storage(bottom).snapshot().amount(), 64, "Lower alembic fills first");
            test.assertValueEqual(fixture.storage(upper).snapshot().amount(), 59, "Upper alembic receives the remaining units");
        }).thenExecute(() -> {
            var lowerStock = fixture.storage(bottom).snapshot().aspects();
            var upperStock = fixture.storage(upper).snapshot().aspects();
            test.assertValueEqual(lowerStock.getOrDefault(TERRA, 0) + upperStock.getOrDefault(TERRA, 0), 4,
                "Exactly the four stone Terra units are distributed");
            test.assertValueEqual(lowerStock.getOrDefault(ORDO, 0) + upperStock.getOrDefault(ORDO, 0), 119,
                "Existing Ordo and the single new unit are conserved");
            test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "Only the completed batch consumes the input");
        }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void retainsJarContentsWhenSavedBrokenAndPlacedAgain(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        var jarPos = fixture.placeJar(2);
        use(player, jarPos, filledPhial(TERRA, 8), false);
        var storage = fixture.storage(jarPos);
        var registries = fixture.level().registryAccess();
        var state = storage.getBlockState();
        var reloaded = (EssentiaStorageBlockEntity) BlockEntity.loadStatic(jarPos, state,
            storage.saveWithFullMetadata(registries), registries);
        test.assertTrue(reloaded != null && reloaded != storage, "Stored jar reads into a fresh block entity");
        test.assertValueEqual(reloaded.snapshot(), storage.snapshot(), "Jar contents survive a block-entity NBT roundtrip");

        var savedPhial = (CompoundTag) filledPhial(TERRA, 4).save(registries);
        assertContents(test, ItemStack.parseOptional(registries, savedPhial), TERRA, 4);
        var jarStack = breakAndGetDrop(test, fixture.level(), jarPos, item("warded_jar"));
        placeWithItem(player, jarPos, jarStack);
        test.assertValueEqual(fixture.storage(jarPos).snapshot().aspects(), Map.of(TERRA, 8),
            "Placing the actual jar drop restores its contents");
        assertContents(test, use(player, jarPos, phial(), false), TERRA, 8);
        test.assertValueEqual(fixture.storage(jarPos).snapshot().amount(), 0, "Extraction after replacement drains the original contents once");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
    public void resumesAnInterruptedFurnaceFromItsRetainedBlockDrop(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        fixture.placeAlembic();
        use(player, fixture.furnacePos(), stack(Items.STONE), false);
        use(player, fixture.furnacePos(), stack(Items.LAVA_BUCKET), true);
        test.assertTrue(use(player, fixture.furnacePos(), stack(Items.STONE), false).is(Items.STONE),
            "A second feed item cannot replace the queued source");

        test.runAfterDelay(25, () -> {
            use(player, fixture.furnacePos(), stack(Items.CHARCOAL), true);
            var running = fixture.furnace().snapshot();
            test.assertTrue(running.progress() > 0 && running.progress() < running.duration(),
                "A distillation in progress can be interrupted before its output commits");
            test.assertTrue(running.fuelRemainder().is(Items.BUCKET) && running.fuel().is(Items.CHARCOAL),
                "Burning lava leaves its container and a new queued fuel remains available");
            var registries = fixture.level().registryAccess();
            var origin = fixture.furnacePos();
            var existing = fixture.furnace();
            var decoded = (EssentiaFurnaceBlockEntity) BlockEntity.loadStatic(origin, existing.getBlockState(),
                existing.saveWithFullMetadata(registries), registries);
            test.assertTrue(decoded != null && decoded != existing, "Furnace data loads into a fresh block entity");
            assertFurnaceRun(test, decoded.snapshot(), running);

            var retained = breakAndGetDrop(test, fixture.level(), origin, item("alchemical_furnace"));
            placeWithItem(player, origin, retained);
            assertFurnaceRun(test, fixture.furnace().snapshot(), running);
            test.assertValueEqual(fixture.alembic().snapshot().amount(), 0, "Reinstallation does not prematurely produce Essentia");
        });
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(fixture.alembic().snapshot().amount(), 5,
            "The originally queued input completes after resuming")).thenExecute(() -> {
                test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 1, TERRA, 4),
                    "Break and reload preserve exactly the expected Essentia");
                test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "Original queued inputs are consumed exactly once");
                test.assertTrue(use(player, fixture.furnacePos(), ItemStack.EMPTY, true).is(Items.BUCKET),
                    "The spent fuel container can be recovered");
                test.assertTrue(use(player, fixture.furnacePos(), ItemStack.EMPTY, true).is(Items.CHARCOAL),
                    "The unused queued fuel can be recovered");
                test.assertTrue(use(player, fixture.furnacePos(), ItemStack.EMPTY, true).isEmpty(),
                    "Repeated recovery creates no extra items");
            }).thenSucceed();
    }

    private static void assertFurnaceRun(GameTestHelper test, EssentiaFurnaceBlockEntity.FurnaceView actual,
                                         EssentiaFurnaceBlockEntity.FurnaceView expected) {
        test.assertTrue(ItemStack.matches(actual.input(), expected.input()), "Queued source stack survives");
        test.assertTrue(ItemStack.matches(actual.fuel(), expected.fuel()), "Queued fuel stack survives");
        test.assertTrue(ItemStack.matches(actual.inputRemainder(), expected.inputRemainder()), "Feed remainder survives");
        test.assertTrue(ItemStack.matches(actual.fuelRemainder(), expected.fuelRemainder()), "Spent fuel container survives");
        test.assertValueEqual(actual.progress(), expected.progress(), "In-progress cooking ticks survive");
        test.assertValueEqual(actual.burnTicks(), expected.burnTicks(), "Remaining burn ticks survive");
    }

    private static ItemStack breakAndGetDrop(GameTestHelper test, ServerLevel level, BlockPos pos, Item blockItem) {
        test.assertTrue(level.destroyBlock(pos, true), "Breaking the block runs normal loot");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1),
            entity -> entity.getItem().is(blockItem));
        test.assertValueEqual(drops.size(), 1, "Breaking produces one retained block item");
        var drop = drops.getFirst();
        var stack = drop.getItem().copy();
        test.assertValueEqual(stack.getCount(), 1, "Only one block item is returned");
        drop.discard();
        return stack;
    }

    private static void assertContents(GameTestHelper test, ItemStack stack, String aspect, int amount) {
        test.assertTrue(stack.is(EssentiaLogistics.ESSENTIA_PHIAL), "Operation returns an Essentia phial");
        var contents = EssentiaPhial.contents(stack);
        test.assertValueEqual(contents.aspect(), aspect, "Phial aspect identity");
        test.assertValueEqual(contents.amount(), amount, "Phial amount");
    }

    @SuppressWarnings("removal")
    private static ServerPlayer newPlayer(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack use(ServerPlayer player, BlockPos target, ItemStack held, boolean crouch) {
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 1.5, 180, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(crouch);
        UseBlockCallback.EVENT.invoker().interact(player, player.serverLevel(), InteractionHand.MAIN_HAND,
            new BlockHitResult(target.getCenter(), Direction.SOUTH, target, false));
        player.setShiftKeyDown(false);
        return player.getMainHandItem();
    }

    private static void placeWithItem(ServerPlayer player, BlockPos pos, ItemStack stack) {
        var ground = pos.below();
        var center = ground.getCenter();
        player.moveTo(center.x, ground.getY() + 1.0, center.z + 1.5, 180, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND,
            new BlockHitResult(center.add(0, 0.5, 0), Direction.UP, ground, false));
    }

    private static ItemStack phial() {
        return stack(EssentiaLogistics.ESSENTIA_PHIAL);
    }

    private static ItemStack filledPhial(String aspect, int amount) {
        var stack = phial();
        stack.set(EssentiaPhial.CONTENTS, new EssentiaPhial.Contents(aspect, amount));
        return stack;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(ThaumicLegacy.id(path));
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private record Fixture(ServerLevel level, BlockPos furnacePos) {
        private Fixture(GameTestHelper test) {
            this(test.getLevel(), test.absolutePos(new BlockPos(0, 1, 0)));
            place(furnacePos.below(), Blocks.STONE);
            place(furnacePos, EssentiaLogistics.ALCHEMICAL_FURNACE);
            level.setBlockAndUpdate(alembicPos(), Blocks.AIR.defaultBlockState());
        }

        private void placeAlembic() {
            place(alembicPos(), EssentiaLogistics.ARCANE_ALEMBIC);
        }

        private BlockPos placeJar(int x) {
            var pos = furnacePos.east(x);
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            place(pos, EssentiaLogistics.WARDED_JAR);
            return pos;
        }

        private BlockPos alembicPos() {
            return furnacePos.above();
        }

        private EssentiaFurnaceBlockEntity furnace() {
            return (EssentiaFurnaceBlockEntity) level.getBlockEntity(furnacePos);
        }

        private EssentiaStorageBlockEntity alembic() {
            return storage(alembicPos());
        }

        private EssentiaStorageBlockEntity storage(BlockPos pos) {
            return (EssentiaStorageBlockEntity) level.getBlockEntity(pos);
        }

        private void place(BlockPos pos, Block block) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }
}
