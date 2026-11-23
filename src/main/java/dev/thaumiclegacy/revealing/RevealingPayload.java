package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

public record RevealingPayload(BlockPos pos, boolean worn, int light, boolean heat,
		Optional<CrucibleContents> crucible) implements CustomPacketPayload {
	static final int MAX_ASPECTS = 256;
	public static final Type<RevealingPayload> TYPE = new Type<>(ThaumicLegacy.id("revealing_sight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RevealingPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, RevealingPayload::pos,
		ByteBufCodecs.BOOL, RevealingPayload::worn,
		ByteBufCodecs.VAR_INT, RevealingPayload::light,
		ByteBufCodecs.BOOL, RevealingPayload::heat,
		ByteBufCodecs.optional(CrucibleContents.STREAM_CODEC), RevealingPayload::crucible,
		RevealingPayload::new);

	@Override
	public Type<RevealingPayload> type() {
		return TYPE;
	}

	public record CrucibleContents(String phase, int amount, int capacity, List<AspectAmount> aspects) {
		private static final StreamCodec<RegistryFriendlyByteBuf, CrucibleContents> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(8), CrucibleContents::phase,
			ByteBufCodecs.VAR_INT, CrucibleContents::amount,
			ByteBufCodecs.VAR_INT, CrucibleContents::capacity,
			AspectAmount.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASPECTS)), CrucibleContents::aspects,
			CrucibleContents::new);

		public CrucibleContents {
			aspects = List.copyOf(aspects);
		}
	}

	public record AspectAmount(String id, int amount, int color, int glyph) {
		private static final StreamCodec<RegistryFriendlyByteBuf, AspectAmount> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(AspectCatalog.MAX_ID_LENGTH), AspectAmount::id,
			ByteBufCodecs.VAR_INT, AspectAmount::amount,
			ByteBufCodecs.INT, AspectAmount::color,
			ByteBufCodecs.VAR_INT, AspectAmount::glyph,
			AspectAmount::new);
	}
}
