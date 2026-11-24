package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.transport.EssentiaTransport;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.Locale;

public final class AdvancedAlchemy {
	private static final String MESSAGE = "message.thaumiclegacy.advanced.";
	public static final Block ALCHEMICAL_CENTRIFUGE = registerMachine("alchemical_centrifuge",
			new AdvancedMachineBlock.Centrifuge(properties()));
	public static final Block THAUMATORIUM = registerMachine("thaumatorium",
			new AdvancedMachineBlock.Thaumatorium(properties()));
	public static final BlockEntityType<AlchemicalCentrifugeBlockEntity> CENTRIFUGE_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("alchemical_centrifuge"),
			FabricBlockEntityTypeBuilder.create(AlchemicalCentrifugeBlockEntity::new, ALCHEMICAL_CENTRIFUGE).build());
	public static final BlockEntityType<ThaumatoriumBlockEntity> THAUMATORIUM_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("thaumatorium"),
			FabricBlockEntityTypeBuilder.create(ThaumatoriumBlockEntity::new, THAUMATORIUM).build());

	private AdvancedAlchemy() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var pos = hit.getBlockPos();
			if (player.isSpectator() || player.getItemInHand(hand).getItem() instanceof FieldLensItem
					|| EssentiaTransport.isPartItem(player.getItemInHand(hand))
					|| !level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof AdvancedMachineBlock)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer
					&& level.getBlockEntity(pos) instanceof AdvancedMachineEntity machine
					&& machine.canUse(serverPlayer)) {
				machine.interact(serverPlayer, hand);
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		});
	}

	public static boolean inputPort(BlockState state, Direction face) {
		return face.getAxis().isHorizontal() && (state.is(ALCHEMICAL_CENTRIFUGE) || state.is(THAUMATORIUM));
	}

	public static boolean outputPort(BlockState state, Direction face) {
		return face == Direction.UP && state.is(ALCHEMICAL_CENTRIFUGE);
	}

	static boolean feedback(ServerPlayer player, boolean changed, String key, Object... args) {
		player.displayClientMessage(Component.translatable(MESSAGE + key, args), true);
		return changed;
	}

	static Component aspectName(String aspect) {
		return Component.translatableWithFallback(ResourceLocation.parse(aspect).toLanguageKey("aspect"), aspect);
	}

	private static BlockBehaviour.Properties properties() {
		return BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.COPPER).noLootTable()
				.pushReaction(PushReaction.BLOCK);
	}

	private static Block registerMachine(String path, Block block) {
		var id = ThaumicLegacy.id(path);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties().stacksTo(1)));
		return block;
	}

	public enum Status {
		IDLE, WORKING, OUTPUT_FULL, INVALID_ESSENTIA, NO_RECIPE, OWNER_UNAVAILABLE, RESEARCH_REQUIRED,
		MISSING_CATALYST, CATALYST_MISMATCH, MISSING_ESSENTIA, REMAINDER_FULL, INVALID_RECIPE;

		public String translationKey() {
			return "state.thaumiclegacy.advanced." + name().toLowerCase(Locale.ROOT);
		}
	}
}
