package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.advanced.AdvancedAlchemy.Status;
import dev.thaumiclegacy.alchemy.Alchemy;
import dev.thaumiclegacy.alchemy.AlchemyRecipe;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.logistics.EssentiaPhial;
import dev.thaumiclegacy.research.ResearchProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ThaumatoriumBlockEntity extends AdvancedMachineEntity {
	public static final int CAPACITY = 64;
	public static final int DURATION = 120;

	private final EssentiaBuffer essentia = new EssentiaBuffer(CAPACITY);
	private ResourceLocation recipeId;
	private UUID owner;
	private ItemStack catalyst = ItemStack.EMPTY;
	private ItemStack result = ItemStack.EMPTY;
	private ItemStack remainder = ItemStack.EMPTY;
	private int progress;
	private AlchemyRecipe lastDefinition;

	ThaumatoriumBlockEntity(BlockPos pos, BlockState state) {
		super(AdvancedAlchemy.THAUMATORIUM_ENTITY, pos, state);
	}

	public ThaumatoriumView snapshot() {
		AlchemyRecipe recipe = level instanceof ServerLevel server ? recipe(server, recipeId) : null;
		Status status = level instanceof ServerLevel server ? plan(server, recipe).status() : Status.NO_RECIPE;
		return new ThaumatoriumView(Optional.ofNullable(recipeId), Optional.ofNullable(owner), essentia.snapshot(),
				recipe == null ? Map.of() : recipe.aspects(), CAPACITY, catalyst.copy(), result.copy(), remainder.copy(),
				recipe == null ? ItemStack.EMPTY : recipe.result(), progress, DURATION, status);
	}

	static void tick(Level level, BlockPos pos, BlockState state, ThaumatoriumBlockEntity machine) {
		if (level instanceof ServerLevel server) {
			machine.tick(server);
		}
	}

	private void tick(ServerLevel server) {
		var recipe = recipe(server, recipeId);
		if (lastDefinition != null && lastDefinition != recipe && progress != 0) {
			progress = 0;
			setChanged();
		}
		lastDefinition = recipe;
		Plan plan = plan(server, recipe);
		boolean working = plan.status() == Status.WORKING;
		if (working) {
			if (++progress >= DURATION) {
				recipe.aspects().forEach(essentia::remove);
				result = combine(result, plan.product());
				catalyst = plan.reusable() ? plan.returned() : ItemStack.EMPTY;
				if (!plan.reusable()) {
					remainder = combine(remainder, plan.returned());
				}
				progress = 0;
			}
			setChanged();
		}
		showWorking(server, working);
	}

	private Plan plan(ServerLevel server, AlchemyRecipe recipe) {
		if (recipeId == null) {
			return Plan.blocked(Status.NO_RECIPE);
		}
		if (!supported(recipe)) {
			return Plan.blocked(Status.INVALID_RECIPE);
		}
		ServerPlayer operator = activeOwner(server);
		if (operator == null) {
			return Plan.blocked(Status.OWNER_UNAVAILABLE);
		}
		if (!eligible(operator, recipe)) {
			return Plan.blocked(Status.RESEARCH_REQUIRED);
		}
		if (catalyst.isEmpty()) {
			return Plan.blocked(Status.MISSING_CATALYST);
		}
		var input = new AlchemyRecipe.CatalystInput(catalyst);
		if (!recipe.matches(input, server)) {
			return Plan.blocked(Status.CATALYST_MISMATCH);
		}
		for (var requirement : recipe.aspects().entrySet()) {
			if (essentia.amount(requirement.getKey()) < requirement.getValue()) {
				return Plan.blocked(Status.MISSING_ESSENTIA);
			}
		}
		ItemStack product = recipe.assemble(input, server.registryAccess());
		if (product.isEmpty() || !fits(result, product)) {
			return Plan.blocked(Status.OUTPUT_FULL);
		}
		ItemStack returned = catalyst.copy().getRecipeRemainder();
		boolean reusable = !returned.isEmpty() && returned.getCount() == 1 && recipe.catalyst().test(returned);
		return !reusable && !fits(remainder, returned) ? Plan.blocked(Status.REMAINDER_FULL)
				: new Plan(Status.WORKING, product, returned, reusable);
	}

	@Override
	public boolean canReceive(Direction face, String aspect) {
		if (!inputPort(face) || !(level instanceof ServerLevel server) || essentia.room() == 0) {
			return false;
		}
		var recipe = recipe(server, recipeId);
		var operator = activeOwner(server);
		return operator != null && missing(recipe, aspect) > 0 && eligible(operator, recipe);
	}

	private ServerPlayer activeOwner(ServerLevel server) {
		ServerPlayer player = owner == null ? null : server.getServer().getPlayerList().getPlayer(owner);
		return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator() ? player : null;
	}

	@Override
	public boolean receiveOne(Direction face, String aspect, BooleanSupplier debit) {
		if (attachedServer() == null || !canReceive(face, aspect) || !debit.getAsBoolean()) {
			return false;
		}
		essentia.add(aspect, 1);
		setChanged();
		return true;
	}

	@Override
	boolean interact(ServerPlayer player, InteractionHand hand) {
		if (!canUse(player)) {
			return false;
		}
		var held = player.getItemInHand(hand);
		var server = player.serverLevel();
		var recipe = recipe(server, recipeId);
		if (isPhial(held)) {
			var contents = EssentiaPhial.contents(held);
			if (contents.isEmpty()) {
				return extractPhial(player, held, essentia);
			}
			var operator = activeOwner(server);
			if (operator == null || !supported(recipe) || !eligible(operator, recipe)) {
				return AdvancedAlchemy.feedback(player, false, "research_required");
			}
			int remaining = missing(recipe, contents.aspect());
			return remaining <= 0 ? AdvancedAlchemy.feedback(player, false, "phial_incompatible")
					: depositPhial(player, held, essentia, remaining);
		}
		if (held.isEmpty()) {
			return useEmptyHand(player, hand, server);
		}
		if (!catalyst.isEmpty()) {
			return AdvancedAlchemy.feedback(player, false, "catalyst_occupied");
		}
		if (!supported(recipe) || !recipe.catalyst().test(held)) {
			return AdvancedAlchemy.feedback(player, false, "invalid_catalyst");
		}
		catalyst = held.split(1);
		if (held.isEmpty()) {
			player.setItemInHand(hand, ItemStack.EMPTY);
		}
		player.getInventory().setChanged();
		setChanged();
		return AdvancedAlchemy.feedback(player, true, "catalyst_inserted", catalyst.getHoverName());
	}

	private boolean useEmptyHand(ServerPlayer player, InteractionHand hand, ServerLevel server) {
		if (player.isShiftKeyDown()) {
			if (!catalyst.isEmpty()) {
				ItemStack recovered = catalyst;
				catalyst = ItemStack.EMPTY;
				progress = 0;
				return recover(player, hand, recovered);
			}
			if (recipeId == null) {
				return AdvancedAlchemy.feedback(player, false, "nothing_to_recover");
			}
			if (!ResearchProgress.isUnlocked(player, ResearchProgress.AUTOMATED_ALCHEMY)) {
				return AdvancedAlchemy.feedback(player, false, "research_required");
			}
			recipeId = null;
			owner = null;
			progress = 0;
			lastDefinition = null;
			setChanged();
			return AdvancedAlchemy.feedback(player, true, "recipe_cleared");
		}
		if (!result.isEmpty()) {
			ItemStack recovered = result;
			result = ItemStack.EMPTY;
			return recover(player, hand, recovered);
		}
		if (!remainder.isEmpty()) {
			ItemStack recovered = remainder;
			remainder = ItemStack.EMPTY;
			return recover(player, hand, recovered);
		}
		if (!ResearchProgress.isUnlocked(player, ResearchProgress.AUTOMATED_ALCHEMY)) {
			return AdvancedAlchemy.feedback(player, false, "research_required");
		}
		var available = server.getRecipeManager().getAllRecipesFor(Alchemy.RECIPE_TYPE).stream()
				.filter(entry -> supported(entry.value()) && recipeUnlocked(player, entry.value()))
				.sorted(Comparator.comparing(RecipeHolder::id)).toList();
		if (available.isEmpty()) {
			return AdvancedAlchemy.feedback(player, false, "no_recipes");
		}
		var choice = available.stream().filter(entry -> recipeId == null || entry.id().compareTo(recipeId) > 0)
				.findFirst().orElse(available.getFirst());
		boolean changed = select(player, choice.id());
		return AdvancedAlchemy.feedback(player, changed, "recipe_selected", choice.value().result().getHoverName());
	}

	boolean select(ServerPlayer player, ResourceLocation id) {
		if (!canUse(player)) {
			return false;
		}
		var recipe = recipe(player.serverLevel(), id);
		if (!supported(recipe) || !eligible(player, recipe)) {
			return false;
		}
		if (id.equals(recipeId) && player.getUUID().equals(owner)) {
			return false;
		}
		recipeId = id;
		owner = player.getUUID();
		progress = 0;
		lastDefinition = recipe;
		setChanged();
		return true;
	}

	private boolean recover(ServerPlayer player, InteractionHand hand, ItemStack stack) {
		player.setItemInHand(hand, stack);
		player.getInventory().setChanged();
		setChanged();
		return AdvancedAlchemy.feedback(player, true, "recovered", stack.getHoverName());
	}

	private int missing(AlchemyRecipe recipe, String aspect) {
		return supported(recipe) ? recipe.aspects().getOrDefault(aspect, 0) - essentia.amount(aspect) : 0;
	}

	private static AlchemyRecipe recipe(ServerLevel server, ResourceLocation id) {
		return id == null ? null : server.getRecipeManager().byKey(id).map(RecipeHolder::value)
				.filter(AlchemyRecipe.class::isInstance).map(AlchemyRecipe.class::cast).orElse(null);
	}

	private static boolean supported(AlchemyRecipe recipe) {
		if (recipe == null || recipe.aspects().isEmpty()) {
			return false;
		}
		var catalog = AspectCatalog.current();
		long total = 0;
		for (var requirement : recipe.aspects().entrySet()) {
			if (!catalog.definitions().containsKey(requirement.getKey()) || requirement.getValue() <= 0) {
				return false;
			}
			total += requirement.getValue();
		}
		return total <= CAPACITY;
	}

	private static boolean eligible(ServerPlayer player, AlchemyRecipe recipe) {
		return ResearchProgress.isUnlocked(player, ResearchProgress.AUTOMATED_ALCHEMY) && recipeUnlocked(player, recipe);
	}

	private static boolean recipeUnlocked(ServerPlayer player, AlchemyRecipe recipe) {
		return recipe.research().isEmpty() || ResearchProgress.isUnlocked(player, recipe.research().get());
	}

	private static boolean fits(ItemStack stored, ItemStack incoming) {
		return incoming.isEmpty() || stored.isEmpty() && incoming.getCount() <= incoming.getMaxStackSize()
				|| ItemStack.isSameItemSameComponents(stored, incoming)
				&& stored.getCount() + incoming.getCount() <= stored.getMaxStackSize();
	}

	private static ItemStack combine(ItemStack stored, ItemStack incoming) {
		if (stored.isEmpty()) {
			return incoming;
		}
		stored.grow(incoming.getCount());
		return stored;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		recipeId = ResourceLocation.tryParse(tag.getString("recipe"));
		if (recipeId != null && recipeId.getPath().isEmpty()) {
			recipeId = null;
		}
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		essentia.load(tag.getCompound("essentia"));
		catalyst = ItemStack.parseOptional(registries, tag.getCompound("catalyst"));
		catalyst.limitSize(1);
		result = ItemStack.parseOptional(registries, tag.getCompound("result"));
		remainder = ItemStack.parseOptional(registries, tag.getCompound("remainder"));
		progress = recipeId == null || catalyst.isEmpty() ? 0 : Mth.clamp(tag.getInt("progress"), 0, DURATION - 1);
		lastDefinition = null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 1);
		if (recipeId != null) {
			tag.putString("recipe", recipeId.toString());
		}
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
		tag.put("essentia", essentia.save());
		tag.put("catalyst", catalyst.saveOptional(registries));
		tag.put("result", result.saveOptional(registries));
		tag.put("remainder", remainder.saveOptional(registries));
		tag.putInt("progress", progress);
	}

	private record Plan(Status status, ItemStack product, ItemStack returned, boolean reusable) {
		private static Plan blocked(Status status) {
			return new Plan(status, ItemStack.EMPTY, ItemStack.EMPTY, false);
		}
	}

	public record ThaumatoriumView(Optional<ResourceLocation> recipe, Optional<UUID> owner, Map<String, Integer> essentia,
								   Map<String, Integer> requirements, int capacity, ItemStack catalyst, ItemStack result,
								   ItemStack remainder, ItemStack preview, int progress, int duration, Status status) { }
}
