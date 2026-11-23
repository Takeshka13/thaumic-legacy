package dev.thaumiclegacy.alchemy;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.workbench.ArcaneWorkbench;
import dev.thaumiclegacy.workbench.ArcaneWorkbenchRecipe;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class CrucibleGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void loadsCrucibleWorkbenchRecipe(GameTestHelper test) {
        var level = test.getLevel();
        var crucible = new Fixture(test);
        test.assertTrue(crucible.entity().getType() == Alchemy.CRUCIBLE_ENTITY, "Crucible block entity is registered");
        test.assertTrue(item("crucible") == Alchemy.CRUCIBLE_ITEM, "Crucible item is registered");
        var recipe = (ArcaneWorkbenchRecipe) level.getRecipeManager().byKey(ThaumicLegacy.id("arcane_crucible"))
            .orElseThrow().value();
        test.assertTrue(recipe.getType() == ArcaneWorkbench.RECIPE_TYPE, "Crucible uses the arcane recipe type");
        test.assertValueEqual(recipe.visCost(), 12, "Crucible costs twelve Vis");
        test.assertValueEqual(recipe.research(), Optional.of(ResearchProgress.ALCHEMY_FOUNDATIONS), "Crucible requires Alchemy Foundations");
        var input = CraftingInput.of(1, 2, List.of(stack(item("resonant_copper")), stack(Items.CAULDRON)));
        test.assertTrue(recipe.matches(input, level), "Crucible pattern matches the documented ingredients");
        test.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(),
            "Ordinary crafting does not produce the crucible");
        var result = recipe.assemble(input, level.registryAccess());
        test.assertTrue(result.is(Alchemy.CRUCIBLE_ITEM) && result.getCount() == 1, "The arcane pattern yields one crucible");
        for (String path : List.of("ember_salt", "cleansing_salt", "lumen_glass")) {
            var decoded = level.getRecipeManager().byKey(ThaumicLegacy.id(path)).orElseThrow().value();
            test.assertTrue(decoded.getType() == Alchemy.RECIPE_TYPE, "Loads the dedicated recipe type for " + path);
        }
        test.assertValueEqual(FuelRegistry.INSTANCE.get(item("ember_salt")), 4_800, "Ember Salt is usable furnace fuel");
        var lumenGlass = BuiltInRegistries.BLOCK.get(ThaumicLegacy.id("lumen_glass"));
        test.assertValueEqual(lumenGlass.defaultBlockState().getLightEmission(), 15, "Lumen Glass emits light when placed");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
    public void heatsAndAccumulatesProfilesWithoutOverfilling(GameTestHelper test) {
        var crucible = new Fixture(test);
        var first = crucible.newPlayer(test);
        var second = crucible.newPlayer(test);
        test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.EMPTY, "The placed crucible starts empty");
        expectHeld(test, crucible.use(first, stack(Items.CHARCOAL), false), Items.CHARCOAL, 1, "A dry crucible refuses ingredients");
        expectHeld(test, crucible.use(first, stack(Items.WATER_BUCKET), false), Items.BUCKET, 1, "Filling returns an empty bucket");
        test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "Water is stored before heat is supplied");
        var unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false);
        crucible.heat(unlit);
        test.runAfterDelay(4, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "An unlit campfire does not heat the crucible");
            expectHeld(test, crucible.use(first, stack(Items.CHARCOAL), false), Items.CHARCOAL, 1, "A cold crucible refuses ingredients");
            crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        });
        test.runAfterDelay(15, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.HEATING, "An active heat source begins warmup");
            test.assertTrue(crucible.entity().snapshot().heatTicks() > 0, "Warmup advances on server ticks");
            crucible.heat(Blocks.STONE.defaultBlockState());
        });
        test.runAfterDelay(18, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "Losing heat returns the filled phase");
            test.assertValueEqual(crucible.entity().snapshot().heatTicks(), 0, "Losing heat resets the countdown");
            crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        });
        test.runAfterDelay(100, () -> test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.HEATING,
            "A newly heated crucible must finish the complete warmup"));
        test.runAfterDelay(125, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.READY, "Continuous heat makes the crucible ready");
            expectHeld(test, crucible.use(first, new ItemStack(Items.STONE, 2), false), Items.STONE, 1, "Dissolving uses one block item");
            test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("terra"), 4, id("ordo"), 1),
                "Stone without an item assignment contributes its block profile");
            expectHeld(test, crucible.use(second, stack(Items.CHARCOAL), false), Items.CHARCOAL, 1,
                "The same crucible cannot accept a second player's item in the same tick");
        });
        test.runAfterDelay(128, () -> expectHeld(test, crucible.use(second, stack(Items.CHARCOAL), false), Items.CHARCOAL, 1,
            "Three elapsed ticks are still inside the throttle"));
        for (int i = 0; i < 11; i++) {
            test.runAfterDelay(129 + 4 * i, () -> expectConsumed(test, crucible.use(second, stack(Items.STONE), false),
                "At four ticks another ingredient can be added"));
        }
        test.runAfterDelay(173, () -> {
            expectHeld(test, crucible.use(second, stack(Items.STONE), false), Items.STONE, 1,
                "The whole ingredient is refused if its profile would exceed capacity");
            expectConsumed(test, crucible.use(second, stack(Items.CHARCOAL), false), "A smaller dedicated item profile fits");
            var snapshot = crucible.entity().snapshot();
            test.assertValueEqual(snapshot.aspects(), Map.of(id("terra"), 48, id("ordo"), 12, id("ignis"), 2,
                id("perditio"), 1), "Overflow does not change the mixture and charcoal contributes its item assignment");
            test.assertValueEqual(snapshot.capacity(), 64, "The bundled reservoir capacity is 64");
            var tag = crucible.entity().saveWithFullMetadata(crucible.level().registryAccess());
            var decoded = (CrucibleBlockEntity) BlockEntity.loadStatic(crucible.pos(),
                crucible.level().getBlockState(crucible.pos()), tag, crucible.level().registryAccess());
            test.assertTrue(decoded != null && decoded != crucible.entity(), "A new crucible can read the saved block entity");
            test.assertValueEqual(decoded.snapshot(), snapshot, "Saved phase, warmup, capacity and stock reload intact");
            test.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 180)
    public void brewsBundledRecipesAndRejectsIncompleteTransactions(GameTestHelper test) {
        var crucible = new Fixture(test);
        var owner = crucible.newPlayer(test);
        var outsider = crucible.newPlayer(test);
        learnAlchemy(test, owner);
        test.assertFalse(ResearchProgress.isUnlocked(outsider, ResearchProgress.ALCHEMY_FOUNDATIONS),
            "Research belongs to the player using the catalyst");
        crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        crucible.use(owner, stack(Items.WATER_BUCKET), false);
        scheduleIngredient(test, crucible, owner, 105, Items.CHARCOAL);
        scheduleIngredient(test, crucible, owner, 109, Items.CHARCOAL);
        test.runAfterDelay(113, () -> {
            var before = crucible.entity().snapshot();
            expectHeld(test, crucible.use(outsider, stack(Items.REDSTONE), true), Items.REDSTONE, 1, "Locked catalyst is not consumed");
            test.assertValueEqual(count(outsider, item("ember_salt")), 0, "Unqualified player receives no product");
            expectHeld(test, crucible.use(owner, stack(Items.PAPER), true), Items.PAPER, 1, "Unknown catalyst is not consumed");
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                owner.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            expectHeld(test, crucible.use(owner, new ItemStack(Items.REDSTONE, 2), true), Items.REDSTONE, 2,
                "Full output storage preserves a stacked catalyst");
            test.assertValueEqual(crucible.entity().snapshot(), before, "Refused catalysts leave the entire mixture unchanged");
            owner.getInventory().clearContent();
            expectHeld(test, crucible.use(owner, stack(Items.REDSTONE), true), item("ember_salt"), 1,
                "Ember Salt replaces its consumed catalyst");
            test.assertValueEqual(count(owner, item("ember_salt")), 1, "Ember Salt recipe gives one product");
            test.assertTrue(crucible.entity().snapshot().aspects().isEmpty(), "Ember Salt consumes exactly Ignis 4 and Perditio 2");
        });
        test.runAfterDelay(117, () -> {
            expectHeld(test, crucible.use(owner, stack(Items.GLASS), true), Items.GLASS, 1,
                "A known catalyst is retained if its required aspects are missing");
            expectConsumed(test, crucible.use(owner, stack(Items.TORCH), false), "Torch contributes its dedicated item profile");
        });
        scheduleIngredient(test, crucible, owner, 121, Items.GLASS_BOTTLE);
        test.runAfterDelay(125, () -> {
            expectHeld(test, crucible.use(owner, stack(Items.GLASS), true), item("lumen_glass"), 2,
                "Lumen Glass replaces its consumed glass catalyst");
            test.assertValueEqual(count(owner, item("lumen_glass")), 2, "Lumen Glass produces exactly two blocks");
            test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("ignis"), 2, id("vacuos"), 1),
                "Only the required aspects are removed");
        });
        scheduleIngredient(test, crucible, owner, 129, Items.CLAY);
        scheduleIngredient(test, crucible, owner, 133, Items.QUARTZ);
        test.runAfterDelay(137, () -> {
            expectHeld(test, crucible.use(owner, stack(Items.SUGAR), true), item("cleansing_salt"), 1,
                "Cleansing Salt replaces its consumed sugar catalyst");
            test.assertValueEqual(count(owner, item("cleansing_salt")), 1, "Cleansing Salt recipe gives one product");
            test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("ignis"), 2, id("vacuos"), 1,
                id("terra"), 2, id("vitreus"), 2), "Unused aspect units persist after all three recipes");
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.READY, "Successful brewing preserves hot water");
            test.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 290)
    public void ventsResidueExactlyOnceAndCleansLocalFlux(GameTestHelper test) {
        var crucible = new Fixture(test);
        var player = crucible.newPlayer(test);
        var chunk = new ChunkPos(crucible.pos());
        AuraService.removeFlux(crucible.level(), chunk, Integer.MAX_VALUE);
        crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        crucible.use(player, stack(Items.WATER_BUCKET), false);
        scheduleIngredient(test, crucible, player, 105, Items.STONE);
        scheduleIngredient(test, crucible, player, 109, Items.STONE);
        test.runAfterDelay(113, () -> {
            crucible.use(player, stack(Items.BUCKET), true);
            test.assertValueEqual(count(player, Items.WATER_BUCKET), 1, "Draining recovers one water bucket");
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.EMPTY, "Draining empties the crucible");
            test.assertTrue(crucible.entity().snapshot().aspects().isEmpty(), "Draining leaves no hidden residue");
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 2, "Ten residue units round up to two Flux");
            crucible.level().removeBlock(crucible.pos(), false);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 2, "Removing a drained vessel cannot vent twice");
            crucible.level().setBlockAndUpdate(crucible.pos(), Alchemy.CRUCIBLE.defaultBlockState());
            crucible.use(player, stack(Items.WATER_BUCKET), false);
        });
        scheduleIngredient(test, crucible, player, 218, Items.STONE);
        test.runAfterDelay(222, () -> {
            crucible.level().removeBlock(crucible.pos(), false);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 3, "Breaking vents five residue units as one Flux");
            crucible.level().removeBlock(crucible.pos(), false);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 3, "Repeated removal changes no aura");

            var center = crucible.pos().getCenter();
            player.moveTo(center.x, center.y, center.z, 0, 0);
            Item salt = item("cleansing_salt");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(salt, 2));
            player.gameMode.useItem(player, crucible.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 0, "Cleansing Salt removes available Flux");
            expectHeld(test, player.getMainHandItem(), salt, 1, "A successful cleansing consumes one salt");
            player.gameMode.useItem(player, crucible.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            expectHeld(test, player.getMainHandItem(), salt, 1, "A clean aura preserves the remaining salt");
            test.succeed();
        });
    }

    private static void scheduleIngredient(GameTestHelper test, Fixture crucible, ServerPlayer player, int tick, Item ingredient) {
        test.runAfterDelay(tick, () -> expectConsumed(test, crucible.use(player, stack(ingredient), false),
            "One ingredient is dissolved: " + BuiltInRegistries.ITEM.getKey(ingredient)));
    }

    private static void expectConsumed(GameTestHelper test, ItemStack held, String label) {
        test.assertTrue(held.isEmpty(), label);
    }

    private static void expectHeld(GameTestHelper test, ItemStack held, Item item, int count, String label) {
        test.assertTrue(held.is(item) && held.getCount() == count, label + " (held: " + held + ")");
    }

    private static void learnAlchemy(GameTestHelper test, ServerPlayer player) {
        var level = test.getLevel();
        var stone = test.absolutePos(new BlockPos(1, 1, 0));
        var torch = stone.east();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack(item("field_lens")));
        for (var pos : List.of(stone, torch)) {
            var center = pos.getCenter();
            player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(center, Direction.UP, pos, false));
        }
        scanOffhand(player, Items.FEATHER);
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ARCANE_FOUNDATIONS), "Prerequisite research is learned");
        test.assertFalse(ResearchProgress.isUnlocked(player, ResearchProgress.ALCHEMY_FOUNDATIONS), "Aqua is still unknown");
        scanOffhand(player, Items.WATER_BUCKET);
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.ALCHEMY_FOUNDATIONS), "Learning Aqua unlocks alchemy");
    }

    private static void scanOffhand(ServerPlayer player, Item item) {
        player.setItemInHand(InteractionHand.OFF_HAND, stack(item));
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
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

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ThaumicLegacy.id(id));
    }

    private static String id(String path) {
        return ThaumicLegacy.id(path).toString();
    }

    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    private record Fixture(ServerLevel level, BlockPos pos) {
        private Fixture(GameTestHelper test) {
            this(test.getLevel(), test.absolutePos(new BlockPos(0, 1, 0)));
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, Alchemy.CRUCIBLE.defaultBlockState());
        }

        private CrucibleBlockEntity entity() {
            return (CrucibleBlockEntity) level.getBlockEntity(pos);
        }

        private void heat(BlockState source) {
            level.setBlockAndUpdate(pos.below(), source);
        }

        private CrucibleBlock.Phase phase() {
            return level.getBlockState(pos).getValue(CrucibleBlock.PHASE);
        }

        @SuppressWarnings("removal")
        private ServerPlayer newPlayer(GameTestHelper test) {
            var player = test.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            return player;
        }

        private ItemStack use(ServerPlayer player, ItemStack hand, boolean crouch) {
            player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5, 180, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.setShiftKeyDown(crouch);
            UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter(), Direction.SOUTH, pos, false));
            player.setShiftKeyDown(false);
            return player.getMainHandItem();
        }
    }
}
