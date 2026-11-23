package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-side lookup for recipe research requirements. */
public final class ResearchProgress {
	public static final ResourceLocation ARCANE_FOUNDATIONS = ThaumicLegacy.id("arcane_foundations");

	private ResearchProgress() {
	}

	public static boolean isUnlocked(ServerPlayer player, ResourceLocation research) {
		if (!ARCANE_FOUNDATIONS.equals(research)) {
			return false;
		}

		AspectDiscoveryData data = AspectDiscoveryData.get(player);
		var legacy = AspectDiscoveryData.legacyObservations(player);
		data.importLegacy(player.getUUID(), legacy, AspectCatalog.current());
		return data.arcaneFoundationsUnlocked(player.getUUID(), legacy);
	}
}
