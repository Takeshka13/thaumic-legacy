package dev.thaumiclegacy.transport;

import dev.thaumiclegacy.advanced.AdvancedAlchemy;
import dev.thaumiclegacy.advanced.AdvancedEssentiaPort;
import dev.thaumiclegacy.logistics.EssentiaLogistics;
import dev.thaumiclegacy.logistics.EssentiaStorageBlockEntity;
import dev.thaumiclegacy.transport.EssentiaTransport.Activity;
import dev.thaumiclegacy.transport.EssentiaTransport.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

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

	static boolean transfer(ServerLevel level, BlockPos sourcePos, BlockState sourceState, AdvancedEssentiaPort source) {
		var output = java.util.Arrays.stream(EssentiaTransport.DIRECTIONS).filter(source::outputPort).findFirst();
		if (output.isEmpty()) {
			return false;
		}
		var contents = source.transportContents(output.orElseThrow());
		if (contents.isEmpty()) {
			return false;
		}
		List<String> aspects = contents.keySet().stream().sorted().toList();
		long order = source instanceof EssentiaStorageBlockEntity storage ? storage.nextTransportOrder()
				: level.getGameTime() / EssentiaTransport.INTERVAL;
		int start = Math.floorMod(order, aspects.size());
		var scanned = new LinkedHashSet<TransportBlockEntity>();
		Status failure = Status.NO_DESTINATION;
		for (int step = 0; step < Math.min(aspects.size(), MAX_ASPECTS_PER_POLL); step++) {
			String aspect = aspects.get((start + step) % aspects.size());
			var search = new Search(level, aspect, scanned, sourceState, source);
			var path = search.find(sourcePos);
			if (path != null && path.target.receiveOne(path.inputFace, aspect,
					() -> source.extractOne(path.outputFace, aspect))) {
				var moved = new Activity(Status.FLOWING, aspect, level.getGameTime());
				note(source, moved);
				note(path.target, moved);
				for (var current = path.route; current.part != null; current = current.parent) {
					current.part.note(moved);
				}
				return true;
			}
			failure = strongest(failure, search.failure);
		}
		var blocked = new Activity(failure, "", level.getGameTime());
		note(source, blocked);
		for (var pipe : scanned) {
			var previous = pipe.activity();
			if (previous == null || previous.status() != Status.FLOWING
					|| blocked.tick() - previous.tick() >= EssentiaTransport.TELEMETRY_TICKS) {
				pipe.note(blocked);
			}
		}
		return false;
	}

	private static void note(AdvancedEssentiaPort port, Activity activity) {
		if (port instanceof EssentiaStorageBlockEntity storage) {
			storage.noteTransport(activity);
		}
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
		private final BlockState sourceState;
		private final AdvancedEssentiaPort source;
		private Status failure = Status.NO_DESTINATION;

		private Search(ServerLevel level, String aspect, Set<TransportBlockEntity> scanned, BlockState sourceState,
						AdvancedEssentiaPort source) {
			this.level = level;
			this.aspect = aspect;
			this.scanned = scanned;
			this.sourceState = sourceState;
			this.source = source;
		}

		private Path find(BlockPos start) {
			var queue = new ArrayDeque<Step>();
			var visited = new HashSet<BlockPos>();
			queue.add(new Step(start, null, null, 0, null));
			visited.add(start);
			while (!queue.isEmpty()) {
				var step = queue.removeFirst();
				var state = EssentiaTransport.loadedState(level, step.pos);
				if (state == null) {
					failed(Status.UNLOADED);
					continue;
				}
				for (var face : EssentiaTransport.DIRECTIONS) {
					if (step.part == null ? !source.outputPort(face) : !EssentiaTransport.hasPort(state, face)) {
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
					var entity = level.getBlockEntity(neighbor);
					Direction input = face.getOpposite();
					if (step.part != null && acceptsDestination(adjacent) && entity instanceof AdvancedEssentiaPort target
							&& !entity.isRemoved() && target.inputPort(input)) {
						visited.add(neighbor);
						if (target.canReceive(input, aspect)) {
							return new Path(target, step, input, step.outputFace);
						}
						failed(target instanceof EssentiaStorageBlockEntity storage && storage.snapshot().amount() >= storage.snapshot().capacity()
								? Status.DESTINATION_FULL : Status.DESTINATION_INCOMPATIBLE);
					} else if (adjacent.getBlock() instanceof TransportBlock
							&& entity instanceof TransportBlockEntity pipe && !pipe.isRemoved()) {
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
							queue.addLast(new Step(neighbor, pipe, step, step.hops + 1,
									step.part == null ? face : step.outputFace));
						}
					}
				}
			}
			return null;
		}

		private boolean acceptsDestination(BlockState target) {
			if (target.is(EssentiaLogistics.WARDED_JAR)) {
				return !sourceState.is(EssentiaLogistics.WARDED_JAR);
			}
			return !sourceState.is(AdvancedAlchemy.ALCHEMICAL_CENTRIFUGE);
		}

		private void failed(Status reason) {
			failure = strongest(failure, reason);
		}
	}

	private record Step(BlockPos pos, TransportBlockEntity part, Step parent, int hops, Direction outputFace) { }
	private record Path(AdvancedEssentiaPort target, Step route, Direction inputFace, Direction outputFace) { }
}
