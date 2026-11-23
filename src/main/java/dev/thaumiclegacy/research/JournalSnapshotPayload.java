package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record JournalSnapshotPayload(List<ResourceLocation> observations, boolean unlocked, boolean arcaneFoundationsUnlocked,
		boolean alchemyFoundationsUnlocked, List<SpecimenView> specimens, List<AspectView> discovered,
		NodeProgress nodeProgress) implements CustomPacketPayload {
	static final int MAX_SPECIMENS = 256;
	static final int MAX_DISCOVERED = 256;
	static final int MAX_LEGACY = 3;
	public static final Type<JournalSnapshotPayload> TYPE = new Type<>(ThaumicLegacy.id("aspect_journal_details_v4"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JournalSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LEGACY)), JournalSnapshotPayload::observations,
			ResearchFlags.STREAM_CODEC, journal -> new ResearchFlags(journal.unlocked, journal.arcaneFoundationsUnlocked,
					journal.alchemyFoundationsUnlocked),
			SpecimenView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SPECIMENS)), JournalSnapshotPayload::specimens,
			AspectView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DISCOVERED)), JournalSnapshotPayload::discovered,
			NodeProgress.STREAM_CODEC, JournalSnapshotPayload::nodeProgress,
			(observations, flags, specimens, discovered, nodes) -> new JournalSnapshotPayload(observations,
					flags.unlocked(), flags.arcane(), flags.alchemy(), specimens, discovered, nodes));

	public JournalSnapshotPayload {
		observations = List.copyOf(observations);
		specimens = List.copyOf(specimens);
		discovered = List.copyOf(discovered);
	}

	@Override
	public Type<JournalSnapshotPayload> type() {
		return TYPE;
	}

	private record ResearchFlags(boolean unlocked, boolean arcane, boolean alchemy) {
		private static final StreamCodec<RegistryFriendlyByteBuf, ResearchFlags> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, ResearchFlags::unlocked, ByteBufCodecs.BOOL, ResearchFlags::arcane,
				ByteBufCodecs.BOOL, ResearchFlags::alchemy, ResearchFlags::new);
	}

	public record NodeProgress(boolean basics, int observed, boolean charged) {
		private static final StreamCodec<RegistryFriendlyByteBuf, NodeProgress> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, NodeProgress::basics,
				ByteBufCodecs.VAR_INT, NodeProgress::observed,
				ByteBufCodecs.BOOL, NodeProgress::charged, NodeProgress::new);
	}

	public record SpecimenView(ResourceLocation id, String kind, List<AspectView> aspects) {
		private static final StreamCodec<RegistryFriendlyByteBuf, SpecimenView> STREAM_CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, SpecimenView::id,
				ByteBufCodecs.stringUtf8(8), SpecimenView::kind,
				AspectView.STREAM_CODEC.apply(ByteBufCodecs.list(AspectCatalog.MAX_PROFILE_SIZE)), SpecimenView::aspects,
				SpecimenView::new);

		public SpecimenView {
			aspects = List.copyOf(aspects);
		}
	}

	public record AspectView(String id, int amount, int color, int glyph, List<String> parents) {
		private static final StreamCodec<RegistryFriendlyByteBuf, AspectView> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(AspectCatalog.MAX_ID_LENGTH), AspectView::id,
				ByteBufCodecs.VAR_INT, AspectView::amount,
				ByteBufCodecs.INT, AspectView::color,
				ByteBufCodecs.VAR_INT, AspectView::glyph,
				ByteBufCodecs.stringUtf8(AspectCatalog.MAX_ID_LENGTH).apply(ByteBufCodecs.list(2)), AspectView::parents,
				AspectView::new);

		public AspectView {
			parents = List.copyOf(parents);
		}
	}
}
