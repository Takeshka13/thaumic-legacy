package dev.thaumiclegacy.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class ResearchJournalScreen extends Screen {
	private static final int COVER = 0xFF462E28;
	private static final int COVER_EDGE = 0xFF9E6649;
	private static final int PAPER = 0xFFF2E0B8;
	private static final int INK = 0xFF37271D;
	private static final int ACCENT = 0xFF744039;
	private static final String KEY = "screen.thaumiclegacy.journal.";

	private final List<ResourceLocation> observations;
	private final boolean unlocked;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;

	ResearchJournalScreen(List<ResourceLocation> observations, boolean unlocked) {
		super(Component.translatable("item.thaumiclegacy.research_journal"));
		this.observations = List.copyOf(observations);
		this.unlocked = unlocked;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(344, width - 8);
		panelHeight = Math.min(270, height - 8);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		addRenderableWidget(new JournalPage(left + 8, top + 8, panelWidth - 24, panelHeight - 44, font, paragraphs()));
		int buttonWidth = Math.min(120, panelWidth - 16);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
			.bounds((width - buttonWidth) / 2, top + panelHeight - 28, buttonWidth, 20)
			.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(graphics);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, COVER);
		graphics.renderOutline(left, top, panelWidth, panelHeight, COVER_EDGE);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Paragraph> paragraphs() {
		List<Paragraph> paragraphs = new ArrayList<>();
		paragraphs.add(new Paragraph(getTitle(), ACCENT, 8));
		paragraphs.add(new Paragraph(Component.translatable(KEY + "specimens", observations.size()), INK, 4));
		if (observations.isEmpty()) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "empty"), INK, 6));
		}
		for (ResourceLocation observation : observations) {
			Component name = Component.translatable(observation.toLanguageKey("block"));
			Component note = Component.translatable(observation.toLanguageKey("observation"));
			paragraphs.add(new Paragraph(name, ACCENT, 1));
			paragraphs.add(new Paragraph(note, INK, 5));
		}
		String state = unlocked ? "unlocked" : "locked";
		paragraphs.add(new Paragraph(Component.translatable(KEY + state + ".title"), ACCENT, 3));
		paragraphs.add(new Paragraph(Component.translatable(KEY + state + ".body"), INK, 0));
		return paragraphs;
	}

	private record Paragraph(Component text, int color, int spacing) {}

	private static final class JournalPage extends AbstractScrollWidget {
		private static final int LINE_HEIGHT = 12;

		private final Font font;
		private final List<Line> lines = new ArrayList<>();
		private final Component[] narration;
		private final int innerHeight;

		JournalPage(int x, int y, int width, int height, Font font, List<Paragraph> paragraphs) {
			super(x, y, width, height, Component.translatable(KEY + "page"));
			this.font = font;
			int offset = 0;
			for (Paragraph paragraph : paragraphs) {
				for (FormattedCharSequence line : font.split(paragraph.text(), width - totalInnerPadding())) {
					lines.add(new Line(line, paragraph.color(), offset));
					offset += LINE_HEIGHT;
				}
				offset += paragraph.spacing();
			}
			innerHeight = offset;
			narration = paragraphs.stream().map(Paragraph::text).toArray(Component[]::new);
		}

		@Override
		protected int getInnerHeight() {
			return innerHeight;
		}

		@Override
		protected double scrollRate() {
			return LINE_HEIGHT;
		}

		@Override
		protected void renderBackground(GuiGraphics graphics) {
			graphics.fill(getX(), getY(), getX() + width, getY() + height, PAPER);
			if (isFocused()) {
				graphics.renderOutline(getX(), getY(), width, height, ACCENT);
			}
		}

		@Override
		protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX() + innerPadding();
			int top = getY() + innerPadding();
			for (Line line : lines) {
				graphics.drawString(font, line.text(), x, top + line.offset(), line.color(), false);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, getMessage());
			output.add(NarratedElementType.USAGE, narration);
		}

		private record Line(FormattedCharSequence text, int color, int offset) {}
	}
}
