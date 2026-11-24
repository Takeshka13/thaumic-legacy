package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.TreeMap;

final class EssentiaBuffer {
	private final TreeMap<String, Integer> contents = new TreeMap<>();
	private final int capacity;
	private int total;

	EssentiaBuffer(int capacity) {
		this.capacity = capacity;
	}

	int room() {
		return capacity - total;
	}

	int amount(String aspect) {
		return contents.getOrDefault(aspect, 0);
	}

	Map<String, Integer> snapshot() {
		return Map.copyOf(contents);
	}

	boolean isEmpty() {
		return contents.isEmpty();
	}

	Map.Entry<String, Integer> first() {
		return contents.firstEntry();
	}

	String firstMatching(java.util.function.Predicate<String> condition) {
		return contents.keySet().stream().filter(condition).findFirst().orElse("");
	}

	void add(String aspect, int quantity) {
		contents.merge(aspect, quantity, Integer::sum);
		total += quantity;
	}

	void remove(String aspect, int quantity) {
		int remaining = amount(aspect) - quantity;
		if (remaining == 0) {
			contents.remove(aspect);
		} else {
			contents.put(aspect, remaining);
		}
		total -= quantity;
	}

	void load(CompoundTag tag) {
		contents.clear();
		total = 0;
		for (String id : tag.getAllKeys().stream().sorted().toList()) {
			if (room() == 0) {
				break;
			}
			if (id.equals(AspectCatalog.normalizeId(id)) && tag.contains(id, Tag.TAG_INT)) {
				int count = Mth.clamp(tag.getInt(id), 0, room());
				if (count > 0) {
					add(id, count);
				}
			}
		}
	}

	CompoundTag save() {
		var tag = new CompoundTag();
		contents.forEach(tag::putInt);
		return tag;
	}
}
