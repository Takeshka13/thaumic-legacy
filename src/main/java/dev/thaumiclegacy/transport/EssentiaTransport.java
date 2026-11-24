package dev.thaumiclegacy.transport;

import dev.thaumiclegacy.ThaumicLegacy;
import dev.thaumiclegacy.advanced.AdvancedAlchemy;
import dev.thaumiclegacy.advanced.AdvancedEssentiaPort;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.research.FieldLensItem;
import dev.thaumiclegacy.research.ResearchProgress;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.Locale;
import java.util.Optional;

public final class EssentiaTransport {
	public static final int INTERVAL = 10;
	public static final int MAX_VISITED = 128;
	public static final int MAX_HOPS = 32;
	static final int TELEMETRY_TICKS = 20;
	static final Direction[] DIRECTIONS = Direction.values();
	private static final String MESSAGE = "message.thaumiclegacy.transport.";

	public static final Block TUBE = registerBlock("essentia_tube", new TransportBlock.Tube(properties()));
	public static final Block VALVE = registerBlock("essentia_valve", new TransportBlock.Valve(properties()));
	public static final Block FILTER = registerBlock("essentia_filter", new TransportBlock.Filter(properties()));
	public static final BlockEntityType<TransportBlockEntity> TRANSPORT_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ThaumicLegacy.id("essentia_transport"),
			FabricBlockEntityTypeBuilder.create(TransportBlockEntity::new, TUBE, VALVE, FILTER).build());

	private EssentiaTransport() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var pos = hit.getBlockPos();
			if (player.isSpectator() || player.getItemInHand(hand).getItem() instanceof FieldLensItem
					|| isPartItem(player.getItemInHand(hand))
					|| !level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof TransportBlock)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer actor) {
				if (actor.isAlive() && actor.canInteractWithBlock(pos, 0) && level.mayInteract(actor, pos)
						&& level.getBlockEntity(pos) instanceof TransportBlockEntity part && !part.isRemoved()) {
					if (player.getItemInHand(hand).isEmpty()) {
						interact(actor, part);
					} else {
						message(actor, "empty_hand");
					}
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide());
		});
	}

	public static boolean isPartItem(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof TransportBlock;
	}

	private static void interact(ServerPlayer player, TransportBlockEntity part) {
		var level = player.serverLevel();
		BlockState state = part.getBlockState();
		if (state.is(VALVE)) {
			boolean open = !state.getValue(TransportBlock.OPEN);
			level.setBlock(part.getBlockPos(), state.setValue(TransportBlock.OPEN, open), Block.UPDATE_CLIENTS);
			part.note(null);
			message(player, open ? "valve_open" : "valve_closed");
		} else if (state.is(FILTER)) {
			if (!ResearchProgress.isUnlocked(player, ResearchProgress.ESSENTIA_CONTROLS)) {
				message(player, "research_required");
				return;
			}
			if (player.isShiftKeyDown()) {
				part.configure("");
				message(player, "filter_cleared");
				return;
			}
			var known = ResearchProgress.knownAspects(player).stream()
					.filter(AspectCatalog.current().definitions()::containsKey).distinct().sorted().toList();
			if (known.isEmpty()) {
				message(player, "no_known_aspects");
				return;
			}
			String id = known.get((known.indexOf(part.filter()) + 1) % known.size());
			part.configure(id);
			message(player, "filter_set", Component.translatableWithFallback(ResourceLocation.parse(id).toLanguageKey("aspect"), id));
		} else {
			read(level, part.getBlockPos()).ifPresent(reading ->
					message(player, "tube_status", Component.translatable(reading.status().translationKey())));
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, EssentiaStorageBlockEntity source) {
		if (level instanceof ServerLevel server && source.hasEssentia()) {
			tickSource(server, pos);
		}
	}

	public static void tickSource(ServerLevel server, BlockPos pos) {
		if (Math.floorMod(server.getGameTime(), INTERVAL) == Math.floorMod(pos.asLong(), INTERVAL)) {
			tryTransfer(server, pos);
		}
	}

	public static boolean tryTransfer(ServerLevel level, BlockPos source) {
		if (!level.getServer().isSameThread()) {
			return false;
		}
		BlockState state = loadedState(level, source);
		if (state == null) {
			return false;
		}
		var blockEntity = level.getBlockEntity(source);
		return blockEntity instanceof AdvancedEssentiaPort port && !blockEntity.isRemoved()
				&& TransportSearch.transfer(level, source, state, port);
	}

	public static Optional<Reading> read(ServerLevel level, BlockPos pos) {
		if (!level.getServer().isSameThread()) {
			return Optional.empty();
		}
		BlockState state = loadedState(level, pos);
		if (state == null) {
			return Optional.empty();
		}
		if (state.getBlock() instanceof TransportBlock && level.getBlockEntity(pos) instanceof TransportBlockEntity transport) {
			int connections = connections(level, pos, state);
			Status local = localStatus(state, transport);
			Activity activity = live(transport.activity(), level.getGameTime());
			Status status = local != null ? local : connections == 0 ? Status.NO_CONNECTION
					: activity != null ? activity.status() : Status.NO_FLOW;
			boolean flowing = status == Status.FLOWING;
			return Optional.of(new Reading(flowing ? activity.aspect() : "", flowing ? 1 : 0,
					connections, status, transport.filter()));
		}
		if ((state.is(EssentiaLogistics.ARCANE_ALEMBIC) || state.is(EssentiaLogistics.WARDED_JAR))
				&& level.getBlockEntity(pos) instanceof EssentiaStorageBlockEntity storage) {
			int connections = connections(level, pos, state);
			var view = storage.snapshot();
			int amount = view.amount();
			String aspect = view.aspects().size() == 1 ? view.aspects().keySet().iterator().next() : "";
			Activity activity = live(storage.transportActivity(), level.getGameTime());
			Status status = connections == 0 ? Status.NO_CONNECTION
					: activity != null ? activity.status() : view.singleAspect() && amount == view.capacity()
					? Status.DESTINATION_FULL : !view.singleAspect() && amount == 0 ? Status.SOURCE_EMPTY : Status.NO_FLOW;
			return Optional.of(new Reading(aspect, amount, connections, status, storage.label()));
		}
		if (level.getBlockEntity(pos) instanceof AdvancedEssentiaPort) {
			int mask = connections(level, pos, state);
			return Optional.of(new Reading("", 0, mask, mask == 0 ? Status.NO_CONNECTION : Status.NO_FLOW, ""));
		}
		return Optional.empty();
	}

	private static Activity live(Activity activity, long now) {
		return activity != null && now >= activity.tick() && now - activity.tick() < TELEMETRY_TICKS ? activity : null;
	}

	private static int connections(ServerLevel level, BlockPos pos, BlockState state) {
		int mask = 0;
		for (Direction direction : DIRECTIONS) {
			if (!hasPort(state, direction)) {
				continue;
			}
			var other = loadedState(level, pos.relative(direction));
			if (other != null && (state.getBlock() instanceof TransportBlock || other.getBlock() instanceof TransportBlock)
					&& hasPort(other, direction.getOpposite())) {
				mask |= 1 << direction.get3DDataValue();
			}
		}
		return mask;
	}

	static boolean hasPort(BlockState state, Direction face) {
		if (state.is(EssentiaLogistics.ARCANE_ALEMBIC)) {
			return face != Direction.DOWN;
		}
		if (state.is(EssentiaLogistics.WARDED_JAR)) {
			return face == Direction.UP;
		}
		return TransportBlock.hasPort(state, face) || AdvancedAlchemy.inputPort(state, face) || AdvancedAlchemy.outputPort(state, face);
	}

	static Status localStatus(BlockState state, TransportBlockEntity transport) {
		if (state.is(VALVE) && !state.getValue(TransportBlock.OPEN)) {
			return Status.VALVE_CLOSED;
		}
		if (state.is(FILTER) && transport.filter().isEmpty()) {
			return Status.FILTER_UNCONFIGURED;
		}
		return null;
	}

	static BlockState loadedState(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos)) {
			return null;
		}
		var chunk = level.getChunkSource().getChunkNow(
				SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		return chunk == null ? null : chunk.getBlockState(pos);
	}

	private static void message(ServerPlayer player, String key, Object... args) {
		player.displayClientMessage(Component.translatable(MESSAGE + key, args), true);
	}

	private static BlockBehaviour.Properties properties() {
		return BlockBehaviour.Properties.of().strength(1.5F, 5.0F).sound(SoundType.COPPER)
				.noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK);
	}

	private static Block registerBlock(String path, Block block) {
		var id = ThaumicLegacy.id(path);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
		return block;
	}

	public record Reading(String aspect, int amount, int connections, Status status, String filter) { }

	public record Activity(Status status, String aspect, long tick) { }

	public enum Status {
		NO_CONNECTION, NO_FLOW, NO_DESTINATION, SOURCE_EMPTY, DESTINATION_FULL, DESTINATION_INCOMPATIBLE,
		FLOWING, VALVE_CLOSED, FILTER_UNCONFIGURED, FILTER_MISMATCH, UNLOADED, NETWORK_LIMIT;

		public String translationKey() {
			return "state.thaumiclegacy.transport." + name().toLowerCase(Locale.ROOT);
		}
	}
}
