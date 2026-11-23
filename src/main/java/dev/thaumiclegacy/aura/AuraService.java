package dev.thaumiclegacy.aura;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Internal server-side aura operations used by gameplay. */
public final class AuraService {
	private AuraService() {
	}

	public static Snapshot read(ServerLevel level, ChunkPos pos) {
		AuraSavedData data = AuraSavedData.get(level);
		long chunk = pos.toLong();
		AuraCell cell = data.project(chunk, level.getGameTime());
		if (cell.isPristine()) {
			data.store(chunk, cell);
		}
		return new Snapshot(cell.vis(), AuraCell.VIS_CAPACITY, cell.flux(), cell.recoveryTicks());
	}

	public static boolean trySpendVis(ServerLevel level, ChunkPos pos, int amount) {
		if (amount <= 0) {
			return false;
		}
		AuraSavedData data = AuraSavedData.get(level);
		long chunk = pos.toLong();
		AuraCell cell = data.project(chunk, level.getGameTime());
		if (!data.canModify() || amount > cell.vis()) {
			return false;
		}
		data.store(chunk, cell.withVis(cell.vis() - amount));
		return true;
	}

	public static int restoreVis(ServerLevel level, ChunkPos pos, int amount) {
		if (amount <= 0) {
			return 0;
		}
		AuraSavedData data = AuraSavedData.get(level);
		long chunk = pos.toLong();
		AuraCell cell = data.project(chunk, level.getGameTime());
		int restored = data.canModify() ? Math.min(amount, AuraCell.VIS_CAPACITY - cell.vis()) : 0;
		if (restored != 0) {
			data.store(chunk, cell.withVis(cell.vis() + restored));
		}
		return restored;
	}

	public static int addFlux(ServerLevel level, ChunkPos pos, int amount) {
		if (amount <= 0) {
			return 0;
		}
		AuraSavedData data = AuraSavedData.get(level);
		long chunk = pos.toLong();
		AuraCell cell = data.project(chunk, level.getGameTime());
		int added = data.canModify() ? Math.min(amount, AuraCell.FLUX_CAPACITY - cell.flux()) : 0;
		if (added != 0) {
			data.store(chunk, cell.withFlux(cell.flux() + added));
		}
		return added;
	}

	public static int removeFlux(ServerLevel level, ChunkPos pos, int amount) {
		if (amount <= 0) {
			return 0;
		}
		AuraSavedData data = AuraSavedData.get(level);
		long chunk = pos.toLong();
		AuraCell cell = data.project(chunk, level.getGameTime());
		int removed = data.canModify() ? Math.min(amount, cell.flux()) : 0;
		if (removed != 0) {
			data.store(chunk, cell.withFlux(cell.flux() - removed));
		}
		return removed;
	}

	/** Recovery is measured in ticks per Vis and is zero while full or suppressed by Flux. */
	public record Snapshot(int vis, int capacity, int flux, int recoveryTicks) {
	}
}
