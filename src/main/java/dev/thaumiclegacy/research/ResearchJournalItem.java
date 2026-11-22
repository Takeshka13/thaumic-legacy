package dev.thaumiclegacy.research;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ResearchJournalItem extends Item {
	public ResearchJournalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		sendSnapshot(context.getPlayer());
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		sendSnapshot(player);
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}

	private static void sendSnapshot(Player player) {
		if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, JournalSnapshotPayload.TYPE)) {
			List<ResourceLocation> observations = ObservationData.get(serverPlayer).getObservations(serverPlayer.getUUID());
			boolean unlocked = ObservationCatalog.firstResearchUnlocked(observations.size());
			ServerPlayNetworking.send(serverPlayer, new JournalSnapshotPayload(observations, unlocked));
		}
	}
}
