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
import java.util.UUID;

public final class VerseDimensionOwnershipSavedData extends SavedData {
    private static final String DATA_NAME = "verseworks_dimension_ownership";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final String OWNER_TAG = "owner";

    private final Map<ResourceLocation, UUID> ownersByDimension = new LinkedHashMap<>();

    public static VerseDimensionOwnershipSavedData get(MinecraftServer server) {
        return dataStorage(server).computeIfAbsent(factory(), DATA_NAME);
    }

    public static Optional<VerseDimensionOwnershipSavedData> getIfPresent(MinecraftServer server) {
        return Optional.ofNullable(dataStorage(server).get(factory(), DATA_NAME));
    }

    public Optional<UUID> owner(ResourceLocation dimensionId) {
        return Optional.ofNullable(ownersByDimension.get(dimensionId));
    }

    public void rememberOwner(ResourceLocation dimensionId, UUID ownerId) {
        UUID existing = ownersByDimension.put(dimensionId, ownerId);
        if (!ownerId.equals(existing)) {
            setDirty();
        }
    }

    public long countOwnedDimensions(MinecraftServer server, UUID ownerId) {
        Set<ResourceLocation> knownDimensions = VerseDimensionCatalog.knownDimensionIds(server);
        return knownDimensions.stream()
            .filter(dimensionId -> ownerId.equals(ownersByDimension.get(dimensionId)))
            .count();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag dimensionsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, UUID> entry : ownersByDimension.entrySet()) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID(OWNER_TAG, entry.getValue());
            dimensionsTag.put(entry.getKey().toString(), ownerTag);
        }
        tag.put(DIMENSIONS_TAG, dimensionsTag);
        return tag;
    }

    private static SavedData.Factory<VerseDimensionOwnershipSavedData> factory() {
        return new SavedData.Factory<>(VerseDimensionOwnershipSavedData::new, VerseDimensionOwnershipSavedData::load, DataFixTypes.LEVEL);
    }

    private static VerseDimensionOwnershipSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VerseDimensionOwnershipSavedData data = new VerseDimensionOwnershipSavedData();
        CompoundTag dimensionsTag = tag.getCompound(DIMENSIONS_TAG);
        for (String key : dimensionsTag.getAllKeys()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(key);
            if (dimensionId == null) {
                continue;
            }

            CompoundTag ownerTag = dimensionsTag.getCompound(key);
            if (!ownerTag.hasUUID(OWNER_TAG)) {
                continue;
            }

            data.ownersByDimension.put(dimensionId, ownerTag.getUUID(OWNER_TAG));
        }
        return data;
    }

    private static DimensionDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}
