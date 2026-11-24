package dev.thaumiclegacy.aura;

import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.NodeResearch;
import dev.thaumiclegacy.revealing.RevealingSight;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ThaumometerItem extends FieldLensItem {
	private static final int READING_COOLDOWN_TICKS = 8;

	public ThaumometerItem(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean canScan(Player player) {
		return super.canScan(player) && !player.getCooldowns().isOnCooldown(this);
	}

	@Override
	protected void beginScan(ServerPlayer player) {
		player.getCooldowns().addCooldown(this, READING_COOLDOWN_TICKS);
		if (ServerPlayNetworking.canSend(player, AuraReadingPayload.TYPE)) {
			var reading = AuraService.read(player.serverLevel(), player.chunkPosition());
			ServerPlayNetworking.send(player,
				new AuraReadingPayload(reading.vis(), reading.capacity(), reading.flux(), reading.recoveryTicks()));
		}
	}

	@Override
	protected boolean revealBlock(ServerPlayer player, BlockPos target) {
		var node = RevealingSight.measureNode(player, target);
		if (node.isPresent()) {
			if (NodeResearch.observe(player, node.orElseThrow())) {
				player.displayClientMessage(Component.translatable("message.thaumiclegacy.node.scan_recorded"), true);
			}
			return true;
		}
		boolean transport = RevealingSight.measureTransport(player, target);
		return RevealingSight.measureCrucible(player, target) || RevealingSight.measureLogistics(player, target) || transport;
	}

	@Override
	protected void onAirScan(ServerPlayer player) {
		// The aura reading supplies feedback when the Thaumometer has no specimen in focus.
	}
}
