package dev.thaumiclegacy.workbench;

import dev.thaumiclegacy.aura.AuraService;
import dev.thaumiclegacy.research.ResearchProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.atomic.AtomicLong;

public final class ArcaneWorkbenchMenu extends AbstractContainerMenu {
	public enum Status { NO_RECIPE, RESEARCH_REQUIRED, INSUFFICIENT_VIS, OUTPUT_FULL, READY }

	private static final int GRID_WIDTH = ArcaneWorkbenchBlockEntity.GRID_WIDTH;
	private static final int PREVIEW_SLOT = ArcaneWorkbenchBlockEntity.GRID_SIZE;
	private static final int PLAYER_SLOTS = PREVIEW_SLOT + 1;
	private static final int STATUS_DATA = 0;
	private static final int COST_DATA = 1;
	private static final int VIS_DATA = 2;
	private static final int TOKEN_DATA = 3;
	private static final int TOKEN_WORDS = 4;
	private static final int REFRESH_INTERVAL = 5;
	private static final Status[] STATUSES = Status.values();
	private static final AtomicLong TOKENS = new AtomicLong();

	private final ArcaneWorkbenchBlockEntity station;
	private final Inventory inventory;
	private final SimpleContainer grid;
	private final SimpleContainer preview = new SimpleContainer(1);
	private final SimpleContainerData data = new SimpleContainerData(TOKEN_DATA + TOKEN_WORDS);
	private RecipeHolder<ArcaneWorkbenchRecipe> selection;
	private long observedGridRevision = Long.MIN_VALUE;
	private int observedInventoryRevision;
	private long lastRefresh = Long.MIN_VALUE;

	public ArcaneWorkbenchMenu(int id, Inventory inventory) {
		this(id, inventory, null);
	}

