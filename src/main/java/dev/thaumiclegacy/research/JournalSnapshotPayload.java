package dev.thaumiclegacy.research;

import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record JournalSnapshotPayload(List<ResourceLocation> observations, boolean unlocked) implements CustomPacketPayload {
	public static final Type<JournalSnapshotPayload> TYPE = new Type<>(ThaumicLegacy.id("journal_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JournalSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), JournalSnapshotPayload::observations,
			ByteBufCodecs.BOOL, JournalSnapshotPayload::unlocked,
			JournalSnapshotPayload::new);

	public JournalSnapshotPayload {
		observations = List.copyOf(observations);
	}

	@Override
	public Type<JournalSnapshotPayload> type() {
		return TYPE;
	}
}
