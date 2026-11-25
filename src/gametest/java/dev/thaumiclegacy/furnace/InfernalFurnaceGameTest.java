package dev.thaumiclegacy.furnace;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.research.NodeResearch;
import dev.thaumiclegacy.research.ResearchProgress;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

public class InfernalFurnaceGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void activationRequiresResearchLayoutAndBothCharges(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(2, 2, 6));
        var front = Direction.EAST;
        build(level, pos, front);
        var player = player(test);
        var wand = new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("wand")));
        var charge = new WandVis(1, 2, 12, 13, 3, 4);
        wand.set(WandVis.COMPONENT, charge);
        click(player, pos, front, wand);
        test.assertTrue(level.getBlockState(pos).is(Blocks.IRON_BARS), "Unresearched player cannot form the furnace");
        test.assertValueEqual(WandVis.get(wand), charge, "Research rejection preserves charge");

        var stone = test.absolutePos(new BlockPos(0, 1, 1));
        var torch = stone.east(2);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        var lens = new ItemStack(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("field_lens")));
        observeBlock(player, stone, lens);
        observeBlock(player, torch, lens);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FEATHER));
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player, level, lens, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        NodeResearch.observe(player, UUID.randomUUID());
        NodeResearch.recordCharge(player);
        test.assertTrue(ResearchProgress.isUnlocked(player, ResearchProgress.INFERNAL_FURNACE), "Player has required evidence");

        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        click(player, pos, front, wand);
        test.assertTrue(level.getBlockState(pos).is(Blocks.IRON_BARS), "Invalid shell cannot form");
        test.assertValueEqual(WandVis.get(wand), charge, "Invalid shell preserves charge");
        level.setBlockAndUpdate(pos.above(), Blocks.NETHER_BRICKS.defaultBlockState());

        var shortCharge = new WandVis(1, 2, 12, 11, 3, 4);
        wand.set(WandVis.COMPONENT, shortCharge);
        click(player, pos, front, wand);
        test.assertTrue(level.getBlockState(pos).is(Blocks.IRON_BARS), "Both primals are required");
        test.assertValueEqual(WandVis.get(wand), shortCharge, "Insufficient charge spends neither primal");

        wand.set(WandVis.COMPONENT, charge);
        click(player, pos, front, wand);
        var state = level.getBlockState(pos);
        test.assertTrue(state.is(InfernalFurnace.BLOCK) && state.getValue(InfernalFurnaceBlock.FACING) == front,
                "Wand forms the correctly oriented furnace");
        test.assertTrue(level.getBlockEntity(pos) instanceof InfernalFurnaceBlockEntity, "Controller stores the furnace");
        var remaining = new WandVis(1, 2, 0, 1, 3, 4);
        test.assertValueEqual(WandVis.get(wand), remaining, "Formation spends only Ignis and Terra");
        click(player, pos, front, wand);
        test.assertValueEqual(WandVis.get(wand), remaining, "Repeated request does not charge again");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void smeltingResumesAfterReloadAndDoesNotDuplicate(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(2, 2, 2));
        var front = Direction.SOUTH;
        build(level, pos, front);
        var state = InfernalFurnace.BLOCK.defaultBlockState().setValue(InfernalFurnaceBlock.FACING, front);
        level.setBlockAndUpdate(pos, state);
        var furnace = (InfernalFurnaceBlockEntity) level.getBlockEntity(pos);
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        test.assertTrue(level.getBlockState(pos.above()).is(InfernalFurnace.PART), "Legacy shell migrates on tick");
        var player = player(test);
        var raw = new ItemStack(Items.RAW_IRON, 2);
        click(player, pos, front, raw);
        test.assertTrue(raw.isEmpty(), "Loading debits the held stack");

        var registries = level.registryAccess();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,
                new SingleRecipeInput(new ItemStack(Items.RAW_IRON)), level).orElseThrow();
        var data = furnace.saveWithoutMetadata(registries);
        data.putString("recipe", recipe.id().toString());
        int beforeCompletion = recipe.value().getCookingTime() * 100 - 1;
        data.putInt("work", beforeCompletion);
        data.putInt("iron_batch", 3);
        furnace.loadWithComponents(data, registries);
        var reloaded = new InfernalFurnaceBlockEntity(pos, state);
        reloaded.loadWithComponents(furnace.saveWithoutMetadata(registries), registries);

        var saved = reloaded.saveWithoutMetadata(registries);
        test.assertValueEqual(saved.getInt("work"), beforeCompletion, "Reload preserves saved progress");
        test.assertValueEqual(ItemStack.parseOptional(registries, saved.getCompound("input")).getCount(), 2,
                "Reload preserves all ingredients");
        furnace.loadWithComponents(saved, registries);
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        var finished = furnace.saveWithoutMetadata(registries);
        test.assertValueEqual(ItemStack.parseOptional(registries, finished.getCompound("input")).getCount(), 1,
                "Completion consumes exactly one ingredient");
        furnace.loadWithComponents(finished, registries);
        player.getInventory().selected = 8;
        click(player, pos, front, ItemStack.EMPTY);
        test.assertValueEqual(player.getInventory().countItem(Items.IRON_INGOT), 1, "First collection yields one ingot");
        click(player, pos, front, ItemStack.EMPTY);
        test.assertValueEqual(player.getInventory().countItem(Items.IRON_INGOT), 1, "Repeated collection yields one ingot");
        test.assertValueEqual(player.getInventory().countItem(Items.IRON_NUGGET), 1, "Fourth raw iron yields one nugget");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void formedShellRestoresSurvivingMaterials(GameTestHelper test) {
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(3, 2, 3));
        var front = Direction.WEST;
        build(level, origin, front);
        var state = InfernalFurnace.BLOCK.defaultBlockState().setValue(InfernalFurnaceBlock.FACING, front);
        level.setBlockAndUpdate(origin, state);
        var furnace = (InfernalFurnaceBlockEntity) level.getBlockEntity(origin);
        InfernalFurnaceBlockEntity.tick(level, origin, state, furnace);
        var heart = origin.relative(front.getOpposite());
        var wall = origin.north();
        var inlet = origin.above();
        test.assertTrue(InfernalFurnace.inputPort(level.getBlockState(inlet), front), "Inlet faces outward");
        test.assertTrue(!InfernalFurnace.inputPort(level.getBlockState(inlet), Direction.UP), "Inlet is sided");
        test.assertTrue(level.getBlockState(wall).is(InfernalFurnace.PART), "Obsidian formed");
        test.assertTrue(level.getBlockState(heart).is(InfernalFurnace.PART), "Lava is sealed");
        level.removeBlock(wall, false);
        test.runAfterDelay(12, () -> {
            test.assertTrue(level.getBlockState(origin).is(Blocks.IRON_BARS), "Controller reverted");
            test.assertTrue(!level.getBlockState(wall).is(Blocks.OBSIDIAN), "Removed block was not restored");
            test.assertTrue(level.getBlockState(inlet).is(Blocks.NETHER_BRICKS), "Inlet reverted");
            test.assertTrue(level.getFluidState(heart).isSource(), "Original lava source restored");
            test.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void bellowsAndIgnisUseSeparateConnections(GameTestHelper test) {
        var level = test.getLevel();
        var pos = test.absolutePos(new BlockPos(5, 4, 5));
        var front = Direction.NORTH;
        build(level, pos, front);
        var state = InfernalFurnace.BLOCK.defaultBlockState().setValue(InfernalFurnaceBlock.FACING, front);
        level.setBlockAndUpdate(pos, state);
        var furnace = (InfernalFurnaceBlockEntity) level.getBlockEntity(pos);
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        var player = player(test);
        click(player, pos, front, new ItemStack(Items.RAW_IRON, 2));

        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        var registries = level.registryAccess();
        int work = furnace.saveWithoutMetadata(registries).getInt("work");
        test.assertValueEqual(work, InfernalFurnaceBlockEntity.rate(0, false), "Base furnace advances faster than vanilla");
        var center = pos.relative(front.getOpposite());
        level.setBlockAndUpdate(center.below(2), InfernalFurnace.BELLOWS.defaultBlockState());
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        int next = furnace.saveWithoutMetadata(registries).getInt("work");
        test.assertValueEqual(next - work, InfernalFurnaceBlockEntity.rate(1, false), "Bottom bellows adds speed");
        for (Direction side : new Direction[] {front.getOpposite(), front.getClockWise(), front.getCounterClockWise()}) {
            level.setBlockAndUpdate(center.relative(side, 2), InfernalFurnace.BELLOWS.defaultBlockState());
        }

        var tube = pos.above().relative(front);
        var jarPos = tube.below();
        level.setBlockAndUpdate(jarPos, EssentiaLogistics.WARDED_JAR.defaultBlockState());
        level.setBlockAndUpdate(tube, EssentiaTransport.TUBE.defaultBlockState());
        var jar = (EssentiaStorageBlockEntity) level.getBlockEntity(jarPos);
        var ignis = ThaumicLegacy.id("ignis").toString();
        var aqua = ThaumicLegacy.id("aqua").toString();
        jar.receiveOne(Direction.UP, aqua, () -> true);
        test.assertFalse(EssentiaTransport.tryTransfer(level, jarPos), "The furnace rejects Aqua");
        jar.extractOne(Direction.UP, aqua);
        jar.receiveOne(Direction.UP, ignis, () -> true);
        jar.receiveOne(Direction.UP, ignis, () -> true);
        test.assertTrue(EssentiaTransport.tryTransfer(level, jarPos), "Front tube supplies Ignis alongside four bellows");
        test.assertFalse(EssentiaTransport.tryTransfer(level, jarPos), "A repeated transfer cannot overfill the reserve");
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        var saved = furnace.saveWithoutMetadata(registries);
        test.assertValueEqual(saved.getInt("work") - next, InfernalFurnaceBlockEntity.rate(4, true),
                "Four bellows and Ignis both speed up the same tick");
        test.assertTrue(saved.getInt("ignis_ticks") > 0, "Boost time is retained for the next tick");
        test.assertValueEqual(jar.transportContents(Direction.UP).get(ignis), 1, "Only one unit left the jar");

        saved.put("output", new ItemStack(Items.IRON_INGOT, 64).save(registries));
        furnace.loadWithComponents(saved, registries);
        InfernalFurnaceBlockEntity.tick(level, pos, state, furnace);
        var blocked = furnace.saveWithoutMetadata(registries);
        test.assertValueEqual(blocked.getInt("ignis_ticks"), saved.getInt("ignis_ticks"),
                "A blocked output does not consume the Ignis boost");
        test.assertFalse(EssentiaTransport.tryTransfer(level, jarPos), "A paused furnace does not take more Ignis");
        test.succeed();
    }

    private static void build(ServerLevel level, BlockPos pos, Direction front) {
        var across = front.getClockWise();
        for (int back = 0; back < 3; back++) {
            for (int right = -1; right <= 1; right++) {
                var cell = pos.relative(front.getOpposite(), back).relative(across, right);
                level.setBlockAndUpdate(cell.below(), Blocks.NETHER_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(cell.above(), back == 1 && right == 0
                        ? Blocks.AIR.defaultBlockState() : Blocks.NETHER_BRICKS.defaultBlockState());
                level.setBlockAndUpdate(cell, cell.equals(pos) ? Blocks.IRON_BARS.defaultBlockState()
                        : back == 1 && right == 0 ? Blocks.LAVA.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
            }
        }
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        return player;
    }

    private static void observeBlock(ServerPlayer player, BlockPos pos, ItemStack lens) {
        var center = pos.getCenter();
        player.moveTo(center.x, center.y + 1.1, center.z, 0, 90);
        player.setItemInHand(InteractionHand.MAIN_HAND, lens);
        var hit = new BlockHitResult(center, Direction.UP, pos, false);
        player.gameMode.useItemOn(player, player.serverLevel(), lens, InteractionHand.MAIN_HAND, hit);
    }

    private static void click(ServerPlayer player, BlockPos pos, Direction face, ItemStack stack) {
        var center = pos.getCenter();
        player.moveTo(center.x + face.getStepX() * 1.6, center.y - 0.5 + face.getStepY() * 1.6,
                center.z + face.getStepZ() * 1.6, 0, face == Direction.UP ? 90 : 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(center.add(face.getStepX() * 0.5, face.getStepY() * 0.5,
                face.getStepZ() * 0.5), face, pos, false);
        player.gameMode.useItemOn(player, player.serverLevel(), stack, InteractionHand.MAIN_HAND, hit);
    }
}
