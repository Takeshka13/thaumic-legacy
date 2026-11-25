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
		NodeProgress nodeProgress, ResearchView research) implements CustomPacketPayload {
	static final int MAX_SPECIMENS = 256;
	static final int MAX_DISCOVERED = 256;
	static final int MAX_LEGACY = 3;
	public static final Type<JournalSnapshotPayload> TYPE = new Type<>(ThaumicLegacy.id("aspect_journal_details_v5"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JournalSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LEGACY)), JournalSnapshotPayload::observations,
			ResearchFlags.STREAM_CODEC, journal -> new ResearchFlags(journal.unlocked, journal.arcaneFoundationsUnlocked,
					journal.alchemyFoundationsUnlocked),
			SpecimenView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SPECIMENS)), JournalSnapshotPayload::specimens,
			AspectView.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_DISCOVERED)), JournalSnapshotPayload::discovered,
			ProgressViews.STREAM_CODEC, journal -> new ProgressViews(journal.nodeProgress, journal.research),
			(observations, flags, specimens, discovered, progress) -> new JournalSnapshotPayload(observations,
					flags.unlocked(), flags.arcane(), flags.alchemy(), specimens, discovered, progress.nodes, progress.research));

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

	private record ProgressViews(NodeProgress nodes, ResearchView research) {
		private static final StreamCodec<RegistryFriendlyByteBuf, ProgressViews> STREAM_CODEC = StreamCodec.composite(
				NodeProgress.STREAM_CODEC, ProgressViews::nodes,
				ResearchView.STREAM_CODEC, ProgressViews::research, ProgressViews::new);
	}

	public record ResearchView(List<CategoryView> categories) {
		private static final StreamCodec<RegistryFriendlyByteBuf, ResearchView> STREAM_CODEC = StreamCodec.composite(
				CategoryView.STREAM_CODEC.apply(ByteBufCodecs.list(32)), ResearchView::categories, ResearchView::new);
		public ResearchView { categories = List.copyOf(categories); }
	}

	public record CategoryView(ResourceLocation id, String title, List<EntryView> entries) {
		private static final StreamCodec<RegistryFriendlyByteBuf, CategoryView> STREAM_CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, CategoryView::id,
				ByteBufCodecs.stringUtf8(160), CategoryView::title,
				EntryView.STREAM_CODEC.apply(ByteBufCodecs.list(256)), CategoryView::entries, CategoryView::new);
		public CategoryView { entries = List.copyOf(entries); }
	}

	public record EntryView(ResourceLocation id, String title, String body, boolean unlocked, List<ResourceLocation> parents) {
		private static final StreamCodec<RegistryFriendlyByteBuf, EntryView> STREAM_CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, EntryView::id,
				ByteBufCodecs.stringUtf8(160), EntryView::title,
				ByteBufCodecs.stringUtf8(160), EntryView::body,
				ByteBufCodecs.BOOL, EntryView::unlocked,
				ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(16)), EntryView::parents, EntryView::new);
		public EntryView { parents = List.copyOf(parents); }
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
		static final StreamCodec<RegistryFriendlyByteBuf, AspectView> STREAM_CODEC = StreamCodec.composite(
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
