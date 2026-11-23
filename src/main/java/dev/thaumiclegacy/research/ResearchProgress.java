package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-side lookup for recipe research requirements. */
public final class ResearchProgress {
	public static final ResourceLocation ARCANE_FOUNDATIONS = ThaumicLegacy.id("arcane_foundations");
	public static final ResourceLocation ALCHEMY_FOUNDATIONS = ThaumicLegacy.id("alchemy_foundations");
	public static final ResourceLocation ESSENTIA_DISTILLATION = ThaumicLegacy.id("essentia_distillation");

	private ResearchProgress() {
	}

	public static void register() {
		ResearchCatalog.register();
	}

	public static void recordLumenBrew(ServerPlayer player) {
		AspectDiscoveryData.get(player).recordLumenBrew(player.getUUID());
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
		var evidence = new Evidence(comparing, data.arcaneFoundationsUnlocked(uuid, legacy),
				data.alchemyFoundationsUnlocked(uuid, legacy), NodeResearch.progress(player, comparing), data.hasLumenBrew(uuid));
		return ResearchCatalog.current().unlocks(evidence::satisfies).contains(research);
	}

	record Evidence(boolean comparing, boolean arcane, boolean alchemy, JournalSnapshotPayload.NodeProgress nodes,
			boolean lumenBrew) {
		boolean satisfies(String gate) {
			return switch (gate) {
				case "always" -> true;
				case "comparing_specimens" -> comparing;
				case "arcane_foundations" -> arcane;
				case "alchemy_foundations" -> alchemy;
				case "node_basics" -> nodes.basics();
				case "node_study" -> nodes.observed() > 0;
				case "wand_charging" -> nodes.charged();
				case "lumen_brew" -> lumenBrew;
				default -> false;
			};
		}
	}
}
