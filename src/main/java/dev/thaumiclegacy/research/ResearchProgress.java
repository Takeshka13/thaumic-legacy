package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/** Server-side lookup for recipe research requirements. */
public final class ResearchProgress {
	public static final ResourceLocation ARCANE_FOUNDATIONS = ThaumicLegacy.id("arcane_foundations");
	public static final ResourceLocation ALCHEMY_FOUNDATIONS = ThaumicLegacy.id("alchemy_foundations");
	public static final ResourceLocation ESSENTIA_DISTILLATION = ThaumicLegacy.id("essentia_distillation");
	public static final ResourceLocation WARDED_JARS = ThaumicLegacy.id("warded_jars");
	public static final ResourceLocation ESSENTIA_TRANSPORT = ThaumicLegacy.id("essentia_transport");
	public static final ResourceLocation ESSENTIA_CONTROLS = ThaumicLegacy.id("essentia_controls");
	public static final ResourceLocation WARDED_JAR_LABELS = ThaumicLegacy.id("warded_jar_labels");
	public static final ResourceLocation IMPROVED_DISTILLATION = ThaumicLegacy.id("improved_distillation");
	public static final ResourceLocation ALCHEMICAL_CENTRIFUGE = ThaumicLegacy.id("alchemical_centrifuge");
	public static final ResourceLocation INFERNAL_FURNACE = ThaumicLegacy.id("infernal_furnace");
	public static final ResourceLocation ARCANE_BELLOWS = ThaumicLegacy.id("arcane_bellows");
	public static final ResourceLocation AUTOMATED_ALCHEMY = ThaumicLegacy.id("automated_alchemy");

	private ResearchProgress() {
	}

	public static void register() {
		ResearchCatalog.register();
	}

	public static void recordLumenBrew(ServerPlayer player) {
		AspectDiscoveryData.get(player).recordLumenBrew(player.getUUID());
	}

	public static void recordJarUse(ServerPlayer player) {
		AspectDiscoveryData.get(player).recordTransportMilestone(player.getUUID(), false);
	}

	public static void recordTubePlacement(ServerPlayer player) {
		if (isUnlocked(player, ESSENTIA_TRANSPORT)) {
			AspectDiscoveryData.get(player).recordTransportMilestone(player.getUUID(), true);
		}
	}

	public static List<String> knownAspects(ServerPlayer player) {
		var data = AspectDiscoveryData.get(player);
		var catalog = AspectCatalog.current();
		data.importLegacy(player.getUUID(), AspectDiscoveryData.legacyObservations(player), catalog);
		return data.knownAspects(player.getUUID(), catalog);
	}

	public static boolean isUnlocked(ServerPlayer player, ResourceLocation research) {
		AspectDiscoveryData data = AspectDiscoveryData.get(player);
		var legacy = AspectDiscoveryData.legacyObservations(player);
		var uuid = player.getUUID();
		data.importLegacy(uuid, legacy, AspectCatalog.current());
		if (ALCHEMY_FOUNDATIONS.equals(research)) {
			return data.alchemyFoundationsUnlocked(uuid, legacy);
		}
		if (ARCANE_FOUNDATIONS.equals(research)) {
			return data.arcaneFoundationsUnlocked(uuid, legacy);
		}
		boolean comparing = data.firstResearchUnlocked(uuid, legacy);
		var evidence = data.evidence(uuid, legacy, NodeResearch.progress(player, comparing));
		return ResearchCatalog.current().unlocks(evidence::satisfies).contains(research);
	}

	record Evidence(boolean comparing, boolean arcane, boolean alchemy, JournalSnapshotPayload.NodeProgress nodes,
			boolean lumenBrew, boolean jarUsed, boolean vitreus, boolean tubePlaced, Set<String> known) {
		boolean satisfies(String gate) {
			if (gate.startsWith("aspect:")) {
				return known.contains(gate.substring("aspect:".length()));
			}
			return switch (gate) {
				case "always" -> true;
				case "comparing_specimens" -> comparing;
				case "arcane_foundations" -> arcane;
				case "alchemy_foundations" -> alchemy;
				case "node_basics" -> nodes.basics();
				case "node_study" -> nodes.observed() > 0;
				case "wand_charging" -> nodes.charged();
				case "lumen_brew" -> lumenBrew;
				case "jar_use" -> jarUsed;
				case "vitreus_discovered" -> vitreus;
				case "tube_placed" -> tubePlaced;
				default -> false;
			};
		}
	}
}
