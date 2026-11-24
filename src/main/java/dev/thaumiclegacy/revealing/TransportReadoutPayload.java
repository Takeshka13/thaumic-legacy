package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TransportReadoutPayload(BlockPos pos, boolean worn, String aspect, int amount, int connections,
		String status, String filter) implements CustomPacketPayload {
	public static final Type<TransportReadoutPayload> TYPE = new Type<>(ThaumicLegacy.id("transport_readout"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TransportReadoutPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, TransportReadoutPayload::pos,
			ByteBufCodecs.BOOL, TransportReadoutPayload::worn,
			ByteBufCodecs.stringUtf8(32), TransportReadoutPayload::status,
			ByteBufCodecs.VAR_INT, TransportReadoutPayload::connections,
			Aspects.STREAM_CODEC, value -> new Aspects(value.aspect, value.amount, value.filter),
			(pos, worn, status, connections, aspects) -> new TransportReadoutPayload(pos, worn, aspects.aspect,
					aspects.amount, connections, status, aspects.filter));

	@Override
	public Type<TransportReadoutPayload> type() {
		return TYPE;
	}

	private record Aspects(String aspect, int amount, String filter) {
		private static final StreamCodec<RegistryFriendlyByteBuf, Aspects> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(128), Aspects::aspect,
				ByteBufCodecs.VAR_INT, Aspects::amount,
				ByteBufCodecs.stringUtf8(128), Aspects::filter, Aspects::new);
	}
}
