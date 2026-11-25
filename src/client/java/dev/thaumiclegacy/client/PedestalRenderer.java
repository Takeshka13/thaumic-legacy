package dev.thaumiclegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.thaumiclegacy.infusion.PedestalEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;

final class PedestalRenderer implements BlockEntityRenderer<PedestalEntity> {
	private final ItemRenderer items;

	PedestalRenderer(ItemRenderer items) {
		this.items = items;
	}

	@Override
	public void render(PedestalEntity pedestal, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		var stack = pedestal.stack();
		if (stack.isEmpty()) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5, 1.12, 0.5);
		items.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, pose, buffers, pedestal.getLevel(), 0);
		pose.popPose();
	}
}
