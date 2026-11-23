package dev.thaumiclegacy.client;

import dev.thaumiclegacy.aura.AuraReadingPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class AuraReadingOverlay {
	private static final String KEY = "hud.thaumiclegacy.aura.";
	private static final int DURATION_MILLIS = 4_000;
	private static final int PULSE_MILLIS = 320;
	private static final int TICKS_PER_SECOND = 20;
	private static final int FRAME = 0xFF9E7450;
	private static final int BACKGROUND = 0xEC1D191D;
	private static final int TITLE = 0xFFF2D39C;
	private static final int VIS = 0xFF8CEDC8;
	private static final int FLUX = 0xFFE0A9E8;
	private static final int TEXT = 0xFFE1D4BB;

	private AuraReadingPayload reading;
	private long expiresAt;

	void show(AuraReadingPayload reading) {
		this.reading = reading;
		expiresAt = Util.getMillis() + DURATION_MILLIS;
	}

	void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		long remaining = expiresAt - Util.getMillis();
		if (reading == null || remaining <= 0 || client.screen != null || client.options.hideGui) {
			return;
		}
		var font = client.font;
		Component title = Component.translatable(KEY + "title");
		Component vis = Component.translatable(KEY + "vis", reading.vis(), reading.capacity());
		Component flux = Component.translatable(KEY + "flux", reading.flux());
		Component recovery;
		int recoveryColor = TEXT;
		if (reading.vis() >= reading.capacity()) {
			recovery = Component.translatable(KEY + "full");
		} else if (reading.recoveryTicks() <= 0) {
			recovery = Component.translatable(KEY + "blocked");
			recoveryColor = FLUX;
		} else {
			int seconds = Math.ceilDiv(reading.recoveryTicks(), TICKS_PER_SECOND);
			recovery = Component.translatable(KEY + "recovery", seconds);
		}

		int x = 6;
		int y = 6;
		int padding = 7;
		int innerWidth = Math.max(112, Math.max(font.width(recovery),
			Math.max(font.width(title), Math.max(font.width(vis), font.width(flux)))));
		int width = innerWidth + padding * 2;
		int height = 61;
		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.renderOutline(x, y, width, height, FRAME);
		long pulseRemaining = remaining - (DURATION_MILLIS - PULSE_MILLIS);
		if (pulseRemaining > 0) {
			int alpha = (int) (pulseRemaining * 180 / PULSE_MILLIS);
			graphics.fill(x + 1, y + 1, x + width - 1, y + 2, (alpha << 24) | (VIS & 0xFFFFFF));
		}
		graphics.drawString(font, title, x + padding, y + 6, TITLE, false);
		graphics.drawString(font, vis, x + padding, y + 18, VIS, false);

		int trackX = x + padding;
		int trackY = y + 28;
		graphics.fill(trackX, trackY, trackX + innerWidth, trackY + 4, 0xFF423C46);
		double fraction = Mth.clamp(reading.vis() / (double) Math.max(1, reading.capacity()), 0.0, 1.0);
		int fillWidth = (int) Math.round(innerWidth * fraction);
		graphics.fill(trackX, trackY, trackX + fillWidth, trackY + 4, VIS);

		graphics.drawString(font, flux, x + padding, y + 36, FLUX, false);
		graphics.drawString(font, recovery, x + padding, y + 47, recoveryColor, false);
	}

	void clear() {
		reading = null;
	}
}
