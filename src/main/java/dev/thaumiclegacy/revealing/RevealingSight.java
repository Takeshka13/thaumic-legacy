package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.alchemy.Alchemy;
import dev.thaumiclegacy.alchemy.CrucibleBlockEntity;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.Optional;

public final class RevealingSight {
	private static final int UPDATE_INTERVAL = 10;
	private static final double MAX_GOGGLES_RANGE = 6.0;

	private RevealingSight() {
	}

	public static void register(Item goggles) {
		PayloadTypeRegistry.playS2C().register(RevealingPayload.TYPE, RevealingPayload.STREAM_CODEC);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % UPDATE_INTERVAL == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.getItemBySlot(EquipmentSlot.HEAD).is(goggles) && player.isAlive() && !player.isSpectator()
							&& ServerPlayNetworking.canSend(player, RevealingPayload.TYPE)) {
						revealFocus(player);
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

	private static void revealFocus(ServerPlayer player) {
		double reach = Math.min(player.blockInteractionRange(), MAX_GOGGLES_RANGE);
		var eye = player.getEyePosition();
		var end = eye.add(player.getViewVector(1.0F).scale(reach));
		var startPos = BlockPos.containing(eye);
		var endPos = BlockPos.containing(end);
		var level = player.serverLevel();
		if (!level.hasChunksAt(BlockPos.min(startPos, endPos), BlockPos.max(startPos, endPos))
				|| !(player.pick(reach, 1.0F, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		var pos = hit.getBlockPos();
		var state = level.getBlockState(pos);
		int light = state.getLightEmission();
		boolean heat = Alchemy.providesHeat(state);
		Optional<RevealingPayload.CrucibleContents> mixture = state.is(Alchemy.CRUCIBLE)
				&& level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible
			? Optional.of(contents(crucible)) : Optional.empty();
		if (light != 0 || heat || mixture.isPresent()) {
			ServerPlayNetworking.send(player, new RevealingPayload(pos, true, light, heat, mixture));
		}
	}

	private static RevealingPayload.CrucibleContents contents(CrucibleBlockEntity crucible) {
		var snapshot = crucible.snapshot();
		var definitions = AspectCatalog.current().definitions();
		var entries = snapshot.aspects().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.limit(RevealingPayload.MAX_ASPECTS)
			.map(entry -> {
				var aspect = definitions.get(entry.getKey());
				return aspect == null
					? new RevealingPayload.AspectAmount(entry.getKey(), entry.getValue(), 0xA29179, -1)
					: new RevealingPayload.AspectAmount(entry.getKey(), entry.getValue(), aspect.color(), aspect.glyph());
			}).toList();
		return new RevealingPayload.CrucibleContents(snapshot.phase().getSerializedName(), snapshot.amount(), snapshot.capacity(), entries);
	}
}
