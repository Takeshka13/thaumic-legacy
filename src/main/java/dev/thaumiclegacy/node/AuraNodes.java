package dev.thaumiclegacy.node;

import dev.thaumiclegacy.ThaumicLegacy;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuraNodes {
	private static final ResourceLocation ID = ThaumicLegacy.id("aura_node");
	private static final int MAX_RADIUS = 64;
	private static final int MAX_RESULTS = 16;
	private static final Map<ServerLevel, Map<Long, Set<AuraNodeBlockEntity>>> LOADED = new HashMap<>();

	public static final AuraNodeBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, ID,
			new AuraNodeBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).noLootTable()
					.pushReaction(PushReaction.BLOCK).noCollission().noOcclusion().lightLevel(state -> 8)));
	public static final BlockEntityType<AuraNodeBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ID, FabricBlockEntityTypeBuilder.create(AuraNodeBlockEntity::new, BLOCK).build());

	private AuraNodes() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE, ID, new AuraNodeFeature());
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				ResourceKey.create(Registries.PLACED_FEATURE, ID));
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AuraNodeBlockEntity node) {
				LOADED.computeIfAbsent(level, ignored -> new HashMap<>())
						.computeIfAbsent(chunkKey(node.getBlockPos()), ignored -> new HashSet<>()).add(node);
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof AuraNodeBlockEntity node) {
				var indexed = LOADED.get(level);
				if (indexed != null) {
					long chunk = chunkKey(node.getBlockPos());
					var nodes = indexed.get(chunk);
					if (nodes != null && nodes.remove(node) && nodes.isEmpty()) {
						indexed.remove(chunk);
					}
				}
			}
		});
		ServerWorldEvents.UNLOAD.register((server, level) -> LOADED.remove(level));
	}

	public static List<AuraNodeBlockEntity> nearby(ServerLevel level, BlockPos center, int radius, int limit) {
		if (!level.getServer().isSameThread() || radius < 0 || limit <= 0) {
			return List.of();
		}
		var indexed = LOADED.get(level);
		if (indexed == null) {
			return List.of();
		}
		int boundedRadius = Math.min(MAX_RADIUS, radius);
		int minX = SectionPos.blockToSectionCoord(center.getX() - boundedRadius);
		int minZ = SectionPos.blockToSectionCoord(center.getZ() - boundedRadius);
		int maxX = SectionPos.blockToSectionCoord(center.getX() + boundedRadius);
		int maxZ = SectionPos.blockToSectionCoord(center.getZ() + boundedRadius);
		int rangeSquared = boundedRadius * boundedRadius;
		var result = new ArrayList<AuraNodeBlockEntity>();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (var node : indexed.getOrDefault(ChunkPos.asLong(x, z), Set.of())) {
					if (!node.isRemoved() && node.getBlockPos().distSqr(center) <= rangeSquared) {
						result.add(node);
					}
				}
			}
		}
		return result.stream().sorted(Comparator
				.comparingDouble((AuraNodeBlockEntity node) -> node.getBlockPos().distSqr(center))
				.thenComparing(AuraNodeBlockEntity::getBlockPos))
				.limit(Math.min(MAX_RESULTS, limit)).toList();
	}

	private static long chunkKey(BlockPos pos) {
		return ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
	}
}
