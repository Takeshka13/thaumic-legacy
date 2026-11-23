package dev.thaumiclegacy.alchemy;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.ThaumicLegacy;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.List;

record AlchemyConfig(int capacity, int heatTicks, int fluxUnitsPerPoint, List<ResourceLocation> heatSources) {
	static final int MAX_CAPACITY = 1_024;
	static final int MAX_HEAT_TICKS = 12_000;
	private static final ResourceLocation CONFIG = ThaumicLegacy.id("thaumiclegacy/alchemy/config.json");
	private static final AlchemyConfig DEFAULT = new AlchemyConfig(64, 100, 8,
			List.of("fire", "soul_fire", "lava", "magma_block", "campfire", "soul_campfire").stream()
					.map(ResourceLocation::withDefaultNamespace).toList());
	private static final Codec<AlchemyConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.intRange(1, MAX_CAPACITY).fieldOf("capacity").forGetter(AlchemyConfig::capacity),
			Codec.intRange(1, MAX_HEAT_TICKS).fieldOf("heat_ticks").forGetter(AlchemyConfig::heatTicks),
			Codec.intRange(1, MAX_CAPACITY).fieldOf("flux_units_per_point").forGetter(AlchemyConfig::fluxUnitsPerPoint),
			ResourceLocation.CODEC.listOf(1, 64).fieldOf("heat_sources").forGetter(AlchemyConfig::heatSources)
	).apply(instance, AlchemyConfig::new));
	private static volatile AlchemyConfig current = DEFAULT;

	static AlchemyConfig current() {
		return current;
	}

	boolean providesHeat(BlockState state) {
		return heatSources.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
				&& (!(state.getBlock() instanceof CampfireBlock) || state.getValue(CampfireBlock.LIT));
	}

	static final class ReloadListener extends SimplePreparableReloadListener<AlchemyConfig>
			implements IdentifiableResourceReloadListener {
		@Override
		public ResourceLocation getFabricId() {
			return ThaumicLegacy.id("alchemy_config");
		}

		@Override
		protected AlchemyConfig prepare(ResourceManager resources, ProfilerFiller profiler) {
			var config = resources.getResource(CONFIG);
			if (config.isEmpty()) {
				return DEFAULT;
			}
			try (var reader = config.get().openAsReader()) {
				AlchemyConfig loaded = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
				for (ResourceLocation id : loaded.heatSources) {
					if (!BuiltInRegistries.BLOCK.containsKey(id) || id.equals(ResourceLocation.withDefaultNamespace("air"))) {
						throw new IllegalArgumentException("Invalid heat source " + id);
					}
				}
				return loaded;
			} catch (IOException | JsonParseException | IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid alchemy config " + CONFIG, exception);
			}
		}

		@Override
		protected void apply(AlchemyConfig prepared, ResourceManager resources, ProfilerFiller profiler) {
			current = prepared;
		}
	}
}
