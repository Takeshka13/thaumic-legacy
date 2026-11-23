package dev.thaumiclegacy.client;

import dev.thaumiclegacy.workbench.ArcaneWorkbenchCraftPayload;
import dev.thaumiclegacy.workbench.ArcaneWorkbenchMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
	private static final String KEY = "screen.thaumiclegacy.arcane_workbench.";
	private static final int PREVIEW_SLOT = 9;
	private static final int FRAME = 0xFF9E7450;
	private static final int COVER = 0xFF402C2C;
	private static final int PANEL = 0xFF272229;
	private static final int SLOT = 0xFF19161D;
	private static final int SLOT_EDGE = 0xFF72534A;
	private static final int TITLE = 0xFFF2D39C;
	private static final int TEXT = 0xFFE8D5BA;
	private static final int VIS = 0xFF8CEDC8;

	private Button craftButton;

	ArcaneWorkbenchScreen(ArcaneWorkbenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 176;
		imageHeight = 185;
		inventoryLabelY = 92;
	}

	@Override
	protected void init() {
		super.init();
		craftButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "craft"),
			button -> ClientPlayNetworking.send(menu.craftRequest())).bounds(leftPos + 94, topPos + 72, 74, 18).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		craftButton.active = menu.status() == ArcaneWorkbenchMenu.Status.READY
			&& ClientPlayNetworking.canSend(ArcaneWorkbenchCraftPayload.TYPE);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COVER);
		graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, FRAME);
		graphics.fill(leftPos + 5, topPos + 15, leftPos + 171, topPos + 91, PANEL);
		graphics.fill(leftPos + 92, topPos + 24, leftPos + 168, topPos + 25, FRAME);
		graphics.fill(leftPos + 92, topPos + 26, leftPos + 93, topPos + 69, FRAME);

		for (int i = 0; i < menu.slots.size(); i++) {
			var slot = menu.getSlot(i);
			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			int outline = i == PREVIEW_SLOT ? FRAME : SLOT_EDGE;
			graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
			graphics.renderOutline(x - 1, y - 1, 18, 18, outline);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE, false);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
		graphics.drawCenteredString(font, Component.translatable(KEY + "preview"), 131, 28, TEXT);

		var status = menu.status();
		Component cost = status == ArcaneWorkbenchMenu.Status.NO_RECIPE
			? Component.translatable(KEY + "no_cost")
			: Component.translatable(KEY + "cost", menu.visCost());
		graphics.drawString(font, cost, 97, 52, TEXT, false);
		graphics.drawString(font, Component.translatable(KEY + "vis", menu.availableVis()), 97, 62, VIS, false);

		String statusKey = switch (status) {
			case NO_RECIPE -> "no_recipe";
			case RESEARCH_REQUIRED -> "research_required";
			case INSUFFICIENT_VIS -> "insufficient_vis";
			case OUTPUT_FULL -> "output_full";
			case READY -> "ready";
		};
		int statusColor = status == ArcaneWorkbenchMenu.Status.READY ? VIS : TEXT;
		int y = 72;
		for (var line : font.split(Component.translatable(KEY + statusKey), 81)) {
			graphics.drawString(font, line, 8, y, statusColor, false);
			y += 9;
		}
	}
}
