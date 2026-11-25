package dev.thaumiclegacy.client;

import dev.thaumiclegacy.logistics.EssentiaFurnaceBlockEntity;
import dev.thaumiclegacy.logistics.EssentiaFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class EssentiaFurnaceScreen extends AbstractContainerScreen<EssentiaFurnaceMenu> {
	private static final String KEY = "screen.thaumiclegacy.alchemical_furnace.";
	private static final int INK = 0xFFF8E4C4;
	private static final int ROWS = 4;
	private int first;

	EssentiaFurnaceScreen(EssentiaFurnaceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageHeight = 186;
		inventoryLabelY = 93;
	}

	@Override
	protected void renderBg(GuiGraphics g, float tickDelta, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC18F51);
		g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF332929);
		for (var slot : menu.slots) {
			g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF80684F);
			g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF201A19);
		}
		g.fill(x + 79, y + 46, x + 97, y + 50, 0xFF201A19);
		g.fill(x + 79, y + 46, x + 79 + 18 * menu.progress() / EssentiaFurnaceBlockEntity.DURATION, y + 50, 0xFFDBB365);
		g.fill(x + 148, y + 24, x + 157, y + 76, 0xFF201A19);
		int fill = 52 * menu.burnTicks() / Math.max(1, menu.burnCapacity());
		g.fill(x + 148, y + 76 - fill, x + 157, y + 76, 0xFFE08B4A);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, INK, false);
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
		int total = menu.aspects().stream().mapToInt(aspect -> aspect.amount()).sum();
		g.drawString(font, Component.translatable(KEY + "buffer", total, EssentiaFurnaceBlockEntity.CAPACITY), 8, 21, INK, false);
		first = Math.min(first, Math.max(0, menu.aspects().size() - ROWS));
		for (int i = first; i < Math.min(first + ROWS, menu.aspects().size()); i++) {
			var aspect = menu.aspects().get(i);
			int y = 33 + 11 * (i - first);
			AspectGlyphs.draw(g, aspect.id(), aspect.glyph(), aspect.color(), 8, y);
			g.drawString(font, Integer.toString(aspect.amount()), 22, y + 2, 0xFF000000 | aspect.color(), false);
		}
		if (menu.aspects().size() > ROWS) {
			g.drawString(font, Component.translatable(KEY + "scroll"), 42, 44, INK, false);
		}
		g.drawString(font, Component.translatable(menu.state().translationKey()), 8, 81, INK, false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float tickDelta) {
		super.render(g, mouseX, mouseY, tickDelta);
		renderTooltip(g, mouseX, mouseY);
		if (isHovering(8, 33, 35, 44, mouseX, mouseY)) {
			int index = first + (mouseY - topPos - 33) / 11;
			if (index < menu.aspects().size()) {
				g.renderTooltip(font, menu.aspects().get(index).name(), mouseX, mouseY);
			}
		} else if (isHovering(148, 24, 9, 52, mouseX, mouseY)) {
			g.renderTooltip(font, Component.translatable(KEY + "fuel", menu.burnTicks(), menu.burnCapacity()), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (isHovering(8, 31, 58, 46, x, y) && vertical != 0) {
			first = Math.clamp(first - (int) Math.signum(vertical), 0, Math.max(0, menu.aspects().size() - ROWS));
			return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}
}
