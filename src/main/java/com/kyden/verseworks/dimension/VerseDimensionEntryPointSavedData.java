package com.kyden.verseworks.dimension;

import net.minecraft.core.BlockPos;
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

public final class VerseDimensionEntryPointSavedData extends SavedData {
    private static final String DATA_NAME = "verseworks_dimension_entry_points";
    private static final String DIMENSIONS_TAG = "dimensions";
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";

    private final Map<ResourceLocation, BlockPos> entryPoints = new LinkedHashMap<>();

    public static VerseDimensionEntryPointSavedData get(MinecraftServer server) {
        return dataStorage(server).computeIfAbsent(factory(), DATA_NAME);
    }

    public static Optional<VerseDimensionEntryPointSavedData> getIfPresent(MinecraftServer server) {
        return Optional.ofNullable(dataStorage(server).get(factory(), DATA_NAME));
    }

    public Optional<BlockPos> entryPoint(ResourceLocation dimensionId) {
        return Optional.ofNullable(entryPoints.get(dimensionId)).map(BlockPos::immutable);
    }

    public void rememberEntryPoint(ResourceLocation dimensionId, BlockPos entryPoint) {
        BlockPos immutable = entryPoint.immutable();
        BlockPos existing = entryPoints.put(dimensionId, immutable);
        if (!immutable.equals(existing)) {
            setDirty();
        }
    }

    public void forgetDimension(ResourceLocation dimensionId) {
        if (entryPoints.remove(dimensionId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag dimensionsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, BlockPos> entry : entryPoints.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt(X_TAG, entry.getValue().getX());
            entryTag.putInt(Y_TAG, entry.getValue().getY());
            entryTag.putInt(Z_TAG, entry.getValue().getZ());
            dimensionsTag.put(entry.getKey().toString(), entryTag);
        }
        tag.put(DIMENSIONS_TAG, dimensionsTag);
        return tag;
    }

    private static SavedData.Factory<VerseDimensionEntryPointSavedData> factory() {
        return new SavedData.Factory<>(VerseDimensionEntryPointSavedData::new, VerseDimensionEntryPointSavedData::load, DataFixTypes.LEVEL);
    }

    private static VerseDimensionEntryPointSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VerseDimensionEntryPointSavedData data = new VerseDimensionEntryPointSavedData();
        CompoundTag dimensionsTag = tag.getCompound(DIMENSIONS_TAG);
        for (String key : dimensionsTag.getAllKeys()) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(key);
            if (dimensionId == null) {
                continue;
            }

            CompoundTag entryTag = dimensionsTag.getCompound(key);
            data.entryPoints.put(
                dimensionId,
                new BlockPos(entryTag.getInt(X_TAG), entryTag.getInt(Y_TAG), entryTag.getInt(Z_TAG))
            );
        }
        return data;
    }

    private static DimensionDataStorage dataStorage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }
}
