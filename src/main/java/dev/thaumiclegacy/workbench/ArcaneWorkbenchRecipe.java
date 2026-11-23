package dev.thaumiclegacy.workbench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ArcaneWorkbenchRecipe(ShapedRecipePattern pattern, ItemStack result, int visCost,
									Optional<ResourceLocation> research) implements Recipe<CraftingInput> {
	public ArcaneWorkbenchRecipe {
		result = result.copy();
	}

	@Override
	public ItemStack result() {
		return result.copy();
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return pattern.matches(input);
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return result();
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return result();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width >= pattern.width() && height >= pattern.height();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ArcaneWorkbench.RECIPE_SERIALIZER;
	}

	@Override
	public RecipeType<?> getType() {
		return ArcaneWorkbench.RECIPE_TYPE;
	}

	static final class Serializer implements RecipeSerializer<ArcaneWorkbenchRecipe> {
		private static final MapCodec<ArcaneWorkbenchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ShapedRecipePattern.MAP_CODEC.forGetter(ArcaneWorkbenchRecipe::pattern),
				ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ArcaneWorkbenchRecipe::result),
				Codec.intRange(1, 100).fieldOf("vis_cost").forGetter(ArcaneWorkbenchRecipe::visCost),
				ResourceLocation.CODEC.optionalFieldOf("research").forGetter(ArcaneWorkbenchRecipe::research)
		).apply(instance, ArcaneWorkbenchRecipe::new));
		private static final StreamCodec<RegistryFriendlyByteBuf, ArcaneWorkbenchRecipe> STREAM_CODEC = StreamCodec.composite(
				ShapedRecipePattern.STREAM_CODEC, ArcaneWorkbenchRecipe::pattern,
				ItemStack.STREAM_CODEC, ArcaneWorkbenchRecipe::result,
				ByteBufCodecs.VAR_INT, ArcaneWorkbenchRecipe::visCost,
				ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ArcaneWorkbenchRecipe::research,
				ArcaneWorkbenchRecipe::new);

		@Override
		public MapCodec<ArcaneWorkbenchRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, ArcaneWorkbenchRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}
