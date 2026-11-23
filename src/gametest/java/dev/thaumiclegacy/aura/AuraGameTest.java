package dev.thaumiclegacy.aura;

import dev.thaumiclegacy.ThaumicLegacy;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

public class AuraGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void spendsAndBoundsAuraBalances(GameTestHelper test) {
        var level = test.getLevel();
        var position = new ChunkPos(80_000, 80_000);
        AuraSavedData.get(level).store(position.toLong(), AuraCell.initial(level.getGameTime()));
        var initial = AuraService.read(level, position);
        assertBalances(test, initial, 100, 0);
        test.assertFalse(AuraService.trySpendVis(level, position, 0), "Zero Vis spend is rejected");
        test.assertFalse(AuraService.trySpendVis(level, position, Integer.MIN_VALUE), "Negative Vis spend is rejected");
        test.assertValueEqual(AuraService.addFlux(level, position, -1), 0, "Negative Flux addition is rejected");
        test.assertValueEqual(AuraService.restoreVis(level, position, -1), 0, "Negative Vis restoration is rejected");
        test.assertValueEqual(AuraService.removeFlux(level, position, -1), 0, "Negative Flux removal is rejected");
        test.assertTrue(AuraService.trySpendVis(level, position, 70), "Available Vis is spent");
        test.assertFalse(AuraService.trySpendVis(level, position, 31), "Insufficient Vis cannot be spent partially");
        assertBalances(test, AuraService.read(level, position), 30, 0);

