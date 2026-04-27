package com.kyden.verseworks.dimension;

import com.mojang.datafixers.util.Pair;
import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LiveDimensionInstantiator {
    private static final ChunkPos LIVE_ENTRY_CHUNK = new ChunkPos(0, 0);
    private static final int ENTRY_WARMUP_RADIUS = 3;
    private static final int ENTRY_CHUNK_MIN_RADIUS = 24;
    private static final int ENTRY_CHUNK_SPREAD = 192;
    private static final long SLOW_PREPARE_THRESHOLD_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10);
    private static final long SLOW_INSTANTIATE_THRESHOLD_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(25);
    private static final Set<ResourceKey<Level>> RUNTIME_LEVELS = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceKey<Level>, ChunkPos> ENTRY_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, CompletableFuture<?>> WARMUP_FUTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, CompletableFuture<Result>> PENDING_ACTIVATIONS = new ConcurrentHashMap<>();
    private static volatile Method levelSaveMethod;
    private static volatile Field serverRegistriesField;
    private static volatile Field serverExecutorField;

    private LiveDimensionInstantiator() {
    }

    public static Result instantiate(MinecraftServer server, ResourceLocation dimensionId) throws Exception {
        return instantiate(server, dimensionId, new VerseDimensionParameters(0, Blocks.GRASS_BLOCK, VerseDimensionWorldType.NORMAL, List.of()));
    }

    public static Result instantiate(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws Exception {
        return instantiate(server, dimensionId, parameters, ActivationMode.FIRST_USE_BLOCKING);
    }

    public static Result instantiate(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters, ActivationMode activationMode) throws Exception {
        return instantiatePrepared(prepareActivation(server, dimensionId, parameters), activationMode);
    }

    private static Result instantiatePrepared(PreparedActivation prepared, ActivationMode activationMode) throws Exception {
        MinecraftServer server = prepared.server();
        ResourceLocation dimensionId = prepared.dimensionId();
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel existingLevel = server.getLevel(levelKey);
        if (existingLevel != null) {
            return new Result(existingLevel, false, "already loaded");
        }

        ServerLevel overworld = prepared.overworld();
        LevelStem levelStem = resolveOrRegisterLevelStem(server, overworld, dimensionId, prepared.parameters(), prepared.dimensionSeed());
        ServerLevelData overworldData = (ServerLevelData) overworld.getLevelData();
        MutableDerivedLevelData derivedLevelData = new MutableDerivedLevelData(server.getWorldData(), overworldData);
        ServerLevel injectedLevel = createInjectedLevel(server, prepared.storageAccess(), derivedLevelData, levelKey, levelStem, prepared.dimensionSeed());

        server.forgeGetWorldMap().put(levelKey, injectedLevel);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(injectedLevel));
        injectedLevel.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        invokePlayerListWorldRegistration(server.getPlayerList(), injectedLevel);
        RUNTIME_LEVELS.add(levelKey);
        ENTRY_CHUNKS.put(levelKey, prepared.entryChunk());

        String detail = activationMode == ActivationMode.CRAFT_BACKGROUND ? "live injected; entry warmup deferred" : warmupDetail(levelKey);
        return new Result(injectedLevel, true, detail);
    }

    public static boolean isRuntimeLevel(ServerLevel level) {
        return RUNTIME_LEVELS.contains(level.dimension());
    }

    public static boolean isReadyForEntry(ServerLevel level) {
        CompletableFuture<?> future = WARMUP_FUTURES.get(level.dimension());
        if (future != null && !future.isDone()) {
            return false;
        }

        return hasLoadedEntryChunkRing(level);
    }

    private static boolean hasLoadedEntryChunkRing(ServerLevel level) {
        ChunkPos entryChunk = getEntryChunk(level);
        for (int chunkX = entryChunk.x - ENTRY_WARMUP_RADIUS; chunkX <= entryChunk.x + ENTRY_WARMUP_RADIUS; chunkX++) {
            for (int chunkZ = entryChunk.z - ENTRY_WARMUP_RADIUS; chunkZ <= entryChunk.z + ENTRY_WARMUP_RADIUS; chunkZ++) {
                if (!level.getChunkSource().isPositionTicking(ChunkPos.asLong(chunkX, chunkZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static ChunkPos getEntryChunk(ServerLevel level) {
        return ENTRY_CHUNKS.getOrDefault(level.dimension(), LIVE_ENTRY_CHUNK);
    }

    public static int entryWarmupRadius() {
        return ENTRY_WARMUP_RADIUS;
    }

    public static void clearRuntimeState() {
        RUNTIME_LEVELS.clear();
        ENTRY_CHUNKS.clear();
        WARMUP_FUTURES.clear();
        PENDING_ACTIVATIONS.clear();
        VerseChunkWarmup.clearRuntimeState();
    }

    public static void requestEntryWarmup(ServerLevel level) {
        if (VerseDimensionRuntimeHooks.isShutdownInProgress(level.getServer())) {
            return;
        }

        if (hasLoadedEntryChunkRing(level)) {
            return;
        }

        scheduleInitialChunkWarmup(level);
    }

    public static CompletableFuture<Result> activateAsync(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
        return activateAsync(server, dimensionId, parameters, ActivationMode.FIRST_USE_BLOCKING);
    }

    public static CompletableFuture<Result> activateAsync(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters, ActivationMode activationMode) {
        if (HyperBookCollapseHooks.isDimensionCollapsing(dimensionId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Dimension " + dimensionId + " is collapsing"));
        }

        if (VerseDimensionRuntimeHooks.isShutdownInProgress(server)) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Overworld is not available during shutdown"));
            }
            return CompletableFuture.completedFuture(new Result(overworld, false, "shutdown in progress"));
        }

        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel existingLevel = server.getLevel(levelKey);
        if (existingLevel != null) {
            if (activationMode == ActivationMode.FIRST_USE_BLOCKING) {
                VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, existingLevel, parameters);
            }
            return CompletableFuture.completedFuture(new Result(existingLevel, false, "already loaded"));
        }

        return PENDING_ACTIVATIONS.computeIfAbsent(levelKey, ignored -> {
            CompletableFuture<Result> future = new CompletableFuture<>();
            CompletableFuture
                .supplyAsync(() -> {
                    long prepareStart = System.nanoTime();
                    try {
                        PreparedActivation prepared = prepareActivation(server, dimensionId, parameters);
                        logSlowOperation("Prepared activation inputs", dimensionId, prepareStart, SLOW_PREPARE_THRESHOLD_NANOS);
                        return prepared;
                    } catch (Exception exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                })
                .whenComplete((prepared, prepareThrowable) -> {
                    if (prepareThrowable != null) {
                        future.completeExceptionally(prepareThrowable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null
                            ? completionException.getCause()
                            : prepareThrowable);
                        return;
                    }

                    server.execute(() -> {
                        if (HyperBookCollapseHooks.isDimensionCollapsing(dimensionId)) {
                            future.completeExceptionally(new IllegalStateException("Dimension " + dimensionId + " is collapsing"));
                            return;
                        }

                        long instantiateStart = System.nanoTime();
                        try {
                            Result result = instantiatePrepared(prepared, activationMode);
                            if (result.injected()) {
                                VerseDimensionRuntimeHooks.applyCreationRules(
                                    server,
                                    result.level(),
                                    prepared.parameters(),
                                    activationMode == ActivationMode.CRAFT_BACKGROUND
                                        ? VerseDimensionRuntimeHooks.EntryPreparationMode.DEFERRED
                                        : VerseDimensionRuntimeHooks.EntryPreparationMode.FULL
                                );
                            }
                            if (activationMode == ActivationMode.FIRST_USE_BLOCKING) {
                                VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, result.level(), prepared.parameters());
                            }
                            logSlowOperation("Instantiated live dimension", dimensionId, instantiateStart, SLOW_INSTANTIATE_THRESHOLD_NANOS);
                            future.complete(result);
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                    });
                });
            future.whenComplete((result, throwable) -> {
                PENDING_ACTIVATIONS.remove(levelKey, future);
                if (throwable != null) {
                    VerseWorks.LOGGER.error("Async live activation failed for {}", dimensionId, throwable);
                }
            });
            return future;
        });
    }

    public static boolean isActivationInProgress(ResourceLocation dimensionId) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        CompletableFuture<Result> future = PENDING_ACTIVATIONS.get(levelKey);
        return future != null && !future.isDone();
    }

    public static boolean isAnyActivationInProgress() {
        return PENDING_ACTIVATIONS.values().stream().anyMatch(future -> future != null && !future.isDone());
    }

    public static boolean unload(ServerLevel level, boolean save, boolean flush) throws Exception {
        ResourceKey<Level> levelKey = level.dimension();
        if (!VerseWorks.MODID.equals(levelKey.location().getNamespace()) || !level.players().isEmpty()) {
            return false;
        }

        if (save) {
            invokeLevelSave(level, flush);
        }

        MinecraftServer server = level.getServer();
        server.forgeGetWorldMap().remove(levelKey);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        level.close();
        RUNTIME_LEVELS.remove(levelKey);
        ENTRY_CHUNKS.remove(levelKey);
        WARMUP_FUTURES.remove(levelKey);
        PENDING_ACTIVATIONS.remove(levelKey);
        return true;
    }

    public static String warmupDetail(ResourceKey<Level> levelKey) {
        CompletableFuture<?> future = WARMUP_FUTURES.get(levelKey);
        if (future == null) {
            return "live injected";
        }

        if (!future.isDone()) {
            return "live injected; chunk warmup still running";
        }

        return future.isCompletedExceptionally()
            ? "live injected; entry warmup failed"
            : "live injected; entry warmup completed";
    }

    private static void scheduleInitialChunkWarmup(ServerLevel level) {
        if (VerseDimensionRuntimeHooks.isShutdownInProgress(level.getServer())) {
            return;
        }

        if (hasLoadedEntryChunkRing(level)) {
            return;
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        ResourceKey<Level> levelKey = level.dimension();
        ChunkPos entryChunk = getEntryChunk(level);
        CompletableFuture<?> existingFuture = WARMUP_FUTURES.get(levelKey);
        if (existingFuture != null && !existingFuture.isDone()) {
            return;
        }

        CompletableFuture<?> loadFuture = requestChunkWarmup(level, entryChunk, ENTRY_WARMUP_RADIUS);
        WARMUP_FUTURES.put(levelKey, loadFuture);
        loadFuture.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                WARMUP_FUTURES.remove(levelKey, loadFuture);
                VerseWorks.LOGGER.error("Live warmup failed for {}", level.dimension().location(), throwable);
                return;
            }

            boolean entryChunkTicking = chunkSource.isPositionTicking(entryChunk.toLong());
            VerseWorks.LOGGER.info(
                "Live warmup completed for {} around chunk {},{} radius {} (entityTicking={})",
                level.dimension().location(),
                entryChunk.x,
                entryChunk.z,
                ENTRY_WARMUP_RADIUS,
                entryChunkTicking
            );
            WARMUP_FUTURES.remove(levelKey, loadFuture);
        });
    }

    private static ChunkPos resolveEntryChunk(long seedOffset) {
        long mixedX = scrambleSeed(seedOffset ^ 0x6A09E667F3BCC909L);
        long mixedZ = scrambleSeed(Long.rotateLeft(seedOffset, 29) ^ 0xBB67AE8584CAA73BL);
        int chunkX = (int) Math.floorMod(mixedX, ENTRY_CHUNK_SPREAD) - ENTRY_CHUNK_SPREAD / 2;
        int chunkZ = (int) Math.floorMod(mixedZ, ENTRY_CHUNK_SPREAD) - ENTRY_CHUNK_SPREAD / 2;
        if (Math.abs(chunkX) < ENTRY_CHUNK_MIN_RADIUS) {
            chunkX += chunkX < 0 ? -ENTRY_CHUNK_MIN_RADIUS : ENTRY_CHUNK_MIN_RADIUS;
        }
        if (Math.abs(chunkZ) < ENTRY_CHUNK_MIN_RADIUS) {
            chunkZ += chunkZ < 0 ? -ENTRY_CHUNK_MIN_RADIUS : ENTRY_CHUNK_MIN_RADIUS;
        }
        return new ChunkPos(chunkX, chunkZ);
    }

    private static long scrambleSeed(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    private static LevelStem resolveOrRegisterLevelStem(MinecraftServer server, ServerLevel overworld, ResourceLocation dimensionId, VerseDimensionParameters parameters, long dimensionSeed) throws Exception {
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dimensionId);
        Registry<LevelStem> levelStemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        Optional<Holder.Reference<LevelStem>> existingStem = levelStemRegistry.getHolder(stemKey);
        if (existingStem.isPresent()) {
            return existingStem.get().value();
        }

        Holder<DimensionType> dimensionTypeHolder = resolveDimensionTypeHolder(server, dimensionId, overworld);
        ChunkGenerator generator = createGenerator(server, dimensionId, parameters, dimensionSeed);
        LevelStem liveStem = new LevelStem(dimensionTypeHolder, generator);

        installLevelStem(server, levelStemRegistry, stemKey, liveStem);

        return liveStem;
    }

    private static void installLevelStem(MinecraftServer server, Registry<LevelStem> currentStemRegistry, ResourceKey<LevelStem> stemKey, LevelStem liveStem) throws Exception {
        WorldDimensions currentDimensions = new WorldDimensions(currentStemRegistry);
        LinkedHashMap<ResourceKey<LevelStem>, LevelStem> updatedDimensions = new LinkedHashMap<>(currentDimensions.dimensions());
        updatedDimensions.put(stemKey, liveStem);
        WorldDimensions.Complete completeDimensions = new WorldDimensions(updatedDimensions).bake(currentStemRegistry);

        var currentRegistries = server.registries();
        var updatedRegistries = currentRegistries.replaceFrom(
            RegistryLayer.DIMENSIONS,
            List.of(completeDimensions.dimensionsRegistryAccess(), currentRegistries.getLayer(RegistryLayer.RELOADABLE))
        );

        getServerRegistriesField().set(server, updatedRegistries);

        if (!server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(stemKey)) {
            throw new IllegalStateException("Failed to publish runtime level stem " + stemKey.location());
        }
    }

    public static Optional<ServerLevel> findLevel(MinecraftServer server, ResourceLocation dimensionId) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return Optional.ofNullable(server.getLevel(levelKey));
    }

    public static boolean hasRegisteredLevelStem(MinecraftServer server, ResourceLocation dimensionId) {
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dimensionId);
        return server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(stemKey);
    }

    public static boolean unregisterLevelStem(MinecraftServer server, ResourceLocation dimensionId) throws Exception {
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, dimensionId);
        Registry<LevelStem> levelStemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        if (!levelStemRegistry.containsKey(stemKey)) {
            return false;
        }

        WorldDimensions currentDimensions = new WorldDimensions(levelStemRegistry);
        LinkedHashMap<ResourceKey<LevelStem>, LevelStem> updatedDimensions = new LinkedHashMap<>(currentDimensions.dimensions());
        if (updatedDimensions.remove(stemKey) == null) {
            return false;
        }

        WorldDimensions.Complete completeDimensions = new WorldDimensions(updatedDimensions).bake(levelStemRegistry);
        var currentRegistries = server.registries();
        var updatedRegistries = currentRegistries.replaceFrom(
            RegistryLayer.DIMENSIONS,
            List.of(completeDimensions.dimensionsRegistryAccess(), currentRegistries.getLayer(RegistryLayer.RELOADABLE))
        );
        getServerRegistriesField().set(server, updatedRegistries);
        return !server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(stemKey);
    }

    private static ChunkGenerator createGenerator(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters, long dimensionSeed) {
        if (parameters.worldType() == VerseDimensionWorldType.FLAT) {
            return createFlatGenerator(server, parameters);
        }

        HolderGetter<NoiseGeneratorSettings> noiseSettingsRegistry = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        Holder<NoiseGeneratorSettings> noiseSettingsHolder = noiseSettingsRegistry.getOrThrow(parameters.worldType().noiseSettingsKey());
        return new VerseChunkGenerator(createBiomeSource(server, parameters), noiseSettingsHolder, parameters);
    }

    private static ChunkGenerator createFlatGenerator(MinecraftServer server, VerseDimensionParameters parameters) {
        HolderGetter<Biome> biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        HolderGetter<PlacedFeature> placedFeatureRegistry = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        Holder<Biome> biomeHolder = resolveFlatBiome(server, parameters, biomeRegistry);
        Optional<HolderSet<StructureSet>> structureOverrides = parameters.structures() ? Optional.empty() : Optional.of(HolderSet.direct(List.of()));
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(structureOverrides, biomeHolder, FlatLevelGeneratorSettings.createLakesList(placedFeatureRegistry));
        if (parameters.lakes()) {
            settings.setAddLakes();
        }
        if (!parameters.worldType().isVoid()) {
            settings.setDecoration();
            settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
            settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.STONE));
            settings.getLayersInfo().add(new FlatLayerInfo(1, parameters.floorBlock()));
        }
        settings.updateLayers();
        return new FlatLevelSource(settings);
    }

    private static Holder<Biome> resolveFlatBiome(MinecraftServer server, VerseDimensionParameters parameters, HolderGetter<Biome> biomeRegistry) {
        if (parameters.biomeIds().isEmpty()) {
            return biomeRegistry.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        }

        if (parameters.biomeIds().size() > 1) {
            throw new IllegalArgumentException("Flat dimensions support at most one biome");
        }

        return resolveBiomeHolder(server, parameters.primaryBiomeId());
    }

    private static BiomeSource createBiomeSource(MinecraftServer server, VerseDimensionParameters parameters) {
        if (parameters.biomeIds().isEmpty()) {
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                return overworld.getChunkSource().getGenerator().getBiomeSource();
            }

            HolderGetter<MultiNoiseBiomeSourceParameterList> presetRegistry = server.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            return MultiNoiseBiomeSource.createFromPreset(presetRegistry.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        }

        List<Holder<Biome>> biomeHolders = resolveBiomeHolders(server, parameters.biomeIds());
        if (biomeHolders.size() == 1) {
            return new FixedBiomeSource(biomeHolders.get(0));
        }

        return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(buildBiomeParameterList(biomeHolders)));
    }

    private static List<Pair<Climate.ParameterPoint, Holder<Biome>>> buildBiomeParameterList(List<Holder<Biome>> biomeHolders) {
        Climate.Parameter fullSpan = Climate.Parameter.span(-1.0F, 1.0F);
        float rangeSize = 2.0F / biomeHolders.size();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> entries = new ArrayList<>(biomeHolders.size());
        for (int index = 0; index < biomeHolders.size(); index++) {
            float minWeirdness = -1.0F + rangeSize * index;
            float maxWeirdness = index == biomeHolders.size() - 1 ? 1.0F : minWeirdness + rangeSize;
            Climate.Parameter weirdness = Climate.Parameter.span(minWeirdness, maxWeirdness);
            Climate.ParameterPoint point = Climate.parameters(fullSpan, fullSpan, fullSpan, fullSpan, fullSpan, weirdness, 0.0F);
            entries.add(Pair.of(point, biomeHolders.get(index)));
        }
        return entries;
    }

    private static List<Holder<Biome>> resolveBiomeHolders(MinecraftServer server, List<ResourceLocation> biomeIds) {
        List<Holder<Biome>> biomeHolders = new ArrayList<>(biomeIds.size());
        for (ResourceLocation biomeId : biomeIds) {
            biomeHolders.add(resolveBiomeHolder(server, biomeId));
        }
        return List.copyOf(biomeHolders);
    }

    private static Holder<Biome> resolveBiomeHolder(MinecraftServer server, ResourceLocation biomeId) {
        HolderGetter<Biome> biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
        return biomeRegistry.get(biomeKey)
            .orElseThrow(() -> new IllegalArgumentException("Unknown biome: " + biomeId));
    }

    private static Holder<DimensionType> resolveDimensionTypeHolder(MinecraftServer server, ResourceLocation dimensionId, ServerLevel overworld) {
        var dimensionTypeRegistry = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
        ResourceKey<DimensionType> dimensionTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, dimensionId);
        Optional<Holder.Reference<DimensionType>> existingType = dimensionTypeRegistry.get(dimensionTypeKey);
        if (existingType.isPresent()) {
            return existingType.get();
        }
        return overworld.dimensionTypeRegistration();
    }

    private static long mixDimensionSeed(long worldSeed, long seedOffset) {
        long mixed = worldSeed;
        mixed ^= 0x9E3779B97F4A7C15L;
        mixed ^= seedOffset * 0xC2B2AE3D27D4EB4FL;
        mixed = Long.rotateLeft(mixed, 27);
        mixed ^= 0x165667B19E3779F9L;
        return mixed;
    }

    private static PreparedActivation prepareActivation(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws Exception {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available");
        }

        long resolvedSeedOffset = parameters.seedOffset() != 0L
            ? parameters.seedOffset()
            : GeneratedDimensionPackWriter.defaultSeedOffset(dimensionId);
        long dimensionSeed = mixDimensionSeed(overworld.getSeed(), resolvedSeedOffset);
        VerseDimensionParameters sanitizedParameters = GeneratedDimensionPackWriter.sanitizeParameters(server, dimensionId, parameters);
        VerseDimensionParameters effectiveParameters = sanitizedParameters.seedOffset() == 0L ? sanitizedParameters.withSeedOffset(resolvedSeedOffset) : sanitizedParameters;
        ChunkPos entryChunk = resolveEntryChunk(resolvedSeedOffset);
        LevelStorageSource.LevelStorageAccess storageAccess = extractStorageAccess(server);
        return new PreparedActivation(server, overworld, storageAccess, dimensionId, effectiveParameters, entryChunk, dimensionSeed);
    }

    private static void logSlowOperation(String action, ResourceLocation dimensionId, long startedAtNanos, long thresholdNanos) {
        long elapsed = System.nanoTime() - startedAtNanos;
        if (elapsed < thresholdNanos) {
            return;
        }

        VerseWorks.LOGGER.info("{} for {} took {} ms", action, dimensionId, elapsed / 1_000_000L);
    }

    private static ServerLevel createInjectedLevel(
        MinecraftServer server,
        LevelStorageSource.LevelStorageAccess storageAccess,
        MutableDerivedLevelData derivedLevelData,
        ResourceKey<Level> levelKey,
        LevelStem levelStem,
        long dimensionSeed
    ) throws Exception {
        for (var constructor : ServerLevel.class.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 12
                || parameterTypes[0] != MinecraftServer.class
                || !Executor.class.isAssignableFrom(parameterTypes[1])
                || parameterTypes[2] != LevelStorageSource.LevelStorageAccess.class
                || parameterTypes[3] != ServerLevelData.class) {
                continue;
            }

            Object progressListener = instantiateProgressListener(parameterTypes[6]);
            return (ServerLevel) constructor.newInstance(
                server,
                extractServerExecutor(server),
                storageAccess,
                derivedLevelData,
                levelKey,
                levelStem,
                progressListener,
                false,
                BiomeManager.obfuscateSeed(dimensionSeed),
                List.of(),
                false,
                null
            );
        }

        throw new IllegalStateException("Could not locate compatible ServerLevel constructor");
    }

    private static CompletableFuture<?> requestChunkWarmup(ServerLevel level, ChunkPos centerChunk, int radius) {
        return VerseChunkWarmup.request(level, centerChunk, radius);
    }

    private static Object instantiateProgressListener(Class<?> listenerType) throws Exception {
        if (listenerType.isInterface()) {
            InvocationHandler handler = (proxy, method, args) -> defaultValue(method.getReturnType());
            return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] { listenerType }, handler);
        }

        try {
            var constructor = listenerType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Executor extractServerExecutor(MinecraftServer server) throws Exception {
        Field cached = serverExecutorField;
        if (cached != null) {
            return (Executor) cached.get(server);
        }

        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (field.getType() != Executor.class) {
                continue;
            }

            field.setAccessible(true);
            Executor executor = (Executor) field.get(server);
            if (executor == null || executor == server) {
                continue;
            }

            serverExecutorField = field;
            return executor;
        }

        throw new IllegalStateException("Could not locate MinecraftServer background executor");
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        return null;
    }

    private static LevelStorageSource.LevelStorageAccess extractStorageAccess(MinecraftServer server) throws Exception {
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (LevelStorageSource.LevelStorageAccess.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (LevelStorageSource.LevelStorageAccess) field.get(server);
            }
        }

        throw new IllegalStateException("Could not locate LevelStorageAccess on MinecraftServer");
    }

    private static void invokePlayerListWorldRegistration(PlayerList playerList, ServerLevel serverLevel) throws Exception {
        for (Method method : PlayerList.class.getMethods()) {
            if (method.getParameterCount() == 1
                && method.getParameterTypes()[0] == ServerLevel.class
                && method.getReturnType() == Void.TYPE) {
                method.invoke(playerList, serverLevel);
                return;
            }
        }

        throw new IllegalStateException("Could not locate PlayerList ServerLevel registration hook");
    }

    private static void invokeLevelSave(ServerLevel level, boolean flush) throws Exception {
        getLevelSaveMethod().invoke(level, null, flush, false);
    }

    private static Method getLevelSaveMethod() throws NoSuchMethodException {
        Method cached = levelSaveMethod;
        if (cached != null) {
            return cached;
        }

        for (Method method : ServerLevel.class.getMethods()) {
            if (method.getReturnType() != Void.TYPE || method.getParameterCount() != 3) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[0] != ProgressListener.class || parameterTypes[1] != boolean.class || parameterTypes[2] != boolean.class) {
                continue;
            }

            method.setAccessible(true);
            levelSaveMethod = method;
            return method;
        }

        throw new NoSuchMethodException("Could not locate ServerLevel save hook");
    }

    private static Field getServerRegistriesField() throws NoSuchFieldException {
        Field cached = serverRegistriesField;
        if (cached != null) {
            return cached;
        }

        Field field = MinecraftServer.class.getDeclaredField("registries");
        field.setAccessible(true);
        serverRegistriesField = field;
        return field;
    }

    public record Result(ServerLevel level, boolean injected, String detail) {
    }

    public enum ActivationMode {
        CRAFT_BACKGROUND,
        FIRST_USE_BLOCKING
    }

    private record PreparedActivation(
        MinecraftServer server,
        ServerLevel overworld,
        LevelStorageSource.LevelStorageAccess storageAccess,
        ResourceLocation dimensionId,
        VerseDimensionParameters parameters,
        ChunkPos entryChunk,
        long dimensionSeed
    ) {
    }
}
