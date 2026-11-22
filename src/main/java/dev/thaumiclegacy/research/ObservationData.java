package dev.thaumiclegacy.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ObservationData extends SavedData {
	private static final String STORAGE_ID = "thaumiclegacy_observations";
	private static final String PLAYERS_TAG = "players";
	private static final String PLAYER_ID_TAG = "uuid";
	private static final String OBSERVATIONS_TAG = "observations";
	// Minecraft 1.21.1 requires a fix type. Command storage also carries arbitrary NBT.
	private static final Factory<ObservationData> FACTORY = new Factory<>(
			ObservationData::new, ObservationData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Set<ResourceLocation>> observationsByPlayer = new HashMap<>();

	static ObservationData get(ServerPlayer player) {
		return player.serverLevel().getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, STORAGE_ID);
	}

	// Returns the new unique observation count, or zero when the entry already exists.
	int recordObservation(UUID playerId, ResourceLocation observation) {
		Set<ResourceLocation> observations = observationsByPlayer.computeIfAbsent(playerId, id -> new LinkedHashSet<>());
		if (!observations.add(observation)) {
			return 0;
		}

		setDirty();
		return observations.size();
	}

	List<ResourceLocation> getObservations(UUID playerId) {
		return List.copyOf(observationsByPlayer.getOrDefault(playerId, Set.of()));
	}

	private static ObservationData load(CompoundTag tag, HolderLookup.Provider registries) {
		ObservationData data = new ObservationData();
		ListTag players = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND);
		for (int i = 0; i < players.size(); i++) {
			CompoundTag player = players.getCompound(i);
			if (!player.hasUUID(PLAYER_ID_TAG)) {
				continue;
			}
			Set<ResourceLocation> observations = data.observationsByPlayer.computeIfAbsent(
					player.getUUID(PLAYER_ID_TAG), id -> new LinkedHashSet<>());
			ListTag ids = player.getList(OBSERVATIONS_TAG, Tag.TAG_STRING);
			for (int j = 0; j < ids.size(); j++) {
				ResourceLocation id = ResourceLocation.tryParse(ids.getString(j));
				if (id != null) {
					observations.add(id);
				}
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag players = new ListTag();
		observationsByPlayer.forEach((uuid, observations) -> {
			ListTag ids = new ListTag();
			for (ResourceLocation observation : observations) {
				ids.add(StringTag.valueOf(observation.toString()));
			}
			CompoundTag player = new CompoundTag();
			player.putUUID(PLAYER_ID_TAG, uuid);
			player.put(OBSERVATIONS_TAG, ids);
			players.add(player);
		});
		tag.put(PLAYERS_TAG, players);
		return tag;
	}
}
