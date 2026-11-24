package dev.thaumiclegacy.advanced;

import net.minecraft.core.Direction;

import java.util.Map;
import java.util.function.BooleanSupplier;

/** Internal directional contract for server-side transport. */
public interface AdvancedEssentiaPort {
	boolean inputPort(Direction face);

	boolean outputPort(Direction face);

	Map<String, Integer> transportContents(Direction face);

	boolean canReceive(Direction face, String aspect);

	/** Calls debit once, after checking the sink can commit one matching unit. */
	boolean receiveOne(Direction face, String aspect, BooleanSupplier debit);

	boolean extractOne(Direction face, String aspect);
}
