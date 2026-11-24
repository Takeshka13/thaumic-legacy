package dev.thaumiclegacy.transport;

import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.transport.EssentiaTransport.Activity;
import dev.thaumiclegacy.transport.EssentiaTransport.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded search through loaded blocks. Only a completed endpoint transfer mutates essentia. */
final class TransportSearch {
	private static final int MAX_ASPECTS_PER_POLL = 4;

	private TransportSearch() {
	}

	static boolean transfer(ServerLevel level, EssentiaStorageBlockEntity source) {
		var contents = source.snapshot().aspects();
		if (contents.isEmpty()) {
			return false;
		}
		List<String> aspects = contents.keySet().stream().sorted().toList();
		int start = Math.floorMod(source.nextTransportOrder(), aspects.size());
		var scanned = new LinkedHashSet<TransportBlockEntity>();
		Status failure = Status.NO_DESTINATION;
		for (int step = 0; step < Math.min(aspects.size(), MAX_ASPECTS_PER_POLL); step++) {
			String aspect = aspects.get((start + step) % aspects.size());
			var search = new Search(level, aspect, scanned);
			var path = search.find(source.getBlockPos());
			if (path != null && source.transferOneTo(path.target, aspect)) {
				var moved = new Activity(Status.FLOWING, aspect, level.getGameTime());
				source.noteTransport(moved);
				path.target.noteTransport(moved);
				for (var current = path.route; current.part != null; current = current.parent) {
					current.part.note(moved);
				}
				return true;
			}
			failure = strongest(failure, search.failure);
		}
		var blocked = new Activity(failure, "", level.getGameTime());
		source.noteTransport(blocked);
		for (var pipe : scanned) {
			var previous = pipe.activity();
			if (previous == null || previous.status() != Status.FLOWING
					|| blocked.tick() - previous.tick() >= EssentiaTransport.TELEMETRY_TICKS) {
				pipe.note(blocked);
			}
		}
		return false;
	}

	private static Status strongest(Status current, Status candidate) {
		return priority(candidate) > priority(current) ? candidate : current;
	}

	private static int priority(Status status) {
		return switch (status) {
			case DESTINATION_FULL, DESTINATION_INCOMPATIBLE -> 4;
			case FILTER_MISMATCH, FILTER_UNCONFIGURED, VALVE_CLOSED -> 3;
			case NETWORK_LIMIT -> 2;
			case UNLOADED -> 1;
			default -> 0;
		};
	}

	private static final class Search {
		private final ServerLevel level;
		private final String aspect;
		private final Set<TransportBlockEntity> scanned;
		private Status failure = Status.NO_DESTINATION;

		private Search(ServerLevel level, String aspect, Set<TransportBlockEntity> scanned) {
			this.level = level;
			this.aspect = aspect;
			this.scanned = scanned;
		}

		private Path find(BlockPos start) {
			var queue = new ArrayDeque<Step>();
			var visited = new HashSet<BlockPos>();
			queue.add(new Step(start, null, null, 0));
			visited.add(start);
			while (!queue.isEmpty()) {
				var step = queue.removeFirst();
				var state = EssentiaTransport.loadedState(level, step.pos);
				if (state == null) {
					failed(Status.UNLOADED);
					continue;
				}
				for (var face : EssentiaTransport.DIRECTIONS) {
					if (!EssentiaTransport.hasPort(state, face)) {
						continue;
					}
					var neighbor = step.pos.relative(face);
					if (visited.contains(neighbor) || level.isOutsideBuildHeight(neighbor)) {
						continue;
					}
					var adjacent = EssentiaTransport.loadedState(level, neighbor);
					if (adjacent == null) {
						failed(Status.UNLOADED);
						continue;
					}
					if (!EssentiaTransport.hasPort(adjacent, face.getOpposite())) {
						continue;
					}
					if (step.part != null && adjacent.is(EssentiaLogistics.WARDED_JAR)
							&& level.getBlockEntity(neighbor) instanceof EssentiaStorageBlockEntity jar && !jar.isRemoved()) {
						visited.add(neighbor);
						var stock = jar.snapshot();
						if (stock.amount() >= stock.capacity()) {
							failed(Status.DESTINATION_FULL);
						} else if (!stock.aspects().isEmpty() && !stock.aspects().containsKey(aspect)) {
							failed(Status.DESTINATION_INCOMPATIBLE);
						} else {
							return new Path(jar, step);
						}
					} else if (adjacent.getBlock() instanceof TransportBlock
							&& level.getBlockEntity(neighbor) instanceof TransportBlockEntity pipe && !pipe.isRemoved()) {
						if (visited.size() >= EssentiaTransport.MAX_VISITED || step.hops >= EssentiaTransport.MAX_HOPS) {
							failed(Status.NETWORK_LIMIT);
							continue;
						}
						visited.add(neighbor);
						scanned.add(pipe);
						var local = EssentiaTransport.localStatus(adjacent, pipe);
						if (local != null) {
							failed(local);
						} else if (!pipe.filter().isEmpty() && !pipe.filter().equals(aspect)) {
							failed(Status.FILTER_MISMATCH);
						} else {
							queue.addLast(new Step(neighbor, pipe, step, step.hops + 1));
						}
					}
				}
			}
			return null;
		}

		private void failed(Status reason) {
			failure = strongest(failure, reason);
		}
	}

	private record Step(BlockPos pos, TransportBlockEntity part, Step parent, int hops) { }
	private record Path(EssentiaStorageBlockEntity target, Step route) { }
}
