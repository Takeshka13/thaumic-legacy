package dev.thaumiclegacy.wand;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.AuraService;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

public class WandGameTest implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE)
    public void storesSixIndependentPrimalVisCounters(GameTestHelper test) {
        var level = test.getLevel();
        var chunk = new ChunkPos(test.absolutePos(BlockPos.ZERO));
        var aura = AuraService.read(level, chunk);
        var id = ThaumicLegacy.id("wand");
        var item = BuiltInRegistries.ITEM.get(id);
        test.assertTrue(item instanceof WandItem, "Wand item is registered");
        var wand = new ItemStack(item);
        var separateWand = new ItemStack(item);
        test.assertValueEqual(WandVis.get(wand), WandVis.EMPTY, "New wand has six empty counters");
        test.assertValueEqual(WandVis.CAPACITY, 25, "Each primal has 25 units of capacity");
        test.assertValueEqual(wand.getMaxStackSize(), 1, "Wands have their own unstackable charge state");

        var elements = WandVis.Primal.values();
        test.assertValueEqual(elements.length, 6, "Six classical primals");
        for (int i = 0; i < elements.length; i++) {
            test.assertValueEqual(WandVis.restore(wand, elements[i], i + 1), i + 1,
                "Charge only the selected primal " + elements[i]);
        }
        test.assertValueEqual(List.of(WandVis.get(wand).aer(), WandVis.get(wand).aqua(), WandVis.get(wand).ignis(),
            WandVis.get(wand).terra(), WandVis.get(wand).ordo(), WandVis.get(wand).perditio()),
            List.of(1, 2, 3, 4, 5, 6), "Each primal retains its own charge");
        test.assertTrue(WandVis.trySpend(wand, WandVis.Primal.AQUA, 2), "Spend exact available Aqua");
        test.assertFalse(WandVis.trySpend(wand, WandVis.Primal.AQUA, 1), "Insufficient Aqua cannot draw from other counters");
        test.assertValueEqual(WandVis.restore(wand, WandVis.Primal.AER, Integer.MAX_VALUE), 24, "Aer clamps at capacity");
        test.assertValueEqual(WandVis.restore(wand, WandVis.Primal.AER, 1), 0, "A full counter refuses more Vis");
        test.assertValueEqual(WandVis.restore(wand, WandVis.Primal.TERRA, -1), 0, "Negative restoration is ignored");
        test.assertFalse(WandVis.trySpend(wand, WandVis.Primal.ORDO, 0), "Zero-cost requests are not spent");
        test.assertValueEqual(WandVis.restore(new ItemStack(Items.STICK), WandVis.Primal.AER, 1), 0,
            "Other item types cannot be charged");
        test.assertValueEqual(WandVis.get(separateWand), WandVis.EMPTY, "A separate wand keeps its own state");

        var saved = (CompoundTag) wand.save(level.registryAccess());
        var reloaded = ItemStack.parseOptional(level.registryAccess(), saved);
        test.assertTrue(reloaded.is(item), "The saved stack still holds a wand");
        test.assertValueEqual(WandVis.get(reloaded), new WandVis(25, 0, 3, 4, 5, 6),
            "ItemStack persistence preserves all charge counters");
        test.assertValueEqual(AuraService.read(level, chunk), aura, "Wand operations do not alter scalar chunk aura");
        test.succeed();
    }
}
