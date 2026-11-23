package dev.thaumiclegacy.item;

import dev.thaumiclegacy.aura.AuraService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CleansingSaltItem extends Item {
	private static final int FLUX_REMOVED = 8;
	private static final String MESSAGE_KEY = "item.thaumiclegacy.cleansing_salt.";

	public CleansingSaltItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			int removed = AuraService.removeFlux(serverPlayer.serverLevel(), serverPlayer.chunkPosition(), FLUX_REMOVED);
			if (removed == 0) {
				player.displayClientMessage(Component.translatable(MESSAGE_KEY + "clean_aura"), true);
				return InteractionResultHolder.fail(held);
			}
			if (!player.getAbilities().instabuild) {
				held.shrink(1);
			}
			player.displayClientMessage(Component.translatable(MESSAGE_KEY + "cleaned", removed), true);
		}
		return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
	}
}
