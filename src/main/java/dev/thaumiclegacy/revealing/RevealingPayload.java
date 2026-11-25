package dev.thaumiclegacy.revealing;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Collection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import dev.thaumiclegacy.research.ResearchProgress;

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
		public static Component nameFor(ServerPlayer player, String id) {
			return visible(id, 0, ResearchProgress.knownAspects(player)).name();
		}

		public static AspectAmount visible(String id, int amount, Collection<String> known) {
			var definition = known.contains(id) ? AspectCatalog.current().definitions().get(id) : null;
			return definition == null ? new AspectAmount("", amount, 0xA29179, -1)
				: new AspectAmount(id, amount, definition.color(), definition.glyph());
		}

		public Component name() {
			if (id.isEmpty()) {
				return Component.translatable("screen.thaumiclegacy.journal.aspect.unknown");
			}
			var key = ResourceLocation.parse(id);
			return Component.translatableWithFallback(key.toLanguageKey("aspect"), key.getPath()).withColor(color);
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, AspectAmount> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(AspectCatalog.MAX_ID_LENGTH), AspectAmount::id,
			ByteBufCodecs.VAR_INT, AspectAmount::amount,
			ByteBufCodecs.INT, AspectAmount::color,
			ByteBufCodecs.VAR_INT, AspectAmount::glyph,
			AspectAmount::new);
	}
}
