package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.ResearchProgress;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record AspectTooltipPayload(List<RevealingPayload.AspectAmount> known) implements CustomPacketPayload {
	public static final Type<AspectTooltipPayload> TYPE = new Type<>(ThaumicLegacy.id("aspect_tooltips"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AspectTooltipPayload> STREAM_CODEC = StreamCodec.composite(
		RevealingPayload.AspectAmount.STREAM_CODEC.apply(ByteBufCodecs.list(RevealingPayload.MAX_ASPECTS)),
		AspectTooltipPayload::known, AspectTooltipPayload::new);

	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, TYPE)) {
			var known = ResearchProgress.knownAspects(player);
			var entries = known.stream().limit(RevealingPayload.MAX_ASPECTS)
				.map(id -> RevealingPayload.AspectAmount.visible(id, 0, known)).toList();
			ServerPlayNetworking.send(player, new AspectTooltipPayload(entries));
		}
	}

	@Override
	public Type<AspectTooltipPayload> type() {
		return TYPE;
	}
}
