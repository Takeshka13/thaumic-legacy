package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.revealing.RevealingPayload.AspectAmount;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record FurnaceBufferPayload(int menuId, List<AspectAmount> aspects) implements CustomPacketPayload {
	public static final Type<FurnaceBufferPayload> TYPE = new Type<>(ThaumicLegacy.id("furnace_buffer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceBufferPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, FurnaceBufferPayload::menuId,
		AspectAmount.STREAM_CODEC.apply(ByteBufCodecs.list(EssentiaFurnaceBlockEntity.CAPACITY)), FurnaceBufferPayload::aspects,
		FurnaceBufferPayload::new);

	@Override
	public Type<FurnaceBufferPayload> type() {
		return TYPE;
	}
}
