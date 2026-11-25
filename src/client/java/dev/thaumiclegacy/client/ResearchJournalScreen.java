package dev.thaumiclegacy.client;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.JournalSnapshotPayload;
import dev.thaumiclegacy.research.JournalSnapshotPayload.AspectView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.CategoryView;
import dev.thaumiclegacy.research.JournalSnapshotPayload.SpecimenView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
	private static final ResourceLocation NODE_STUDY = ThaumicLegacy.id("node_study");
	private static final int CATEGORIES_PER_PAGE = 8;

	private final JournalSnapshotPayload journal;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;
	private Tab tab = Tab.RESEARCH;
	private int categoryIndex;
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
			tabs.put(choice, addRenderableWidget(Button.builder(label, button -> selectTab(choice))
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
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void selectTab(Tab choice) {
		tab = choice;
		tabs.forEach((section, button) -> button.active = section != choice);
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
			case OBSERVATIONS -> observationParagraphs();
			case ASPECTS -> aspectParagraphs();
		};
		page = addRenderableWidget(new JournalPage(left + 8, top + offset, panelWidth - 24,
			Math.max(36, panelHeight - offset - 36), font, paragraphs));
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

	private List<Paragraph> observationParagraphs() {
		List<Paragraph> paragraphs = new ArrayList<>();
		paragraphs.add(new Paragraph(getTitle(), ACCENT, 8));

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
		RESEARCH, OBSERVATIONS, ASPECTS
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
