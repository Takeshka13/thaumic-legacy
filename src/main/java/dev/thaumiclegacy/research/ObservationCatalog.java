package dev.thaumiclegacy.research;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ObservationCatalog {
	private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("stone");
	private static final ResourceLocation TORCH = ResourceLocation.withDefaultNamespace("torch");
	private static final ResourceLocation OAK_LOG = ResourceLocation.withDefaultNamespace("oak_log");
	private static final int FIRST_RESEARCH_OBSERVATIONS = 2;

	private ObservationCatalog() {
	}

	public static ResourceLocation find(BlockState state) {
		if (state.is(Blocks.STONE)) {
			return STONE;
		}
		if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
			return TORCH;
		}
		if (state.is(Blocks.OAK_LOG)) {
			return OAK_LOG;
		}
		return null;
	}

	public static boolean firstResearchUnlocked(int distinctObservations) {
		return distinctObservations >= FIRST_RESEARCH_OBSERVATIONS;
	}
}
