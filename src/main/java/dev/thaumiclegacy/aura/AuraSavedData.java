package dev.thaumiclegacy.aura;

import dev.thaumiclegacy.ThaumicLegacy;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Sparse ledger owned by one dimension's data storage. */
final class AuraSavedData extends SavedData {
	static final String STORAGE_ID = "thaumiclegacy_aura";
	private static final int VERSION = 1;
	private static final Factory<AuraSavedData> FACTORY = new Factory<>(
			AuraSavedData::new, AuraSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	private final Long2ObjectOpenHashMap<AuraCell> cells = new Long2ObjectOpenHashMap<>();
	private CompoundTag unsupportedData;

	static AuraSavedData get(ServerLevel level) {
		if (!level.getServer().isSameThread()) {
			throw new IllegalStateException("Aura can only be accessed from the server thread");
		}
		return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_ID);
	}

	boolean canModify() {
		return unsupportedData == null;
	}

	AuraCell project(long chunk, long gameTime) {
		AuraCell cell = cells.get(chunk);
		return cell == null ? AuraCell.initial(gameTime) : cell.advance(gameTime);
	}

	void store(long chunk, AuraCell cell) {
		if (cell.isPristine()) {
			if (cells.remove(chunk) != null) {
				setDirty();
			}
		} else {
			cells.put(chunk, cell);
			setDirty();
		}
	}

	static AuraSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
		AuraSavedData data = new AuraSavedData();
		if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION) {
			data.unsupportedData = tag.copy();
			ThaumicLegacy.LOGGER.warn("Unsupported aura save version; its data will be preserved.");
			return data;
		}

		ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
		for (int i = 0; i < cells.size(); i++) {
			CompoundTag entry = cells.getCompound(i);
			if (entry.contains("chunk", Tag.TAG_LONG)) {
				AuraCell cell = AuraCell.read(entry);
				if (!cell.isPristine()) {
					data.cells.put(entry.getLong("chunk"), cell);
				}
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		if (unsupportedData != null) {
			return unsupportedData.copy();
		}
		ListTag entries = new ListTag();
		for (var cell : cells.long2ObjectEntrySet()) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("chunk", cell.getLongKey());
			cell.getValue().write(entry);
			entries.add(entry);
		}
		tag.putInt("version", VERSION);
		tag.put("cells", entries);
		return tag;
	}
}
