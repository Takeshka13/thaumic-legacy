package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.revealing.TransportReadoutPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class TransportOverlay {
	private static final String KEY = "hud.thaumiclegacy.transport.";
	private static final ResourceLocation GOGGLES = ThaumicLegacy.id("goggles_of_revealing");
	private static final ResourceLocation FILTER = ThaumicLegacy.id("essentia_filter");
	private static final ResourceLocation WARDED_JAR = ThaumicLegacy.id("warded_jar");
	private static final Set<ResourceLocation> STORAGE = Set.of(ThaumicLegacy.id("arcane_alembic"), WARDED_JAR);
	private static final Set<ResourceLocation> PARTS = Set.of(ThaumicLegacy.id("essentia_tube"),
			ThaumicLegacy.id("essentia_valve"), FILTER);
	private static final int FRAME = 0xFF9E7450;
	private static final int BACKGROUND = 0xEC1D191D;
	private static final int TEXT = 0xFFE1D4BB;
	private static final int TITLE = 0xFF8CEDC8;
	private static final int ROW = 11;

	private TransportReadoutPayload reading;
	private ClientLevel measuredLevel;
	private long expiresAt;

	void show(Minecraft client, TransportReadoutPayload payload) {
		reading = payload;
		measuredLevel = client.level;
		expiresAt = Util.getMillis() + (payload.worn() ? 1_000 : 4_000);
	}

	boolean render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (reading == null || Util.getMillis() >= expiresAt || measuredLevel != client.level || client.player == null
				|| client.screen != null || client.options.hideGui
				|| !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
				|| !reading.pos().equals(hit.getBlockPos())) {
			return false;
		}
		if (reading.worn() && !client.player.getItemBySlot(EquipmentSlot.HEAD).is(BuiltInRegistries.ITEM.get(GOGGLES))) {
			return false;
		}

		var block = client.level.getBlockState(reading.pos()).getBlock();
		int width = Math.min(168, graphics.guiWidth() / 2 - 8);
		var content = new ArrayList<FormattedCharSequence>();
		for (Component line : details(BuiltInRegistries.BLOCK.getKey(block))) {
			content.addAll(client.font.split(line, width - 12));
		}
		var titleLines = client.font.split(block.getName(), width - 12);
		int height = 14 + (titleLines.size() + content.size()) * ROW;
		int top = graphics.guiHeight() >= 76 + height + 6 ? 76 : 6;
		int x = 6;
		graphics.fill(x, top, x + width, top + height, BACKGROUND);
		graphics.renderOutline(x, top, width, height, FRAME);
		int rowY = top + 6;
		for (FormattedCharSequence title : titleLines) {
			graphics.drawString(client.font, title, x + 6, rowY, TITLE, false);
			rowY += ROW;
		}
		rowY += 2;
		for (FormattedCharSequence line : content) {
			graphics.drawString(client.font, line, x + 6, rowY, TEXT, false);
			rowY += ROW;
		}
		return true;
	}

	private List<Component> details(ResourceLocation block) {
		var lines = new ArrayList<Component>();
		lines.add(Component.translatable("state.thaumiclegacy.transport." + reading.status()));
		lines.add(Component.translatable(KEY + "connections", connections(reading.connections())));
		if (block.equals(FILTER)) {
			lines.add(Component.translatable(KEY + "filter", aspect(reading.filter())));
		}
		if (block.equals(WARDED_JAR)) {
			lines.add(Component.translatable(KEY + "jar_label", aspect(reading.filter())));
		}
		if (STORAGE.contains(block)) {
			lines.add(Component.translatable(KEY + "stored_amount", reading.amount()));
			Component contents = reading.amount() > 0 && reading.aspect().isEmpty()
				? Component.translatable(KEY + "mixed") : aspect(reading.aspect());
			lines.add(Component.translatable(KEY + "stored_aspect", contents));
		} else if (PARTS.contains(block)) {
			lines.add(Component.translatable(KEY + "buffer"));
			lines.add(Component.translatable(KEY + "recent_amount", reading.amount()));
			lines.add(Component.translatable(KEY + "last_aspect", aspect(reading.aspect())));
		}
		return lines;
	}

	private static Component connections(int mask) {
		MutableComponent sides = Component.empty();
		for (Direction direction : Direction.values()) {
			if ((mask & (1 << direction.get3DDataValue())) != 0) {
				if (!sides.getSiblings().isEmpty()) {
					sides.append(", ");
				}
				sides.append(Component.translatable(KEY + "side." + direction.getName()));
			}
		}
		return sides.getSiblings().isEmpty() ? Component.translatable(KEY + "none") : sides;
	}

	private static Component aspect(String id) {
		ResourceLocation key = ResourceLocation.tryParse(id);
		return id.isEmpty() || key == null ? Component.translatable(KEY + "none")
			: Component.translatableWithFallback(key.toLanguageKey("aspect"), key.getPath());
	}

	void clear() {
		reading = null;
		measuredLevel = null;
	}
}
