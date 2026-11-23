package dev.thaumiclegacy;

import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.AuraCommands;
import dev.thaumiclegacy.aura.AuraReadingPayload;
import dev.thaumiclegacy.aura.ThaumometerItem;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.ResearchJournalItem;
import dev.thaumiclegacy.research.ScanFeedbackPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThaumicLegacy implements ModInitializer {
	public static final String MOD_ID = "thaumiclegacy";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Item fieldLens = Registry.register(BuiltInRegistries.ITEM, id("field_lens"),
				new FieldLensItem(new Item.Properties().stacksTo(1)));
		Item researchJournal = Registry.register(BuiltInRegistries.ITEM, id("research_journal"),
				new ResearchJournalItem(new Item.Properties().stacksTo(1)));
		Item thaumometer = Registry.register(BuiltInRegistries.ITEM, id("thaumometer"),
				new ThaumometerItem(new Item.Properties().stacksTo(1)));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(fieldLens);
			entries.accept(researchJournal);
			entries.accept(thaumometer);
		});
		PayloadTypeRegistry.playS2C().register(JournalSnapshotPayload.TYPE, JournalSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(ScanFeedbackPayload.TYPE, ScanFeedbackPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AuraReadingPayload.TYPE, AuraReadingPayload.STREAM_CODEC);
		FieldLensItem.registerEntityInteraction();
		AspectCatalog.register();
		AuraCommands.register();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
