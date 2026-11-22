package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

        var overworld = level.getServer().overworld();
        overworld.getDataStorage().save();
        var dataPath = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data");
        var storage = new DimensionDataStorage(
            dataPath.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
        var factoryField = ObservationData.class.getDeclaredField("FACTORY");
        factoryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var factory = (SavedData.Factory<ObservationData>) factoryField.get(null);
        var reloaded = storage.get(factory, "thaumiclegacy_observations");
        test.assertTrue(reloaded != null && reloaded != data, "SavedData did not reload from disk");
        checkObservations(test, reloaded, first, true, "stone", "torch", "oak_log");
        checkObservations(test, reloaded, second, false, "oak_log");
        test.succeed();
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
        Vec3 feet = target.getCenter().add(0, feetAboveCenter, 0);
        player.moveTo(feet.x, feet.y, feet.z, 0, 90);
    }

    private static void useLens(ServerPlayer player, BlockPos target) {
        ItemStack stack = player.getMainHandItem();
        var hit = new BlockHitResult(target.getCenter(), Direction.UP, target, false);
        player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }
}
