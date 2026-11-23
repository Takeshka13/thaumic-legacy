package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.revealing.RevealingPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class RevealingOverlay {
	private static final String KEY = "hud.thaumiclegacy.revealing.";
	private static final ResourceLocation GOGGLES = ThaumicLegacy.id("goggles_of_revealing");
	private static final int ROW = 12;
	private static final int FRAME = 0xFF9E7450;
	private static final int BACKGROUND = 0xEC1D191D;
	private static final int TEXT = 0xFFE1D4BB;
	private static final int REVEALED = 0xFF8CEDC8;

	private RevealingPayload reading;
	private ClientLevel measuredLevel;
	private long expiresAt;

	void show(Minecraft client, RevealingPayload payload) {
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
		var font = client.font;
		if (reading.crucible().isPresent()) {
			var crucible = reading.crucible().orElseThrow();
			int shown = Math.min(crucible.aspects().size(), Math.max(0, (graphics.guiHeight() - y - 49) / ROW));
			boolean more = shown < crucible.aspects().size();
			panel(graphics, x, y, width, 28 + ROW * (shown + (more ? 1 : 0)));
			graphics.drawString(font, Component.translatable("block.thaumiclegacy.crucible"), x + 6, y + 5, REVEALED, false);
			Component phase = Component.translatable("state.thaumiclegacy.crucible." + crucible.phase());
			graphics.drawString(font, Component.translatable(KEY + "mixture", phase, crucible.amount(), crucible.capacity()),
				x + 6, y + 16, TEXT, false);
			for (int index = 0; index < shown; index++) {
				var aspect = crucible.aspects().get(index);
				int rowY = y + 27 + index * ROW;
				AspectGlyphs.draw(graphics, aspect.id(), aspect.glyph(), aspect.color(), x + 6, rowY - 1);
				var id = ResourceLocation.parse(aspect.id());
				var name = Component.translatableWithFallback(id.toLanguageKey("aspect"), id.getPath());
				graphics.drawString(font, Component.translatable("message.thaumiclegacy.crucible.aspect", name, aspect.amount()),
					x + 21, rowY, TEXT, false);
			}
			if (more) {
				var hint = Component.translatable(KEY + "more", crucible.aspects().size() - shown);
				graphics.drawString(font, hint, x + 6, y + 27 + shown * ROW, TEXT, false);
			}
		} else {
			panel(graphics, x, y, width, 35 + (reading.heat() ? ROW : 0));
			graphics.drawString(font, client.level.getBlockState(reading.pos()).getBlock().getName(),
				x + 6, y + 5, REVEALED, false);
			graphics.drawString(font, Component.translatable(KEY + "light", reading.light()), x + 6, y + 18, TEXT, false);
			if (reading.heat()) {
				graphics.drawString(font, Component.translatable(KEY + "heat"), x + 6, y + 30, TEXT, false);
			}
		}
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
