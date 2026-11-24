package dev.thaumiclegacy.workbench;

import dev.thaumiclegacy.ThaumicLegacy;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ArcaneWorkbench {
	private static final ResourceLocation ID = ThaumicLegacy.id("arcane_workbench");

	public static final Block BLOCK = Registry.register(BuiltInRegistries.BLOCK, ID, new ArcaneWorkbenchBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).pushReaction(PushReaction.BLOCK)));
	public static final BlockItem ITEM = Registry.register(BuiltInRegistries.ITEM, ID, new BlockItem(BLOCK, new Item.Properties()));
	public static final BlockEntityType<ArcaneWorkbenchBlockEntity> BLOCK_ENTITY_TYPE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ID, FabricBlockEntityTypeBuilder.create(ArcaneWorkbenchBlockEntity::new, BLOCK).build());
	public static final MenuType<ArcaneWorkbenchMenu> MENU = Registry.register(BuiltInRegistries.MENU, ID,
			new MenuType<>(ArcaneWorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
	public static final RecipeType<ArcaneWorkbenchRecipe> RECIPE_TYPE = Registry.register(
			BuiltInRegistries.RECIPE_TYPE, ID, new RecipeType<>() {
				@Override
				public String toString() {
					return ID.toString();
				}
			});
	public static final RecipeSerializer<ArcaneWorkbenchRecipe> RECIPE_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, ID, new ArcaneWorkbenchRecipe.Serializer());

	private ArcaneWorkbench() {
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ArcaneWorkbenchCraftPayload.TYPE, ArcaneWorkbenchCraftPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ArcaneWorkbenchCraftPayload.TYPE, (payload, context) -> {
			var player = context.player();
			if (player.containerMenu instanceof ArcaneWorkbenchMenu menu && menu.containerId == payload.containerId()) {
				menu.craft(player, payload.token());
			}
		});
	}
}
