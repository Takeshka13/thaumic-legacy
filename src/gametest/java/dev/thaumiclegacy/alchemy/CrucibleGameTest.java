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
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.phys.AABB;
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

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 225)
    public void heatsAndAccumulatesProfilesWithoutOverfilling(GameTestHelper test) {
        var crucible = new Fixture(test);
        var first = crucible.newPlayer(test);
        var second = crucible.newPlayer(test);
        test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.EMPTY, "The placed crucible starts empty");
        var dry = crucible.drop(first, Items.CHARCOAL, 1);
        test.runAfterDelay(5, () -> {
            assertDrop(test, dry, 1, "A dry crucible refuses dropped ingredients");
            dry.discard();
            expectHeld(test, crucible.use(first, stack(Items.WATER_BUCKET), false), Items.BUCKET, 1, "Filling returns an empty bucket");
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "Water is stored before heat is supplied");
            crucible.heat(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
        });
        test.runAfterDelay(10, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "Unlit campfire does not provide heat");
            var cold = crucible.drop(first, Items.CHARCOAL, 1);
            test.runAfterDelay(5, () -> {
                assertDrop(test, cold, 1, "A cold crucible refuses dropped ingredients");
                cold.discard();
                crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
            });
        });
        test.runAfterDelay(23, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.HEATING, "An active heat source begins warmup");
            test.assertTrue(crucible.entity().snapshot().heatTicks() > 0, "Warmup advances on server ticks");
            crucible.heat(Blocks.STONE.defaultBlockState());
        });
        test.runAfterDelay(26, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.FILLED, "Losing heat returns the filled phase");
            test.assertValueEqual(crucible.entity().snapshot().heatTicks(), 0, "Losing heat resets warmup");
            crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        });
        test.runAfterDelay(110, () -> test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.HEATING,
            "Restored heat must finish the complete warmup"));
        test.runAfterDelay(132, () -> {
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.READY, "Continuous heat makes the crucible ready");
            expectHeld(test, crucible.use(first, new ItemStack(Items.STONE, 2), false), Items.STONE, 2,
                "Right-click no longer dissolves held ingredients");
            var stone = crucible.drop(first, Items.STONE, 12);
            var charcoal = crucible.drop(second, Items.CHARCOAL, 1);
            test.runAfterDelay(4, () -> {
                int amount = crucible.entity().snapshot().amount();
                test.assertTrue(amount == 5 || amount == 3, "Only one dropped item from both players enters during one intake period");
            });
            test.runAfterDelay(58, () -> {
                test.assertFalse(stone.isAlive() || charcoal.isAlive(), "The dropped stack is dissolved one unit at a time");
                var overflow = crucible.drop(second, Items.STONE, 1);
                test.runAfterDelay(5, () -> {
                    assertDrop(test, overflow, 1, "A whole block is refused if its profile would exceed capacity");
                    var snapshot = crucible.entity().snapshot();
                    test.assertValueEqual(snapshot.aspects(), Map.of(id("terra"), 48, id("ordo"), 12, id("ignis"), 2,
                        id("perditio"), 1), "Stone contributes its block profile and charcoal its item profile");
                    test.assertValueEqual(snapshot.capacity(), 64, "The default reservoir capacity is 64");
                    var tag = crucible.entity().saveWithFullMetadata(crucible.level().registryAccess());
                    var decoded = (CrucibleBlockEntity) BlockEntity.loadStatic(crucible.pos(),
                        crucible.level().getBlockState(crucible.pos()), tag, crucible.level().registryAccess());
                    test.assertTrue(decoded != null && decoded != crucible.entity(), "A new crucible can read the saved block entity");
                    test.assertValueEqual(decoded.snapshot(), snapshot, "Saved phase, warmup, capacity and stock reload intact");
                    overflow.discard();
                    test.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 210)
    public void brewsBundledRecipesAndRejectsIncompleteTransactions(GameTestHelper test) {
        var crucible = new Fixture(test);
        var owner = crucible.newPlayer(test);
        var outsider = crucible.newPlayer(test);
        learnAlchemy(test, owner);
        test.assertFalse(ResearchProgress.isUnlocked(owner, ResearchProgress.ESSENTIA_DISTILLATION),
            "Alchemy alone does not demonstrate distillation prerequisites");
        test.assertFalse(ResearchProgress.isUnlocked(outsider, ResearchProgress.ALCHEMY_FOUNDATIONS),
            "Research belongs to the player who dropped the catalyst");
        crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        crucible.use(owner, stack(Items.WATER_BUCKET), false);
        scheduleIngredient(test, crucible, owner, 105, Items.REDSTONE, 1);
        test.runAfterDelay(111, () -> {
            test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("potentia"), 2, id("perditio"), 1),
                "A known catalyst with insufficient stock dissolves by its usual profile");
            test.assertTrue(crucible.outputs(item("ember_salt")).isEmpty(), "Insufficient stock produces no recipe result");
        });
        scheduleIngredient(test, crucible, owner, 112, Items.CHARCOAL, 2);
        test.runAfterDelay(124, () -> {
            var before = crucible.entity().snapshot();
            var locked = crucible.drop(outsider, Items.REDSTONE, 1);
            var unowned = crucible.drop(null, Items.REDSTONE, 1);
            var unrelated = crucible.drop(owner, Items.POISONOUS_POTATO, 1);
            test.runAfterDelay(5, () -> {
                assertDrop(test, locked, 1, "An unqualified thrower's catalyst remains intact");
                assertDrop(test, unowned, 1, "An unattributed catalyst cannot bypass research");
                assertDrop(test, unrelated, 1, "An unrelated item without a profile remains untouched");
                test.assertValueEqual(crucible.entity().snapshot(), before, "Refused items leave stock unchanged");
                locked.discard();
                unowned.discard();
                unrelated.discard();
            });
        });
        test.runAfterDelay(131, () -> {
            for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
                owner.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            var redstone = crucible.drop(owner, Items.REDSTONE, 2);
            test.runAfterDelay(10, () -> {
                test.assertFalse(redstone.isAlive(), "One redstone crafts and the remaining item dissolves");
                var outputs = crucible.outputs(item("ember_salt"));
                test.assertValueEqual(outputs.size(), 1, "A stacked catalyst creates one world drop with only one recipe's stock");
                test.assertValueEqual(outputs.getFirst().getItem().getCount(), 1, "Ember Salt recipe drops exactly one product");
                test.assertFalse(ResearchProgress.isUnlocked(owner, ResearchProgress.ESSENTIA_DISTILLATION),
                    "An unrelated successful brew does not open distillation");
                test.assertTrue(outputs.getFirst().getTags().contains("thaumiclegacy.alchemy_output"), "Ejected product has a reingestion marker");
                test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("potentia"), 4, id("perditio"), 2),
                    "The second redstone adds its profile without replaying the craft");
                var output = outputs.getFirst();
                // Keep the real output marker while making a normal dissolvable stack enter the basin again.
                output.setItem(stack(Items.CHARCOAL));
                crucible.center(output);
                test.runAfterDelay(5, () -> {
                    assertDrop(test, output, 1, "Marked world output is excluded even with a valid profile inside the basin");
                    test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("potentia"), 4, id("perditio"), 2),
                        "An ejected stack cannot contribute stock on re-entry");
                    output.discard();
                });
            });
        });
        scheduleIngredient(test, crucible, owner, 149, Items.TORCH, 1);
        scheduleIngredient(test, crucible, owner, 156, Items.GLASS_BOTTLE, 1);
        test.runAfterDelay(163, () -> {
            var glass = crucible.drop(owner, Items.GLASS, 1);
            test.runAfterDelay(5, () -> {
                test.assertFalse(glass.isAlive(), "Lumen Glass consumes one glass catalyst");
                test.assertValueEqual(crucible.outputCount(item("lumen_glass")), 2, "Lumen Glass ejects two blocks");
                test.assertTrue(ResearchProgress.isUnlocked(owner, ResearchProgress.ESSENTIA_DISTILLATION),
                    "First successful Lumen Glass brew opens distillation");
                test.assertFalse(ResearchProgress.isUnlocked(outsider, ResearchProgress.ESSENTIA_DISTILLATION),
                    "A nearby player does not gain distillation progress from someone else's brew");
                test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("ignis"), 2, id("vacuos"), 1,
                    id("potentia"), 4, id("perditio"), 2), "Only the required aspects are removed");
            });
        });
        scheduleIngredient(test, crucible, owner, 170, Items.CLAY, 1);
        scheduleIngredient(test, crucible, owner, 177, Items.QUARTZ, 1);
        test.runAfterDelay(184, () -> {
            var sugar = crucible.drop(owner, Items.SUGAR, 1);
            test.runAfterDelay(5, () -> {
                test.assertFalse(sugar.isAlive(), "Cleansing Salt consumes one sugar catalyst");
                test.assertValueEqual(crucible.outputCount(item("cleansing_salt")), 1, "Cleansing Salt ejects exactly one item");
                test.assertValueEqual(crucible.entity().snapshot().aspects(), Map.of(id("ignis"), 2, id("vacuos"), 1,
                    id("terra"), 2, id("vitreus"), 2, id("potentia"), 4, id("perditio"), 2),
                    "Unused aspect units persist through all three recipes");
                test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.READY, "Brewing preserves hot water");
                test.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 365)
    public void ventsResidueExactlyOnceAndCleansLocalFlux(GameTestHelper test) {
        var crucible = new Fixture(test);
        var player = crucible.newPlayer(test);
        var chunk = new ChunkPos(crucible.pos());
        AuraService.removeFlux(crucible.level(), chunk, Integer.MAX_VALUE);
        crucible.heat(Blocks.MAGMA_BLOCK.defaultBlockState());
        crucible.use(player, stack(Items.WATER_BUCKET), false);
        scheduleIngredient(test, crucible, player, 105, Items.STONE, 2);
        test.runAfterDelay(116, () -> {
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
        scheduleIngredient(test, crucible, player, 222, Items.STONE, 1);
        test.runAfterDelay(228, () -> {
            crucible.use(player, ItemStack.EMPTY, true);
            test.assertValueEqual(crucible.phase(), CrucibleBlock.Phase.EMPTY, "Sneaking with an empty hand dumps the water");
            test.assertTrue(crucible.entity().snapshot().aspects().isEmpty(), "Empty-hand purge removes every stored unit");
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 3, "Empty-hand purge adds Flux exactly once");
            crucible.use(player, ItemStack.EMPTY, true);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 3, "Repeating purge releases nothing");
        });
        test.runAfterDelay(234, () -> crucible.use(player, stack(Items.WATER_BUCKET), false));
        scheduleIngredient(test, crucible, player, 340, Items.STONE, 1);
        test.runAfterDelay(347, () -> {
            crucible.level().removeBlock(crucible.pos(), false);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 4, "Breaking vents five residue units as one Flux");
            crucible.level().removeBlock(crucible.pos(), false);
            test.assertValueEqual(AuraService.read(crucible.level(), chunk).flux(), 4, "Repeated removal changes no aura");

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

    private static void scheduleIngredient(GameTestHelper test, Fixture crucible, ServerPlayer player, int tick,
                                           Item ingredient, int count) {
        test.runAfterDelay(tick, () -> {
            var entity = crucible.drop(player, ingredient, count);
            test.runAfterDelay(4 * count + 1, () -> test.assertFalse(entity.isAlive(),
                "Ingredient is dissolved: " + BuiltInRegistries.ITEM.getKey(ingredient)));
        });
    }

    private static void assertDrop(GameTestHelper test, ItemEntity entity, int count, String label) {
        test.assertTrue(entity.isAlive() && entity.getItem().getCount() == count, label);
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

        private ItemEntity drop(ServerPlayer owner, Item ingredient, int count) {
            var entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                new ItemStack(ingredient, count), 0, 0, 0);
            if (owner != null) {
                entity.setThrower(owner);
            }
            center(entity);
            level.addFreshEntity(entity);
            return entity;
        }

        private void center(ItemEntity entity) {
            entity.setNoGravity(true);
            entity.setNeverPickUp();
            entity.setDeltaMovement(0, 0, 0);
            entity.setPos(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5);
        }

        private List<ItemEntity> outputs(Item item) {
            return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), drop -> drop.getItem().is(item));
        }

        private int outputCount(Item item) {
            return outputs(item).stream().mapToInt(drop -> drop.getItem().getCount()).sum();
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
