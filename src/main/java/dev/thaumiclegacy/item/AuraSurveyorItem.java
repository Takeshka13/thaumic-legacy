package dev.thaumiclegacy.item;

import dev.thaumiclegacy.aura.AuraService;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class AuraSurveyorItem extends Item {
	private static final String MESSAGE_KEY = "item.thaumiclegacy.aura_surveyor.";
	private static final int COOLDOWN_TICKS = 20;

	public AuraSurveyorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !survey(player)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		return survey(player) ? InteractionResultHolder.sidedSuccess(held, level.isClientSide())
			: InteractionResultHolder.pass(held);
	}

	private boolean survey(Player player) {
		if (player.getCooldowns().isOnCooldown(this)) {
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			ChunkPos current = player.chunkPosition();
			sendReading(serverPlayer, current, "here");
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				ChunkPos neighbor = new ChunkPos(current.x + direction.getStepX(), current.z + direction.getStepZ());
				sendReading(serverPlayer, neighbor, direction.getName());
			}
			player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
		}
		return true;
	}

	private static void sendReading(ServerPlayer player, ChunkPos chunk, String location) {
		var reading = AuraService.read(player.serverLevel(), chunk);
		player.displayClientMessage(Component.translatable(MESSAGE_KEY + "reading",
			Component.translatable(MESSAGE_KEY + location), chunk.x, chunk.z,
			reading.vis() + "/" + reading.capacity(), reading.flux()), false);
	}
}
