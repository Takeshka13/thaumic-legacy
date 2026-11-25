package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

public record NodeSightPayload(boolean worn, Optional<NodeReading> focus) implements CustomPacketPayload {
	public static final Type<NodeSightPayload> TYPE = new Type<>(ThaumicLegacy.id("node_sight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NodeSightPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, NodeSightPayload::worn,
		ByteBufCodecs.optional(NodeReading.STREAM_CODEC), NodeSightPayload::focus,
		NodeSightPayload::new);

	@Override
	public Type<NodeSightPayload> type() {
		return TYPE;
	}

	public record NodeMarker(BlockPos pos, String variant) {
		private static final StreamCodec<RegistryFriendlyByteBuf, NodeMarker> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, NodeMarker::pos,
			ByteBufCodecs.stringUtf8(8), NodeMarker::variant,
			NodeMarker::new);
	}

	public record NodeReading(NodeMarker marker, List<NodeReserve> reserves) {
		private static final StreamCodec<RegistryFriendlyByteBuf, NodeReading> STREAM_CODEC = StreamCodec.composite(
			NodeMarker.STREAM_CODEC, NodeReading::marker,
			NodeReserve.STREAM_CODEC.apply(ByteBufCodecs.list(RevealingPayload.MAX_ASPECTS)), NodeReading::reserves,
			NodeReading::new);

		public NodeReading {
			reserves = List.copyOf(reserves);
		}
	}

	public record NodeReserve(RevealingPayload.AspectAmount aspect, int capacity) {
		private static final StreamCodec<RegistryFriendlyByteBuf, NodeReserve> STREAM_CODEC = StreamCodec.composite(
			RevealingPayload.AspectAmount.STREAM_CODEC, NodeReserve::aspect,
			ByteBufCodecs.VAR_INT, NodeReserve::capacity,
			NodeReserve::new);
	}
}
