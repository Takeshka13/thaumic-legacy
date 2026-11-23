package dev.thaumiclegacy.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.aspect.AspectCatalog;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record AlchemyRecipe(Ingredient catalyst, Map<String, Integer> aspects, ItemStack result,
							Optional<ResourceLocation> research) implements Recipe<AlchemyRecipe.CatalystInput> {
	private static final Codec<Map<String, Integer>> ASPECTS_CODEC = Codec.unboundedMap(
			Codec.STRING, Codec.intRange(1, AlchemyConfig.MAX_CAPACITY)).validate(AlchemyRecipe::validateRequirements);

	public AlchemyRecipe {
		aspects = Map.copyOf(aspects);
		result = result.copy();
	}

	@Override
	public ItemStack result() {
		return result.copy();
	}

	@Override
	public boolean matches(CatalystInput input, Level level) {
		return catalyst.test(input.held());
	}

	boolean hasAspects(Map<String, Integer> contents, AspectCatalog catalog) {
		return aspects.entrySet().stream().allMatch(entry -> catalog.definitions().containsKey(entry.getKey())
				&& contents.getOrDefault(entry.getKey(), 0) >= entry.getValue());
	}

	@Override
	public ItemStack assemble(CatalystInput input, HolderLookup.Provider registries) {
		return result();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return false;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return result();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Alchemy.RECIPE_SERIALIZER;
	}

	@Override
	public RecipeType<?> getType() {
		return Alchemy.RECIPE_TYPE;
	}

	private static DataResult<Map<String, Integer>> validateRequirements(Map<String, Integer> requirements) {
		if (requirements.isEmpty() || requirements.size() > AlchemyConfig.MAX_CAPACITY) {
			return DataResult.error(() -> "Alchemy recipe must have a bounded, non-empty aspect requirement");
		}
		int units = 0;
		for (var entry : requirements.entrySet()) {
			String id = entry.getKey();
			if (!id.equals(AspectCatalog.normalizeId(id))) {
				return DataResult.error(() -> "Alchemy requires a fully qualified aspect ID: " + id);
			}
			units += entry.getValue();
			if (units > AlchemyConfig.MAX_CAPACITY) {
				return DataResult.error(() -> "Alchemy recipe exceeds the maximum number of aspect units");
			}
		}
		return DataResult.success(requirements);
	}

	public record CatalystInput(ItemStack held) implements RecipeInput {
		@Override
		public ItemStack getItem(int index) {
			return index == 0 ? held : ItemStack.EMPTY;
		}

		@Override
		public int size() {
			return 1;
		}
	}

	static final class Serializer implements RecipeSerializer<AlchemyRecipe> {
		private static final MapCodec<AlchemyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(AlchemyRecipe::catalyst),
				ASPECTS_CODEC.fieldOf("aspects").forGetter(AlchemyRecipe::aspects),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlchemyRecipe::result),
				ResourceLocation.CODEC.optionalFieldOf("research").forGetter(AlchemyRecipe::research)
		).apply(instance, AlchemyRecipe::new));
		private static final StreamCodec<RegistryFriendlyByteBuf, AlchemyRecipe> STREAM_CODEC = StreamCodec.composite(
				Ingredient.CONTENTS_STREAM_CODEC, AlchemyRecipe::catalyst,
				ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new,
						ByteBufCodecs.stringUtf8(AspectCatalog.MAX_ID_LENGTH), ByteBufCodecs.VAR_INT,
						AlchemyConfig.MAX_CAPACITY), AlchemyRecipe::aspects,
				ItemStack.STREAM_CODEC, AlchemyRecipe::result,
				ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), AlchemyRecipe::research,
				AlchemyRecipe::new);

		@Override
		public MapCodec<AlchemyRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, AlchemyRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
