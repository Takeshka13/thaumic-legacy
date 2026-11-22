package dev.thaumiclegacy.client;

import dev.thaumiclegacy.research.ScanFeedbackPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class ScanFeedback {
	private static final int PULSE_MILLIS = 520;
	private static final int DISCOVERY_MILLIS = 1_800;
	private static final Component DISCOVERY = Component.translatable("hud.thaumiclegacy.aspect_discovered");

	private long pulseUntil;
	private long discoveryUntil;
	private int pulseColor;

	void show(Minecraft client, ScanFeedbackPayload payload) {
		if (client.player == null || client.level == null) {
			return;
		}
		long now = Util.getMillis();
		pulseColor = payload.color() & 0xFFFFFF;
		pulseUntil = now + PULSE_MILLIS;
		if (payload.newAspect()) {
			discoveryUntil = now + DISCOVERY_MILLIS;
		}

		Vector3f rgb = new Vector3f((pulseColor >> 16) / 255.0F,
			((pulseColor >> 8) & 0xFF) / 255.0F, (pulseColor & 0xFF) / 255.0F);
		DustParticleOptions dust = new DustParticleOptions(rgb, payload.newAspect() ? 0.75F : 0.55F);
		Vec3 eye = client.player.getEyePosition();
		Vec3 forward = client.player.getViewVector(1.0F);
		Vec3 center = eye.add(forward.scale(0.65)).add(0.0, -0.2, 0.0);
		Vec3 right = Vec3.directionFromRotation(0.0F, client.player.getYRot() + 90.0F);
		Vec3 up = right.cross(forward);
		int count = payload.newAspect() ? 12 : 6;
		for (int i = 0; i < count; i++) {
			double angle = i * (Math.PI * 2.0 / count);
			Vec3 position = center.add(right.scale(Math.cos(angle) * 0.19)).add(up.scale(Math.sin(angle) * 0.13));
			client.level.addParticle(dust, position.x, position.y, position.z, 0.0, 0.0, 0.0);
		}
	}

	void render(GuiGraphics graphics) {
		long now = Util.getMillis();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.screen != null || client.options.hideGui) {
			return;
		}

		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2;
		long pulseRemaining = pulseUntil - now;
		if (pulseRemaining > 0) {
			float remaining = (float) pulseRemaining / PULSE_MILLIS;
			int radius = 8 + (int) ((1.0F - remaining) * 14.0F);
			int color = ((int) (remaining * 255.0F) << 24) | pulseColor;
			graphics.fill(x - radius - 1, y - 1, x - radius + 2, y + 2, color);
			graphics.fill(x + radius - 1, y - 1, x + radius + 2, y + 2, color);
			graphics.fill(x - 1, y - radius - 1, x + 2, y - radius + 2, color);
			graphics.fill(x - 1, y + radius - 1, x + 2, y + radius + 2, color);
		}

		long discoveryRemaining = discoveryUntil - now;
		if (discoveryRemaining > 0) {
			int alpha = Mth.clamp((int) (discoveryRemaining * 255 / 400), 4, 255);
			int labelX = x - client.font.width(DISCOVERY) / 2;
			graphics.drawString(client.font, DISCOVERY, labelX, y + 30, (alpha << 24) | 0xFFF0CB, true);
		}
	}

	void clear() {
		pulseUntil = 0;
		discoveryUntil = 0;
	}
}
