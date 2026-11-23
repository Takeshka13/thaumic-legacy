package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record LogisticsReadoutPayload(BlockPos pos, boolean worn, String state, int amount, int capacity,
		List<RevealingPayload.AspectAmount> aspects, int progress, int duration, int burnTicks) implements CustomPacketPayload {
	public static final Type<LogisticsReadoutPayload> TYPE = new Type<>(ThaumicLegacy.id("logistics_readout"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsReadoutPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, LogisticsReadoutPayload::pos,
			ByteBufCodecs.BOOL, LogisticsReadoutPayload::worn,
			ByteBufCodecs.stringUtf8(32), LogisticsReadoutPayload::state,
			RevealingPayload.AspectAmount.STREAM_CODEC.apply(ByteBufCodecs.list(RevealingPayload.MAX_ASPECTS)), LogisticsReadoutPayload::aspects,
			Metrics.STREAM_CODEC, value -> new Metrics(value.amount, value.capacity, value.progress, value.duration, value.burnTicks),
			(pos, worn, state, aspects, metrics) -> new LogisticsReadoutPayload(pos, worn, state, metrics.amount,
					metrics.capacity, aspects, metrics.progress, metrics.duration, metrics.burnTicks));

	public LogisticsReadoutPayload {
		aspects = List.copyOf(aspects);
	}

	@Override
	public Type<LogisticsReadoutPayload> type() {
		return TYPE;
	}

	private record Metrics(int amount, int capacity, int progress, int duration, int burnTicks) {
		private static final StreamCodec<RegistryFriendlyByteBuf, Metrics> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Metrics::amount, ByteBufCodecs.VAR_INT, Metrics::capacity,
				ByteBufCodecs.VAR_INT, Metrics::progress, ByteBufCodecs.VAR_INT, Metrics::duration,
				ByteBufCodecs.VAR_INT, Metrics::burnTicks, Metrics::new);
	}
}
