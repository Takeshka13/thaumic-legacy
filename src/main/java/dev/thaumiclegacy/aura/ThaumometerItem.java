package dev.thaumiclegacy.aura;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ThaumometerItem extends Item {
	private static final int READING_COOLDOWN_TICKS = 8;

	public ThaumometerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null || !measure(context.getPlayer())) {
			return InteractionResult.PASS;
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		return measure(player) ? InteractionResultHolder.sidedSuccess(held, level.isClientSide())
			: InteractionResultHolder.pass(held);
	}

	private boolean measure(Player player) {
		if (player.getCooldowns().isOnCooldown(this)) {
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (!ServerPlayNetworking.canSend(serverPlayer, AuraReadingPayload.TYPE)) {
				return false;
			}
			var reading = AuraService.read(serverPlayer.serverLevel(), serverPlayer.chunkPosition());
			player.getCooldowns().addCooldown(this, READING_COOLDOWN_TICKS);
			ServerPlayNetworking.send(serverPlayer,
				new AuraReadingPayload(reading.vis(), reading.capacity(), reading.flux(), reading.recoveryTicks()));
		}
		return true;
	}
}
