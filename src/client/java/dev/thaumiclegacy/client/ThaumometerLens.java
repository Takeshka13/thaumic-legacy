package dev.thaumiclegacy.client;

import dev.thaumiclegacy.aura.ThaumometerItem;
import dev.thaumiclegacy.research.LensFocusPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class ThaumometerLens {
	private LensFocusPayload focus;
	private long receivedAt;

	void show(LensFocusPayload payload) {
		focus = payload;
		receivedAt = Util.getMillis();
	}

	void clear() {
		focus = null;
	}

	static boolean held(Minecraft client) {
		return client.player != null && client.screen == null && !client.options.hideGui
				&& (client.player.getMainHandItem().getItem() instanceof ThaumometerItem
					|| client.player.getOffhandItem().getItem() instanceof ThaumometerItem);
	}

	static boolean active(Minecraft client) {
		return held(client) && client.options.getCameraType().isFirstPerson();
	}

	void render(GuiGraphics graphics) {
		if (!active(Minecraft.getInstance())) {
			return;
		}
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2;
		int height = Math.clamp(graphics.guiHeight() / 7, 17, 42);
		int width = Math.min(x - 8, height * 3 / 2);
		for (int row = -height; row <= height; row++) {
			int half = width - Math.abs(row) * width / (2 * height);
			int color = Math.abs(row) >= height - 2 ? 0xE6A8713F : 0xB891643C;
			int thickness = Math.abs(row) >= height - 2 ? half : 2;
			graphics.fill(x - half, y + row, x - half + thickness, y + row + 1, color);
			graphics.fill(x + half - thickness, y + row, x + half, y + row + 1, color);
		}
		if (focus == null || Util.getMillis() - receivedAt > 500 || focus.name().getString().isEmpty()) {
			return;
		}
		var font = Minecraft.getInstance().font;
		var name = font.substrByWidth(focus.name(), width * 2 - 8);
		graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(name), x - font.width(name) / 2, y - height - 12, 0xFFFFFF);
		int shown = Math.min(focus.aspects().size(), Math.max(1, (width * 2 - 8) / 24));
		int start = x - shown * 24 / 2;
		for (int index = 0; index < shown; index++) {
			var aspect = focus.aspects().get(index);
			int left = start + index * 24;
			AspectGlyphs.draw(graphics, aspect, left + 6, y + height + 3);
			String amount = aspect.id().isEmpty() ? "?" : Integer.toString(aspect.amount());
			graphics.drawString(font, amount, left + 12 - font.width(amount) / 2, y + height + 16, 0xFFFFFF);
		}
	}
}
