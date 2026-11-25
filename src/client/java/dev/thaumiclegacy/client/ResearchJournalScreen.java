package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.CategoryView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class ResearchJournalScreen extends Screen {
	private static final int COVER = 0xFF462E28;
	private static final int COVER_EDGE = 0xFF9E6649;
	private static final int PAPER = 0xFFF2E0B8;
	private static final int INK = 0xFF37271D;
	private static final int ACCENT = 0xFF744039;
	private static final int MUTED_INK = 0xFF624E48;
	private static final String KEY = "screen.thaumiclegacy.journal.";
	private static final ResourceLocation NODE_STUDY = ThaumicLegacy.id("node_study");
	private static final int CATEGORIES_PER_PAGE = 8;

	private final JournalSnapshotPayload journal;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private Tab tab = Tab.RESEARCH;
	private int categoryIndex;
	private AspectView selectedAspect;
	private final Map<Tab, Button> tabs = new EnumMap<>(Tab.class);
	private final List<Button> categoryButtons = new ArrayList<>();
	private Button previousCategories;
	private Button nextCategories;
	private JournalPage page;
	private int researchTop;

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
		tabs.clear();
		categoryButtons.clear();
		page = null;
		previousCategories = null;
		nextCategories = null;
		int tabWidth = (panelWidth - 24) / Tab.values().length;
		for (Tab choice : Tab.values()) {
			Component label = Component.translatable(KEY + "tab." + choice.name().toLowerCase(Locale.ROOT));
			int tabX = left + 8 + choice.ordinal() * (tabWidth + 4);
			tabs.put(choice, addRenderableWidget(Button.builder(label, button -> { selectedAspect = null; selectTab(choice); })
				.bounds(tabX, top + 8, tabWidth, 20).build()));
		}
		var categories = journal.research().categories();
		int columns = panelWidth >= 290 ? 4 : 3;
		int categoryWidth = (panelWidth - 16 - (columns - 1) * 4) / columns;
		for (int index = 0; index < categories.size(); index++) {
			int selection = index;
			int position = index % CATEGORIES_PER_PAGE;
			var category = categories.get(index);
			Component fullTitle = categoryTitle(category);
			Component buttonTitle = Component.translatableWithFallback(category.title() + ".short", fullTitle.getString());
			Component label = font.width(buttonTitle) <= categoryWidth - 8 ? buttonTitle
				: Component.literal(font.plainSubstrByWidth(buttonTitle.getString(), categoryWidth - 8 - font.width("…")) + "…");
			int x = left + 8 + position % columns * (categoryWidth + 4);
			int y = top + 33 + position / columns * 20;
			categoryButtons.add(addRenderableWidget(Button.builder(label, button -> {
				categoryIndex = selection;
				selectTab(Tab.RESEARCH);
			}).bounds(x, y, categoryWidth, 17).tooltip(Tooltip.create(fullTitle)).build()));
		}
		researchTop = 34 + Math.ceilDiv(Math.min(CATEGORIES_PER_PAGE, categories.size()), columns) * 20;
		if (categories.size() > CATEGORIES_PER_PAGE) {
			previousCategories = addRenderableWidget(Button.builder(Component.literal("‹"), button -> changeCategoryPage(-1))
				.bounds(left + 8, top + researchTop, 20, 17)
				.tooltip(Tooltip.create(Component.translatable(KEY + "research.previous_categories"))).build());
			nextCategories = addRenderableWidget(Button.builder(Component.literal("›"), button -> changeCategoryPage(1))
				.bounds(left + 32, top + researchTop, 20, 17)
				.tooltip(Tooltip.create(Component.translatable(KEY + "research.next_categories"))).build());
			researchTop += 20;
		}
		selectTab(tab);
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
		if (tab == Tab.ASPECTS && selectedAspect == null && page != null) {
			var aspect = page.aspectAt(mouseX, mouseY);
			if (aspect != null) {
				var sources = sourceParagraphs(aspect);
				List<Component> tooltip = new ArrayList<>();
				sources.stream().limit(5).map(Paragraph::text).forEach(tooltip::add);
				if (sources.size() > 5) {
					tooltip.add(Component.translatable(KEY + "sources.more"));
				}
				graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void selectTab(Tab choice) {
		tab = choice;
		tabs.forEach((section, button) -> button.active = section != choice || selectedAspect != null);
		int categoryPage = categoryIndex / CATEGORIES_PER_PAGE;
		for (int index = 0; index < categoryButtons.size(); index++) {
			var button = categoryButtons.get(index);
			button.visible = choice == Tab.RESEARCH && index / CATEGORIES_PER_PAGE == categoryPage;
			button.active = button.visible && index != categoryIndex;
		}
		if (previousCategories != null && nextCategories != null) {
			previousCategories.visible = choice == Tab.RESEARCH;
			previousCategories.active = previousCategories.visible && categoryPage > 0;
			nextCategories.visible = choice == Tab.RESEARCH;
			nextCategories.active = nextCategories.visible && (categoryPage + 1) * CATEGORIES_PER_PAGE < categoryButtons.size();
		}
		if (page != null) {
			removeWidget(page);
		}
		int offset = choice == Tab.RESEARCH ? researchTop : 34;
		var paragraphs = switch (choice) {
			case RESEARCH -> researchParagraphs();

			case ASPECTS -> selectedAspect == null ? aspectParagraphs() : sourceParagraphs(selectedAspect);
		};
		page = addRenderableWidget(new JournalPage(left + 8, top + offset, panelWidth - 24,
			Math.max(36, panelHeight - offset - 36), font, paragraphs));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && tab == Tab.ASPECTS && selectedAspect == null && page != null) {
			var aspect = page.aspectAt(mouseX, mouseY);
			if (aspect != null) {
				selectedAspect = aspect;
				selectTab(Tab.ASPECTS);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private List<Paragraph> sourceParagraphs(AspectView aspect) {
		List<Paragraph> paragraphs = new ArrayList<>();
		paragraphs.add(aspectParagraph(aspect, false));
		paragraphs.add(new Paragraph(Component.translatable(KEY + "sources.title"), ACCENT, 4));
		for (var source : journal.specimens()) {
			if (!List.of("block", "item", "fluid").contains(source.kind())) {
				continue;
			}
			for (var amount : source.aspects()) {
				if (aspect.id().equals(amount.id())) {
					var name = FieldLensItem.specimenName(source.kind(), source.id());
					paragraphs.add(new Paragraph(Component.translatable(KEY + "sources.entry", name, amount.amount()), INK, 2));
				}
			}
		}
		if (paragraphs.size() == 2) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "sources.empty"), INK, 2));
		}
		return paragraphs;
	}

	private void changeCategoryPage(int direction) {
		categoryIndex = (categoryIndex / CATEGORIES_PER_PAGE + direction) * CATEGORIES_PER_PAGE;
		selectTab(Tab.RESEARCH);
	}

	private List<Paragraph> researchParagraphs() {
		List<Paragraph> paragraphs = new ArrayList<>();
		var categories = journal.research().categories();
		if (categories.isEmpty()) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "research.empty"), INK, 0));
			return paragraphs;
		}
		var category = categories.get(categoryIndex);
		paragraphs.add(new Paragraph(categoryTitle(category), ACCENT, 8));
		if (category.entries().isEmpty()) {
			paragraphs.add(new Paragraph(Component.translatable(KEY + "research.empty"), INK, 0));
			return paragraphs;
		}
		Map<ResourceLocation, Component> titles = new HashMap<>();
		for (var group : categories) {
			for (var entry : group.entries()) {
				titles.put(entry.id(), Component.translatableWithFallback(entry.title(), entry.id().getPath()));
			}
		}
		for (var entry : category.entries()) {
			String status = entry.unlocked() ? "unlocked" : "locked";
			Component title = Component.translatable(KEY + "research." + status, titles.get(entry.id()));
			paragraphs.add(new Paragraph(title, entry.unlocked() ? ACCENT : MUTED_INK, 3));
			if (!entry.body().isEmpty()) {
				Component body = entry.unlocked() && entry.id().equals(NODE_STUDY)
					? Component.translatable(entry.body(), journal.nodeProgress().observed())
					: Component.translatable(entry.body());
				paragraphs.add(new Paragraph(body, INK, 4));
			}
			if (!entry.parents().isEmpty()) {
				MutableComponent parents = Component.empty();
				for (ResourceLocation parent : entry.parents()) {
					if (!parents.getSiblings().isEmpty()) {
						parents.append(Component.translatable(KEY + "research.separator"));
					}
					parents.append(titles.getOrDefault(parent, Component.literal(parent.getPath())));
				}
				paragraphs.add(new Paragraph(Component.translatable(KEY + "research.parents", parents), MUTED_INK, 4));
			}
			paragraphs.add(new Paragraph(Component.empty(), INK, 4));
		}
		return paragraphs;
	}

	private static Component categoryTitle(CategoryView category) {
		return Component.translatableWithFallback(category.title(), category.id().getPath());
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

	private static Paragraph aspectParagraph(AspectView aspect, boolean showAmount) {
		boolean known = !aspect.id().isEmpty();
		Component name = known ? aspectName(aspect.id()).copy().withColor(aspect.color()) : Component.translatable(KEY + "aspect.unknown");
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

	private enum Tab {
		RESEARCH, ASPECTS
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

		AspectView aspectAt(double mouseX, double mouseY) {
			if (!isMouseOver(mouseX, mouseY)) {
				return null;
			}
			double position = mouseY - getY() - innerPadding() + scrollAmount();
			for (Line line : lines) {
				if (position >= line.offset() && position < line.offset() + LINE_HEIGHT) {
					return line.aspect();
				}
			}
			return null;
		}

		private record Line(FormattedCharSequence text, int color, int offset, int indent, AspectView aspect) {}
	}
}
