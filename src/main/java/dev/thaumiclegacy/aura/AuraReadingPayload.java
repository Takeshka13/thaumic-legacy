package dev.thaumiclegacy.aura;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuraReadingPayload(int vis, int capacity, int flux, int recoveryTicks) implements CustomPacketPayload {
	public static final Type<AuraReadingPayload> TYPE = new Type<>(ThaumicLegacy.id("aura_reading"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AuraReadingPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, AuraReadingPayload::vis,
		ByteBufCodecs.VAR_INT, AuraReadingPayload::capacity,
		ByteBufCodecs.VAR_INT, AuraReadingPayload::flux,
		ByteBufCodecs.VAR_INT, AuraReadingPayload::recoveryTicks,
		AuraReadingPayload::new);

	@Override
	public Type<AuraReadingPayload> type() {
		return TYPE;
	}
}
