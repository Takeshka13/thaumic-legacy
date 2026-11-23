package dev.thaumiclegacy.client;

import dev.thaumiclegacy.aura.AuraReadingPayload;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.ScanFeedbackPayload;
import dev.thaumiclegacy.workbench.ArcaneWorkbench;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.MenuScreens;

public class ThaumicLegacyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ArcaneWorkbench.MENU, ArcaneWorkbenchScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(JournalSnapshotPayload.TYPE, (payload, context) ->
			context.client().setScreen(new ResearchJournalScreen(payload)));

		ScanFeedback scanFeedback = new ScanFeedback();
		ClientPlayNetworking.registerGlobalReceiver(ScanFeedbackPayload.TYPE, (payload, context) ->
			scanFeedback.show(context.client(), payload));
		AuraReadingOverlay auraReading = new AuraReadingOverlay();
		ClientPlayNetworking.registerGlobalReceiver(AuraReadingPayload.TYPE, (payload, context) -> auraReading.show(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			scanFeedback.clear();
			auraReading.clear();
		});
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			scanFeedback.render(graphics);
			auraReading.render(graphics);
		});
	}
}
