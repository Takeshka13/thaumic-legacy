package dev.thaumiclegacy.advanced;

import dev.thaumiclegacy.advanced.AdvancedAlchemy.Status;
import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.aspect.AspectDefinition;
import dev.thaumiclegacy.logistics.EssentiaPhial;
import dev.thaumiclegacy.transport.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.function.BooleanSupplier;

public final class AlchemicalCentrifugeBlockEntity extends AdvancedMachineEntity {
	public static final int DURATION = 80;
	public static final int INPUT_CAPACITY = 32;
	public static final int OUTPUT_CAPACITY = 64;
	private final EssentiaBuffer input = new EssentiaBuffer(INPUT_CAPACITY);
	private final EssentiaBuffer output = new EssentiaBuffer(OUTPUT_CAPACITY);
	private String activeAspect = "";
	private int progress;

	AlchemicalCentrifugeBlockEntity(BlockPos pos, BlockState state) {
		super(AdvancedAlchemy.CENTRIFUGE_ENTITY, pos, state);
	}

	public CentrifugeView snapshot() {
		return new CentrifugeView(input.snapshot(), output.snapshot(), INPUT_CAPACITY, OUTPUT_CAPACITY,
				activeAspect, progress, DURATION, status());
	}

	static void tick(Level level, BlockPos pos, BlockState state, AlchemicalCentrifugeBlockEntity centrifuge) {
		if (level instanceof ServerLevel server) {
			centrifuge.tick(server);
		}
	}

	private void tick(ServerLevel server) {
		if (input.amount(activeAspect) == 0 || compound(activeAspect) == null) {
			String next = nextCompound();
			if (!activeAspect.equals(next) || progress != 0) {
				activeAspect = next;
				progress = 0;
				setChanged();
			}
		}
		boolean working = status() == Status.WORKING;
		if (working) {
			if (++progress >= DURATION) {
				var parents = compound(activeAspect).parents();
				input.remove(activeAspect, 1);
				parents.forEach(parent -> output.add(parent, 1));
				activeAspect = "";
				progress = 0;
			}
			setChanged();
		}
		showWorking(server, working);
		if (!output.isEmpty()) {
			EssentiaTransport.tickSource(server, worldPosition);
		}
	}

	@Override
	public boolean canReceive(Direction face, String aspect) {
		return inputPort(face) && input.room() > 0 && compound(aspect) != null;
	}

	@Override
	public boolean receiveOne(Direction face, String aspect, BooleanSupplier debit) {
		if (attachedServer() == null || !canReceive(face, aspect) || !debit.getAsBoolean()) {
			return false;
		}
		input.add(aspect, 1);
		setChanged();
		return true;
	}

	@Override
	public Map<String, Integer> transportContents(Direction face) {
		return outputPort(face) ? output.snapshot() : Map.of();
	}

	@Override
	public boolean extractOne(Direction face, String aspect) {
		if (attachedServer() == null || !outputPort(face) || output.amount(aspect) == 0) {
			return false;
		}
		output.remove(aspect, 1);
		setChanged();
		return true;
	}

	@Override
	boolean interact(ServerPlayer player, InteractionHand hand) {
		if (!canUse(player)) {
			return false;
		}
		var held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return AdvancedAlchemy.feedback(player, false, "centrifuge_status",
					Component.translatable(status().translationKey()), progress, DURATION);
		}
		if (!isPhial(held)) {
			return AdvancedAlchemy.feedback(player, false, "use_phial");
		}
		var contents = EssentiaPhial.contents(held);
		if (contents.isEmpty()) {
			return extractPhial(player, held, player.isShiftKeyDown() ? input : output);
		}
		if (compound(contents.aspect()) == null) {
			return AdvancedAlchemy.feedback(player, false, "phial_incompatible");
		}
		return depositPhial(player, held, input, input.room());
	}

	private Status status() {
		if (input.isEmpty()) {
			return Status.IDLE;
		}
		if (nextCompound().isEmpty()) {
			return Status.INVALID_ESSENTIA;
		}
		return output.room() < 2 ? Status.OUTPUT_FULL : Status.WORKING;
	}

	private String nextCompound() {
		return input.firstMatching(id -> compound(id) != null);
	}

	private static AspectDefinition compound(String aspect) {
		var definitions = AspectCatalog.current().definitions();
		var definition = definitions.get(aspect);
		return definition != null && definition.parents().size() == 2 && definition.parents().stream().allMatch(definitions::containsKey)
				? definition : null;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		input.load(tag.getCompound("input"));
		output.load(tag.getCompound("output"));
		String active = tag.getString("active_aspect");
		activeAspect = input.amount(active) > 0 ? active : "";
		progress = activeAspect.isEmpty() ? 0 : Mth.clamp(tag.getInt("progress"), 0, DURATION - 1);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("version", 1);
		tag.put("input", input.save());
		tag.put("output", output.save());
		tag.putString("active_aspect", activeAspect);
		tag.putInt("progress", progress);
	}

	public record CentrifugeView(Map<String, Integer> input, Map<String, Integer> output, int inputCapacity,
								 int outputCapacity, String activeAspect, int progress, int duration, Status status) { }
}
