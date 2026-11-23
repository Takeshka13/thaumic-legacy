package dev.thaumiclegacy.alchemy;

import dev.thaumiclegacy.ThaumicLegacy;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class Alchemy {
	private static final ResourceLocation CRUCIBLE_ID = ThaumicLegacy.id("crucible");
	private static final ResourceLocation RECIPE_ID = ThaumicLegacy.id("alchemy");

	public static final CrucibleBlock CRUCIBLE = Registry.register(BuiltInRegistries.BLOCK, CRUCIBLE_ID,
			new CrucibleBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).pushReaction(PushReaction.BLOCK)));
	public static final BlockItem CRUCIBLE_ITEM = Registry.register(BuiltInRegistries.ITEM, CRUCIBLE_ID,
			new BlockItem(CRUCIBLE, new Item.Properties()));
	public static final BlockEntityType<CrucibleBlockEntity> CRUCIBLE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, CRUCIBLE_ID,
			FabricBlockEntityTypeBuilder.create(CrucibleBlockEntity::new, CRUCIBLE).build());
	public static final RecipeType<AlchemyRecipe> RECIPE_TYPE = Registry.register(BuiltInRegistries.RECIPE_TYPE,
			RECIPE_ID, new RecipeType<>() {
				@Override
				public String toString() {
					return RECIPE_ID.toString();
				}
			});
	public static final RecipeSerializer<AlchemyRecipe> RECIPE_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, RECIPE_ID, new AlchemyRecipe.Serializer());

	private Alchemy() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(entries -> entries.accept(CRUCIBLE_ITEM));
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new AlchemyConfig.ReloadListener());
		// Vanilla skips block item-use callbacks while sneaking with an item in hand.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var pos = hit.getBlockPos();
			if (player.isSpectator() || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(CRUCIBLE)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer
					&& level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
				crucible.interact(serverPlayer, hand);
			}
			// Detach every outcome from vanilla item placement and second-hand fallback.
			return InteractionResult.sidedSuccess(level.isClientSide());
		});
	}
}
