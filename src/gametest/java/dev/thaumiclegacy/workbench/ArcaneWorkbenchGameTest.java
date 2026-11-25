package dev.thaumiclegacy.workbench;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.ResearchProgress;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class ArcaneWorkbenchGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void loadsWorkbenchAndArcaneRecipes(GameTestHelper test) {
        var level = test.getLevel();
        var bench = new Fixture(test);
        test.assertTrue(bench.entity().getType() == ArcaneWorkbench.BLOCK_ENTITY_TYPE, "Workbench block entity is registered");
        test.assertTrue(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("arcane_workbench")) == ArcaneWorkbench.ITEM,
            "Workbench item is registered");
        var id = ThaumicLegacy.id("arcane_workbench");
        var benchRecipe = level.getRecipeManager().byKey(id).orElseThrow().value();
        test.assertTrue(benchRecipe.getType() == RecipeType.CRAFTING, "Workbench is obtained through ordinary crafting");
        var regular = (net.minecraft.world.item.crafting.CraftingRecipe) benchRecipe;
        var benchInput = CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, stack(Items.COPPER_INGOT), ItemStack.EMPTY,
            stack(Items.GLASS_PANE), stack(Items.CRAFTING_TABLE), stack(Items.GLASS_PANE),
            ItemStack.EMPTY, stack(Items.STONE), ItemStack.EMPTY));
        test.assertTrue(regular.matches(benchInput, level), "Workbench recipe accepts the documented pattern");
        var workbenchResult = regular.assemble(benchInput, level.registryAccess());
        test.assertTrue(workbenchResult.is(ArcaneWorkbench.ITEM) && workbenchResult.getCount() == 1,
            "Ordinary crafting creates one workbench");

        checkArcaneRecipe(test, "resonant_copper", 8, false, CraftingInput.of(1, 2, List.of(
            stack(Items.REDSTONE), stack(Items.COPPER_INGOT))));
        checkArcaneRecipe(test, "goggles_of_revealing", 24, true, CraftingInput.of(3, 2, List.of(
            stack(item("thaumometer")), ItemStack.EMPTY, stack(item("thaumometer")),
            stack(Items.LEATHER), stack(Items.LEATHER), stack(Items.LEATHER))));
        checkArcaneRecipe(test, "arcane_bellows", 20, true, CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, stack(item("resonant_copper")), ItemStack.EMPTY,
            stack(Items.LEATHER), stack(Items.PISTON), stack(Items.LEATHER),
            ItemStack.EMPTY, stack(item("resonant_copper")), ItemStack.EMPTY)));
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void chargesExactlyOnceAndRejectsStaleAttempts(GameTestHelper test) {
        var bench = new Fixture(test);
        var first = bench.newPlayer(test);
        var second = bench.newPlayer(test);
        bench.resonantIngredients(3);
        var firstMenu = bench.open(first);
        var secondMenu = bench.open(second);
        long firstAttempt = firstMenu.attemptToken();
        long secondAttempt = secondMenu.attemptToken();
        test.assertValueEqual(firstMenu.status(), ArcaneWorkbenchMenu.Status.READY, "Matching recipe is ready");
        test.assertValueEqual(firstMenu.visCost(), 8, "Preview displays the declared Vis cost");
        test.assertValueEqual(firstMenu.availableVis(), 100, "Preview displays the local aura balance");
        firstMenu.clicked(9, 0, ClickType.PICKUP, first);
        test.assertTrue(firstMenu.getCarried().isEmpty(), "Preview cannot be moved onto the cursor");
        test.assertTrue(firstMenu.quickMoveStack(first, 9).isEmpty(), "Quick-move cannot claim a preview");
        test.assertFalse(firstMenu.craft(first, secondAttempt), "An attempt token belongs to one open menu");
        test.assertTrue(firstMenu.craft(first, firstAttempt), "A valid arcane craft succeeds");
        checkResonantTransaction(test, bench, first, 92, 2, 1);
        test.assertFalse(firstMenu.craft(first, firstAttempt), "The same attempt cannot be replayed with stacked ingredients");
        checkResonantTransaction(test, bench, first, 92, 2, 1);
        test.assertFalse(secondMenu.craft(second, secondAttempt), "A second viewer cannot use a stale shared-grid preview");
        checkResonantTransaction(test, bench, second, 92, 2, 0);

        secondMenu.broadcastChanges();
        test.assertTrue(secondMenu.craft(second, secondMenu.attemptToken()), "A fresh shared-grid preview can be used");
        checkResonantTransaction(test, bench, second, 84, 1, 1);
        test.assertValueEqual(count(first, item("resonant_copper")), 1, "Outputs go only to the player who crafted");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void abortsWithoutChargingOrRemovingInputs(GameTestHelper test) {
        var bench = new Fixture(test);
        var player = bench.newPlayer(test);
        bench.entity().grid().setItem(1, new ItemStack(Items.REDSTONE, 2));
        var menu = bench.open(player);
        test.assertValueEqual(menu.status(), ArcaneWorkbenchMenu.Status.NO_RECIPE, "Incomplete grid has no recipe");
        test.assertFalse(menu.craft(player, menu.attemptToken()), "A partial pattern cannot craft");
        test.assertValueEqual(bench.vis(), 100, "Missing ingredients do not cost Vis");
        test.assertValueEqual(bench.entity().grid().getItem(1).getCount(), 2, "Partial pattern is unchanged");

        bench.resonantIngredients(2);
        test.assertTrue(AuraService.trySpendVis(bench.level(), bench.chunk(), 93), "Fixture lowers available Vis below cost");
        menu.broadcastChanges();
        test.assertValueEqual(menu.status(), ArcaneWorkbenchMenu.Status.INSUFFICIENT_VIS, "Preview reports insufficient Vis");
        test.assertFalse(menu.craft(player, menu.attemptToken()), "Seven Vis cannot pay for an eight-Vis recipe");
        checkResonantTransaction(test, bench, player, 7, 2, 0);

        AuraService.restoreVis(bench.level(), bench.chunk(), 93);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        player.getInventory().setChanged();
        menu.broadcastChanges();
        test.assertValueEqual(menu.status(), ArcaneWorkbenchMenu.Status.OUTPUT_FULL, "Preview reports full output storage");
        test.assertFalse(menu.craft(player, menu.attemptToken()), "A full inventory cannot accept the result");
        checkResonantTransaction(test, bench, player, 100, 2, 0);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void enforcesResearchSeparatelyForEachPlayer(GameTestHelper test) {
        var bench = new Fixture(test);
        var first = bench.newPlayer(test);
        var second = bench.newPlayer(test);
        bench.gogglesIngredients();
        observeStoneAndTorch(test, first);
        test.assertFalse(ResearchProgress.isUnlocked(first, ResearchProgress.ARCANE_FOUNDATIONS),
            "Stone and torch complete the first milestone but cannot reveal Lux alone");
        var firstMenu = bench.open(first);
        test.assertValueEqual(firstMenu.status(), ArcaneWorkbenchMenu.Status.RESEARCH_REQUIRED, "Preview reports the research gate");
        test.assertFalse(firstMenu.craft(first, firstMenu.attemptToken()), "A matching locked recipe cannot craft");
        test.assertValueEqual(bench.vis(), 100, "Locked recipe costs no Vis");
        observeFeather(first);
        test.assertTrue(ResearchProgress.isUnlocked(first, ResearchProgress.ARCANE_FOUNDATIONS),
            "The feather's Aer reveals Lux and completes Arcane Foundations");

        var secondMenu = bench.open(second);
        test.assertFalse(ResearchProgress.isUnlocked(second, ResearchProgress.ARCANE_FOUNDATIONS),
            "Another player on the same bench remains locked");
        test.assertFalse(secondMenu.craft(second, secondMenu.attemptToken()), "Another player cannot use the same recipe");
        firstMenu = bench.open(first);
        test.assertTrue(firstMenu.craft(first, firstMenu.attemptToken()), "The qualified player can craft");
        test.assertValueEqual(bench.vis(), 76, "Revealing goggles cost twenty-four Vis");
        test.assertValueEqual(count(first, item("goggles_of_revealing")), 1, "Qualified player receives goggles");
        test.assertValueEqual(count(second, item("goggles_of_revealing")), 0, "Unqualified player receives no item");
        test.assertTrue(bench.entity().grid().isEmpty(), "Advanced crafting consumes each grid input once");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void rejectsClosedDistantAndDestroyedWorkbench(GameTestHelper test) {
        var bench = new Fixture(test);
        var player = bench.newPlayer(test);
        bench.resonantIngredients(2);
        var closed = bench.open(player);
        long closedAttempt = closed.attemptToken();
        closed.removed(player);
        player.containerMenu = player.inventoryMenu;
        test.assertFalse(closed.craft(player, closedAttempt), "A closed or disconnected player's old menu cannot craft");
        checkResonantTransaction(test, bench, player, 100, 2, 0);

        var distant = bench.open(player);
        long distantAttempt = distant.attemptToken();
        player.moveTo(bench.pos().getX() + 30, bench.pos().getY(), bench.pos().getZ(), 0, 0);
        test.assertFalse(distant.craft(player, distantAttempt), "Out-of-reach viewer cannot craft");
        checkResonantTransaction(test, bench, player, 100, 2, 0);

        var destroyed = bench.open(player);
        long destroyedAttempt = destroyed.attemptToken();
        bench.level().setBlockAndUpdate(bench.pos(), Blocks.AIR.defaultBlockState());
        test.assertFalse(destroyed.craft(player, destroyedAttempt), "A destroyed block invalidates the old menu");
        test.assertValueEqual(bench.vis(), 100, "Destroyed workbench costs no Vis");
        test.assertValueEqual(count(player, item("resonant_copper")), 0, "Destroyed workbench grants no result");
        var drops = bench.level().getEntitiesOfClass(ItemEntity.class, new AABB(bench.pos()).inflate(1));
        for (Item item : List.of(Items.REDSTONE, Items.COPPER_INGOT)) {
            int amount = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
            test.assertValueEqual(amount, 2, "Breaking the bench drops each stored stack once");
        }
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void serializesRemainingIngredientsAndChargedAura(GameTestHelper test) {
        var bench = new Fixture(test);
        var player = bench.newPlayer(test);
        bench.resonantIngredients(3);
        var menu = bench.open(player);
        test.assertTrue(menu.craft(player, menu.attemptToken()), "A craft changes grid and aura before saving");
        var registries = bench.level().registryAccess();
        var tag = bench.entity().saveWithFullMetadata(registries);
        var loaded = (ArcaneWorkbenchBlockEntity) BlockEntity.loadStatic(bench.pos(),
            bench.level().getBlockState(bench.pos()), tag, registries);
        test.assertTrue(loaded != null && loaded != bench.entity(), "Saved grid is decoded into a new block entity");
        test.assertValueEqual(loaded.grid().getItem(1).getCount(), 2, "Redstone remainder survives block reload");
        test.assertValueEqual(loaded.grid().getItem(4).getCount(), 2, "Copper remainder survives block reload");
        bench.level().getDataStorage().save();
        assertStoredVis(test, bench, 92);
        test.succeed();
    }

    private static void checkArcaneRecipe(GameTestHelper test, String path, int cost, boolean gated, CraftingInput input) {
        var level = test.getLevel();
        var holder = level.getRecipeManager().byKey(ThaumicLegacy.id(path)).orElseThrow();
        test.assertTrue(holder.value() instanceof ArcaneWorkbenchRecipe, "Custom shaped recipe was decoded for " + path);
        var recipe = (ArcaneWorkbenchRecipe) holder.value();
        test.assertTrue(recipe.getType() == ArcaneWorkbench.RECIPE_TYPE, "Arcane recipes use their own recipe type");
        test.assertValueEqual(recipe.visCost(), cost, "Vis cost for " + path);
        test.assertValueEqual(recipe.research(), gated ? Optional.of(path.equals("arcane_bellows")
                ? ResearchProgress.ARCANE_BELLOWS : ResearchProgress.ARCANE_FOUNDATIONS) : Optional.empty(),
            "Research requirement for " + path);
        test.assertTrue(recipe.matches(input, level), "Recipe accepts the documented shaped pattern");
        test.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(),
            "Ordinary crafting does not produce " + path);
        var result = recipe.assemble(input, level.registryAccess());
        test.assertTrue(result.is(item(path)) && !result.isEmpty() && result.getCount() == 1, "Result for " + path);
    }

    private static void checkResonantTransaction(GameTestHelper test, Fixture bench, ServerPlayer player, int vis,
                                                 int ingredients, int output) {
        test.assertValueEqual(bench.vis(), vis, "Current Vis");
        test.assertValueEqual(bench.entity().grid().getItem(1).getCount(), ingredients, "Redstone count");
        test.assertValueEqual(bench.entity().grid().getItem(4).getCount(), ingredients, "Copper ingot count");
        test.assertValueEqual(count(player, item("resonant_copper")), output, "Crafted result count");
    }

    private static void observeStoneAndTorch(GameTestHelper test, ServerPlayer player) {
        var level = test.getLevel();
        var stone = test.absolutePos(new BlockPos(0, 1, 6));
        var torch = stone.east(3);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(item("field_lens")));
        observeBlock(player, stone);
        observeBlock(player, torch);
    }

    private static void observeBlock(ServerPlayer player, BlockPos pos) {
        var center = pos.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
        var hit = new BlockHitResult(center, Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static void observeFeather(ServerPlayer player) {
        player.setItemInHand(InteractionHand.OFF_HAND, stack(Items.FEATHER));
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void assertStoredVis(GameTestHelper test, Fixture bench, int expected) {
        var path = bench.level().getServer().getWorldPath(LevelResource.ROOT)
            .resolve("data").resolve("thaumiclegacy_aura.dat");
        try {
            var root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            var cells = root.getCompound("data").getList("cells", Tag.TAG_COMPOUND);
            var cell = cells.stream().map(CompoundTag.class::cast)
                .filter(candidate -> candidate.getLong("chunk") == bench.chunk().toLong()).findFirst().orElseThrow();
            test.assertValueEqual(cell.getInt("vis"), expected, "Aura SavedData records the charged Vis");
        } catch (IOException exception) {
            test.fail("Unable to read saved aura: " + exception.getMessage());
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static Item item(String name) {
        return BuiltInRegistries.ITEM.get(ThaumicLegacy.id(name));
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private record Fixture(ServerLevel level, BlockPos pos, ArcaneWorkbenchBlockEntity entity) {
        private Fixture(GameTestHelper test) {
            this(test.getLevel(), test.absolutePos(new BlockPos(0, 1, 0)));
        }

        private Fixture(ServerLevel level, BlockPos pos) {
            this(level, pos, place(level, pos));
            AuraService.restoreVis(level, chunk(), Integer.MAX_VALUE);
            AuraService.removeFlux(level, chunk(), Integer.MAX_VALUE);
        }

        private static ArcaneWorkbenchBlockEntity place(ServerLevel level, BlockPos pos) {
            level.setBlockAndUpdate(pos, ArcaneWorkbench.BLOCK.defaultBlockState());
            return (ArcaneWorkbenchBlockEntity) level.getBlockEntity(pos);
        }

        @SuppressWarnings("removal")
        private ServerPlayer newPlayer(GameTestHelper test) {
            return test.makeMockServerPlayerInLevel();
        }

        private ArcaneWorkbenchMenu open(ServerPlayer player) {
            player.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            var menu = entity.createMenu(1, player.getInventory(), player);
            player.containerMenu = menu;
            menu.broadcastChanges();
            return menu;
        }

        private ChunkPos chunk() {
            return new ChunkPos(pos);
        }

        private int vis() {
            return AuraService.read(level, chunk()).vis();
        }

        private void resonantIngredients(int count) {
            entity.grid().setItem(1, new ItemStack(Items.REDSTONE, count));
            entity.grid().setItem(4, new ItemStack(Items.COPPER_INGOT, count));
        }

        private void gogglesIngredients() {
            entity.grid().setItem(0, stack(item("thaumometer")));
            entity.grid().setItem(2, stack(item("thaumometer")));
            entity.grid().setItem(3, stack(Items.LEATHER));
            entity.grid().setItem(4, stack(Items.LEATHER));
            entity.grid().setItem(5, stack(Items.LEATHER));
        }
    }
}