	ArcaneWorkbenchMenu(int id, Inventory inventory, ArcaneWorkbenchBlockEntity station) {
		super(ArcaneWorkbench.MENU, id);
		this.station = station;
		this.inventory = inventory;
		this.grid = station == null ? new SimpleContainer(ArcaneWorkbenchBlockEntity.GRID_SIZE) : station.grid();
		for (int y = 0; y < GRID_WIDTH; y++) {
			for (int x = 0; x < GRID_WIDTH; x++) {
				addSlot(new Slot(grid, y * GRID_WIDTH + x, 30 + x * 18, 17 + y * 18));
			}
		}
		addSlot(new Slot(preview, 0, 124, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player player) {
				return false;
			}

			@Override
			public boolean isFake() {
				return true;
			}
		});
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 9; x++) {
				addSlot(new Slot(inventory, x + y * 9 + 9, 8 + x * 18, 103 + y * 18));
			}
		}
		for (int x = 0; x < 9; x++) {
			addSlot(new Slot(inventory, x, 8 + x * 18, 161));
		}
		addDataSlots(data);
		if (station != null && inventory.player instanceof ServerPlayer player) {
			refresh(player);
		}
	}

	public Status status() {
		return STATUSES[data.get(STATUS_DATA)];
	}

	public int visCost() {
		return data.get(COST_DATA);
	}

	public int availableVis() {
		return data.get(VIS_DATA);
	}

	public ArcaneWorkbenchCraftPayload craftRequest() {
		return new ArcaneWorkbenchCraftPayload(containerId, attemptToken());
	}

	long attemptToken() {
		long token = 0;
		for (int i = 0; i < TOKEN_WORDS; i++) {
			// Vanilla synchronizes each menu data field as a signed 16-bit value.
			token |= (data.get(TOKEN_DATA + i) & 0xFFFFL) << (i * 16);
		}
		return token;
	}

	private void rotateToken() {
		long token = TOKENS.incrementAndGet();
		for (int i = 0; i < TOKEN_WORDS; i++) {
			data.set(TOKEN_DATA + i, (short) (token >>> (i * 16)));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		if (station == null) {
			return true;
		}
		var level = player.level();
		var pos = station.getBlockPos();
		return !station.isRemoved() && station.getLevel() == level
				&& level.hasChunkAt(pos) && level.getBlockEntity(pos) == station
				&& level.getBlockState(pos).is(ArcaneWorkbench.BLOCK) && player.canInteractWithBlock(pos, 0);
	}

	@Override
	public void broadcastChanges() {
		if (station != null && inventory.player instanceof ServerPlayer player && stillValid(player)) {
			long tick = player.serverLevel().getGameTime();
			if (station.revision() != observedGridRevision || inventory.getTimesChanged() != observedInventoryRevision
					|| tick < lastRefresh || tick - lastRefresh >= REFRESH_INTERVAL) {
				refresh(player);
			}
		}
		super.broadcastChanges();
	}

	private ArcaneCraftPlan refresh(ServerPlayer player) {
		var level = player.serverLevel();
		var positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, grid.getItems());
		var chosen = level.getRecipeManager().getRecipeFor(ArcaneWorkbench.RECIPE_TYPE, positioned.input(), level)
				.orElse(null);
		long revision = station.revision();
		// Recipe holders are stable within a reload and replaced with new definitions on reload.
		if (revision != observedGridRevision || chosen != selection) {
			rotateToken();
		}
		observedGridRevision = revision;
		observedInventoryRevision = inventory.getTimesChanged();
		lastRefresh = level.getGameTime();
		selection = chosen;

		int vis = AuraService.read(level, new ChunkPos(station.getBlockPos())).vis();
		data.set(VIS_DATA, vis);
		if (chosen == null) {
			preview.setItem(0, ItemStack.EMPTY);
			data.set(COST_DATA, 0);
			data.set(STATUS_DATA, Status.NO_RECIPE.ordinal());
			return null;
		}
		var recipe = chosen.value();
		ItemStack output = recipe.assemble(positioned.input(), level.registryAccess());
		preview.setItem(0, output);
		data.set(COST_DATA, recipe.visCost());
		if (recipe.research().isPresent() && !ResearchProgress.isUnlocked(player, recipe.research().get())) {
			data.set(STATUS_DATA, Status.RESEARCH_REQUIRED.ordinal());
			return null;
		}
		if (vis < recipe.visCost()) {
			data.set(STATUS_DATA, Status.INSUFFICIENT_VIS.ordinal());
			return null;
		}
		ArcaneCraftPlan plan = ArcaneCraftPlan.prepare(grid, inventory, positioned, recipe, output);
		data.set(STATUS_DATA, (plan == null ? Status.OUTPUT_FULL : Status.READY).ordinal());
		return plan;
	}

	boolean craft(ServerPlayer player, long token) {
		if (station == null || player != inventory.player || player.containerMenu != this
				|| player.isSpectator() || !player.isAlive() || !stillValid(player)
				|| token == 0 || token != attemptToken()) {
			return false;
		}

		ArcaneCraftPlan plan = refresh(player);
		if (token != attemptToken()) {
			super.broadcastChanges();
			return false;
		}
		rotateToken();
		boolean crafted = plan != null && AuraService.trySpendVis(
				player.serverLevel(), new ChunkPos(station.getBlockPos()), visCost());
		if (crafted) {
			plan.apply(grid, inventory);
			refresh(player);
		}
		super.broadcastChanges();
		return crafted;
	}

	@Override
	public void clicked(int slot, int button, ClickType type, Player player) {
		if (slot != PREVIEW_SLOT && (station == null || stillValid(player))) {
			super.clicked(slot, button, type, player);
		}
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return slot.container != preview;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
		return slot.container != preview;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (index < 0 || index >= slots.size() || index == PREVIEW_SLOT) {
			return ItemStack.EMPTY;
		}
		Slot slot = slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean moved = index < PREVIEW_SLOT
				? moveItemStackTo(stack, PLAYER_SLOTS, slots.size(), true)
				: moveItemStackTo(stack, 0, PREVIEW_SLOT, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, stack);
		return original;
	}
}
