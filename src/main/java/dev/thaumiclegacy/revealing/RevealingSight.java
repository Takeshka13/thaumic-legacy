package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.alchemy.Alchemy;
import dev.thaumiclegacy.advanced.AlchemicalCentrifugeBlockEntity;
import dev.thaumiclegacy.advanced.ThaumatoriumBlockEntity;
import dev.thaumiclegacy.alchemy.CrucibleBlockEntity;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.ThaumometerItem;
import dev.thaumiclegacy.logistics.EssentiaFurnaceBlockEntity;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.node.AuraNodeBlock;
import dev.thaumiclegacy.node.AuraNodeBlockEntity;
import dev.thaumiclegacy.node.AuraNodes;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.wand.WandVis;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RevealingSight {
	private static final int UPDATE_INTERVAL = 10;
	private static final double MAX_GOGGLES_RANGE = 6.0;
	private static final int NODE_SIGHT_RANGE = 24;

	private RevealingSight() {
	}

	public static void register(Item goggles) {
		PayloadTypeRegistry.playS2C().register(RevealingPayload.TYPE, RevealingPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(NodeSightPayload.TYPE, NodeSightPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(LogisticsReadoutPayload.TYPE, LogisticsReadoutPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(TransportReadoutPayload.TYPE, TransportReadoutPayload.STREAM_CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_INTERVAL == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (!player.isAlive() || player.isSpectator()) {
						continue;
					}
					if (player.getItemBySlot(EquipmentSlot.HEAD).is(goggles)) {
						revealFocus(player, true);
					} else if (player.getMainHandItem().getItem() instanceof ThaumometerItem
							|| player.getOffhandItem().getItem() instanceof ThaumometerItem) {
						revealFocus(player, false);
					}
				}
			}
		});
	}

	public static boolean measureCrucible(ServerPlayer player, BlockPos pos) {
		var level = player.serverLevel();
		if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
			return false;
		}
		if (ServerPlayNetworking.canSend(player, RevealingPayload.TYPE)) {
			ServerPlayNetworking.send(player, new RevealingPayload(pos, false, 0, false, Optional.of(contents(crucible))));
		}
		return true;
	}

	public static Optional<UUID> measureNode(ServerPlayer player, BlockPos pos) {
		var level = player.serverLevel();
		if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
			return Optional.empty();
		}
		var snapshot = node.snapshot();
		if (ServerPlayNetworking.canSend(player, NodeSightPayload.TYPE)) {
			ServerPlayNetworking.send(player, new NodeSightPayload(false, List.of(), Optional.of(nodeReading(snapshot))));
		}
		return Optional.of(snapshot.id());
	}

	public static boolean measureLogistics(ServerPlayer player, BlockPos pos) {
		return revealLogistics(player, pos, false);
	}

	public static boolean measureTransport(ServerPlayer player, BlockPos pos) {
		return revealTransport(player, pos, false);
	}

	private static boolean revealTransport(ServerPlayer player, BlockPos pos, boolean worn) {
		var reading = EssentiaTransport.read(player.serverLevel(), pos);
		if (reading.isEmpty()) {
			return false;
		}
		if (ServerPlayNetworking.canSend(player, TransportReadoutPayload.TYPE)) {
			var view = reading.orElseThrow();
			ServerPlayNetworking.send(player, new TransportReadoutPayload(pos, worn, view.aspect(), view.amount(),
					view.connections(), view.status().name().toLowerCase(Locale.ROOT), view.filter()));
		}
		return true;
	}

	private static boolean revealLogistics(ServerPlayer player, BlockPos pos, boolean worn) {
		var level = player.serverLevel();
		if (!level.hasChunkAt(pos)) {
			return false;
		}
		var entity = level.getBlockEntity(pos);
		LogisticsReadoutPayload reading;
		if (entity instanceof EssentiaStorageBlockEntity storage) {
			var view = storage.snapshot();
			reading = new LogisticsReadoutPayload(pos, worn, "storage", view.amount(), view.capacity(),
					aspectAmounts(view.aspects()), 0, 0, 0);
		} else if (entity instanceof EssentiaFurnaceBlockEntity furnace) {
			var view = furnace.snapshot();
			reading = new LogisticsReadoutPayload(pos, worn, view.state().name().toLowerCase(Locale.ROOT),
					0, 0, List.of(), view.progress(), view.duration(), view.burnTicks());
		} else if (entity instanceof AlchemicalCentrifugeBlockEntity centrifuge) {
			var view = centrifuge.snapshot();
			reading = machineReading(pos, worn, view.status().name(), view.output(), view.outputCapacity(), view.progress(), view.duration());
		} else if (entity instanceof ThaumatoriumBlockEntity thaumatorium) {
			var view = thaumatorium.snapshot();
			reading = machineReading(pos, worn, view.status().name(), view.essentia(), view.capacity(), view.progress(), view.duration());
		} else {
			return false;
		}
		if (ServerPlayNetworking.canSend(player, LogisticsReadoutPayload.TYPE)) {
			ServerPlayNetworking.send(player, reading);
		}
		return true;
	}

	private static LogisticsReadoutPayload machineReading(BlockPos pos, boolean worn, String status, Map<String, Integer> contents,
			int capacity, int progress, int duration) {
		int amount = contents.values().stream().mapToInt(Integer::intValue).sum();
		return new LogisticsReadoutPayload(pos, worn, status.toLowerCase(Locale.ROOT), amount, capacity,
				aspectAmounts(contents), progress, duration, 0);
	}

	private static void revealFocus(ServerPlayer player, boolean worn) {
		boolean readNodes = ServerPlayNetworking.canSend(player, NodeSightPayload.TYPE);
		boolean revealBlocks = worn && ServerPlayNetworking.canSend(player, RevealingPayload.TYPE);
		boolean readTransport = ServerPlayNetworking.canSend(player, TransportReadoutPayload.TYPE);
		if (!readNodes && !revealBlocks && !readTransport) {
			return;
		}
		var level = player.serverLevel();
		var focus = visibleBlock(player);
		if (readNodes) {
			List<NodeSightPayload.NodeMarker> nearby = worn
				? AuraNodes.nearby(level, player.blockPosition(), NODE_SIGHT_RANGE, NodeSightPayload.MAX_MARKERS)
				.stream().map(node -> new NodeSightPayload.NodeMarker(node.getBlockPos(),
					node.getBlockState().getValue(AuraNodeBlock.VARIANT).getSerializedName())).toList() : List.of();
			Optional<NodeSightPayload.NodeReading> nodeReading = focus
				.filter(pos -> level.getBlockState(pos).is(AuraNodes.BLOCK))
				.map(level::getBlockEntity)
				.filter(AuraNodeBlockEntity.class::isInstance)
				.map(AuraNodeBlockEntity.class::cast)
				.map(node -> nodeReading(node.snapshot()));
			if (!nearby.isEmpty() || nodeReading.isPresent()) {
				ServerPlayNetworking.send(player, new NodeSightPayload(worn, nearby, nodeReading));
			}
		}
		if (focus.isEmpty()) {
			return;
		}
		var pos = focus.orElseThrow();
		boolean transport = readTransport && revealTransport(player, pos, worn);
		if (!revealBlocks) {
			return;
		}
		if (revealLogistics(player, pos, true)) {
			return;
		}
		if (transport) {
			return;
		}
		var state = level.getBlockState(pos);
		int light = state.getLightEmission();
		boolean heat = Alchemy.providesHeat(state);
		Optional<RevealingPayload.CrucibleContents> mixture = state.is(Alchemy.CRUCIBLE)
				&& level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible
			? Optional.of(contents(crucible)) : Optional.empty();
		if (!state.is(AuraNodes.BLOCK) && (light != 0 || heat || mixture.isPresent())) {
			ServerPlayNetworking.send(player, new RevealingPayload(pos, true, light, heat, mixture));
		}
	}

	private static Optional<BlockPos> visibleBlock(ServerPlayer player) {
		double reach = Math.min(player.blockInteractionRange(), MAX_GOGGLES_RANGE);
		var eye = player.getEyePosition();
		var end = eye.add(player.getViewVector(1.0F).scale(reach));
		var startPos = BlockPos.containing(eye);
		var endPos = BlockPos.containing(end);
		var level = player.serverLevel();
		if (!level.hasChunksAt(BlockPos.min(startPos, endPos), BlockPos.max(startPos, endPos))
				|| !(player.pick(reach, 1.0F, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return Optional.empty();
		}
		return Optional.of(hit.getBlockPos());
	}

	private static NodeSightPayload.NodeReading nodeReading(AuraNodeBlockEntity.Snapshot snapshot) {
		var marker = new NodeSightPayload.NodeMarker(snapshot.position(), snapshot.variant().getSerializedName());
		var reserves = Arrays.stream(WandVis.Primal.values())
			.map(primal -> new NodeSightPayload.NodeReserve(primal.translationKey(), snapshot.reserve(primal), snapshot.capacity(primal)))
			.toList();
		return new NodeSightPayload.NodeReading(marker, reserves);
	}

	private static RevealingPayload.CrucibleContents contents(CrucibleBlockEntity crucible) {
		var snapshot = crucible.snapshot();
		return new RevealingPayload.CrucibleContents(snapshot.phase().getSerializedName(), snapshot.amount(), snapshot.capacity(),
				aspectAmounts(snapshot.aspects()));
	}

	private static List<RevealingPayload.AspectAmount> aspectAmounts(Map<String, Integer> aspects) {
		var definitions = AspectCatalog.current().definitions();
		return aspects.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.limit(RevealingPayload.MAX_ASPECTS)
			.map(entry -> {
				var aspect = definitions.get(entry.getKey());
				return aspect == null
					? new RevealingPayload.AspectAmount(entry.getKey(), entry.getValue(), 0xA29179, -1)
					: new RevealingPayload.AspectAmount(entry.getKey(), entry.getValue(), aspect.color(), aspect.glyph());
			}).toList();
	}
}
