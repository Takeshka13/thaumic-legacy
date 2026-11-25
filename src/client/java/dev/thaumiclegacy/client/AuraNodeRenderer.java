package dev.thaumiclegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.node.AuraNodeBlock;
import dev.thaumiclegacy.node.AuraNodeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

final class AuraNodeRenderer implements BlockEntityRenderer<AuraNodeBlockEntity> {
	private static final RenderType GLOW = RenderType.entityTranslucent(ThaumicLegacy.id("textures/block/aura_node_glow.png"));

	@Override
	public void render(AuraNodeBlockEntity node, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int light, int overlay) {
		var client = Minecraft.getInstance();
		var camera = client.gameRenderer.getMainCamera();
		boolean goggles = client.player != null && client.player.getItemBySlot(EquipmentSlot.HEAD)
				.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ThaumicLegacy.id("goggles_of_revealing")));
		var bearing = Vec3.atCenterOf(node.getBlockPos()).subtract(camera.getPosition()).normalize();
		boolean inLens = ThaumometerLens.active(client)
				&& client.player.getViewVector(partialTick).dot(bearing) > 0.965;
		var variant = node.getBlockState().getValue(AuraNodeBlock.VARIANT);
		int rgb = switch (variant) {
			case NORMAL -> 0xA1E7D9;
			case BRIGHT -> 0xFFE296;
			case UNSTABLE -> 0xF0A1BE;
		};
		int opacity = inLens ? 245 : goggles ? 190 : 63;
		float radius = inLens || goggles ? 0.47F : 0.33F;
		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(camera.rotation());
		var pose = poseStack.last();
		var vertices = buffers.getBuffer(GLOW);
		vertex(vertices, pose, -radius, -radius, 0, 1, rgb, opacity);
		vertex(vertices, pose, radius, -radius, 1, 1, rgb, opacity);
		vertex(vertices, pose, radius, radius, 1, 0, rgb, opacity);
		vertex(vertices, pose, -radius, radius, 0, 0, rgb, opacity);
		poseStack.popPose();
	}

	private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, int u, int v, int rgb, int opacity) {
		vertices.addVertex(pose, x, y, 0)
				.setColor(rgb >> 16, rgb >> 8 & 255, rgb & 255, opacity).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
	}
}
