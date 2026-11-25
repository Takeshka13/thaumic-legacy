package dev.thaumiclegacy.infusion;

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

import java.util.List;
import java.util.Map;

public record InfusionRecipe(Ingredient central, List<Ingredient> components, Map<String, Integer> aspects,
							 ResourceLocation research, int instability, ItemStack result) implements Recipe<InfusionRecipe.Input> {
	static final int MAX_ESSENTIA = 64;
	private static final Codec<Map<String, Integer>> ASPECTS = Codec.unboundedMap(Codec.STRING, Codec.intRange(1, MAX_ESSENTIA))
			.validate(requirements -> !requirements.isEmpty() && requirements.size() <= MAX_ESSENTIA
					&& requirements.values().stream().mapToInt(Integer::intValue).sum() <= MAX_ESSENTIA
					&& requirements.keySet().stream().allMatch(id -> id.equals(AspectCatalog.normalizeId(id)))
					? DataResult.success(requirements) : DataResult.error(() -> "Invalid or excessive infusion essentia"));
	private static final MapCodec<InfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Ingredient.CODEC_NONEMPTY.fieldOf("central").forGetter(InfusionRecipe::central),
			Ingredient.CODEC_NONEMPTY.listOf(1, 8).fieldOf("components").forGetter(InfusionRecipe::components),
			ASPECTS.fieldOf("aspects").forGetter(InfusionRecipe::aspects),
			ResourceLocation.CODEC.fieldOf("research").forGetter(InfusionRecipe::research),
			Codec.intRange(0, 12).fieldOf("instability").forGetter(InfusionRecipe::instability),
			ItemStack.STRICT_CODEC.fieldOf("result").forGetter(InfusionRecipe::result)
	).apply(instance, InfusionRecipe::new));

	public InfusionRecipe {
		components = List.copyOf(components);
		aspects = Map.copyOf(aspects);
		result = result.copy();
	}

	@Override
	public ItemStack result() {
		return result.copy();
	}

	@Override
	public boolean matches(Input input, Level level) {
		return central.test(input.central()) && input.components().size() == components.size()
				&& assign(input.components(), 0, 0);
	}

	private boolean assign(List<ItemStack> stacks, int ingredient, int used) {
		if (ingredient == components.size()) {
			return true;
		}
		for (int slot = 0; slot < stacks.size(); slot++) {
			int bit = 1 << slot;
			if ((used & bit) == 0 && components.get(ingredient).test(stacks.get(slot))
					&& assign(stacks, ingredient + 1, used | bit)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ItemStack assemble(Input input, HolderLookup.Provider registries) {
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
		return Infusion.RECIPE_SERIALIZER;
	}

	@Override
	public RecipeType<?> getType() {
		return Infusion.RECIPE_TYPE;
	}

	public record Input(ItemStack central, List<ItemStack> components) implements RecipeInput {
		@Override
		public ItemStack getItem(int index) {
			return index == 0 ? central : components.get(index - 1);
		}

		@Override
		public int size() {
			return 1 + components.size();
		}
	}

	static final class Serializer implements RecipeSerializer<InfusionRecipe> {
		@Override
		public MapCodec<InfusionRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, InfusionRecipe> streamCodec() {
			return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
		}
	}
}
