package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aura.ThaumometerItem;
import dev.thaumiclegacy.revealing.NodeSightPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class NodeSightOverlay {
	private static final String KEY = "hud.thaumiclegacy.node.";
	private static final int ROW = 12;
	private static final int FRAME = 0xFF9E7450;
	private static final int BACKGROUND = 0xEC1D191D;
	private static final int TEXT = 0xFFE1D4BB;
	private static final int TITLE = 0xFFF2D39C;

	private Snapshot worn;
	private Snapshot measured;

	void show(Minecraft client, NodeSightPayload payload) {
		long expiresAt = Util.getMillis() + (payload.worn() ? 1_000 : 4_000);
		var snapshot = new Snapshot(client.level, payload, expiresAt);
		if (payload.worn()) {
			worn = snapshot;
		} else {
			measured = snapshot;
		}
	}

	void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.screen != null || client.options.hideGui) {
			return;
		}
		long now = Util.getMillis();
		boolean live = worn != null && worn.isCurrent(client.level, now)
			&& client.player.getItemBySlot(EquipmentSlot.HEAD)
				.is(BuiltInRegistries.ITEM.get(ThaumicLegacy.id("goggles_of_revealing")));
		int y = graphics.guiHeight() >= 150 ? 76 : 6;
		if (live && !worn.data().nearby().isEmpty()) {
			renderNearby(graphics, client, y);
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		int focusY = Math.min(y, Math.max(6, graphics.guiHeight() - 20 - 7 * ROW - 6));
		if (live && renderFocus(graphics, client, focusY, worn, hit)) {
			return;
		}
		if (measured != null && measured.isCurrent(client.level, now)
				&& (client.player.getMainHandItem().getItem() instanceof ThaumometerItem
					|| client.player.getOffhandItem().getItem() instanceof ThaumometerItem)) {
			renderFocus(graphics, client, focusY, measured, hit);
		}
	}

	private void renderNearby(GuiGraphics graphics, Minecraft client, int y) {
		var markers = worn.data().nearby();
		int width = Math.min(158, graphics.guiWidth() / 2 - 8);
		int count = Math.min(markers.size(), Math.max(0, (graphics.guiHeight() - y - 22) / (2 * ROW)));
		int x = 6;
		panel(graphics, x, y, width, 18 + count * 2 * ROW);
		graphics.drawString(client.font, Component.translatable(KEY + "nearby", markers.size()), x + 6, y + 5, TITLE, false);
		for (int index = 0; index < count; index++) {
			var marker = markers.get(index);
			var pos = marker.pos();
			int distance = (int) Math.round(Math.sqrt(pos.distSqr(client.player.blockPosition())));
			int rowY = y + 17 + index * 2 * ROW;
			var line = Component.translatable(KEY + "marker", variant(marker.variant()), distance);
			graphics.drawString(client.font, line, x + 6, rowY, tint(marker.variant()), false);
			graphics.drawString(client.font, Component.translatable(KEY + "coordinates", pos.getX(), pos.getY(), pos.getZ()),
				x + 12, rowY + ROW, TEXT, false);
		}
	}

	private boolean renderFocus(GuiGraphics graphics, Minecraft client, int y, Snapshot snapshot, BlockHitResult hit) {
		var maybeReading = snapshot.data().focus();
		if (maybeReading.isEmpty() || !maybeReading.orElseThrow().marker().pos().equals(hit.getBlockPos())) {
			return false;
		}
		var reading = maybeReading.orElseThrow();
		String variant = reading.marker().variant();
		int width = Math.min(158, graphics.guiWidth() / 2 - 8);
		int x = graphics.guiWidth() - width - 6;
		panel(graphics, x, y, width, 20 + (1 + reading.reserves().size()) * ROW);
		var title = Component.translatable(KEY + "focus", variant(variant));
		graphics.drawString(client.font, title, x + 6, y + 5, tint(variant), false);
		String condition = reading.reserves().stream().noneMatch(vis -> vis.amount() > 0) ? "depleted"
			: reading.reserves().stream().anyMatch(vis -> vis.amount() < vis.capacity()) ? "recovering" : "full";
		graphics.drawString(client.font, Component.translatable(KEY + "condition." + condition), x + 6, y + 17, TEXT, false);
		for (int index = 0; index < reading.reserves().size(); index++) {
			var vis = reading.reserves().get(index);
			var line = Component.translatable(KEY + "reserve", Component.translatable(vis.aspectKey()), vis.amount(), vis.capacity());
			graphics.drawString(client.font, line, x + 6, y + 17 + (index + 1) * ROW, TEXT, false);
		}
		return true;
	}

	private static Component variant(String variant) {
		return Component.translatable("state.thaumiclegacy.node." + variant);
	}

	private static int tint(String variant) {
		return switch (variant) {
			case "bright" -> 0xFFFFDB85;
			case "unstable" -> 0xFFF0ABB7;
			default -> 0xFF8CDBDC;
		};
	}

	private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.renderOutline(x, y, width, height, FRAME);
	}

	void clear() {
		worn = null;
		measured = null;
	}

	private record Snapshot(ClientLevel level, NodeSightPayload data, long expiresAt) {
		boolean isCurrent(ClientLevel current, long now) {
			return level == current && now < expiresAt;
		}
	}
}
