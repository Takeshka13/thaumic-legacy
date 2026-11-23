package dev.thaumiclegacy.client;

import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.SpecimenView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;

final class ResearchJournalScreen extends Screen {
	private static final int COVER = 0xFF462E28;
	private static final int COVER_EDGE = 0xFF9E6649;
	private static final int PAPER = 0xFFF2E0B8;
	private static final int INK = 0xFF37271D;
	private static final int ACCENT = 0xFF744039;
	private static final int MUTED_INK = 0xFF624E48;
	private static final String KEY = "screen.thaumiclegacy.journal.";

	private final JournalSnapshotPayload journal;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private boolean showCatalog;
	private JournalPage observationsPage;
	private JournalPage catalogPage;
	private Button observationsTab;
	private Button catalogTab;

	ResearchJournalScreen(JournalSnapshotPayload journal) {
		super(Component.translatable("item.thaumiclegacy.research_journal"));
		this.journal = journal;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(344, width - 8);
		panelHeight = Math.min(270, height - 8);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		int tabWidth = (panelWidth - 20) / 2;
		observationsTab = addRenderableWidget(Button.builder(Component.translatable(KEY + "tab.observations"),
			button -> selectPage(false)).bounds(left + 8, top + 8, tabWidth, 20).build());
		catalogTab = addRenderableWidget(Button.builder(Component.translatable(KEY + "tab.aspects"),
			button -> selectPage(true)).bounds(left + 12 + tabWidth, top + 8, tabWidth, 20).build());
		observationsPage = addRenderableWidget(new JournalPage(left + 8, top + 34, panelWidth - 24,
			panelHeight - 70, font, observationParagraphs()));
		catalogPage = addRenderableWidget(new JournalPage(left + 8, top + 34, panelWidth - 24,
			panelHeight - 70, font, aspectParagraphs()));
		selectPage(showCatalog);
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

	private void selectPage(boolean catalog) {
		showCatalog = catalog;
		observationsTab.active = catalog;
		catalogTab.active = !catalog;
		observationsPage.visible = !catalog;
		observationsPage.active = !catalog;
		catalogPage.visible = catalog;
		catalogPage.active = catalog;
	}

	private List<Paragraph> observationParagraphs() {
		List<Paragraph> paragraphs = new ArrayList<>();
		paragraphs.add(new Paragraph(getTitle(), ACCENT, 8));
		String state = journal.unlocked() ? "unlocked" : "locked";
		paragraphs.add(new Paragraph(Component.translatable(KEY + state + ".title"), ACCENT, 3));
		paragraphs.add(new Paragraph(Component.translatable(KEY + state + ".body"), INK, 8));
		String arcaneState = journal.arcaneFoundationsUnlocked() ? "unlocked" : "locked";
		paragraphs.add(new Paragraph(Component.translatable(KEY + "arcane." + arcaneState + ".title"), ACCENT, 3));
		paragraphs.add(new Paragraph(Component.translatable(KEY + "arcane." + arcaneState + ".body"), INK, 8));
		String alchemyState = journal.alchemyFoundationsUnlocked() ? "unlocked" : "locked";
		paragraphs.add(new Paragraph(Component.translatable(KEY + "alchemy." + alchemyState + ".title"), ACCENT, 3));
		paragraphs.add(new Paragraph(Component.translatable(KEY + "alchemy." + alchemyState + ".body"), INK, 8));
		var nodeProgress = journal.nodeProgress();
		addNodeResearch(paragraphs, "node_basics", nodeProgress.basics());
		addNodeResearch(paragraphs, "node_study", nodeProgress.observed() > 0, nodeProgress.observed());
		addNodeResearch(paragraphs, "wand_charging", nodeProgress.charged());

		Set<ResourceLocation> detailedBlocks = new HashSet<>();
		for (SpecimenView specimen : journal.specimens()) {
			if (specimen.kind().equalsIgnoreCase("block")) {
				detailedBlocks.add(specimen.id());
			}
		}
		List<ResourceLocation> legacy = journal.observations().stream()
			.filter(observation -> !detailedBlocks.contains(observation))
			.toList();
		int specimenCount = journal.specimens().size() + legacy.size();
		paragraphs.add(new Paragraph(Component.translatable(KEY + "specimens", specimenCount), INK, 4));
		if (specimenCount == 0) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "empty"), INK, 6));
		}
		for (SpecimenView specimen : journal.specimens()) {
			addSpecimen(paragraphs, specimen.id(), specimen.kind(), specimen.aspects());
		}
		for (ResourceLocation observation : legacy) {
			addSpecimen(paragraphs, observation, "block", List.of());
		}

		return paragraphs;
	}

	private static void addNodeResearch(List<Paragraph> paragraphs, String id, boolean completed, Object... detail) {
		String key = KEY + id + (completed ? ".unlocked." : ".locked.");
		paragraphs.add(new Paragraph(Component.translatable(key + "title"), ACCENT, 3));
		paragraphs.add(new Paragraph(Component.translatable(key + "body", detail), INK, 8));
	}

	private List<Paragraph> aspectParagraphs() {
		List<Paragraph> paragraphs = new ArrayList<>();
		paragraphs.add(new Paragraph(Component.translatable(KEY + "discovered", journal.discovered().size()), ACCENT, 4));
		if (journal.discovered().isEmpty()) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "discovered.empty"), INK, 0));
		}
		for (AspectView aspect : journal.discovered()) {
			if (aspect.parents().isEmpty()) {
				paragraphs.add(aspectParagraph(aspect, false));
			}
		}
		for (AspectView aspect : journal.discovered()) {
			if (!aspect.parents().isEmpty()) {
				paragraphs.add(aspectParagraph(aspect, false));
			}
		}
		return paragraphs;
	}

	private static void addSpecimen(List<Paragraph> paragraphs, ResourceLocation id, String kind, List<AspectView> aspects) {
		String prefix = kind.toLowerCase(Locale.ROOT);
		String nameKey = id.toLanguageKey(prefix);
		if (prefix.equals("item")) {
			nameKey = BuiltInRegistries.ITEM.getOptional(id).map(Item::getDescriptionId).orElse(nameKey);
		}
		Component name = Component.translatableWithFallback(nameKey, id.toString());
		if (I18n.exists(KEY + "kind." + prefix)) {
			name = Component.translatable(KEY + "specimen", name, Component.translatable(KEY + "kind." + prefix));
		}
		paragraphs.add(new Paragraph(name, ACCENT, 3));
		String noteKey = id.toLanguageKey("observation");
		if (prefix.equals("block") && I18n.exists(noteKey)) {
			paragraphs.add(new Paragraph(Component.translatable(noteKey), INK, 4));
		}
		for (AspectView aspect : aspects) {
			paragraphs.add(aspectParagraph(aspect, true));
		}
		paragraphs.add(new Paragraph(Component.empty(), INK, 3));
	}

	private static Paragraph aspectParagraph(AspectView aspect, boolean showAmount) {
		boolean known = !aspect.id().isEmpty();
		Component name = known ? aspectName(aspect.id()) : Component.translatable(KEY + "aspect.unknown");
		if (known && showAmount && aspect.amount() > 0) {
			name = Component.translatable(KEY + "aspect.amount", name, aspect.amount());
		}
		if (known && !showAmount && aspect.parents().isEmpty()) {
			name = Component.translatable(KEY + "aspect.primary", name);
		}
		if (known && !showAmount && aspect.parents().size() == 2
			&& aspect.parents().stream().noneMatch(String::isEmpty)) {
			name = Component.translatable(KEY + "aspect.formula", name,
				aspectName(aspect.parents().getFirst()), aspectName(aspect.parents().getLast()));
		}
		return new Paragraph(name, known ? INK : MUTED_INK, 3, aspect);
	}

	private static Component aspectName(String id) {
		ResourceLocation key = id.contains(":") ? ResourceLocation.tryParse(id) : null;
		return key == null ? Component.translatableWithFallback("aspect.thaumiclegacy." + id, id)
			: Component.translatableWithFallback(key.toLanguageKey("aspect"), key.getPath());
	}

	private record Paragraph(Component text, int color, int spacing, AspectView aspect) {
		Paragraph(Component text, int color, int spacing) {
			this(text, color, spacing, null);
		}
	}

	private static final class JournalPage extends AbstractScrollWidget {
		private static final int LINE_HEIGHT = 12;
		private static final int GLYPH_INDENT = 17;

		private final Font font;
		private final List<Line> lines = new ArrayList<>();
		private final Component[] narration;
		private final int innerHeight;

		JournalPage(int x, int y, int width, int height, Font font, List<Paragraph> paragraphs) {
			super(x, y, width, height, Component.translatable(KEY + "page"));
			this.font = font;
			int offset = 0;
			for (Paragraph paragraph : paragraphs) {
				int indent = paragraph.aspect() == null ? 0 : GLYPH_INDENT;
				int textWidth = Math.max(1, width - totalInnerPadding() - indent);
				AspectView aspect = paragraph.aspect();
				for (FormattedCharSequence line : font.split(paragraph.text(), textWidth)) {
					lines.add(new Line(line, paragraph.color(), offset, indent, aspect));
					aspect = null;
					offset += LINE_HEIGHT;
				}
				offset += paragraph.spacing();
			}
			innerHeight = offset;
			narration = paragraphs.stream().map(Paragraph::text)
				.filter(text -> !text.equals(Component.empty())).toArray(Component[]::new);
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
				int y = top + line.offset();
				if (!withinContentAreaTopBottom(y - 1, y + LINE_HEIGHT)) {
					continue;
				}
				if (line.aspect() != null) {
					AspectGlyphs.draw(graphics, line.aspect(), x, y - 1);
				}
				graphics.drawString(font, line.text(), x + line.indent(), y, line.color(), false);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			output.add(NarratedElementType.TITLE, getMessage());
			output.add(NarratedElementType.USAGE, narration);
		}

		private record Line(FormattedCharSequence text, int color, int offset, int indent, AspectView aspect) {}
	}
}
