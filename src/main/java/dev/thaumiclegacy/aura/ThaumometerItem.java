package dev.thaumiclegacy.aura;

import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.NodeResearch;
import dev.thaumiclegacy.revealing.RevealingSight;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ThaumometerItem extends FieldLensItem {
	private static final int READING_COOLDOWN_TICKS = 8;
	private final java.util.Map<ServerPlayer, Long> nextReading = new java.util.WeakHashMap<>();

	public ThaumometerItem(Properties properties) {
		super(properties);
	}

	/** Convert carried legacy instruments without changing inventories in unloaded containers. */
	public static void registerLegacyExchange(Item thaumometer, Item... legacy) {
		Set<Item> oldItems = Set.of(legacy);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				exchangeLegacy(player.getInventory(), thaumometer, oldItems);
			}
		});
	}

	static void exchangeLegacy(Inventory inventory, Item thaumometer, Set<Item> oldItems) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack old = inventory.getItem(slot);
			if (old.isEmpty() || !oldItems.contains(old.getItem())) {
				continue;
			}
			ItemStack replacement = new ItemStack(thaumometer);
			replacement.applyComponents(old.getComponentsPatch());
			while (old.getCount() > replacement.getMaxStackSize()) {
				int free = inventory.getFreeSlot();
				if (free < 0) {
					break;
				}
				inventory.setItem(free, replacement.copyWithCount(replacement.getMaxStackSize()));
				old.shrink(replacement.getMaxStackSize());
				inventory.setChanged();
			}
			if (old.getCount() <= replacement.getMaxStackSize()) {
				inventory.setItem(slot, replacement.copyWithCount(old.getCount()));
			}
		}
	}

	@Override
	protected void beginScan(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now >= nextReading.getOrDefault(player, Long.MIN_VALUE)
				&& ServerPlayNetworking.canSend(player, AuraReadingPayload.TYPE)) {
			nextReading.put(player, now + READING_COOLDOWN_TICKS);
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
		if (!player.isShiftKeyDown()) {
			return;
		}
		var level = player.serverLevel();
		var center = player.chunkPosition();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			var chunk = new ChunkPos(center.x + direction.getStepX(), center.z + direction.getStepZ());
			if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
				var reading = AuraService.read(level, chunk);
				player.sendSystemMessage(Component.translatable("item.thaumiclegacy.aura_surveyor.reading",
						Component.translatable("item.thaumiclegacy.aura_surveyor." + direction.getName()),
						chunk.x, chunk.z, reading.vis() + "/" + reading.capacity(), reading.flux()));
			}
		}
	}
}