        test.assertValueEqual(AuraService.addFlux(level, position, Integer.MAX_VALUE), 100, "Flux saturates at its cap");
        assertBalances(test, AuraService.read(level, position), 30, 100);
        test.assertTrue(AuraService.trySpendVis(level, position, 30), "Pollution does not prevent spending stored Vis");
        test.assertFalse(AuraService.trySpendVis(level, position, 1), "Vis cannot become negative");
        test.assertValueEqual(AuraService.restoreVis(level, position, Integer.MAX_VALUE), 100, "Vis saturates at capacity");
        test.assertValueEqual(AuraService.removeFlux(level, position, Integer.MAX_VALUE), 100, "Flux removal clamps at zero");
        assertBalances(test, AuraService.read(level, position), 100, 0);
        var saved = AuraSavedData.get(level).save(new CompoundTag(), level.registryAccess());
        test.assertFalse(hasRecordedChunk(saved, position), "A clean, full balance does not need a saved row");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void keepsAuraLocalToUnloadedChunksAndDimensions(GameTestHelper test) {
        var level = test.getLevel();
        var nether = level.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            test.fail("Aura isolation requires the Nether");
            return;
        }
        var position = new ChunkPos(81_000, -81_000);
        var neighbor = new ChunkPos(position.x + 1, position.z);
        var data = AuraSavedData.get(level);
        // The GameTest runner keeps the test world across runs.
        var pristine = AuraCell.initial(level.getGameTime());
        data.store(position.toLong(), pristine);
        test.assertFalse(level.hasChunk(position.x, position.z), "Fixture chunk starts unloaded");
        test.assertFalse(level.hasChunk(neighbor.x, neighbor.z), "Neighbor starts unloaded");
        assertBalances(test, AuraService.read(level, position), 100, 0);
        test.assertFalse(level.hasChunk(position.x, position.z), "Reading does not load the measured chunk");
        test.assertTrue(AuraService.trySpendVis(level, position, 41), "Vis can be spent from recorded aura without a terrain load");
        test.assertValueEqual(AuraService.addFlux(level, position, 51), 51, "Flux addition is local");
        assertBalances(test, AuraService.read(level, position), 59, 51);
        assertBalances(test, AuraService.read(level, neighbor), 100, 0);
        assertBalances(test, AuraService.read(nether, position), 100, 0);
        test.assertFalse(level.hasChunk(position.x, position.z), "Mutation does not load the measured chunk");
        test.assertFalse(level.hasChunk(neighbor.x, neighbor.z), "Aura does not load the neighbor");
        test.assertFalse(nether.hasChunk(position.x, position.z), "Measurement does not load the other dimension's chunk");
        data.store(position.toLong(), pristine);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void craftsThaumometer(GameTestHelper test) {
        var level = test.getLevel();
        var id = ThaumicLegacy.id("thaumometer");
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(id).orElseThrow().value();
        var input = CraftingInput.of(3, 3, List.of(
            ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY,
            new ItemStack(Items.GLASS_PANE), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.GLASS_PANE),
            ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY));
        test.assertTrue(recipe.matches(input, level), "Thaumometer recipe accepts the documented pattern");
        var result = recipe.assemble(input, level.registryAccess());
        test.assertTrue(result.getItem() instanceof ThaumometerItem && result.getCount() == 1,
            "Recipe creates exactly one registered Thaumometer");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void projectsDecayAndRecoveryAcrossFluxBands(GameTestHelper test) {
        var base = AuraCell.initial(0).withVis(20).withFlux(75);
        test.assertValueEqual(base.recoveryTicks(), 0, "Flux 75 blocks recovery");
        test.assertValueEqual(base.advance(1_199).vis(), 20, "Blocked band cannot recover before decay");
        var enteredSlowBand = base.advance(1_200);
        test.assertValueEqual(enteredSlowBand.flux(), 74, "Flux decays after exactly 1200 ticks");
        test.assertValueEqual(enteredSlowBand.recoveryTicks(), 400, "Flux 74 permits slow recovery");
        test.assertValueEqual(enteredSlowBand.vis(), 20, "The boundary tick uses the previous band's rate");
        test.assertValueEqual(base.advance(1_599).vis(), 20, "399 ticks in the slow band yield no Vis");
        test.assertValueEqual(base.advance(1_600).vis(), 21, "400 ticks in the slow band recover one Vis");
        test.assertValueEqual(base.advance(31_200).flux(), 49, "Further decay crosses the middle band");
        test.assertValueEqual(base.advance(31_200).recoveryTicks(), 200, "Flux 49 doubles the recovery rate");

        var partlyRecovered = AuraCell.initial(0).withVis(10).advance(50);
        var polluted = partlyRecovered.withFlux(25);
        test.assertValueEqual(polluted.advance(149).vis(), 10, "Partial recovery remains below one whole Vis");
        test.assertValueEqual(polluted.advance(150).vis(), 11, "Changing rates preserves partial recovery");

        var worstCase = AuraCell.initial(0).withVis(0).withFlux(100);
        test.assertValueEqual(worstCase.advance(33_333).advance(80_001), worstCase.advance(80_001),
            "Intermediate projections do not compound credit");
        test.assertValueEqual(worstCase.advance(Long.MAX_VALUE), AuraCell.initial(Long.MAX_VALUE),
            "Extremely old ledgers reach equilibrium without overflow");
        test.assertValueEqual(base.advance(1_600).advance(1_000), base.advance(1_600),
            "Clock rollback cannot grant recovery");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void auraReadsAreStableAndBalancesReloadFromDisk(GameTestHelper test) {
        test.runAfterDelay(25, () -> {
            var level = test.getLevel();
            var pos = new ChunkPos(82_000, 82_000);
            long now = level.getGameTime();
            var cell = new AuraCell(30, 25, now - 15, 390, 1_180);
            var data = AuraSavedData.get(level);
            data.store(pos.toLong(), cell);

            var initial = AuraService.read(level, pos);
            assertBalances(test, initial, 31, 25);
            test.assertValueEqual(AuraService.read(level, pos), initial, "Identical reads cannot grant more recovery");
            test.assertValueEqual(data.project(pos.toLong(), cell.tick()), cell, "Reading keeps the stored time and remainders");

            level.getDataStorage().save();
            var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(AuraSavedData.STORAGE_ID + ".dat");
            try {
                var persisted = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound("data");
                test.assertValueEqual(persisted.getInt("version"), 1, "Aura schema has a version");
                var loaded = AuraSavedData.load(persisted, level.registryAccess());
                test.assertValueEqual(loaded.project(pos.toLong(), cell.tick()), cell, "Disk roundtrip retains both remainders");
                test.assertValueEqual(loaded.project(pos.toLong(), now + 5), cell.advance(now + 5),
                    "Flux projection continues at the persisted decay boundary");
            } catch (java.io.IOException exception) {
                test.fail("Aura SavedData could not be loaded: " + exception.getMessage());
            } finally {
                data.store(pos.toLong(), AuraCell.initial(level.getGameTime()));
            }
            test.succeed();
        });
    }

    private static boolean hasRecordedChunk(CompoundTag saved, ChunkPos pos) {
        return saved.getList("cells", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
            .anyMatch(cell -> cell.getLong("chunk") == pos.toLong());
    }

    private static void assertBalances(GameTestHelper test, AuraService.Snapshot reading, int vis, int flux) {
        test.assertValueEqual(reading.capacity(), 100, "Vis capacity");
        test.assertValueEqual(reading.vis(), vis, "Available Vis");
        test.assertValueEqual(reading.flux(), flux, "Flux");
    }
}
