package dev.thaumiclegacy;

import java.util.HashSet;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class CreativeInventoryGameTest implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE)
	public void tabContainsEveryRegisteredModItemOnce(GameTestHelper test) {
		var tabId = ThaumicLegacy.id("main");
		test.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId), "Creative tab must be registered");
		var tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(tabId);
		var level = test.getLevel();
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
		var entries = tab.getDisplayItems().stream().map(ItemStack::getItem).map(BuiltInRegistries.ITEM::getKey).toList();
		var registeredItems = BuiltInRegistries.ITEM.keySet().stream()
				.filter(id -> id.getNamespace().equals(ThaumicLegacy.MOD_ID)).collect(Collectors.toSet());
		test.assertTrue(tab.shouldDisplay(), "The tab must be visible");
		test.assertValueEqual(new HashSet<>(entries), registeredItems, "Tab contents match registered mod items");
		test.assertValueEqual(entries.size(), registeredItems.size(), "Each item is listed once");
		test.assertValueEqual(entries.getFirst(), ThaumicLegacy.id("thaumometer"), "Tools appear first");
		test.assertValueEqual(BuiltInRegistries.ITEM.getKey(tab.getIconItem().getItem()), ThaumicLegacy.id("thaumometer"),
				"Tab icon");
		test.assertTrue(!entries.contains(ThaumicLegacy.id("aura_node")), "World-only aura node stays out of the tab");
		test.succeed();
	}
}
