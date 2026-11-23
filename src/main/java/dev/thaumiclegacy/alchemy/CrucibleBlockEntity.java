package dev.thaumiclegacy.alchemy;

import dev.thaumiclegacy.alchemy.CrucibleBlock.Phase;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.ResearchProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public final class CrucibleBlockEntity extends BlockEntity {
	private static final String MESSAGE = "message.thaumiclegacy.crucible.";
	private static final int VERSION = 1;
	private static final int COOLDOWN_TICKS = 4;
	private final Map<String, Integer> aspects = new TreeMap<>();
	private int heatTicks;
	private long lastChangeTick = Long.MIN_VALUE;

	CrucibleBlockEntity(BlockPos pos, BlockState state) {
		super(Alchemy.CRUCIBLE_ENTITY, pos, state);
	}

	public Snapshot snapshot() {
		return new Snapshot(getBlockState().getValue(CrucibleBlock.PHASE), heatTicks,
				AlchemyConfig.current().capacity(), Map.copyOf(aspects));
	}

	static void tick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
		if (level instanceof ServerLevel server && state.getValue(CrucibleBlock.PHASE) != Phase.EMPTY) {
			crucible.refreshHeat(server, state.getValue(CrucibleBlock.PHASE), true);
		}
	}

	boolean interact(ServerPlayer player, InteractionHand hand) {
		ServerLevel server = player.serverLevel();
		var pos = getBlockPos();
		if (isRemoved() || getLevel() != server || player.isSpectator() || !player.isAlive() || !server.hasChunkAt(pos)
				|| !player.canInteractWithBlock(pos, 0) || !server.mayInteract(player, pos)
				|| server.getBlockEntity(pos) != this || !server.getBlockState(pos).is(Alchemy.CRUCIBLE)) {
			return false;
		}
		Phase phase = refreshHeat(server, getBlockState().getValue(CrucibleBlock.PHASE), false);
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			describe(player, phase);
			return false;
		}

		long now = server.getGameTime();
		if (lastChangeTick != Long.MIN_VALUE && now >= lastChangeTick && now - lastChangeTick < COOLDOWN_TICKS) {
			return false;
		}
		boolean changed;
		if (held.is(Items.WATER_BUCKET)) {
			changed = fill(player, hand, phase);
		} else if (held.is(Items.BUCKET)) {
			changed = player.isShiftKeyDown() ? purge(player, hand, phase) : reject(player, "purge_requires_sneak");
		} else if (phase == Phase.EMPTY) {
			changed = reject(player, "needs_water");
		} else if (phase != Phase.READY) {
			changed = reject(player, "not_ready");
		} else {
			changed = player.isShiftKeyDown() ? craft(player, hand, held) : dissolve(player, hand, held);
		}
		if (changed) {
			lastChangeTick = now;
			setChanged();
		}
		return changed;
	}

	private Phase refreshHeat(ServerLevel server, Phase phase, boolean advance) {
		if (phase == Phase.EMPTY) {
			return phase;
		}
		AlchemyConfig config = AlchemyConfig.current();
		// The source is always within this same, attached chunk.
		boolean heated = config.providesHeat(server.getBlockState(worldPosition.below()));
		int nextHeat = heated ? Math.min(config.heatTicks(), heatTicks + (advance ? 1 : 0)) : 0;
		if (nextHeat != heatTicks) {
			heatTicks = nextHeat;
			setChanged();
		}
		Phase next = !heated ? Phase.FILLED : heatTicks < config.heatTicks() ? Phase.HEATING : Phase.READY;
		if (next != phase) {
			setPhase(server, next);
			if (next == Phase.READY) {
				effect(server, ParticleTypes.BUBBLE_POP, SoundEvents.BREWING_STAND_BREW);
			}
		}
		return next;
	}

	private void setPhase(ServerLevel server, Phase phase) {
		BlockState state = getBlockState();
		if (state.getValue(CrucibleBlock.PHASE) != phase) {
			server.setBlock(worldPosition, state.setValue(CrucibleBlock.PHASE, phase), Block.UPDATE_CLIENTS);
		}
	}

	private boolean fill(ServerPlayer player, InteractionHand hand, Phase phase) {
		if (phase != Phase.EMPTY) {
			return reject(player, "already_filled");
		}
		AlchemyInventory inventory = AlchemyInventory.plan(player, hand, Items.BUCKET.getDefaultInstance());
		if (inventory == null) {
			return reject(player, "inventory_full");
		}
		inventory.apply(player);
		heatTicks = 0;
		ServerLevel server = player.serverLevel();
		setPhase(server, Phase.FILLED);
		refreshHeat(server, Phase.FILLED, false);
		effect(server, ParticleTypes.SPLASH, SoundEvents.BUCKET_EMPTY);
		feedback(player, "filled");
		return true;
	}

	private boolean purge(ServerPlayer player, InteractionHand hand, Phase phase) {
		if (phase == Phase.EMPTY) {
			return reject(player, "needs_water");
		}
		AlchemyInventory inventory = AlchemyInventory.plan(player, hand, Items.WATER_BUCKET.getDefaultInstance());
		if (inventory == null) {
			return reject(player, "inventory_full");
		}
		inventory.apply(player);
		ServerLevel server = player.serverLevel();
		int flux = releaseResidue(server);
		heatTicks = 0;
		setPhase(server, Phase.EMPTY);
		effect(server, ParticleTypes.SMOKE, SoundEvents.BUCKET_FILL);
		feedback(player, "purged", flux);
		return true;
	}

	int releaseResidue(ServerLevel server) {
		int total = totalAspects();
		if (total == 0) {
			return 0;
		}
		aspects.clear();
		setChanged();
		int unitsPerFlux = AlchemyConfig.current().fluxUnitsPerPoint();
		return AuraService.addFlux(server, new ChunkPos(worldPosition), (total + unitsPerFlux - 1) / unitsPerFlux);
	}

	private boolean dissolve(ServerPlayer player, InteractionHand hand, ItemStack held) {
		var catalog = AspectCatalog.current();
		var profile = catalog.aspects("item", BuiltInRegistries.ITEM.getKey(held.getItem()));
		if (profile.isEmpty() && held.getItem() instanceof BlockItem blockItem) {
			profile = catalog.aspects("block", BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
		}
		if (profile.isEmpty()) {
			return reject(player, "no_profile");
		}
		long added = profile.values().stream().mapToLong(Integer::longValue).sum();
		if (added > AlchemyConfig.current().capacity() - totalAspects()) {
			return reject(player, "full");
		}
		AlchemyInventory inventory = AlchemyInventory.plan(player, hand, AlchemyInventory.remainder(held));
		if (inventory == null) {
			return reject(player, "inventory_full");
		}
		Component name = held.getHoverName();
		inventory.apply(player);
		profile.forEach((id, amount) -> aspects.merge(id, amount, Integer::sum));
		effect(player.serverLevel(), ParticleTypes.BUBBLE_POP, SoundEvents.BREWING_STAND_BREW);
		feedback(player, "dissolved", name, (int) added);
		return true;
	}

	private boolean craft(ServerPlayer player, InteractionHand hand, ItemStack held) {
		ServerLevel server = player.serverLevel();
		var input = new AlchemyRecipe.CatalystInput(held);
		var matches = server.getRecipeManager().getRecipesFor(Alchemy.RECIPE_TYPE, input, server);
		if (matches.isEmpty()) {
			return reject(player, "no_recipe");
		}
		var catalog = AspectCatalog.current();
		var authorized = matches.stream().sorted(Comparator.comparing(RecipeHolder::id))
				.map(RecipeHolder::value)
				.filter(recipe -> recipe.research().isEmpty() || ResearchProgress.isUnlocked(player, recipe.research().get()))
				.toList();
		if (authorized.isEmpty()) {
			return reject(player, "research_required");
		}
		var recipe = authorized.stream().filter(candidate -> candidate.hasAspects(aspects, catalog)).findFirst().orElse(null);
		if (recipe == null) {
			return reject(player, "missing_aspects");
		}
		ItemStack result = recipe.assemble(input, server.registryAccess());
		AlchemyInventory inventory = AlchemyInventory.plan(player, hand, result, AlchemyInventory.remainder(held));
		if (inventory == null) {
			return reject(player, "inventory_full");
		}
		inventory.apply(player);
		recipe.aspects().forEach((id, cost) -> aspects.compute(id, (key, stored) -> stored == cost.intValue() ? null : stored - cost));
		effect(server, ParticleTypes.EFFECT, SoundEvents.BREWING_STAND_BREW);
		feedback(player, "crafted", result.getHoverName(), result.getCount());
		return true;
	}

	private int totalAspects() {
		return aspects.values().stream().mapToInt(Integer::intValue).sum();
	}

	private void describe(ServerPlayer player, Phase phase) {
		AlchemyConfig config = AlchemyConfig.current();
		player.sendSystemMessage(Component.translatable(MESSAGE + "status",
				Component.translatable("state.thaumiclegacy.crucible." + phase.getSerializedName()),
				heatTicks, config.heatTicks(), totalAspects(), config.capacity()));
		var line = Component.empty();
		int columns = 0;
		for (var entry : aspects.entrySet()) {
			String id = entry.getKey();
			var name = Component.translatableWithFallback(ResourceLocation.parse(id).toLanguageKey("aspect"), id);
			if (columns > 0) {
				line.append(" · ");
			}
			line.append(Component.translatable(MESSAGE + "aspect", name, entry.getValue()));
			if (++columns == 4) {
				player.sendSystemMessage(line);
				line = Component.empty();
				columns = 0;
			}
		}
		if (columns > 0) {
			player.sendSystemMessage(line);
		}
	}

	private static boolean reject(ServerPlayer player, String reason) {
		feedback(player, reason);
		return false;
	}

	private static void feedback(ServerPlayer player, String action, Object... arguments) {
		player.displayClientMessage(Component.translatable(MESSAGE + action, arguments), true);
	}

	private void effect(ServerLevel server, ParticleOptions particle, SoundEvent sound) {
		server.sendParticles(particle, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
				worldPosition.getZ() + 0.5, 8, 0.23, 0.09, 0.23, 0.02);
		server.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.55F, 1.0F);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		heatTicks = Mth.clamp(tag.getInt("heat_ticks"), 0, AlchemyConfig.MAX_HEAT_TICKS);
		aspects.clear();
		CompoundTag stored = tag.getCompound("aspects");
		int remaining = AlchemyConfig.MAX_CAPACITY;
		for (String id : stored.getAllKeys().stream().sorted().toList()) {
			if (remaining == 0) {
				break;
			}
			if (id.equals(AspectCatalog.normalizeId(id)) && stored.contains(id, Tag.TAG_INT)) {
				int amount = Mth.clamp(stored.getInt(id), 0, remaining);
				if (amount > 0) {
					aspects.put(id, amount);
					remaining -= amount;
				}
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", VERSION);
		tag.putInt("heat_ticks", heatTicks);
		CompoundTag stored = new CompoundTag();
		aspects.forEach(stored::putInt);
		tag.put("aspects", stored);
	}

	public record Snapshot(Phase phase, int heatTicks, int capacity, Map<String, Integer> aspects) {
		public int amount() {
			return aspects.values().stream().mapToInt(Integer::intValue).sum();
		}
	}
}
