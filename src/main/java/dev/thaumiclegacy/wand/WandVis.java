package dev.thaumiclegacy.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.ThaumicLegacy;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

/** Discrete, per-item primal charge. The dimension's scalar aura is a separate resource. */
public record WandVis(int aer, int aqua, int ignis, int terra, int ordo, int perditio) {
	public static final int CAPACITY = 25;
	public static final WandVis EMPTY = new WandVis(0, 0, 0, 0, 0, 0);
	private static final Codec<Integer> AMOUNT = Codec.intRange(0, CAPACITY);
	private static final Codec<WandVis> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			AMOUNT.optionalFieldOf("aer", 0).forGetter(WandVis::aer),
			AMOUNT.optionalFieldOf("aqua", 0).forGetter(WandVis::aqua),
			AMOUNT.optionalFieldOf("ignis", 0).forGetter(WandVis::ignis),
			AMOUNT.optionalFieldOf("terra", 0).forGetter(WandVis::terra),
			AMOUNT.optionalFieldOf("ordo", 0).forGetter(WandVis::ordo),
			AMOUNT.optionalFieldOf("perditio", 0).forGetter(WandVis::perditio)
	).apply(instance, WandVis::new));
	public static final DataComponentType<WandVis> COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ThaumicLegacy.id("wand_vis"), DataComponentType.<WandVis>builder().persistent(CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(CODEC)).build());

	public WandVis {
		validate(aer);
		validate(aqua);
		validate(ignis);
		validate(terra);
		validate(ordo);
		validate(perditio);
	}

	public enum Primal {
		AER("aer"), AQUA("aqua"), IGNIS("ignis"), TERRA("terra"), ORDO("ordo"), PERDITIO("perditio");

		private final String id;

		Primal(String id) {
			this.id = id;
		}

		public String translationKey() {
			return ThaumicLegacy.id(id).toLanguageKey("aspect");
		}
	}

	public int amount(Primal primal) {
		return switch (primal) {
			case AER -> aer;
			case AQUA -> aqua;
			case IGNIS -> ignis;
			case TERRA -> terra;
			case ORDO -> ordo;
			case PERDITIO -> perditio;
		};
	}

	public static WandVis get(ItemStack wand) {
		return wand.getOrDefault(COMPONENT, EMPTY);
	}

	/** Call after validating the charging source on the server. */
	public static int restore(ItemStack wand, Primal primal, int requested) {
		if (!(wand.getItem() instanceof WandItem) || requested <= 0) {
			return 0;
		}
		WandVis vis = get(wand);
		int current = vis.amount(primal);
		int accepted = Math.min(requested, CAPACITY - current);
		if (accepted > 0) {
			wand.set(COMPONENT, vis.with(primal, current + accepted));
		}
		return accepted;
	}

	public static boolean trySpend(ItemStack wand, Primal primal, int requested) {
		if (!(wand.getItem() instanceof WandItem) || requested <= 0) {
			return false;
		}
		WandVis vis = get(wand);
		int current = vis.amount(primal);
		if (requested > current) {
			return false;
		}
		wand.set(COMPONENT, vis.with(primal, current - requested));
		return true;
	}

	private WandVis with(Primal primal, int amount) {
		return switch (primal) {
			case AER -> new WandVis(amount, aqua, ignis, terra, ordo, perditio);
			case AQUA -> new WandVis(aer, amount, ignis, terra, ordo, perditio);
			case IGNIS -> new WandVis(aer, aqua, amount, terra, ordo, perditio);
			case TERRA -> new WandVis(aer, aqua, ignis, amount, ordo, perditio);
			case ORDO -> new WandVis(aer, aqua, ignis, terra, amount, perditio);
			case PERDITIO -> new WandVis(aer, aqua, ignis, terra, ordo, amount);
		};
	}

	private static void validate(int amount) {
		if (amount < 0 || amount > CAPACITY) {
			throw new IllegalArgumentException("Primal Vis outside wand capacity");
		}
	}
}
