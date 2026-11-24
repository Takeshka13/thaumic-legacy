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
	private static final ResourceLocation FURNACE = ThaumicLegacy.id("alchemical_furnace");
	private static final ResourceLocation CENTRIFUGE = ThaumicLegacy.id("alchemical_centrifuge");
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
		var block = client.level.getBlockState(reading.pos()).getBlock();
		if (reading.state().equals("storage")) {
			drawStorage(graphics, client, block.getName(), x, y, width);
		} else {
			drawProcess(graphics, client, block.getName(), x, y, width, BuiltInRegistries.BLOCK.getKey(block));
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
			drawAspect(graphics, client, x, y + 27 + index * ROW, index);
		}
		if (more) {
			var hint = Component.translatable("hud.thaumiclegacy.revealing.more", reading.aspects().size() - shown);
			graphics.drawString(client.font, hint, x + 6, y + 27 + shown * ROW, TEXT, false);
		}
	}

	private void drawProcess(GuiGraphics graphics, Minecraft client, Component label, int x, int y, int width, ResourceLocation block) {
		boolean burnsFuel = block.equals(FURNACE);
		String statusPrefix = burnsFuel ? "state.thaumiclegacy.logistics." : "state.thaumiclegacy.advanced.";
		var state = Component.translatable(statusPrefix + reading.state());
		var titleLines = client.font.split(label, width - 12);
		var statusLines = client.font.split(state, width - 12);
		int baseHeight = 22 + ROW * (titleLines.size() + statusLines.size() + 1);
		int shown = burnsFuel ? 0 : Math.min(reading.aspects().size(),
			Math.max(0, (graphics.guiHeight() - y - baseHeight - 6) / ROW));
		boolean more = !burnsFuel && shown < reading.aspects().size();
		int height = baseHeight + ROW * (shown + (more ? 1 : 0));
		y = Math.min(y, Math.max(6, graphics.guiHeight() - height - 6));
		panel(graphics, x, y, width, height);
		int lineY = y + 5;
		for (var line : titleLines) {
			graphics.drawString(client.font, line, x + 6, lineY, TITLE, false);
			lineY += ROW;
		}
		for (var line : statusLines) {
			graphics.drawString(client.font, line, x + 6, lineY, TEXT, false);
			lineY += ROW;
		}
		var progress = Component.translatable(KEY + "progress", reading.progress(), reading.duration());
		graphics.drawString(client.font, progress, x + 6, lineY, TEXT, false);
		int track = width - 12;
		int trackY = lineY + 11;
		double fraction = Mth.clamp(reading.progress() / (double) Math.max(1, reading.duration()), 0.0, 1.0);
		graphics.fill(x + 6, trackY, x + 6 + track, trackY + 4, 0xFF423C46);
		graphics.fill(x + 6, trackY, x + 6 + (int) Math.round(track * fraction), trackY + 4, TITLE);
		if (burnsFuel) {
			var fuel = Component.translatable(KEY + "fuel", Math.ceilDiv(Math.max(0, reading.burnTicks()), 20));
			graphics.drawString(client.font, fuel, x + 6, trackY + 8, TEXT, false);
		} else {
			String buffer = block.equals(CENTRIFUGE) ? "output" : "input";
			var total = Component.translatable(KEY + buffer, reading.amount(), reading.capacity());
			graphics.drawString(client.font, total, x + 6, trackY + 8, TEXT, false);
			for (int index = 0; index < shown; index++) {
				drawAspect(graphics, client, x, trackY + 8 + (index + 1) * ROW, index);
			}
			if (more) {
				var hint = Component.translatable("hud.thaumiclegacy.revealing.more", reading.aspects().size() - shown);
				graphics.drawString(client.font, hint, x + 6, trackY + 8 + (shown + 1) * ROW, TEXT, false);
			}
		}
	}

	private void drawAspect(GuiGraphics graphics, Minecraft client, int x, int y, int index) {
		var aspect = reading.aspects().get(index);
		AspectGlyphs.draw(graphics, aspect.id(), aspect.glyph(), aspect.color(), x + 6, y - 1);
		var id = ResourceLocation.parse(aspect.id());
		var name = Component.translatableWithFallback(id.toLanguageKey("aspect"), id.getPath());
		var line = Component.translatable("message.thaumiclegacy.logistics.aspect", name, aspect.amount());
		graphics.drawString(client.font, line, x + 21, y, TEXT, false);
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
