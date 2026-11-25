package dev.thaumiclegacy.infusion;

import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.multiblock.MultiblockPattern.Match;
import dev.thaumiclegacy.research.ResearchProgress;
import dev.thaumiclegacy.transport.EssentiaTransport;
import dev.thaumiclegacy.wand.WandVis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class RunicMatrixEntity extends BlockEntity {
	static final WandVis FORMATION_COST = new WandVis(0, 0, 0, 5, 5, 0);
	static final int INTERVAL = 10;
	private static final int JAR_RADIUS = 4;
	private static final int MAX_RISK = 25;
	private Run run;
	private Status status = Status.INFUSION_UNFORMED;

	RunicMatrixEntity(BlockPos pos, BlockState state) {
		super(Infusion.MATRIX_ENTITY, pos, state);
	}

	void useWand(ServerLevel level, ServerPlayer player, ItemStack wand) {
		if (run != null) {
			if (player.isShiftKeyDown() && run.owner.equals(player.getUUID())) {
				cancel(Status.INFUSION_CANCELLED);
			}
			message(player, status);
			return;
		}
		Match match = match(level);
		if (match != Match.MATCH) {
			message(player, match == Match.UNLOADED ? Status.INFUSION_PAUSED : Status.INFUSION_STRUCTURE);
			return;
		}
		if (!ResearchProgress.isUnlocked(player, Infusion.RESEARCH)) {
			message(player, Status.INFUSION_RESEARCH);
			return;
		}
		if (!getBlockState().getValue(RunicMatrixBlock.FORMED)) {
			if (!WandVis.canSpend(wand, FORMATION_COST)) {
				message(player, Status.INFUSION_WAND);
				return;
			}
			var formed = getBlockState().setValue(RunicMatrixBlock.FORMED, true);
			if (level.setBlock(worldPosition, formed, Block.UPDATE_ALL)) {
				WandVis.trySpend(wand, FORMATION_COST);
				status = Status.INFUSION_READY;
				message(player, status);
			}
			return;
		}
		status = start(level, player);
		message(player, status);
	}

	private Status start(ServerLevel level, ServerPlayer player) {
		var revisions = new TreeMap<Integer, UUID>();
		var components = new ArrayList<ItemStack>();
		ItemStack central = ItemStack.EMPTY;
		for (int index = 0; index <= Infusion.RING.size(); index++) {
			var pedestal = pedestal(level, index);
			if (pedestal == null || pedestal.stack().isEmpty()) {
				continue;
			}
			var stack = pedestal.stack();
			if (index == 0) {
				if (stack.getCount() != 1) {
					return Status.INFUSION_RECIPE;
				}
				central = stack;
			} else {
				if (stack.getCount() != 1) {
					return Status.INFUSION_RECIPE;
				}
				components.add(stack);
			}
			revisions.put(index, pedestal.revision());
		}
		var input = new InfusionRecipe.Input(central, components);
		var matching = level.getRecipeManager().getRecipesFor(Infusion.RECIPE_TYPE, input, level);
		var authorized = matching.stream()
				.filter(entry -> ResearchProgress.isUnlocked(player, entry.value().research()))
				.sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
		if (authorized.isEmpty()) {
			return matching.isEmpty() ? Status.INFUSION_RECIPE : Status.INFUSION_RESEARCH;
		}
		var catalog = AspectCatalog.current().definitions();
		for (var entry : authorized) {
			var recipe = entry.value();
			if (recipe.aspects().entrySet().stream().allMatch(requirement -> catalog.containsKey(requirement.getKey())
					&& available(level, requirement.getKey()) >= requirement.getValue())) {
				int total = total(recipe.aspects());
				run = new Run(player.getUUID(), revisions, new TreeMap<>(recipe.aspects()), recipe.result(), total, recipe.instability());
				setChanged();
				return Status.INFUSION_RUNNING;
			}
		}
		return Status.INFUSION_ESSENTIA;
	}

	static void tick(Level level, BlockPos pos, BlockState state, RunicMatrixEntity matrix) {
		if (level instanceof ServerLevel server && Math.floorMod(level.getGameTime() + pos.asLong(), INTERVAL) == 0) {
			matrix.advance(server);
		}
	}

	void advance(ServerLevel level) {
		Match match = match(level);
		if (match == Match.UNLOADED) {
			status = Status.INFUSION_PAUSED;
			return;
		}
		if (match == Match.INVALID) {
			cancel(Status.INFUSION_STRUCTURE);
			level.setBlock(worldPosition, getBlockState().setValue(RunicMatrixBlock.FORMED, false), Block.UPDATE_ALL);
			return;
		}
		if (run == null) {
			if (status == Status.INFUSION_PAUSED || status == Status.INFUSION_UNFORMED) {
				status = Status.INFUSION_READY;
			}
			return;
		}
		if (!inputsIntact(level)) {
			cancel(Status.INFUSION_INPUTS_CHANGED);
			return;
		}
		if (run.remaining.isEmpty()) {
			complete(level);
			return;
		}
		var aspect = run.remaining.firstKey();
		if (!AspectCatalog.current().definitions().containsKey(aspect)) {
			cancel(Status.INFUSION_CANCELLED);
			return;
		}
		for (var jar : sources(level, aspect)) {
			if (jar.extractOne(Direction.UP, aspect)) {
				run.remaining.compute(aspect, (id, units) -> units == 1 ? null : units - 1);
				status = Status.INFUSION_RUNNING;
				setChanged();
				if (!accident(level, level.random.nextInt(100)) && run.remaining.isEmpty()) {
					complete(level);
				}
				return;
			}
		}
		status = Status.INFUSION_ESSENTIA;
	}

	private boolean inputsIntact(ServerLevel level) {
		for (int index = 0; index <= Infusion.RING.size(); index++) {
			var pedestal = pedestal(level, index);
			var expected = run.revisions.get(index);
			if (expected == null ? pedestal != null && !pedestal.stack().isEmpty()
					: pedestal == null || pedestal.stack().isEmpty() || !expected.equals(pedestal.revision())) {
				return false;
			}
		}
		return true;
	}

	private void complete(ServerLevel level) {
		var completed = run;
		// All positions and revisions were checked in this server tick. No delayed or client-supplied output exists.
		run = null;
		for (int index : completed.revisions.keySet()) {
			pedestal(level, index).setStack(index == 0 ? completed.result : ItemStack.EMPTY);
		}
		status = Status.INFUSION_COMPLETE;
		setChanged();
	}

	boolean accident(ServerLevel level, int roll) {
		int points = instability(level);
		int chance = Math.min(MAX_RISK, points * level.getGameRules().getInt(Infusion.RISK));
		if (chance == 0 || roll >= chance) {
			return false;
		}
		// One mishap ends the ritual. All surviving items remain exactly where the player put them.
		var positions = run.revisions.keySet().stream().filter(index -> index != 0).toList();
		int index = positions.get(level.random.nextInt(positions.size()));
		var pedestal = pedestal(level, index);
		var center = pedestal.getBlockPos().getCenter();
		var ejected = new ItemEntity(level, center.x, center.y + 0.6, center.z, pedestal.stack());
		ejected.setDefaultPickUpDelay();
		if (level.addFreshEntity(ejected)) {
			pedestal.setStack(ItemStack.EMPTY);
		}
		int flux = level.getGameRules().getInt(Infusion.FLUX);
		if (flux > 0) {
			AuraService.addFlux(level, new ChunkPos(worldPosition), flux);
		}
		cancel(Status.INFUSION_ACCIDENT);
		return true;
	}

	int instability(ServerLevel level) {
		if (run == null) {
			return 0;
		}
		int points = run.instability;
		for (int index = 0; index < Infusion.RING.size(); index += 2) {
			var a = Infusion.RING.get(index);
			var b = Infusion.RING.get(index + 1);
			boolean first = run.revisions.containsKey(index + 1);
			boolean opposite = run.revisions.containsKey(index + 2);
			if (first || opposite) {
				points += first && opposite ? -1 : 2;
			}
			var candleA = candlePosition(a);
			var candleB = candlePosition(b);
			if (isCandle(level, candleA) && isCandle(level, candleB)) {
				points -= 1;
			}
		}
		return Math.max(0, points);
	}

	private BlockPos candlePosition(BlockPos offset) {
		return worldPosition.offset(offset.getX() + Integer.signum(offset.getX()), -2,
				offset.getZ() + Integer.signum(offset.getZ()));
	}

	private static boolean isCandle(ServerLevel level, BlockPos pos) {
		var state = EssentiaTransport.loadedState(level, pos);
		return state != null && state.is(BlockTags.CANDLES);
	}

	private Match match(ServerLevel level) {
		Match match = Infusion.PATTERN.check(level, worldPosition, Direction.NORTH);
		if (match != Match.MATCH) {
			return match;
		}
		for (var offset : Infusion.RING) {
			if (EssentiaTransport.loadedState(level, worldPosition.offset(offset)) == null) {
				return Match.UNLOADED;
			}
		}
		return Match.MATCH;
	}

	private PedestalEntity pedestal(ServerLevel level, int index) {
		var pos = index == 0 ? worldPosition.below(2) : worldPosition.offset(Infusion.RING.get(index - 1));
		return level.getBlockEntity(pos) instanceof PedestalEntity pedestal ? pedestal : null;
	}

	private int available(ServerLevel level, String aspect) {
		return sources(level, aspect).stream().mapToInt(jar -> jar.transportContents(Direction.UP).getOrDefault(aspect, 0)).sum();
	}

	private LinkedHashSet<EssentiaStorageBlockEntity> sources(ServerLevel level, String aspect) {
		var jars = new LinkedHashSet<>(EssentiaTransport.reachableJars(level, worldPosition, aspect));
		for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-JAR_RADIUS, -2, -JAR_RADIUS),
				worldPosition.offset(JAR_RADIUS, 0, JAR_RADIUS))) {
			var state = EssentiaTransport.loadedState(level, pos);
			if (state != null && state.is(EssentiaLogistics.WARDED_JAR)
					&& level.getBlockEntity(pos) instanceof EssentiaStorageBlockEntity jar && openJar(level, pos)) {
				jars.add(jar);
			}
		}
		return jars;
	}

	private static boolean openJar(ServerLevel level, BlockPos pos) {
		var lid = EssentiaTransport.loadedState(level, pos.above());
		return lid != null && lid.isAir();
	}

	private void cancel(Status reason) {
		run = null;
		status = reason;
		setChanged();
	}

	private static void message(ServerPlayer player, Status status) {
		player.displayClientMessage(Component.translatable("screen.thaumiclegacy.logistics.status." + status.key()), true);
	}

	public Reading reading() {
		Map<String, Integer> remaining = run == null ? Map.of() : Map.copyOf(run.remaining);
		int units = run == null ? 0 : run.total;
		return new Reading(status.key(), remaining, units - total(remaining), units);
	}

	private static int total(Map<String, Integer> values) {
		return values.values().stream().mapToInt(Integer::intValue).sum();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (run != null) {
			tag.put("ritual", run.save(registries));
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		run = tag.contains("ritual", Tag.TAG_COMPOUND) ? Run.load(tag.getCompound("ritual"), registries) : null;
		status = run != null ? Status.INFUSION_RUNNING
				: getBlockState().getValue(RunicMatrixBlock.FORMED) ? Status.INFUSION_READY : Status.INFUSION_UNFORMED;
	}

	public record Reading(String status, Map<String, Integer> remaining, int absorbed, int total) {}

	private enum Status {
		INFUSION_UNFORMED, INFUSION_STRUCTURE, INFUSION_READY, INFUSION_WAND, INFUSION_RESEARCH, INFUSION_RECIPE,
		INFUSION_ESSENTIA, INFUSION_RUNNING, INFUSION_PAUSED, INFUSION_INPUTS_CHANGED, INFUSION_CANCELLED,
		INFUSION_ACCIDENT, INFUSION_COMPLETE;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private record Run(UUID owner, Map<Integer, UUID> revisions, TreeMap<String, Integer> remaining,
					   ItemStack result, int total, int instability) {
		CompoundTag save(HolderLookup.Provider registries) {
			var tag = new CompoundTag();
			tag.putUUID("owner", owner);
			tag.put("result", result.saveOptional(registries));
			tag.putInt("total", total);
			tag.putInt("instability", instability);
			var inputs = new CompoundTag();
			revisions.forEach((index, revision) -> inputs.putUUID(Integer.toString(index), revision));
			tag.put("inputs", inputs);
			var needed = new CompoundTag();
			remaining.forEach(needed::putInt);
			tag.put("remaining", needed);
			return tag;
		}

		static Run load(CompoundTag tag, HolderLookup.Provider registries) {
			var result = ItemStack.parseOptional(registries, tag.getCompound("result"));
			int total = tag.getInt("total");
			int instability = tag.getInt("instability");
			if (!tag.hasUUID("owner") || result.isEmpty() || total < 1 || total > InfusionRecipe.MAX_ESSENTIA
					|| instability < 0 || instability > 12 || !tag.contains("remaining", Tag.TAG_COMPOUND)) {
				return null;
			}
			var inputs = tag.getCompound("inputs");
			var revisions = new TreeMap<Integer, UUID>();
			for (int index = 0; index <= Infusion.RING.size(); index++) {
				String key = Integer.toString(index);
				if (inputs.hasUUID(key)) {
					revisions.put(index, inputs.getUUID(key));
				}
			}
			if (!revisions.containsKey(0) || revisions.size() < 2) {
				return null;
			}
			var remaining = new TreeMap<String, Integer>();
			var needed = tag.getCompound("remaining");
			int count = 0;
			for (String id : needed.getAllKeys()) {
				int units = needed.getInt(id);
				if (!id.equals(AspectCatalog.normalizeId(id)) || units < 1 || units > total - count) {
					return null;
				}
				remaining.put(id, units);
				count += units;
			}
			return new Run(tag.getUUID("owner"), revisions, remaining, result, total, instability);
		}
	}
}
