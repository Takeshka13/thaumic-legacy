package dev.thaumiclegacy.research;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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
			ServerPlayNetworking.send(serverPlayer, snapshot(serverPlayer));
		}
	}

	static JournalSnapshotPayload snapshot(ServerPlayer player) {
		return AspectDiscoveryData.get(player).snapshot(player.getUUID(),
				AspectDiscoveryData.legacyObservations(player), AspectCatalog.current(), NodeResearch.progress(player));
	}
}
