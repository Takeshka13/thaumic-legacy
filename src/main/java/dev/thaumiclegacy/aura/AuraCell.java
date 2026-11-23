package dev.thaumiclegacy.aura;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/** A chunk ledger at a specific world tick, including partial recovery and decay. */
record AuraCell(int vis, int flux, long tick, int visCredit, int fluxTicks) {
	static final int VIS_CAPACITY = 100;
	static final int FLUX_CAPACITY = 100;
	static final int FLUX_DECAY_TICKS = 1_200;
	private static final int FLUX_BAND_SIZE = 25;
	private static final int BLOCKED_BAND = 3;
	private static final int BASE_RECOVERY_TICKS = 100;
	// One Vis uses this many credits, even when a change in Flux changes the effective rate.
	private static final int CREDITS_PER_VIS = BASE_RECOVERY_TICKS * 4;
	private static final int MAX_PROJECTION_TICKS = FLUX_CAPACITY * FLUX_DECAY_TICKS
			+ VIS_CAPACITY * CREDITS_PER_VIS;

	static AuraCell initial(long gameTime) {
		return new AuraCell(VIS_CAPACITY, 0, Math.max(0, gameTime), 0, 0);
	}

	boolean isPristine() {
		return vis == VIS_CAPACITY && flux == 0;
	}

	int recoveryTicks() {
		return vis == VIS_CAPACITY ? 0 : recoveryTicks(fluxBand(flux));
	}

	AuraCell withVis(int amount) {
		return new AuraCell(amount, flux, tick, amount == VIS_CAPACITY ? 0 : visCredit, fluxTicks);
	}

	AuraCell withFlux(int amount) {
		return new AuraCell(vis, amount, tick, visCredit, amount == 0 ? 0 : fluxTicks);
	}

	AuraCell advance(long gameTime) {
		// Preserve the last processed point through a world-clock rollback.
		if (gameTime <= tick) {
			return this;
		}
		if (isPristine()) {
			return initial(gameTime);
		}
		int remaining = (int) Math.min(gameTime - tick, MAX_PROJECTION_TICKS);
		int available = vis;
		int residue = flux;
		int credit = visCredit;
		int decayProgress = fluxTicks;
		while (remaining > 0) {
			int band = fluxBand(residue);
			int cadence = recoveryTicks(band);
			// A decay at the end of this segment changes the rate for subsequent ticks.
			// Flux decreases monotonically, so at most four bands are visited.
			int segment = remaining;
			if (band > 0) {
				int decaysToNextBand = residue - band * FLUX_BAND_SIZE + 1;
				segment = Math.min(segment, decaysToNextBand * FLUX_DECAY_TICKS - decayProgress);
			}
			if (available < VIS_CAPACITY && cadence != 0) {
				credit += segment * (CREDITS_PER_VIS / cadence);
				available = Math.min(VIS_CAPACITY, available + credit / CREDITS_PER_VIS);
				credit = available == VIS_CAPACITY ? 0 : credit % CREDITS_PER_VIS;
			}
			if (residue > 0) {
				int totalDecayTicks = decayProgress + segment;
				residue = Math.max(0, residue - totalDecayTicks / FLUX_DECAY_TICKS);
				decayProgress = residue == 0 ? 0 : totalDecayTicks % FLUX_DECAY_TICKS;
			}
			remaining -= segment;
		}
		return new AuraCell(available, residue, gameTime, credit, decayProgress);
	}

	private static int fluxBand(int flux) {
		return Math.min(BLOCKED_BAND, flux / FLUX_BAND_SIZE);
	}

	private static int recoveryTicks(int band) {
		return band == BLOCKED_BAND ? 0 : BASE_RECOVERY_TICKS << band;
	}

	static AuraCell read(CompoundTag entry) {
		int vis = entry.contains("vis", Tag.TAG_INT) ? Mth.clamp(entry.getInt("vis"), 0, VIS_CAPACITY) : VIS_CAPACITY;
		int flux = Mth.clamp(entry.getInt("flux"), 0, FLUX_CAPACITY);
		return new AuraCell(vis, flux, Math.max(0, entry.getLong("tick")),
				vis == VIS_CAPACITY ? 0 : Mth.clamp(entry.getInt("vis_credit"), 0, CREDITS_PER_VIS - 1),
				flux == 0 ? 0 : Mth.clamp(entry.getInt("flux_ticks"), 0, FLUX_DECAY_TICKS - 1));
	}

	void write(CompoundTag entry) {
		entry.putInt("vis", vis);
		entry.putInt("flux", flux);
		entry.putLong("tick", tick);
		entry.putInt("vis_credit", visCredit);
		entry.putInt("flux_ticks", fluxTicks);
	}
}
