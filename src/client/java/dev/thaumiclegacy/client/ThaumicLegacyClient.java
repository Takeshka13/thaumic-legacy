package dev.thaumiclegacy.client;

import dev.thaumiclegacy.research.JournalSnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ThaumicLegacyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(JournalSnapshotPayload.TYPE, (payload, context) ->
			context.client().setScreen(new ResearchJournalScreen(payload.observations(), payload.unlocked())));
	}
}
