package dev.thaumiclegacy;

import dev.thaumiclegacy.advanced.AdvancedAlchemy;
import dev.thaumiclegacy.furnace.InfernalFurnace;
import dev.thaumiclegacy.alchemy.Alchemy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.AuraCommands;
import dev.thaumiclegacy.aura.AuraReadingPayload;
import dev.thaumiclegacy.aura.ThaumometerItem;
import dev.thaumiclegacy.item.CleansingSaltItem;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.node.AuraNodes;
import dev.thaumiclegacy.revealing.GogglesItem;
import dev.thaumiclegacy.revealing.RevealingSight;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.ResearchJournalItem;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.research.ScanFeedbackPayload;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.wand.WandItem;
import dev.thaumiclegacy.workbench.ArcaneWorkbench;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

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
				new ThaumometerItem(new Item.Properties().stacksTo(1)));
		Item researchJournal = Registry.register(BuiltInRegistries.ITEM, id("research_journal"),
				new ResearchJournalItem(new Item.Properties().stacksTo(1)));
		Item thaumometer = Registry.register(BuiltInRegistries.ITEM, id("thaumometer"),
				new ThaumometerItem(new Item.Properties().stacksTo(1)));
		Item goggles = Registry.register(BuiltInRegistries.ITEM, id("goggles_of_revealing"),
				new GogglesItem(new Item.Properties().stacksTo(1)));
		Item wand = Registry.register(BuiltInRegistries.ITEM, id("wand"), new WandItem(new Item.Properties()));
		Item resonantCopper = Registry.register(BuiltInRegistries.ITEM, id("resonant_copper"), new Item(new Item.Properties()));
		Item attunedLens = Registry.register(BuiltInRegistries.ITEM, id("attuned_lens"), new ThaumometerItem(new Item.Properties()));
		Item auraSurveyor = Registry.register(BuiltInRegistries.ITEM, id("aura_surveyor"),
				new ThaumometerItem(new Item.Properties().stacksTo(1)));
		ThaumometerItem.registerLegacyExchange(thaumometer, fieldLens, auraSurveyor, attunedLens);
		Item emberSalt = Registry.register(BuiltInRegistries.ITEM, id("ember_salt"), new Item(new Item.Properties()));
		Item cleansingSalt = Registry.register(BuiltInRegistries.ITEM, id("cleansing_salt"),
				new CleansingSaltItem(new Item.Properties()));
		var lumenGlass = Registry.register(BuiltInRegistries.BLOCK, id("lumen_glass"), new TransparentBlock(
				BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).lightLevel(state -> 15)));
		Item lumenGlassItem = Registry.register(BuiltInRegistries.ITEM, id("lumen_glass"),
				new BlockItem(lumenGlass, new Item.Properties()));
		FuelRegistry.INSTANCE.add(emberSalt, 4_800);
		ArcaneWorkbench.register();
		Alchemy.register();
		EssentiaLogistics.register();
		EssentiaTransport.register();
		AdvancedAlchemy.register();
		InfernalFurnace.register();
		ResearchProgress.register();
		AuraNodes.register();
		RevealingSight.register(goggles);
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.thaumiclegacy.main"))
				.icon(thaumometer::getDefaultInstance)
				.displayItems((parameters, entries) -> {
					entries.accept(thaumometer);
					entries.accept(researchJournal);
					entries.accept(goggles);
					entries.accept(wand);

					entries.accept(resonantCopper);
					entries.accept(emberSalt);

					entries.accept(cleansingSalt);
					entries.accept(Alchemy.CRUCIBLE_ITEM);
					entries.accept(EssentiaLogistics.ALCHEMICAL_FURNACE);
					entries.accept(EssentiaLogistics.ARCANE_ALEMBIC);
					entries.accept(EssentiaLogistics.WARDED_JAR);
					entries.accept(EssentiaLogistics.WARDED_JAR_LABEL);
					entries.accept(EssentiaLogistics.ESSENTIA_PHIAL);

					entries.accept(EssentiaTransport.TUBE);
					entries.accept(EssentiaTransport.VALVE);
					entries.accept(EssentiaTransport.FILTER);
					entries.accept(AdvancedAlchemy.ALCHEMICAL_CENTRIFUGE);
					entries.accept(AdvancedAlchemy.THAUMATORIUM);

					entries.accept(InfernalFurnace.BELLOWS_ITEM);
					entries.accept(ArcaneWorkbench.ITEM);
					entries.accept(lumenGlassItem);
				}).build());
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
