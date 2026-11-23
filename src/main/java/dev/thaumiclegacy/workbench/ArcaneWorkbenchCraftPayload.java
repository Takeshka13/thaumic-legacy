package dev.thaumiclegacy.workbench;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ArcaneWorkbenchCraftPayload(int containerId, long token) implements CustomPacketPayload {
	public static final Type<ArcaneWorkbenchCraftPayload> TYPE =
			new Type<>(ThaumicLegacy.id("arcane_workbench_craft"));
	static final StreamCodec<RegistryFriendlyByteBuf, ArcaneWorkbenchCraftPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ArcaneWorkbenchCraftPayload::containerId,
			ByteBufCodecs.VAR_LONG, ArcaneWorkbenchCraftPayload::token,
			ArcaneWorkbenchCraftPayload::new);

	@Override
	public Type<ArcaneWorkbenchCraftPayload> type() {
		return TYPE;
	}
}
