package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.revealing.LogisticsReadoutPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class LogisticsOverlay {
	private static final String KEY = "hud.thaumiclegacy.logistics.";
	private static final ResourceLocation GOGGLES = ThaumicLegacy.id("goggles_of_revealing");
	private static final int ROW = 12;
	private static final int FRAME = 0xFF9E7450;
	private static final int BACKGROUND = 0xEC1D191D;
	private static final int TEXT = 0xFFE1D4BB;
	private static final int TITLE = 0xFF8CEDC8;

	private LogisticsReadoutPayload reading;
	private ClientLevel measuredLevel;
	private long expiresAt;

	void show(Minecraft client, LogisticsReadoutPayload payload) {
		reading = payload;
		measuredLevel = client.level;
		expiresAt = Util.getMillis() + (payload.worn() ? 1_000 : 4_000);
	}

	void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (reading == null || Util.getMillis() >= expiresAt || measuredLevel != client.level || client.player == null
				|| client.screen != null || client.options.hideGui
				|| !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| !reading.pos().equals(hit.getBlockPos())) {
			return;
		}
		if (reading.worn() && !client.player.getItemBySlot(EquipmentSlot.HEAD).is(BuiltInRegistries.ITEM.get(GOGGLES))) {
			return;
		}
		int width = Math.min(168, graphics.guiWidth() - 12);
		int x = graphics.guiWidth() - width - 6;
		int y = graphics.guiHeight() >= 150 ? 76 : 6;
		Component label = client.level.getBlockState(reading.pos()).getBlock().getName();
		if (reading.state().equals("storage")) {
			drawStorage(graphics, client, label, x, y, width);
		} else {
			drawFurnace(graphics, client, label, x, y, width);
		}
	}

	private void drawStorage(GuiGraphics graphics, Minecraft client, Component label, int x, int y, int width) {
		int shown = Math.min(reading.aspects().size(), Math.max(0, (graphics.guiHeight() - y - 49) / ROW));
		boolean more = shown < reading.aspects().size();
		panel(graphics, x, y, width, 28 + ROW * (shown + (more ? 1 : 0)));
		graphics.drawString(client.font, label, x + 6, y + 5, TITLE, false);
		var total = Component.translatable(KEY + "storage", reading.amount(), reading.capacity());
		graphics.drawString(client.font, total, x + 6, y + 16, TEXT, false);
		for (int index = 0; index < shown; index++) {
			var aspect = reading.aspects().get(index);
			int rowY = y + 27 + index * ROW;
			AspectGlyphs.draw(graphics, aspect.id(), aspect.glyph(), aspect.color(), x + 6, rowY - 1);
			var id = ResourceLocation.parse(aspect.id());
			var name = Component.translatableWithFallback(id.toLanguageKey("aspect"), id.getPath());
			var line = Component.translatable("message.thaumiclegacy.logistics.aspect", name, aspect.amount());
			graphics.drawString(client.font, line, x + 21, rowY, TEXT, false);
		}
		if (more) {
			var hint = Component.translatable("hud.thaumiclegacy.revealing.more", reading.aspects().size() - shown);
			graphics.drawString(client.font, hint, x + 6, y + 27 + shown * ROW, TEXT, false);
		}
	}

	private void drawFurnace(GuiGraphics graphics, Minecraft client, Component label, int x, int y, int width) {
		panel(graphics, x, y, width, 59);
		graphics.drawString(client.font, label, x + 6, y + 5, TITLE, false);
		var state = Component.translatable("state.thaumiclegacy.logistics." + reading.state());
		graphics.drawString(client.font, state, x + 6, y + 16, TEXT, false);
		var progress = Component.translatable(KEY + "progress", reading.progress(), reading.duration());
		graphics.drawString(client.font, progress, x + 6, y + 27, TEXT, false);
		int track = width - 12;
		double fraction = Mth.clamp(reading.progress() / (double) Math.max(1, reading.duration()), 0.0, 1.0);
		graphics.fill(x + 6, y + 38, x + 6 + track, y + 42, 0xFF423C46);
		graphics.fill(x + 6, y + 38, x + 6 + (int) Math.round(track * fraction), y + 42, TITLE);
		var fuel = Component.translatable(KEY + "fuel", Math.ceilDiv(Math.max(0, reading.burnTicks()), 20));
		graphics.drawString(client.font, fuel, x + 6, y + 46, TEXT, false);
	}

	private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.renderOutline(x, y, width, height, FRAME);
	}

	void clear() {
		reading = null;
		measuredLevel = null;
	}
}
