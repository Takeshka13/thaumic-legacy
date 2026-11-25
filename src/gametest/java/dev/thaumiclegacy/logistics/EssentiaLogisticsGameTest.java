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

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 150)
    public void distillsThroughMenuAndRoutesEachAspectSeparately(GameTestHelper test) {
        var fixture = new Fixture(test);
        var player = newPlayer(test);
        var menu = new EssentiaFurnaceMenu(1, player.getInventory(), fixture.furnace());
        test.assertFalse(menu.getSlot(0).mayPlace(stack(Items.POISONOUS_POTATO)), "Feed slot refuses unassigned items");
        menu.getSlot(0).set(stack(Items.STONE));
        player.getInventory().setItem(9, stack(Items.CHARCOAL));
        test.assertTrue(menu.quickMoveStack(player, 4).is(Items.CHARCOAL), "Shift-click loads fuel");
        fixture.placeAlembic();

        test.runAfterDelay(10, () -> {
            menu.broadcastChanges();
            test.assertTrue(menu.progress() > 0 && menu.burnTicks() > 0 && menu.burnTicks() < menu.burnCapacity(),
                "Menu properties reflect progress and remaining fuel");
        });
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(fixture.alembic().snapshot().aspects(),
            Map.of(ORDO, 1), "Lower alembic receives only the first aspect")).thenExecute(() -> {
                var furnace = fixture.furnace();
                var registries = fixture.level().registryAccess();
                var decoded = (EssentiaFurnaceBlockEntity) BlockEntity.loadStatic(fixture.furnacePos(), furnace.getBlockState(),
                    furnace.saveWithFullMetadata(registries), registries);
                test.assertValueEqual(decoded.snapshot().buffer(), Map.of(TERRA, 4), "Blocked stock survives NBT reload");
                test.assertValueEqual(furnace.snapshot().buffer(), Map.of(TERRA, 4), "Incompatible stock stays in the buffer");
                var rejected = use(player, fixture.alembicPos(), filledPhial(AER, 8), false);
                assertContents(test, rejected, AER, 8);
                var upper = fixture.alembicPos().above();
                placeWithItem(player, upper, stack(EssentiaLogistics.ARCANE_ALEMBIC.asItem()));
            }).thenWaitUntil(() -> test.assertValueEqual(fixture.storage(fixture.alembicPos().above()).snapshot().aspects(),
                Map.of(TERRA, 4), "Upper alembic receives the second aspect")).thenExecute(() -> {
                test.assertValueEqual(fixture.furnace().snapshot().buffered(), 0, "Both draws leave the buffer empty");
                test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "The batch consumes one feed");
                var ordo = use(player, fixture.alembicPos(), phial(), false);
                assertContents(test, ordo, ORDO, 1);
                var jar = fixture.placeJar(2);
                use(player, jar, ordo, false);
                test.assertValueEqual(fixture.storage(jar).snapshot().aspects(), Map.of(ORDO, 1), "Phials retain their transfer behavior");
            }).thenSucceed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void preservesMixedLegacyAlembicAsAQueue(GameTestHelper test) {
        var fixture = new Fixture(test);
        fixture.placeAlembic();
        var existing = fixture.alembic();
        var pos = fixture.alembicPos();
        var registries = fixture.level().registryAccess();
        var tag = existing.saveWithFullMetadata(registries);
        var mixed = new CompoundTag();
        mixed.putInt(ORDO, 2);
        mixed.putInt(TERRA, 5);
        tag.putInt("version", 1);
        tag.put("aspects", mixed);
        var loaded = (EssentiaStorageBlockEntity) BlockEntity.loadStatic(pos, existing.getBlockState(), tag, registries);
        var reloaded = (EssentiaStorageBlockEntity) BlockEntity.loadStatic(pos, existing.getBlockState(),
            loaded.saveWithFullMetadata(registries), registries);
        test.assertValueEqual(reloaded.snapshot().aspects(), Map.of(ORDO, 2), "Only one legacy aspect is active");
        test.assertValueEqual(reloaded.freeCapacity(), 57, "Both legacy aspects occupy capacity");
        test.assertFalse(reloaded.canAccept(Map.of(TERRA, 1)), "Reserved aspect cannot mix into the active stock");
        test.assertFalse(reloaded.canAccept(Map.of(ORDO, 58)), "The reserve reduces free capacity");
        fixture.level().setBlockEntity(reloaded);
        test.assertTrue(reloaded.extractOne(Direction.EAST, ORDO), "The first active unit is extracted");
        test.assertTrue(reloaded.extractOne(Direction.EAST, ORDO), "The second active unit is extracted");
        test.assertValueEqual(reloaded.snapshot().aspects(), Map.of(TERRA, 5), "The second aspect becomes available in full");
        test.assertFalse(reloaded.extractOne(Direction.EAST, ORDO), "The first aspect cannot be withdrawn twice");
        for (int i = 0; i < 5; i++) {
            test.assertTrue(reloaded.extractOne(Direction.EAST, TERRA), "Each reserved unit is recovered");
        }
        test.assertFalse(reloaded.extractOne(Direction.EAST, TERRA), "The reserve cannot be drawn twice");
        test.assertTrue(reloaded.canAccept(Map.of(AER, 8)), "A drained alembic can change aspects");
        test.succeed();
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
        fixture.furnace().inventory().setItem(EssentiaFurnaceBlockEntity.INPUT, stack(Items.STONE));
        fixture.furnace().inventory().setItem(EssentiaFurnaceBlockEntity.FUEL, stack(Items.LAVA_BUCKET));

        test.runAfterDelay(25, () -> {
            fixture.furnace().inventory().setItem(EssentiaFurnaceBlockEntity.FUEL, stack(Items.CHARCOAL));
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
        test.startSequence().thenWaitUntil(() -> test.assertValueEqual(fixture.alembic().snapshot().amount(), 1,
            "The originally queued input completes after resuming")).thenExecute(() -> {
                test.assertValueEqual(fixture.alembic().snapshot().aspects(), Map.of(ORDO, 1),
                    "Break and reload preserve exactly the expected Essentia");
                test.assertTrue(fixture.furnace().snapshot().input().isEmpty(), "Original queued inputs are consumed exactly once");
                test.assertValueEqual(fixture.furnace().snapshot().buffer(), Map.of(TERRA, 4),
                    "Remaining stock stays in the reinstalled furnace");
                var menu = new EssentiaFurnaceMenu(1, player.getInventory(), fixture.furnace());
                test.assertTrue(menu.quickMoveStack(player, EssentiaFurnaceBlockEntity.FUEL_REMAINDER).is(Items.BUCKET),
                    "The spent container can be recovered through the menu");
                test.assertTrue(menu.quickMoveStack(player, EssentiaFurnaceBlockEntity.FUEL).is(Items.CHARCOAL),
                    "The queued fuel can be recovered through the menu");
                test.assertTrue(menu.quickMoveStack(player, EssentiaFurnaceBlockEntity.FUEL_REMAINDER).isEmpty(),
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
