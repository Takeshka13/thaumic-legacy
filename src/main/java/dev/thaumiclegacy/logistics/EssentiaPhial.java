package dev.thaumiclegacy.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;


public final class EssentiaPhial extends Item {
	public static final int CAPACITY = 8;
	private static final Contents EMPTY = new Contents("", 0);
	private static final Codec<Contents> CODEC = RecordCodecBuilder.<Contents>create(instance -> instance.group(
			Codec.STRING.fieldOf("aspect").forGetter(Contents::aspect),
			Codec.intRange(1, CAPACITY).fieldOf("amount").forGetter(Contents::amount)
	).apply(instance, Contents::new)).validate(contents -> contents.aspect().equals(AspectCatalog.normalizeId(contents.aspect()))
			? DataResult.success(contents) : DataResult.error(() -> "Invalid essentia aspect identifier"));
	public static final DataComponentType<Contents> CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ThaumicLegacy.id("essentia_contents"), DataComponentType.<Contents>builder()
					.persistent(CODEC).networkSynchronized(ByteBufCodecs.fromCodec(CODEC)).build());

	EssentiaPhial(Properties properties) {
		super(properties.stacksTo(1));
	}

	public static Contents contents(ItemStack stack) {
		return stack.getOrDefault(CONTENTS, EMPTY);
	}

	static void writeContents(ItemStack stack, String aspect, int amount) {
		if (amount == 0) {
			stack.remove(CONTENTS);
		} else {
			stack.set(CONTENTS, new Contents(aspect, amount));
		}
	}

	public record Contents(String aspect, int amount) {
		public boolean isEmpty() {
			return amount == 0;
		}
	}
}
