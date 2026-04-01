package com.kyden.verseworks.dimension;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VerseDimensionCatalog {
    private static final Map<ResourceLocation, VerseDimensionParameters> KNOWN_DIMENSIONS = new ConcurrentHashMap<>();
    private static volatile String loadedServerKey;

    private VerseDimensionCatalog() {
    }

    public static void remember(ResourceLocation dimensionId, VerseDimensionParameters parameters) {
        KNOWN_DIMENSIONS.put(dimensionId, parameters);
    }

    public static void forget(ResourceLocation dimensionId) {
        KNOWN_DIMENSIONS.remove(dimensionId);
    }

    public static Optional<VerseDimensionParameters> get(ResourceLocation dimensionId) {
        return Optional.ofNullable(KNOWN_DIMENSIONS.get(dimensionId));
    }

    public static Optional<VerseDimensionParameters> getCached(ResourceLocation dimensionId) {
        return Optional.ofNullable(KNOWN_DIMENSIONS.get(dimensionId));
    }

    public static Optional<VerseDimensionParameters> get(MinecraftServer server, ResourceLocation dimensionId) {
        ensureServerScope(server);
        VerseDimensionParameters cached = KNOWN_DIMENSIONS.get(dimensionId);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<VerseDimensionParameters> loaded = GeneratedDimensionPackWriter.readParameters(server, dimensionId);
        loaded.ifPresent(parameters -> KNOWN_DIMENSIONS.put(dimensionId, parameters));
        return loaded;
    }

    public static void ensureLoaded(MinecraftServer server) {
        ensureServerScope(server);
        String serverKey = server.getWorldPath(LevelResource.ROOT).toString();
        if (serverKey.equals(loadedServerKey)) {
            return;
        }

        try {
            for (ResourceLocation dimensionId : GeneratedDimensionPackWriter.listGeneratedDimensions(server)) {
                GeneratedDimensionPackWriter.readParameters(server, dimensionId)
                    .ifPresent(parameters -> KNOWN_DIMENSIONS.putIfAbsent(dimensionId, parameters));
            }
            loadedServerKey = serverKey;
        } catch (IOException ignored) {
        }
    }

    public static Set<ResourceLocation> knownDimensionIds(MinecraftServer server) {
        ensureServerScope(server);
        LinkedHashSet<ResourceLocation> dimensionIds = new LinkedHashSet<>();
        server.forgeGetWorldMap().keySet().stream()
            .map(net.minecraft.resources.ResourceKey::location)
            .filter(identifier -> "verseworks".equals(identifier.getNamespace()))
            .forEach(dimensionIds::add);
        dimensionIds.addAll(KNOWN_DIMENSIONS.keySet());
        try {
            dimensionIds.addAll(GeneratedDimensionPackWriter.listGeneratedDimensions(server));
        } catch (IOException ignored) {
        }
        return Set.copyOf(dimensionIds);
    }

    public static void invalidate(MinecraftServer server) {
        ensureServerScope(server);
        loadedServerKey = null;
    }

    private static void ensureServerScope(MinecraftServer server) {
        String serverKey = server.getWorldPath(LevelResource.ROOT).toString();
        if (serverKey.equals(loadedServerKey)) {
            return;
        }

        KNOWN_DIMENSIONS.clear();
        loadedServerKey = null;
    }
}