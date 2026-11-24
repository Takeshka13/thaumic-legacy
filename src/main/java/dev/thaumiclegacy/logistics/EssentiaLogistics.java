package dev.thaumiclegacy.logistics;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.transport.EssentiaTransport;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class EssentiaLogistics {
	private static final String MESSAGE = "message.thaumiclegacy.logistics.";
	public static final Block ALCHEMICAL_FURNACE = block("alchemical_furnace", new EssentiaFurnaceBlock(
			machineProperties(SoundType.STONE).lightLevel(state -> state.getValue(EssentiaFurnaceBlock.LIT) ? 13 : 0)));
	public static final Block ARCANE_ALEMBIC = block("arcane_alembic",
			new EssentiaStorageBlock(machineProperties(SoundType.COPPER).noOcclusion()));
	public static final Block WARDED_JAR = block("warded_jar",
			new EssentiaStorageBlock(machineProperties(SoundType.GLASS).noOcclusion()));
	public static final EssentiaPhial ESSENTIA_PHIAL = Registry.register(BuiltInRegistries.ITEM,
			ThaumicLegacy.id("essentia_phial"), new EssentiaPhial(new Item.Properties()));
	public static final BlockEntityType<EssentiaFurnaceBlockEntity> FURNACE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("alchemical_furnace"),
			FabricBlockEntityTypeBuilder.create(EssentiaFurnaceBlockEntity::new, ALCHEMICAL_FURNACE).build());
	public static final BlockEntityType<EssentiaStorageBlockEntity> STORAGE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("essentia_storage"),
			FabricBlockEntityTypeBuilder.create(EssentiaStorageBlockEntity::new, ARCANE_ALEMBIC, WARDED_JAR).build());

	private EssentiaLogistics() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
			entries.accept(ALCHEMICAL_FURNACE);
			entries.accept(ARCANE_ALEMBIC);
			entries.accept(WARDED_JAR);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(ESSENTIA_PHIAL));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			if (player.isSpectator() || player.getItemInHand(hand).getItem() instanceof FieldLensItem
					|| EssentiaTransport.isPartItem(player.getItemInHand(hand))
					|| !level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof EssentiaMachineBlock)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				BlockEntity target = level.getBlockEntity(pos);
				if (target instanceof EssentiaFurnaceBlockEntity furnace) {
					furnace.interact(serverPlayer, hand);
				} else if (target instanceof EssentiaStorageBlockEntity storage) {
					storage.interact(serverPlayer, hand);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		});
	}

	static boolean canInteract(BlockEntity target, ServerPlayer player) {
		var level = player.serverLevel();
		var pos = target.getBlockPos();
		return level.getServer().isSameThread() && !target.isRemoved() && target.getLevel() == level
				&& !player.isSpectator() && player.isAlive()
				&& level.hasChunkAt(pos) && player.canInteractWithBlock(pos, 0) && level.mayInteract(player, pos)
				&& level.getBlockEntity(pos) == target;
	}

	static Component text(String key, Object... arguments) {
		return Component.translatable(MESSAGE + key, arguments);
	}

	static Component aspectName(String id) {
		return Component.translatableWithFallback(ResourceLocation.parse(id).toLanguageKey("aspect"), id);
	}

	static boolean feedback(ServerPlayer player, boolean accepted, String key, Object... arguments) {
		player.displayClientMessage(text(key, arguments), true);
		return accepted;
	}

	private static BlockBehaviour.Properties machineProperties(SoundType sound) {
		return BlockBehaviour.Properties.of().strength(2.5F, 6.0F).sound(sound).pushReaction(PushReaction.BLOCK).noLootTable();
	}

	private static Block block(String path, Block block) {
		ResourceLocation id = ThaumicLegacy.id(path);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties().stacksTo(1)));
		return block;
	}
}
