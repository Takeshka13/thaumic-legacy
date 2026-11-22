package dev.thaumiclegacy.client;

import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.ScanFeedbackPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ThaumicLegacyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(JournalSnapshotPayload.TYPE, (payload, context) ->
			context.client().setScreen(new ResearchJournalScreen(payload)));

		ScanFeedback scanFeedback = new ScanFeedback();
		ClientPlayNetworking.registerGlobalReceiver(ScanFeedbackPayload.TYPE, (payload, context) ->
			scanFeedback.show(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scanFeedback.clear());
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> scanFeedback.render(graphics));
	}
}
