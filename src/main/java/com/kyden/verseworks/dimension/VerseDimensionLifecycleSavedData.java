package com.kyden.verseworks.dimension;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class VerseDimensionLifecycleSavedData extends SavedData {
    private static final String DATA_NAME = "verseworks_dimension_lifecycle";
    private static final String INITIALIZED_TAG = "initialized";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final String LOADED_AT_SAVE_TAG = "loaded_at_save";
    private static final String PINNED_AT_SAVE_TAG = "pinned_at_save";
    private static final String RESTORE_ON_STARTUP_TAG = "restore_on_startup";
    private static final String LAST_STATE_TAG = "last_state";

    private boolean initialized;
    private final Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();

    public static VerseDimensionLifecycleSavedData get(MinecraftServer server) {
        return dataStorage(server).computeIfAbsent(factory(), DATA_NAME);
    }

    public static Optional<VerseDimensionLifecycleSavedData> getIfPresent(MinecraftServer server) {
        return Optional.ofNullable(dataStorage(server).get(factory(), DATA_NAME));
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Set<ResourceLocation> startupDimensions() {
        return entries.entrySet().stream()
            .filter(entry -> entry.getValue().shouldRestoreOnStartup())
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<Entry> entry(ResourceLocation dimensionId) {
        return Optional.ofNullable(entries.get(dimensionId));
    }

    public void replaceEntries(Map<ResourceLocation, Entry> replacements, boolean initialized) {
        this.entries.clear();
        this.entries.putAll(replacements);
        this.initialized = initialized;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean(INITIALIZED_TAG, initialized);
        CompoundTag dimensionsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Entry> entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putBoolean(LOADED_AT_SAVE_TAG, entry.getValue().loadedAtSave());
            entryTag.putBoolean(PINNED_AT_SAVE_TAG, entry.getValue().pinnedAtSave());
            entryTag.putBoolean(RESTORE_ON_STARTUP_TAG, entry.getValue().shouldRestoreOnStartup());
            entryTag.putString(LAST_STATE_TAG, entry.getValue().lastState());
            dimensionsTag.put(entry.getKey().toString(), entryTag);
        }
        tag.put(DIMENSIONS_TAG, dimensionsTag);
        return tag;
    }

    public record Entry(boolean loadedAtSave, boolean pinnedAtSave, boolean shouldRestoreOnStartup, String lastState) {
    }

    private static SavedData.Factory<VerseDimensionLifecycleSavedData> factory() {
        return new SavedData.Factory<>(VerseDimensionLifecycleSavedData::new, VerseDimensionLifecycleSavedData::load, DataFixTypes.LEVEL);
    }

    private static VerseDimensionLifecycleSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VerseDimensionLifecycleSavedData data = new VerseDimensionLifecycleSavedData();
        data.initialized = tag.getBoolean(INITIALIZED_TAG);
        CompoundTag dimensionsTag = tag.getCompound(DIMENSIONS_TAG);
        for (String key : dimensionsTag.getAllKeys()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(key);
            if (dimensionId == null) {
                continue;
            }

            CompoundTag entryTag = dimensionsTag.getCompound(key);
            data.entries.put(
                dimensionId,
                new Entry(
                    entryTag.getBoolean(LOADED_AT_SAVE_TAG),
                    entryTag.getBoolean(PINNED_AT_SAVE_TAG),
                    entryTag.getBoolean(RESTORE_ON_STARTUP_TAG),
                    entryTag.contains(LAST_STATE_TAG) ? entryTag.getString(LAST_STATE_TAG) : "unknown"
                )
            );
        }
        return data;
    }

    private static DimensionDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}