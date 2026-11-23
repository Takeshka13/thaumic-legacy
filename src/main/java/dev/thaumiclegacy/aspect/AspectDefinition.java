package dev.thaumiclegacy.aspect;

import java.util.List;

public record AspectDefinition(String id, List<String> parents, int color, int glyph) {
	public AspectDefinition {
		parents = List.copyOf(parents);
	}

	public Category category() {
		return parents.isEmpty() ? Category.PRIMARY : Category.COMPOUND;
	}

	public enum Category { PRIMARY, COMPOUND }
}
