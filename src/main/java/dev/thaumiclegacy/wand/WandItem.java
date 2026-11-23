package dev.thaumiclegacy.wand;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class WandItem extends Item {
	public WandItem(Properties properties) {
		super(properties.stacksTo(1).component(WandVis.COMPONENT, WandVis.EMPTY));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		WandVis vis = WandVis.get(stack);
		for (var primal : WandVis.Primal.values()) {
			tooltip.add(Component.translatable("tooltip.thaumiclegacy.wand.primal",
					Component.translatable(primal.translationKey()), vis.amount(primal), WandVis.CAPACITY));
		}
	}
}
