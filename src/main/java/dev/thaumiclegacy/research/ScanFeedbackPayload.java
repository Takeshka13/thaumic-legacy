package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScanFeedbackPayload(int color, boolean newAspect) implements CustomPacketPayload {
	public static final Type<ScanFeedbackPayload> TYPE = new Type<>(ThaumicLegacy.id("aspect_scan_feedback"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScanFeedbackPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, ScanFeedbackPayload::color,
			ByteBufCodecs.BOOL, ScanFeedbackPayload::newAspect,
			ScanFeedbackPayload::new);

	@Override
	public Type<ScanFeedbackPayload> type() {
		return TYPE;
	}
}
