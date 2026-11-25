package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record LensFocusPayload(Component name, List<AspectView> aspects) implements CustomPacketPayload {
	static final int MAX_ASPECTS = 16;
	public static final Type<LensFocusPayload> TYPE = new Type<>(ThaumicLegacy.id("lens_focus"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LensFocusPayload> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC, LensFocusPayload::name,
			AspectView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASPECTS)), LensFocusPayload::aspects,
			LensFocusPayload::new);

	@Override
	public Type<LensFocusPayload> type() {
		return TYPE;
	}
}
