package dev.thaumiclegacy.client;

import dev.thaumiclegacy.aura.ThaumometerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class ThaumometerLens {
	private ThaumometerLens() {
	}

	static boolean held(Minecraft client) {
		return client.player != null && client.screen == null && !client.options.hideGui
				&& (client.player.getMainHandItem().getItem() instanceof ThaumometerItem
					|| client.player.getOffhandItem().getItem() instanceof ThaumometerItem);
	}

	static boolean active(Minecraft client) {
		return held(client) && client.options.getCameraType().isFirstPerson();
	}

	static void render(GuiGraphics graphics) {
		if (!active(Minecraft.getInstance())) {
			return;
		}
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2;
		int height = graphics.guiHeight() / 3;
		int width = Math.min(x - 8, height * 5 / 4);
		for (int row = -height; row <= height; row++) {
			int half = width - Math.abs(row) * width / (2 * height);
			int color = Math.abs(row) >= height - 2 ? 0xE6A8713F : 0xB891643C;
			int thickness = Math.abs(row) >= height - 2 ? half : 2;
			graphics.fill(x - half, y + row, x - half + thickness, y + row + 1, color);
			graphics.fill(x + half - thickness, y + row, x + half, y + row + 1, color);
		}
	}
}
