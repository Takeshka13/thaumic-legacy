package dev.thaumiclegacy.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** A horizontal pattern whose origin is owned by the multiblock controller. */
public final class MultiblockPattern {
	private final List<Cell> cells;

	public MultiblockPattern(List<Cell> cells) {
		this.cells = List.copyOf(cells);
	}

	public Match check(ServerLevel level, BlockPos origin, Direction front) {
		if (!front.getAxis().isHorizontal()) {
			return Match.INVALID;
		}
		return check(level, origin, front, (cell, pos) -> cell.matches.test(level.getBlockState(pos)));
	}

	public List<Cell> cells() {
		return cells;
	}

	public Match check(ServerLevel level, BlockPos origin, Direction front, BiPredicate<Cell, BlockPos> matches) {
		boolean unloaded = false;
		boolean invalid = false;
		for (Cell cell : cells) {
			BlockPos pos = cell.position(origin, front);
			if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
				return Match.INVALID;
			}
			if (!level.hasChunkAt(pos)) {
				unloaded = true;
			} else if (!matches.test(cell, pos)) {
				invalid = true;
			}
		}
		return unloaded ? Match.UNLOADED : invalid ? Match.INVALID : Match.MATCH;
	}

	public enum Match { MATCH, INVALID, UNLOADED }

	public record Cell(int right, int up, int back, Predicate<BlockState> matches) {
		public BlockPos position(BlockPos origin, Direction front) {
			Direction side = front.getClockWise();
			Direction rear = front.getOpposite();
			return origin.relative(side, right).above(up).relative(rear, back);
		}
	}
}
