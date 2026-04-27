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

public final class VerseDimensionUsageSavedData extends SavedData {
    private static final String DATA_NAME = "verseworks_dimension_usage";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final String VISIT_COUNT_TAG = "visit_count";
    private static final String LAST_VISITED_AT_TAG = "last_visited_at";

    private final Map<ResourceLocation, UsageStats> usageByDimension = new LinkedHashMap<>();

    public static VerseDimensionUsageSavedData get(MinecraftServer server) {
        return dataStorage(server).computeIfAbsent(factory(), DATA_NAME);
    }

    public static Optional<VerseDimensionUsageSavedData> getIfPresent(MinecraftServer server) {
        return Optional.ofNullable(dataStorage(server).get(factory(), DATA_NAME));
    }

    public UsageStats usage(ResourceLocation dimensionId) {
        return usageByDimension.getOrDefault(dimensionId, UsageStats.EMPTY);
    }

    public void recordVisit(ResourceLocation dimensionId, long visitedAtEpochMillis) {
        UsageStats existing = usageByDimension.getOrDefault(dimensionId, UsageStats.EMPTY);
        usageByDimension.put(dimensionId, new UsageStats(existing.visitCount() + 1L, Math.max(existing.lastVisitedAtEpochMillis(), visitedAtEpochMillis)));
        setDirty();
    }

    public void forgetDimension(ResourceLocation dimensionId) {
        if (usageByDimension.remove(dimensionId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag dimensionsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, UsageStats> entry : usageByDimension.entrySet()) {
            CompoundTag usageTag = new CompoundTag();
            usageTag.putLong(VISIT_COUNT_TAG, entry.getValue().visitCount());
            usageTag.putLong(LAST_VISITED_AT_TAG, entry.getValue().lastVisitedAtEpochMillis());
            dimensionsTag.put(entry.getKey().toString(), usageTag);
        }
        tag.put(DIMENSIONS_TAG, dimensionsTag);
        return tag;
    }

    public record UsageStats(long visitCount, long lastVisitedAtEpochMillis) {
        public static final UsageStats EMPTY = new UsageStats(0L, 0L);
    }

    private static SavedData.Factory<VerseDimensionUsageSavedData> factory() {
        return new SavedData.Factory<>(VerseDimensionUsageSavedData::new, VerseDimensionUsageSavedData::load, DataFixTypes.LEVEL);
    }

    private static VerseDimensionUsageSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VerseDimensionUsageSavedData data = new VerseDimensionUsageSavedData();
        CompoundTag dimensionsTag = tag.getCompound(DIMENSIONS_TAG);
        for (String key : dimensionsTag.getAllKeys()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(key);
            if (dimensionId == null) {
                continue;
            }

            CompoundTag usageTag = dimensionsTag.getCompound(key);
            data.usageByDimension.put(
                dimensionId,
                new UsageStats(
                    usageTag.contains(VISIT_COUNT_TAG) ? usageTag.getLong(VISIT_COUNT_TAG) : 0L,
                    usageTag.contains(LAST_VISITED_AT_TAG) ? usageTag.getLong(LAST_VISITED_AT_TAG) : 0L
                )
            );
        }
        return data;
    }

    private static DimensionDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}
