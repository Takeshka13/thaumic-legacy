package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.AuraReadingPayload;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.ScanFeedbackPayload;
import dev.thaumiclegacy.revealing.LogisticsReadoutPayload;
import dev.thaumiclegacy.revealing.NodeSightPayload;
import dev.thaumiclegacy.revealing.RevealingPayload;
import dev.thaumiclegacy.workbench.ArcaneWorkbench;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;

public class ThaumicLegacyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.INSTANCE.putBlock(BuiltInRegistries.BLOCK.get(ThaumicLegacy.id("lumen_glass")),
			RenderType.translucent());
		MenuScreens.register(ArcaneWorkbench.MENU, ArcaneWorkbenchScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(JournalSnapshotPayload.TYPE, (payload, context) ->
			context.client().setScreen(new ResearchJournalScreen(payload)));

		ScanFeedback scanFeedback = new ScanFeedback();
		ClientPlayNetworking.registerGlobalReceiver(ScanFeedbackPayload.TYPE, (payload, context) ->
			scanFeedback.show(context.client(), payload));
		AuraReadingOverlay auraReading = new AuraReadingOverlay();
		ClientPlayNetworking.registerGlobalReceiver(AuraReadingPayload.TYPE, (payload, context) -> auraReading.show(payload));
		RevealingOverlay revealing = new RevealingOverlay();
		ClientPlayNetworking.registerGlobalReceiver(RevealingPayload.TYPE, (payload, context) ->
			revealing.show(context.client(), payload));
		NodeSightOverlay nodeSight = new NodeSightOverlay();
		ClientPlayNetworking.registerGlobalReceiver(NodeSightPayload.TYPE, (payload, context) ->
			nodeSight.show(context.client(), payload));
		LogisticsOverlay logistics = new LogisticsOverlay();
		ClientPlayNetworking.registerGlobalReceiver(LogisticsReadoutPayload.TYPE, (payload, context) ->
			logistics.show(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			scanFeedback.clear();
			auraReading.clear();
			revealing.clear();
			nodeSight.clear();
			logistics.clear();
		});
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			scanFeedback.render(graphics);
			auraReading.render(graphics);
			revealing.render(graphics);
			nodeSight.render(graphics);
			logistics.render(graphics);
		});
	}
}
