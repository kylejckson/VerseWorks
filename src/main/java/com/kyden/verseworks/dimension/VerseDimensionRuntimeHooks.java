package com.kyden.verseworks.dimension;

import com.kyden.verseworks.Config;
import com.kyden.verseworks.advancement.VerseWorksAdvancements;
import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.block.WarpSpreadHelper;
import com.kyden.verseworks.block.WarpVineBlock;
import com.kyden.verseworks.entity.MeteorEntity;
import com.kyden.verseworks.ritual.HyperBookRitualHooks;
import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public final class VerseDimensionRuntimeHooks {
    private static final double BASE_GRAVITY_PER_TICK = 0.08D;
    private static final int ENTRY_SAFE_SEARCH_RADIUS_BLOCKS = 96;
    private static final int ENTRY_SAFE_SEARCH_STEP_BLOCKS = 2;
    private static final long SERVER_STALL_WARNING_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    private static final int POI_GUARD_SCAN_INTERVAL_TICKS = 20;
    private static final int POI_GUARD_CHUNK_RADIUS = 2;
    private static final int BLOCK_ENTITY_GUARD_CHUNK_RADIUS = 1;
    private static final int PLAYER_ENTRY_RELEASE_CHUNK_RADIUS = 0;
    private static final int PLAYER_ENTRY_WARMUP_CHUNK_RADIUS = 2;
    private static final int LIGHTNING_HAZARD_SCAN_INTERVAL_TICKS = 12;
    private static final int LIGHTNING_DIRECT_STRIKE_INTERVAL_TICKS = 90;
    private static final int LIGHTNING_ARC_MIN_RADIUS = 20;
    private static final int LIGHTNING_ARC_MAX_RADIUS = 100;
    private static final int METEOR_SHOWER_MIN_INTERVAL_TICKS = 20 * 60 * 5;
    private static final int METEOR_SHOWER_MAX_INTERVAL_TICKS = 20 * 60 * 30;
    private static final int METEOR_SHOWER_MIN_PLAYER_DISTANCE = 30;
    private static final int METEOR_SHOWER_MIN_RADIUS = 36;
    private static final int METEOR_SHOWER_MAX_RADIUS = 112;
    private static final int METEOR_SHOWER_TARGET_ATTEMPTS = 18;
    private static final int WARP_SPAWN_MIN_INTERVAL_TICKS = 20 * 60 * 5;
    private static final int WARP_SPAWN_MAX_INTERVAL_TICKS = 20 * 60 * 10;
    private static final int WARP_SPAWN_CHUNK_RADIUS = 8;
    private static final int WARP_SPAWN_ATTEMPTS = 24;
    private static final int INITIAL_WARP_CLUSTER_RADIUS_BLOCKS = 50;
    private static final int RUNTIME_WARP_CLUSTER_RADIUS = 2;
    private static final double POI_GUARD_HORIZONTAL_RANGE = 192.0D;
    private static final double POI_GUARD_VERTICAL_RANGE = 96.0D;
    private static final int MAX_PENDING_DECORATION_JOBS = 24;
    private static final int COMMAND_TELEPORT_WARMUP_RADIUS = 2;
    private static final double COMMAND_TELEPORT_WARMUP_DISTANCE_SQR = 256.0D * 256.0D;
    private static final int COMMAND_TELEPORT_TIMEOUT_TICKS = 20 * 20;
    private static final int HYPER_BOOK_WARMUP_RADIUS = 2;
    private static final int HYPER_BOOK_TELEPORT_TIMEOUT_TICKS = 20 * 10;
    private static final int HYPER_BOOK_READY_TICKET_TICKS = 20 * 30;
    private static final long SLOW_ENTRY_PREPARATION_THRESHOLD_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10);
    private static final long STARTUP_DIMENSION_IDLE_UNLOAD_DELAY_TICKS = 20L * 5L;
    private static final long STARTUP_PREPARATION_STAGGER_TICKS = 20L;
    private static final long RUNTIME_DIMENSION_IDLE_SLEEP_DELAY_TICKS = 20L * 60L * 5L;
    private static final long RUNTIME_DIMENSION_IDLE_UNLOAD_DELAY_TICKS = 20L * 60L * 5L;
    private static final long RUNTIME_DIMENSION_IDLE_HOUSEKEEPING_INTERVAL_TICKS = 20L * 15L;
    private static final Block DEFAULT_SAFETY_PLATFORM_BLOCK = Blocks.COBBLESTONE;
    private static final int DEFAULT_SAFETY_PLATFORM_RADIUS = 2;
    private static final Component EXTERNAL_VERSE_ENTRY_BLOCKED_MESSAGE = Component.literal(
        "This dimension is too difficult to reach, use a hyperbook instead to tunnel space-time and teleport into it"
    ).withStyle(ChatFormatting.YELLOW);
    // These caches coordinate live-only runtime state for generated dimensions and are rebuilt
    // from saved metadata whenever a level is reactivated.
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Double> TIME_RATE_ACCUMULATORS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Deque<DecorationJob>> PENDING_DECORATION_JOBS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, PendingSpawnPreparation> PENDING_SPAWN_PREPARATIONS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, BlockPos> PREPARED_ENTRY_POINTS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, PreparationWarmupTicket> ENTRY_PREPARATION_TICKETS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, ChunkDecorationState> DECORATED_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Map<Integer, PendingCommandTeleport>> PENDING_COMMAND_TELEPORTS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Map<Integer, PendingCommandEntry>> PENDING_COMMAND_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Map<Integer, PendingHyperBookTeleport>> PENDING_HYPER_BOOK_TELEPORTS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Set<UUID>> PAUSED_POI_VILLAGERS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> NEXT_METEOR_SHOWER_TICKS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> NEXT_WARP_SPAWN_TICKS = new ConcurrentHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> LAST_ACTIVE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingPlayerEntry> PENDING_PLAYER_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<UUID, AuthorizedVerseEntry> AUTHORIZED_VERSE_ENTRIES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_TELEPORT_MESSAGE_COORDINATES = ConcurrentHashMap.newKeySet();
    private static final String CORRUPTION_WARNINGS_TAG = "VerseWorksCorruptionWarnings";
    private static final Set<net.minecraft.resources.ResourceKey<Level>> LEGACY_WEATHER_WARNING_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final Set<net.minecraft.resources.ResourceKey<Level>> BLOCK_ENTITY_GUARD_WARNING_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final Set<net.minecraft.resources.ResourceKey<Level>> LEVEL_DATA_WRAP_WARNING_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final Set<String> STARTUP_LIFECYCLE_SYNCED_SERVERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> STARTUP_PLAYER_DIMENSIONS_SCANNED_SERVERS = ConcurrentHashMap.newKeySet();
    private static final Set<String> ACTIVE_PACK_ENABLE_REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ResourceLocation> STARTUP_EXPECTED_PLAYER_DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> STARTUP_PROTECTED_PLAYER_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> STARTUP_PREPARED_DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final Deque<StartupPreparedDimension> STARTUP_PREPARATION_QUEUE = new ArrayDeque<>();
    private static StartupPreparedDimension activeStartupPreparation;
    private static long nextStartupPreparationTick;
    private static Field blockEntityTickersField;
    private static Field pendingBlockEntityTickersField;
    private static Field levelDataField;
    private static Field serverLevelDataField;
    private static volatile Thread serverTickThread;
    private static volatile MinecraftServer activeServer;
    private static volatile long lastServerTickNanos;
    private static volatile long lastServerTickDumpNanos;
    private static volatile String lastServerTickDimension = "unknown";
    private static volatile boolean stallWatchdogStarted;
    private static volatile boolean shutdownInProgress;

    private VerseDimensionRuntimeHooks() {
    }

    public static void register() {
        startServerStallWatchdog();
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onEntityTravelToDimension);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onTeleportCommand);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onLevelSave);
        NeoForge.EVENT_BUS.addListener(VerseDimensionRuntimeHooks::onServerStopping);
    }

    public static boolean isShutdownInProgress() {
        return shutdownInProgress;
    }

    public static boolean isShutdownInProgress(MinecraftServer server) {
        return shutdownInProgress && server != null;
    }

    public static void applyCreationRules(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters) {
        applyCreationRules(server, level, parameters, EntryPreparationMode.FULL);
    }

    public static void applyCreationRules(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, EntryPreparationMode entryPreparationMode) {
        refreshDimensionRules(level, parameters);
        VerseDimensionParameterSync.syncToPlayers(level, parameters);
        resetTransientDimensionState(level.dimension());
        if (entryPreparationMode == EntryPreparationMode.FULL) {
            queueEntryPreparation(level, parameters);
        } else {
            VerseWorks.LOGGER.info("Deferred initial entry preparation for {}", level.dimension().location());
        }
    }

    public static void ensureEntryPreparationScheduled(MinecraftServer server, ServerLevel level) {
        if (isShutdownInProgress(server)) {
            return;
        }

        resolveParameters(level).ifPresent(parameters -> ensureEntryPreparationScheduled(server, level, parameters));
    }

    public static void ensureEntryPreparationScheduled(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters) {
        ensureEntryPreparationScheduled(server, level, parameters, EntryPreparationMode.FULL);
    }

    public static void ensureEntryPreparationScheduled(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, EntryPreparationMode entryPreparationMode) {
        if (isShutdownInProgress(server)) {
            return;
        }

        if (!isVerseWorksLevel(level)) {
            return;
        }

        refreshDimensionRules(level, parameters);

        if (entryPreparationMode == EntryPreparationMode.DEFERRED) {
            return;
        }

        Optional<Vec3> preparedArrival = preparedEntryArrival(level);
        if (preparedArrival.isPresent() || PENDING_SPAWN_PREPARATIONS.containsKey(level.dimension())) {
            return;
        }

        VerseWorks.LOGGER.info("Scheduling VerseWorks entry preparation for {}", level.dimension().location());
        queueEntryPreparation(level, parameters);
    }

    private static void refreshDimensionRules(ServerLevel level, VerseDimensionParameters parameters) {
        ensureMutableWeatherData(level);
        VerseDimensionCatalog.remember(level.dimension().location(), parameters);
        LAST_ACTIVE_TICKS.put(level.dimension(), level.getGameTime());
        if (parameters.timeOfDay() != null || parameters.forcesPermanentNight()) {
            level.setDayTime(parameters.effectiveResolvedTimeOfDay());
        }
        applyWeatherRules(level, parameters);
    }

    private static void resetTransientDimensionState(net.minecraft.resources.ResourceKey<Level> levelKey) {
        PREPARED_ENTRY_POINTS.remove(levelKey);
        DECORATED_CHUNKS.remove(levelKey);
        PENDING_DECORATION_JOBS.remove(levelKey);
        NEXT_METEOR_SHOWER_TICKS.remove(levelKey);
        NEXT_WARP_SPAWN_TICKS.remove(levelKey);
    }

    private static void queueEntryPreparation(ServerLevel level, VerseDimensionParameters parameters) {
        long startedAtNanos = System.nanoTime();
        ChunkPos entryChunk = LiveDimensionInstantiator.getEntryChunk(level);
        int searchRadius = shouldPreferNaturalSpawn(parameters) ? naturalSpawnSearchRadius(parameters) : 0;
        CompletableFuture<?> warmupFuture = loadChunks(level, entryChunk, searchRadius);
        PendingSpawnPreparation pending = new PendingSpawnPreparation(
            parameters,
            entryChunk,
            searchRadius,
            warmupFuture
        );
        PENDING_SPAWN_PREPARATIONS.put(level.dimension(), pending);
        ENTRY_PREPARATION_TICKETS.put(level.dimension(), new PreparationWarmupTicket(entryChunk, searchRadius));
        logSlowEntryPreparation(level, startedAtNanos, "Queued entry preparation");
    }

    public static boolean isEntryPreparationComplete(ServerLevel level) {
        Deque<DecorationJob> jobs = PENDING_DECORATION_JOBS.get(level.dimension());
        return !PENDING_SPAWN_PREPARATIONS.containsKey(level.dimension()) && (jobs == null || jobs.isEmpty());
    }

    public static boolean isPreparedEntryReady(ServerLevel level) {
        if (PENDING_SPAWN_PREPARATIONS.containsKey(level.dimension())) {
            return false;
        }

        return preparedEntryArrival(level).isPresent();
    }

    public static boolean hasPendingEntryPreparation(ServerLevel level) {
        return PENDING_SPAWN_PREPARATIONS.containsKey(level.dimension());
    }

    public static boolean isDimensionSleeping(ServerLevel level) {
        return shouldSleepRuntimeLevel(level);
    }

    public static boolean isDimensionPinned(ServerLevel level) {
        return isChunkForcedPinned(level);
    }

    private static boolean isChunkForcedPinned(ServerLevel level) {
        return !level.getForcedChunks().isEmpty();
    }

    public static long idleTicks(ServerLevel level) {
        if (!isVerseWorksLevel(level) || !level.players().isEmpty() || hasPendingActivity(level)) {
            return 0L;
        }

        Long lastActiveTick = LAST_ACTIVE_TICKS.get(level.dimension());
        if (lastActiveTick == null) {
            return level.getGameTime();
        }

        return Math.max(0L, level.getGameTime() - lastActiveTick);
    }

    public static boolean restoresOnStartup(MinecraftServer server, ResourceLocation dimensionId) {
        return VerseDimensionLifecycleSavedData.getIfPresent(server)
            .flatMap(data -> data.entry(dimensionId))
            .map(VerseDimensionLifecycleSavedData.Entry::shouldRestoreOnStartup)
            .orElse(false);
    }

    public static Optional<Vec3> preparedEntryArrival(ServerLevel level) {
        BlockPos preparedArrival = PREPARED_ENTRY_POINTS.get(level.dimension());
        if (preparedArrival == null) {
            return Optional.empty();
        }

        return Optional.of(Vec3.atBottomCenterOf(preparedArrival));
    }

    public static Vec3 findSafeArrival(ServerLevel destination) {
        ChunkPos entryChunk = LiveDimensionInstantiator.getEntryChunk(destination);
        if (!ensureChunkAvailable(destination, entryChunk.x, entryChunk.z, true)) {
            int fallbackY = Math.max(destination.dimensionType().minY() + 8, 96);
            return Vec3.atBottomCenterOf(new BlockPos(entryChunk.getMiddleBlockX(), fallbackY, entryChunk.getMiddleBlockZ()));
        }

        VerseDimensionParameters parameters = resolveParameters(destination).orElse(new VerseDimensionParameters(0, Blocks.GRASS_BLOCK, VerseDimensionWorldType.NORMAL, java.util.List.of()));
        Optional<Vec3> preparedArrival = preparedEntryArrival(destination);
        if (preparedArrival.isPresent()) {
            return preparedArrival.get();
        }

        BlockPos resolvedArrival = resolvePreparedEntryPoint(destination, parameters, entryChunk);
        return Vec3.atBottomCenterOf(resolvedArrival);
    }

    public static void registerPendingPlayerEntry(ServerPlayer player, ServerLevel destination, Vec3 arrival) {
        registerPendingPlayerEntry(player, destination, arrival, true);
    }

    public static void registerPendingPlayerEntry(ServerPlayer player, ServerLevel destination, Vec3 arrival, boolean recordVisitOnArrival) {
        if (isShutdownInProgress(destination.getServer())) {
            return;
        }

        if (!isVerseWorksLevel(destination)) {
            return;
        }

        PendingPlayerEntry pendingEntry = new PendingPlayerEntry(destination.dimension(), arrival, player.getYRot(), player.getXRot(), recordVisitOnArrival);
        PENDING_PLAYER_ENTRIES.put(player.getUUID(), pendingEntry);
        sendCorruptionWarnings(player, destination);
        stabilizePendingPlayerEntry(player, destination, pendingEntry);
    }

    public static boolean queueHyperBookTeleport(ServerPlayer player, ServerLevel destination, Vec3 target, float yRot, float xRot) {
        return !ensureHyperBookTargetWarmup(player, destination, target, yRot, xRot, true);
    }

    public static boolean ensureHyperBookTargetWarmup(ServerPlayer player, ServerLevel destination, Vec3 target, float yRot, float xRot, boolean notifyWhenReady) {
        if (isShutdownInProgress(destination.getServer())) {
            return false;
        }

        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(target));
        if (hasReadyHyperBookTeleport(player, destination, targetChunk)) {
            return true;
        }

        boolean alreadyTicking = destination.getChunkSource().isPositionTicking(targetChunk.toLong());
        markDimensionActive(destination);
        var future = warmChunks(destination, targetChunk, HYPER_BOOK_WARMUP_RADIUS);
        Map<Integer, PendingHyperBookTeleport> pendingTeleports = PENDING_HYPER_BOOK_TELEPORTS.computeIfAbsent(destination.dimension(), ignored -> new ConcurrentHashMap<>());
        PendingHyperBookTeleport pending = new PendingHyperBookTeleport(player, targetChunk, future, destination.getGameTime(), alreadyTicking ? destination.getGameTime() : 0L, alreadyTicking, notifyWhenReady);
        PendingHyperBookTeleport previous = pendingTeleports.put(player.getId(), pending);
        if (previous != null) {
            releaseWarmupTicket(destination, previous.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
        }
        return alreadyTicking;
    }

    public static boolean hasReadyHyperBookTeleport(ServerPlayer player, ServerLevel destination, Vec3 target) {
        return hasReadyHyperBookTeleport(player, destination, new ChunkPos(BlockPos.containing(target)));
    }

    private static boolean hasReadyHyperBookTeleport(ServerPlayer player, ServerLevel destination, ChunkPos targetChunk) {
        Map<Integer, PendingHyperBookTeleport> pendingTeleports = PENDING_HYPER_BOOK_TELEPORTS.get(destination.dimension());
        if (pendingTeleports == null || pendingTeleports.isEmpty()) {
            return false;
        }

        PendingHyperBookTeleport pending = pendingTeleports.get(player.getId());
        return pending != null
            && pending.notified()
            && pending.targetChunk().equals(targetChunk)
            && !pending.warmupFuture().isCompletedExceptionally();
    }

    public static void queueCommandEntry(Entity entity, ServerLevel destination) {
        if (entity == null || entity.isRemoved() || isShutdownInProgress(destination.getServer())) {
            return;
        }

        cancelPendingCommandEntries(entity);
        markDimensionActive(destination);
        if (!hasPendingEntryPreparation(destination) && !LiveDimensionInstantiator.isReadyForEntry(destination)) {
            LiveDimensionInstantiator.requestEntryWarmup(destination);
        }

        PENDING_COMMAND_ENTRIES
            .computeIfAbsent(destination.dimension(), ignored -> new ConcurrentHashMap<>())
            .put(entity.getId(), new PendingCommandEntry(entity, destination.getGameTime()));
    }

    public static boolean hasPendingTeleportWork() {
        return PENDING_COMMAND_TELEPORTS.values().stream().anyMatch(map -> map != null && !map.isEmpty())
            || PENDING_COMMAND_ENTRIES.values().stream().anyMatch(map -> map != null && !map.isEmpty())
            || PENDING_HYPER_BOOK_TELEPORTS.values().stream().anyMatch(map -> map != null && !map.isEmpty());
    }

    public static void cancelDimensionWork(MinecraftServer server, ResourceLocation dimensionId) {
        net.minecraft.resources.ResourceKey<Level> levelKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(levelKey);

        Map<Integer, PendingCommandTeleport> pendingCommandTeleports = PENDING_COMMAND_TELEPORTS.remove(levelKey);
        if (pendingCommandTeleports != null) {
            for (PendingCommandTeleport pending : pendingCommandTeleports.values()) {
                PENDING_TELEPORT_MESSAGE_COORDINATES.remove(pending.coordinateKey());
                if (level != null) {
                    releaseWarmupTicket(level, pending.targetChunk(), COMMAND_TELEPORT_WARMUP_RADIUS);
                }
                if (pending.entity() instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("Teleport canceled; the destination dimension has collapsed.").withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
        }

        Map<Integer, PendingHyperBookTeleport> pendingHyperBookTeleports = PENDING_HYPER_BOOK_TELEPORTS.remove(levelKey);
        if (pendingHyperBookTeleports != null) {
            for (PendingHyperBookTeleport pending : pendingHyperBookTeleports.values()) {
                if (level != null) {
                    releaseWarmupTicket(level, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
                }
                pending.player().sendSystemMessage(Component.literal("That Hyperbook's destination has collapsed.").withStyle(net.minecraft.ChatFormatting.RED));
            }
        }

        PENDING_COMMAND_ENTRIES.remove(levelKey);
        PENDING_PLAYER_ENTRIES.entrySet().removeIf(entry -> entry.getValue().levelKey().equals(levelKey));
        if (level != null) {
            releaseEntryPreparationTicket(level);
        }
        clearRuntimeLevelState(levelKey);
    }

    public static boolean unloadRuntimeLevelForCollapse(ServerLevel level) {
        for (long forcedChunk : level.getForcedChunks().toLongArray()) {
            ChunkPos chunkPos = new ChunkPos(forcedChunk);
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        return unloadRuntimeLevel(level, true, true, "dimension collapse");
    }

    public static void forgetStartupDimension(ResourceLocation dimensionId) {
        STARTUP_EXPECTED_PLAYER_DIMENSIONS.entrySet().removeIf(entry -> dimensionId.equals(entry.getValue()));
        STARTUP_PROTECTED_PLAYER_DIMENSIONS.remove(dimensionId);
        STARTUP_PREPARED_DIMENSIONS.remove(dimensionId);
        STARTUP_PREPARATION_QUEUE.removeIf(preparation -> preparation.dimensionId().equals(dimensionId));
        if (activeStartupPreparation != null && activeStartupPreparation.dimensionId().equals(dimensionId)) {
            activeStartupPreparation = null;
        }
        refreshStartupProtectedPlayerDimensions();
    }

    public static void syncLifecycleState(MinecraftServer server, String reason) {
        syncLifecyclePersistence(server, false, reason);
    }

    public static void performHyperBookTeleport(ServerPlayer player, ServerLevel destination, Vec3 target, float yRot, float xRot) {
        markDimensionActive(destination);
        boolean crossDimensionEntry = player.level() != destination;
        if (player.level() == destination) {
            player.moveTo(target.x, target.y, target.z, yRot, xRot);
            player.connection.teleport(target.x, target.y, target.z, yRot, xRot);
            player.connection.resetPosition();
        } else {
            teleportWithVerseEntryAuthorization(player, destination, target, yRot, xRot);
        }

        if (isVerseWorksLevel(destination)) {
            registerPendingPlayerEntry(player, destination, target, crossDimensionEntry);
            VerseWorksAdvancements.award(player, VerseWorksAdvancements.ENTER_A_NEW_WORLD, "entered_world");
        }
        clearReadyHyperBookWarmup(player, destination, new ChunkPos(BlockPos.containing(target)));
    }

    private static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (shutdownInProgress) {
            return;
        }

        if (!isVerseWorksLevel(level)) {
            return;
        }

        ensureMutableWeatherData(level);
        if (shouldSleepRuntimeLevel(level)) {
            if (level.getGameTime() % RUNTIME_DIMENSION_IDLE_HOUSEKEEPING_INTERVAL_TICKS == 0L) {
                pruneIdleRuntimeState(level);
            }
            return;
        }

        installBlockEntityTickGuards(level);
        stabilizePendingPlayerEntries(level);
        stabilizePendingHyperBookSourceWaits(level);

        if (level.getGameTime() % POI_GUARD_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        regulatePoiVillagers(level);
    }

    private static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!VerseWorks.MODID.equals(event.getDimension().location().getNamespace())) {
            return;
        }

        if (consumeAuthorizedVerseEntry(player, event.getDimension())) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null || isShutdownInProgress(server)) {
            return;
        }

        ServerLevel destination = server.getLevel(event.getDimension());
        if (destination == null) {
            event.setCanceled(true);
            player.sendSystemMessage(EXTERNAL_VERSE_ENTRY_BLOCKED_MESSAGE);
            return;
        }

        ensureEntryPreparationScheduled(server, destination);
        if (LiveDimensionInstantiator.isRuntimeLevel(destination) && !LiveDimensionInstantiator.isReadyForEntry(destination)) {
            LiveDimensionInstantiator.requestEntryWarmup(destination);
        }

        if (hasPendingEntryPreparation(destination)
            || preparedEntryArrival(destination).isEmpty()
            || (LiveDimensionInstantiator.isRuntimeLevel(destination) && !LiveDimensionInstantiator.isReadyForEntry(destination))) {
            event.setCanceled(true);
            player.sendSystemMessage(EXTERNAL_VERSE_ENTRY_BLOCKED_MESSAGE);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        markServerTick(level);

        if (shutdownInProgress) {
            return;
        }

        if (level == level.getServer().overworld()) {
            ensureLifecycleStartupState(level.getServer());
            VerseDimensionCatalog.ensureLoaded(level.getServer());
            processStartupPreparationQueue(level.getServer(), level.getGameTime());
            VerseChunkWarmup.tickAll(level.getServer());
            processPendingVerseDimensionWork(level.getServer(), level);
            if (level.getGameTime() % RUNTIME_DIMENSION_IDLE_HOUSEKEEPING_INTERVAL_TICKS == 0L
                && !HyperBookRitualHooks.hasRecentRitualActivity(level)) {
                maintainSleepingRuntimeLevels(level.getServer(), level);
            }
        }

        processPendingCommandTeleports(level);
        processPendingCommandEntries(level);
        processPendingHyperBookTeleports(level);

        Optional<VerseDimensionParameters> parameters = resolveParameters(level);
        if (parameters.isEmpty()) {
            return;
        }

        processPendingSpawnPreparation(level);
        processDecorationJobs(level);

        if (shouldSleepRuntimeLevel(level)) {
            if (level.getGameTime() % RUNTIME_DIMENSION_IDLE_HOUSEKEEPING_INTERVAL_TICKS == 0L) {
                pruneIdleRuntimeState(level);
            }
            return;
        }

        VerseDimensionParameters dimensionParameters = parameters.get();
        applyTimeRules(level, dimensionParameters);
        applyWeatherRules(level, dimensionParameters);
        applyLightningHazards(level, dimensionParameters);
        applyMeteorShowers(level, dimensionParameters);
        applyWarpSpawns(level, dimensionParameters);
    }

    private static void processPendingVerseDimensionWork(MinecraftServer server, ServerLevel excludedLevel) {
        for (ServerLevel level : new ArrayList<>(server.forgeGetWorldMap().values())) {
            if (level == null || level == excludedLevel || !isVerseWorksLevel(level)) {
                continue;
            }

            if (resolveParameters(level).isEmpty()) {
                continue;
            }

            processPendingSpawnPreparation(level);
            processDecorationJobs(level);
            processPendingCommandEntries(level);
            processPendingHyperBookTeleports(level);
        }
    }

    private static void regulatePoiVillagers(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }

        Set<UUID> pausedVillagers = PAUSED_POI_VILLAGERS.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet());
        Set<UUID> seenVillagers = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            AABB searchBox = player.getBoundingBox().inflate(POI_GUARD_HORIZONTAL_RANGE, POI_GUARD_VERTICAL_RANGE, POI_GUARD_HORIZONTAL_RANGE);
            for (Villager villager : level.getEntitiesOfClass(Villager.class, searchBox)) {
                UUID villagerId = villager.getUUID();
                if (!seenVillagers.add(villagerId)) {
                    continue;
                }

                boolean ready = hasEntityTickingChunkRing(level, villager.chunkPosition(), POI_GUARD_CHUNK_RADIUS);
                if (!ready) {
                    if (!villager.isNoAi()) {
                        villager.setNoAi(true);
                    }
                    pausedVillagers.add(villagerId);
                    continue;
                }

                if (pausedVillagers.remove(villagerId) && villager.isNoAi()) {
                    villager.setNoAi(false);
                }
            }
        }
    }

    private static void stabilizePendingPlayerEntries(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            PendingPlayerEntry pendingEntry = PENDING_PLAYER_ENTRIES.get(player.getUUID());
            if (pendingEntry == null) {
                continue;
            }

            if (!pendingEntry.levelKey().equals(level.dimension())) {
                PENDING_PLAYER_ENTRIES.remove(player.getUUID(), pendingEntry);
                player.setNoGravity(false);
                continue;
            }

            ChunkPos arrivalChunk = new ChunkPos(BlockPos.containing(pendingEntry.arrival()));
            if (hasEntityTickingChunkRing(level, arrivalChunk, PLAYER_ENTRY_RELEASE_CHUNK_RADIUS)) {
                PENDING_PLAYER_ENTRIES.remove(player.getUUID(), pendingEntry);
                releaseWarmupTicket(level, arrivalChunk, PLAYER_ENTRY_WARMUP_CHUNK_RADIUS);
                player.setNoGravity(false);
                player.connection.resetPosition();
                if (pendingEntry.recordVisitOnArrival()) {
                    recordDimensionVisit(level.getServer(), level.dimension().location());
                }
                continue;
            }

            stabilizePendingPlayerEntry(player, level, pendingEntry);
        }
    }

    private static void stabilizePendingPlayerEntry(ServerPlayer player, ServerLevel level, PendingPlayerEntry pendingEntry) {
        Vec3 arrival = pendingEntry.arrival();
        ensureArrivalSupport(level, arrival);
        player.setNoGravity(true);
        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.moveTo(arrival.x(), arrival.y(), arrival.z(), pendingEntry.yRot(), pendingEntry.xRot());
        player.connection.teleport(arrival.x(), arrival.y(), arrival.z(), pendingEntry.yRot(), pendingEntry.xRot());
        player.connection.resetPosition();
        warmChunks(level, new ChunkPos(BlockPos.containing(arrival)), PLAYER_ENTRY_WARMUP_CHUNK_RADIUS);
    }

    private static void stabilizePendingHyperBookSourceWaits(ServerLevel level) {
    }

    private static void clearPendingHyperBookSourceWait(ServerPlayer player) {
    }

    private static void ensureArrivalSupport(ServerLevel level, Vec3 arrival) {
        Optional<VerseDimensionParameters> parameters = resolveParameters(level);
        if (parameters.isEmpty()) {
            return;
        }

        VerseDimensionParameters dimensionParameters = parameters.get();
        if (!dimensionParameters.requiresSafetyPlatformSpawn()) {
            return;
        }

        BlockPos center = BlockPos.containing(arrival).below();
        ensurePlatform(level, center, platformBlock(dimensionParameters), platformRadius(dimensionParameters));
        clearHeadroom(level, center.above(), 4);
    }

    private static void ensureMutableWeatherData(ServerLevel level) {
        if (level.getLevelData() instanceof MutableDerivedLevelData) {
            return;
        }

        if (!(level.getLevelData() instanceof DerivedLevelData derivedLevelData)) {
            return;
        }

        try {
            MutableDerivedLevelData mutableLevelData = new MutableDerivedLevelData(level.getServer().getWorldData(), derivedLevelData);
            getLevelDataField().set(level, mutableLevelData);
            getServerLevelDataField().set(level, mutableLevelData);
            LEGACY_WEATHER_WARNING_DIMENSIONS.remove(level.dimension());
        } catch (ReflectiveOperationException exception) {
            if (LEVEL_DATA_WRAP_WARNING_DIMENSIONS.add(level.dimension())) {
                VerseWorks.LOGGER.warn("VerseWorks could not install mutable weather data for {}", level.dimension().location(), exception);
            }
        }
    }

    private static Field getLevelDataField() throws NoSuchFieldException {
        if (levelDataField == null) {
            Field field = Level.class.getDeclaredField("levelData");
            field.setAccessible(true);
            levelDataField = field;
        }
        return levelDataField;
    }

    private static Field getServerLevelDataField() throws NoSuchFieldException {
        if (serverLevelDataField == null) {
            Field field = ServerLevel.class.getDeclaredField("serverLevelData");
            field.setAccessible(true);
            serverLevelDataField = field;
        }
        return serverLevelDataField;
    }

    private static boolean hasEntityTickingChunkRing(ServerLevel level, ChunkPos centerChunk, int radius) {
        for (int chunkX = centerChunk.x - radius; chunkX <= centerChunk.x + radius; chunkX++) {
            for (int chunkZ = centerChunk.z - radius; chunkZ <= centerChunk.z + radius; chunkZ++) {
                if (!level.getChunkSource().isPositionTicking(ChunkPos.asLong(chunkX, chunkZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void installBlockEntityTickGuards(ServerLevel level) {
        try {
            Field activeField = getBlockEntityTickersField();
            Field pendingField = getPendingBlockEntityTickersField();
            wrapBlockEntityTickerList(level, (List<TickingBlockEntity>) activeField.get(level));
            wrapBlockEntityTickerList(level, (List<TickingBlockEntity>) pendingField.get(level));
        } catch (ReflectiveOperationException exception) {
            if (BLOCK_ENTITY_GUARD_WARNING_DIMENSIONS.add(level.dimension())) {
                VerseWorks.LOGGER.warn("VerseWorks could not install block entity tick guards for {}", level.dimension().location(), exception);
            }
        }
    }

    private static Field getBlockEntityTickersField() throws NoSuchFieldException {
        if (blockEntityTickersField == null) {
            Field field = Level.class.getDeclaredField("blockEntityTickers");
            field.setAccessible(true);
            blockEntityTickersField = field;
        }
        return blockEntityTickersField;
    }

    private static Field getPendingBlockEntityTickersField() throws NoSuchFieldException {
        if (pendingBlockEntityTickersField == null) {
            Field field = Level.class.getDeclaredField("pendingBlockEntityTickers");
            field.setAccessible(true);
            pendingBlockEntityTickersField = field;
        }
        return pendingBlockEntityTickersField;
    }

    private static void wrapBlockEntityTickerList(ServerLevel level, List<TickingBlockEntity> tickers) {
        for (int index = 0; index < tickers.size(); index++) {
            TickingBlockEntity ticker = tickers.get(index);
            if (ticker instanceof GuardedTickingBlockEntity) {
                continue;
            }
            tickers.set(index, new GuardedTickingBlockEntity(level, ticker));
        }
    }

    private static boolean hasBlockEntityTickingChunkRing(ServerLevel level, BlockPos pos, int radius) {
        return hasEntityTickingChunkRing(level, new ChunkPos(pos), radius);
    }

    private static synchronized void startServerStallWatchdog() {
        if (stallWatchdogStarted) {
            return;
        }

        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Thread tickThread = serverTickThread;
                long lastTick = lastServerTickNanos;
                if (tickThread == null || lastTick == 0L) {
                    continue;
                }

                long now = System.nanoTime();
                if (now - lastTick < SERVER_STALL_WARNING_NANOS || lastServerTickDumpNanos >= lastTick) {
                    continue;
                }

                StackTraceElement[] stackTrace = tickThread.getStackTrace();
                if (isExpectedIdleServerWait(activeServer, stackTrace) || isExpectedIntegratedPauseOrShutdown(activeServer, stackTrace)) {
                    lastServerTickDumpNanos = now;
                    continue;
                }

                lastServerTickDumpNanos = now;
                VerseWorks.LOGGER.error(
                    "VerseWorks detected a possible server stall after {} ms in {}. Server thread '{}' stack follows.",
                    (now - lastTick) / 1_000_000L,
                    lastServerTickDimension,
                    tickThread.getName()
                );
                for (StackTraceElement element : stackTrace) {
                    VerseWorks.LOGGER.error("  at {}", element);
                }
            }
        }, "VerseWorks Stall Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        stallWatchdogStarted = true;
    }

    private static void markServerTick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (activeServer != server) {
            shutdownInProgress = false;
            lastServerTickDumpNanos = 0L;
        }
        serverTickThread = Thread.currentThread();
        activeServer = server;
        lastServerTickNanos = System.nanoTime();
        lastServerTickDimension = level.dimension().location().toString();
    }

    private static boolean isExpectedIdleServerWait(MinecraftServer server, StackTraceElement[] stackTrace) {
        if (server == null) {
            return false;
        }

        boolean hasTickWaitFrame = false;
        for (StackTraceElement element : stackTrace) {
            if (!"net.minecraft.server.MinecraftServer".equals(element.getClassName())) {
                continue;
            }

            String methodName = element.getMethodName();
            if ("waitUntilNextTick".equals(methodName) || "waitForTasks".equals(methodName)) {
                hasTickWaitFrame = true;
                break;
            }
        }

        if (!hasTickWaitFrame) {
            return false;
        }

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if ("net.minecraft.server.level.ChunkMap".equals(className)
                || "net.minecraft.server.level.ServerChunkCache".equals(className)
                || "net.minecraft.server.level.ServerLevel".equals(className)
                || "com.kyden.verseworks.dimension.LiveDimensionInstantiator".equals(className)) {
                return false;
            }
        }

        if (server.getPlayerList().getPlayerCount() == 0) {
            return true;
        }

        return "IntegratedServer".equals(server.getClass().getSimpleName());
    }

    private static boolean isExpectedIntegratedPauseOrShutdown(MinecraftServer server, StackTraceElement[] stackTrace) {
        if (server == null || server.isDedicatedServer()) {
            return false;
        }

        if (server.getPlayerList().getPlayerCount() == 0) {
            return true;
        }

        if (stackTrace.length <= 3
            && stackTrace.length > 0
            && "net.minecraft.server.MinecraftServer".equals(stackTrace[0].getClassName())
            && "runServer".equals(stackTrace[0].getMethodName())) {
            return true;
        }

        for (StackTraceElement element : stackTrace) {
            if (("net.minecraft.server.MinecraftServer".equals(element.getClassName())
                    && ("stopServer".equals(element.getMethodName())
                        || "saveEverything".equals(element.getMethodName())
                        || "saveAllChunks".equals(element.getMethodName())))
                || ("net.minecraft.client.server.IntegratedServer".equals(element.getClassName())
                    && "stopServer".equals(element.getMethodName()))
                || ("net.minecraft.server.level.ChunkMap".equals(element.getClassName())
                    && "saveAllChunks".equals(element.getMethodName()))) {
                return true;
            }
        }

        return false;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        Optional<VerseDimensionParameters> parameters = resolveParameters((ServerLevel) player.level());
        if (parameters.isEmpty()) {
            return;
        }

        VerseDimensionParameters dimensionParameters = parameters.get();
        applyGravity(player, dimensionParameters);
        if (player.tickCount % 8 == 0 && WarpVineBlock.hasNearbyWarpVine((ServerLevel) player.level(), player.blockPosition())) {
            WarpVineBlock.spawnDreadParticles((ServerLevel) player.level(), player);
        }
        if (WarpVineBlock.isTouchingWarpVine(player)) {
            WarpSpreadHelper.applyWarpHazard(player.level(), player, true);
        }
    }

    private static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!isVerseWorksLevel(level)) {
            return;
        }

        double distanceSqr = event.getPrev().distanceToSqr(event.getTarget());
        if (distanceSqr < COMMAND_TELEPORT_WARMUP_DISTANCE_SQR) {
            return;
        }

        ChunkPos targetChunk = new ChunkPos(BlockPos.containing(event.getTarget()));
        if (level.getChunkSource().isPositionTicking(targetChunk.toLong())) {
            return;
        }

        double originalTargetX = event.getTargetX();
        double originalTargetY = event.getTargetY();
        double originalTargetZ = event.getTargetZ();
        String coordinateKey = teleportCoordinateKey(originalTargetX, originalTargetY, originalTargetZ);

        event.setTargetX(event.getPrevX());
        event.setTargetY(event.getPrevY());
        event.setTargetZ(event.getPrevZ());
        markDimensionActive(level);
        var future = warmChunks(level, targetChunk, COMMAND_TELEPORT_WARMUP_RADIUS);
        PENDING_TELEPORT_MESSAGE_COORDINATES.add(coordinateKey);
        PENDING_COMMAND_TELEPORTS
            .computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
            .put(player.getId(), new PendingCommandTeleport(player, originalTargetX, originalTargetY, originalTargetZ, player.getYRot(), player.getXRot(), targetChunk, future, level.getGameTime(), coordinateKey));

        player.sendSystemMessage(Component.literal(
            "Preparing teleport destination in " + level.dimension().location() + " at "
                + (int) originalTargetX + ", " + (int) originalTargetY + ", " + (int) originalTargetZ + "..."
        ));
    }

    private static void processPendingCommandTeleports(ServerLevel level) {
        Map<Integer, PendingCommandTeleport> pendingTeleports = PENDING_COMMAND_TELEPORTS.get(level.dimension());
        if (pendingTeleports == null || pendingTeleports.isEmpty()) {
            return;
        }

        List<Integer> completed = new ArrayList<>();
        for (Map.Entry<Integer, PendingCommandTeleport> entry : pendingTeleports.entrySet()) {
            PendingCommandTeleport pending = entry.getValue();
            if (pending.entity().isRemoved() || pending.entity().level() != level) {
                releaseWarmupTicket(level, pending.targetChunk(), COMMAND_TELEPORT_WARMUP_RADIUS);
                PENDING_TELEPORT_MESSAGE_COORDINATES.remove(pending.coordinateKey());
                completed.add(entry.getKey());
                continue;
            }

            if (pending.warmupFuture().isCompletedExceptionally()) {
                VerseWorks.LOGGER.warn("VerseWorks teleport warmup failed for {} chunk {},{}", level.dimension().location(), pending.targetChunk().x, pending.targetChunk().z);
                releaseWarmupTicket(level, pending.targetChunk(), COMMAND_TELEPORT_WARMUP_RADIUS);
                PENDING_TELEPORT_MESSAGE_COORDINATES.remove(pending.coordinateKey());
                if (pending.entity() instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("Teleport warmup failed for destination chunk " + pending.targetChunk().x + ", " + pending.targetChunk().z));
                }
                completed.add(entry.getKey());
                continue;
            }

            boolean timedOut = level.getGameTime() - pending.queuedAtGameTime() >= COMMAND_TELEPORT_TIMEOUT_TICKS;
            if (!pending.warmupFuture().isDone() && !timedOut) {
                continue;
            }

            if (timedOut) {
                VerseWorks.LOGGER.warn(
                    "VerseWorks teleport warmup timed out for {} chunk {},{} after {} ticks; attempting teleport anyway",
                    level.dimension().location(),
                    pending.targetChunk().x,
                    pending.targetChunk().z,
                    COMMAND_TELEPORT_TIMEOUT_TICKS
                );
            }

            releaseWarmupTicket(level, pending.targetChunk(), COMMAND_TELEPORT_WARMUP_RADIUS);
            PENDING_TELEPORT_MESSAGE_COORDINATES.remove(pending.coordinateKey());
            pending.entity().teleportTo(level, pending.targetX(), pending.targetY(), pending.targetZ(), Set.<RelativeMovement>of(), pending.yRot(), pending.xRot());
            if (pending.entity() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal(
                    "Teleported to " + (int) pending.targetX() + ", " + (int) pending.targetY() + ", " + (int) pending.targetZ()
                ));
            }
            completed.add(entry.getKey());
        }

        completed.forEach(pendingTeleports::remove);
        if (pendingTeleports.isEmpty()) {
            PENDING_COMMAND_TELEPORTS.remove(level.dimension());
        }
    }

    private static void processPendingCommandEntries(ServerLevel level) {
        Map<Integer, PendingCommandEntry> pendingEntries = PENDING_COMMAND_ENTRIES.get(level.dimension());
        if (pendingEntries == null || pendingEntries.isEmpty()) {
            return;
        }

        if (!hasPendingEntryPreparation(level) && !LiveDimensionInstantiator.isReadyForEntry(level)) {
            LiveDimensionInstantiator.requestEntryWarmup(level);
        }
        if (hasPendingEntryPreparation(level) || !LiveDimensionInstantiator.isReadyForEntry(level) || preparedEntryArrival(level).isEmpty()) {
            return;
        }

        List<Integer> completed = new ArrayList<>();
        for (Map.Entry<Integer, PendingCommandEntry> entry : pendingEntries.entrySet()) {
            PendingCommandEntry pending = entry.getValue();
            Entity entity = pending.entity();
            if (entity == null || entity.isRemoved()) {
                completed.add(entry.getKey());
                continue;
            }

            try {
                teleportQueuedCommandEntry(entity, level);
            } catch (Exception exception) {
                VerseWorks.LOGGER.warn("VerseWorks could not complete queued command entry into {}", level.dimension().location(), exception);
                if (entity instanceof ServerPlayer player) {
                    player.sendSystemMessage(Component.literal("Queued entry into " + level.dimension().location() + " failed: " + exception.getClass().getSimpleName()));
                }
            }
            completed.add(entry.getKey());
        }

        completed.forEach(pendingEntries::remove);
        if (pendingEntries.isEmpty()) {
            PENDING_COMMAND_ENTRIES.remove(level.dimension());
        }
    }

    private static void teleportQueuedCommandEntry(Entity entity, ServerLevel destination) {
        Vec3 arrival = findSafeArrival(destination);
        boolean crossDimensionEntry = entity.level() != destination;
        if (entity.level() == destination) {
            entity.moveTo(arrival.x, arrival.y, arrival.z, entity.getYRot(), entity.getXRot());
            if (entity instanceof ServerPlayer player) {
                player.connection.teleport(arrival.x, arrival.y, arrival.z, player.getYRot(), player.getXRot());
                player.connection.resetPosition();
            }
        } else {
            teleportWithVerseEntryAuthorization(entity, destination, arrival, entity.getYRot(), entity.getXRot());
        }

        if (entity instanceof ServerPlayer player) {
            registerPendingPlayerEntry(player, destination, arrival, crossDimensionEntry);
            player.sendSystemMessage(Component.literal("Teleported to " + destination.dimension().location()));
        }
    }

    private static void cancelPendingCommandEntries(Entity entity) {
        int entityId = entity.getId();
        for (Map<Integer, PendingCommandEntry> pendingEntries : PENDING_COMMAND_ENTRIES.values()) {
            if (pendingEntries == null) {
                continue;
            }

            pendingEntries.remove(entityId);
        }
    }

    private static void processPendingHyperBookTeleports(ServerLevel level) {
        Map<Integer, PendingHyperBookTeleport> pendingTeleports = PENDING_HYPER_BOOK_TELEPORTS.get(level.dimension());
        if (pendingTeleports == null || pendingTeleports.isEmpty()) {
            return;
        }

        List<Integer> completed = new ArrayList<>();
        for (Map.Entry<Integer, PendingHyperBookTeleport> entry : pendingTeleports.entrySet()) {
            PendingHyperBookTeleport pending = entry.getValue();
            if (!(pending.player() instanceof ServerPlayer player) || pending.player().isRemoved()) {
                releaseWarmupTicket(level, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
                completed.add(entry.getKey());
                continue;
            }

            if (pending.warmupFuture().isCompletedExceptionally()) {
                releaseWarmupTicket(level, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
                player.sendSystemMessage(Component.literal("Hyperbook warmup failed for destination chunk " + pending.targetChunk().x + ", " + pending.targetChunk().z));
                completed.add(entry.getKey());
                continue;
            }

            boolean warmupDone = pending.notified() || pending.warmupFuture().isDone();
            boolean timedOut = !warmupDone && level.getGameTime() - pending.queuedAtGameTime() >= HYPER_BOOK_TELEPORT_TIMEOUT_TICKS;
            if (!warmupDone && !timedOut) {
                continue;
            }

            if (timedOut) {
                releaseWarmupTicket(level, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
                player.sendSystemMessage(Component.literal("The Hyperbook is still tunneling. Try again soon.").withStyle(net.minecraft.ChatFormatting.YELLOW));
                completed.add(entry.getKey());
                continue;
            }

            if (!pending.notified()) {
                if (pending.notifyWhenReady()) {
                    player.sendSystemMessage(Component.literal("The Hyperbook has finished tunneling.").withStyle(net.minecraft.ChatFormatting.AQUA));
                }
                pendingTeleports.put(entry.getKey(), pending.markNotified(level.getGameTime()));
                continue;
            }

            if (level.getGameTime() - pending.readyAtGameTime() >= HYPER_BOOK_READY_TICKET_TICKS) {
                releaseWarmupTicket(level, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
                completed.add(entry.getKey());
            }
        }

        completed.forEach(pendingTeleports::remove);
        if (pendingTeleports.isEmpty()) {
            PENDING_HYPER_BOOK_TELEPORTS.remove(level.dimension());
        }
    }

    private static void clearReadyHyperBookWarmup(ServerPlayer player, ServerLevel destination, ChunkPos targetChunk) {
        Map<Integer, PendingHyperBookTeleport> pendingTeleports = PENDING_HYPER_BOOK_TELEPORTS.get(destination.dimension());
        if (pendingTeleports == null || pendingTeleports.isEmpty()) {
            return;
        }

        PendingHyperBookTeleport pending = pendingTeleports.get(player.getId());
        if (pending == null || !pending.targetChunk().equals(targetChunk)) {
            return;
        }

        pendingTeleports.remove(player.getId());
        releaseWarmupTicket(destination, pending.targetChunk(), HYPER_BOOK_WARMUP_RADIUS);
        if (pendingTeleports.isEmpty()) {
            PENDING_HYPER_BOOK_TELEPORTS.remove(destination.dimension(), pendingTeleports);
        }
    }

    private static void playHyperBookTeleportDeparture(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.9D, pos.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.9D, pos.z, 12, 0.2D, 0.2D, 0.2D, 0.03D);
        level.playSound(null, BlockPos.containing(pos), VerseSounds.ENTER_DIMENSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void playHyperBookTeleportArrival(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.9D, pos.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.9D, pos.z, 12, 0.2D, 0.2D, 0.2D, 0.03D);
        level.playSound(null, BlockPos.containing(pos), VerseSounds.ENTERED_DIMENSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static boolean isVerseWorksLevel(ServerLevel level) {
        return VerseWorks.MODID.equals(level.dimension().location().getNamespace());
    }

    public static Optional<Component> rewritePendingTeleportSystemMessage(Component message) {
        String plain = message.getString();
        if (!plain.startsWith("Teleported")) {
            return Optional.empty();
        }

        for (String coordinateKey : PENDING_TELEPORT_MESSAGE_COORDINATES) {
            if (plain.contains(coordinateKey)) {
                return Optional.of(Component.literal("Teleport queued; destination is still warming."));
            }
        }

        return Optional.empty();
    }

    public static void clearRuntimeState() {
        TIME_RATE_ACCUMULATORS.clear();
        PENDING_DECORATION_JOBS.clear();
        PENDING_SPAWN_PREPARATIONS.clear();
        PREPARED_ENTRY_POINTS.clear();
        ENTRY_PREPARATION_TICKETS.clear();
        DECORATED_CHUNKS.clear();
        PENDING_COMMAND_TELEPORTS.clear();
        PENDING_HYPER_BOOK_TELEPORTS.clear();
        PAUSED_POI_VILLAGERS.clear();
        NEXT_METEOR_SHOWER_TICKS.clear();
        NEXT_WARP_SPAWN_TICKS.clear();
        LAST_ACTIVE_TICKS.clear();
        PENDING_PLAYER_ENTRIES.clear();
        PENDING_TELEPORT_MESSAGE_COORDINATES.clear();
        LEGACY_WEATHER_WARNING_DIMENSIONS.clear();
        BLOCK_ENTITY_GUARD_WARNING_DIMENSIONS.clear();
        LEVEL_DATA_WRAP_WARNING_DIMENSIONS.clear();
        STARTUP_LIFECYCLE_SYNCED_SERVERS.clear();
        STARTUP_PLAYER_DIMENSIONS_SCANNED_SERVERS.clear();
        ACTIVE_PACK_ENABLE_REQUESTED.clear();
        STARTUP_EXPECTED_PLAYER_DIMENSIONS.clear();
        STARTUP_PROTECTED_PLAYER_DIMENSIONS.clear();
        STARTUP_PREPARED_DIMENSIONS.clear();
        STARTUP_PREPARATION_QUEUE.clear();
        activeStartupPreparation = null;
        nextStartupPreparationTick = 0L;
        serverTickThread = null;
        lastServerTickNanos = 0L;
        lastServerTickDumpNanos = 0L;
        lastServerTickDimension = "unknown";
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        shutdownInProgress = true;
        logShutdownState(event.getServer());
        quiesceShutdownWork(event.getServer());
        unloadRuntimeLevels(event.getServer(), true, true, null, "server shutdown");
        syncLifecyclePersistence(event.getServer(), false, "server-stop");
        clearRuntimeState();
        LiveDimensionInstantiator.clearRuntimeState();
    }

    private static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }

        syncLifecyclePersistence(level.getServer(), false, "level-save");
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation currentDimensionId = player.serverLevel().dimension().location();
        STARTUP_EXPECTED_PLAYER_DIMENSIONS.remove(player.getUUID());
        STARTUP_PROTECTED_PLAYER_DIMENSIONS.remove(currentDimensionId);
        refreshStartupProtectedPlayerDimensions();
        if (VerseWorks.MODID.equals(currentDimensionId.getNamespace())) {
            recordDimensionVisit(player.getServer(), currentDimensionId);
        }
        VerseDimensionParameterSync.syncKnownDimensions(player);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        AUTHORIZED_VERSE_ENTRIES.remove(player.getUUID());
        clearPendingHyperBookSourceWait(player);

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            return;
        }

        boolean otherPlayersRemain = server.getPlayerList().getPlayers().stream().anyMatch(other -> other != player);
        if (otherPlayersRemain) {
            return;
        }

        if (server.isDedicatedServer()) {
            return;
        }
    }

    private static boolean isIntegratedServer(MinecraftServer server) {
        return !server.isDedicatedServer();
    }

    private static void markDimensionActive(ServerLevel level) {
        if (isVerseWorksLevel(level)) {
            LAST_ACTIVE_TICKS.put(level.dimension(), level.getGameTime());
        }
    }

    private static void recordDimensionVisit(MinecraftServer server, ResourceLocation dimensionId) {
        if (server == null || !VerseWorks.MODID.equals(dimensionId.getNamespace())) {
            return;
        }

        VerseDimensionUsageSavedData.get(server).recordVisit(dimensionId, System.currentTimeMillis());
    }

    private static void sendCorruptionWarnings(ServerPlayer player, ServerLevel destination) {
        Optional<VerseDimensionParameters> parameters = resolveParameters(destination);
        if (parameters.isEmpty() || !parameters.get().hasCorruptions()) {
            return;
        }

        CompoundTag root = player.getPersistentData();
        CompoundTag warnings = root.contains(CORRUPTION_WARNINGS_TAG) ? root.getCompound(CORRUPTION_WARNINGS_TAG) : new CompoundTag();
        String dimensionKey = destination.dimension().location().toString();
        CompoundTag dimensionWarnings = warnings.contains(dimensionKey) ? warnings.getCompound(dimensionKey) : new CompoundTag();
        boolean changed = false;
        java.util.ArrayList<VerseDimensionCorruption> unseenCorruptions = new java.util.ArrayList<>();

        for (VerseDimensionCorruption corruption : parameters.get().corruptions()) {
            if (dimensionWarnings.getBoolean(corruption.id())) {
                continue;
            }

            unseenCorruptions.add(corruption);
        }

        if (unseenCorruptions.isEmpty()) {
            return;
        }

        if (unseenCorruptions.size() > 3) {
            player.sendSystemMessage(
                Component.literal("Many things appear to be strange with this world...")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
            );
            for (VerseDimensionCorruption corruption : unseenCorruptions) {
                dimensionWarnings.putBoolean(corruption.id(), true);
            }
            changed = true;
        } else {
            for (VerseDimensionCorruption corruption : unseenCorruptions) {
                player.sendSystemMessage(
                    Component.literal("This world is strange, " + corruption.message() + ".")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                );
                dimensionWarnings.putBoolean(corruption.id(), true);
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        warnings.put(dimensionKey, dimensionWarnings);
        root.put(CORRUPTION_WARNINGS_TAG, warnings);
    }

    private static boolean shouldSleepRuntimeLevel(ServerLevel level) {
        if (shutdownInProgress) {
            return false;
        }

        if (!isVerseWorksLevel(level)) {
            return false;
        }

        long gameTime = level.getGameTime();
        if (!level.players().isEmpty() || hasPendingActivity(level)) {
            LAST_ACTIVE_TICKS.put(level.dimension(), gameTime);
            return false;
        }

        if (STARTUP_PROTECTED_PLAYER_DIMENSIONS.contains(level.dimension().location())) {
            LAST_ACTIVE_TICKS.put(level.dimension(), gameTime);
            return false;
        }

        Long lastActiveTick = LAST_ACTIVE_TICKS.get(level.dimension());
        if (lastActiveTick == null) {
            return gameTime >= STARTUP_DIMENSION_IDLE_UNLOAD_DELAY_TICKS;
        }

        return gameTime - lastActiveTick >= RUNTIME_DIMENSION_IDLE_SLEEP_DELAY_TICKS;
    }

    private static boolean shouldUnloadDormantLevel(ServerLevel level) {
        if (!isVerseWorksLevel(level) || !shouldSleepRuntimeLevel(level) || isDimensionPinned(level)) {
            return false;
        }

        return idleTicks(level) >= RUNTIME_DIMENSION_IDLE_UNLOAD_DELAY_TICKS;
    }

    public static boolean hasPendingActivity(ServerLevel level) {
        if (PENDING_SPAWN_PREPARATIONS.containsKey(level.dimension())) {
            return true;
        }

        Deque<DecorationJob> decorationJobs = PENDING_DECORATION_JOBS.get(level.dimension());
        if (decorationJobs != null && !decorationJobs.isEmpty()) {
            return true;
        }

        Map<Integer, PendingCommandTeleport> commandTeleports = PENDING_COMMAND_TELEPORTS.get(level.dimension());
        if (commandTeleports != null && !commandTeleports.isEmpty()) {
            return true;
        }

        Map<Integer, PendingCommandEntry> commandEntries = PENDING_COMMAND_ENTRIES.get(level.dimension());
        if (commandEntries != null && !commandEntries.isEmpty()) {
            return true;
        }

        Map<Integer, PendingHyperBookTeleport> hyperBookTeleports = PENDING_HYPER_BOOK_TELEPORTS.get(level.dimension());
        if (hyperBookTeleports != null && !hyperBookTeleports.isEmpty()) {
            return true;
        }

        for (PendingPlayerEntry pendingEntry : PENDING_PLAYER_ENTRIES.values()) {
            if (pendingEntry.levelKey().equals(level.dimension())) {
                return true;
            }
        }

        return false;
    }

    private static void pruneIdleRuntimeState(ServerLevel level) {
        releaseWarmupTicket(level, LiveDimensionInstantiator.getEntryChunk(level), LiveDimensionInstantiator.entryWarmupRadius());
        TIME_RATE_ACCUMULATORS.remove(level.dimension());
        PAUSED_POI_VILLAGERS.remove(level.dimension());
        NEXT_METEOR_SHOWER_TICKS.remove(level.dimension());
        NEXT_WARP_SPAWN_TICKS.remove(level.dimension());
    }

    private static void unloadRuntimeLevels(MinecraftServer server, boolean allowPendingActivity, boolean flushSave, ServerLevel excludedLevel, String reason) {
        for (ServerLevel level : new ArrayList<>(server.forgeGetWorldMap().values())) {
            if (level == null || level == excludedLevel || !isVerseWorksLevel(level)) {
                continue;
            }

            unloadRuntimeLevel(level, allowPendingActivity, flushSave, reason);
        }
    }

    private static void maintainSleepingRuntimeLevels(MinecraftServer server, ServerLevel excludedLevel) {
        for (ServerLevel level : new ArrayList<>(server.forgeGetWorldMap().values())) {
            if (level == null || level == excludedLevel || !isVerseWorksLevel(level)) {
                continue;
            }

            if (!shouldSleepRuntimeLevel(level)) {
                continue;
            }

            if (shouldUnloadDormantLevel(level)) {
                unloadRuntimeLevel(level, false, true, "idle dormancy");
                continue;
            }

            releaseEntryPreparationTicket(level);
            pruneIdleRuntimeState(level);
        }
    }

    private static boolean unloadRuntimeLevel(ServerLevel level, boolean allowPendingActivity, boolean flushSave, String reason) {
        if (!isVerseWorksLevel(level) || !level.players().isEmpty()) {
            return false;
        }

        if (isDimensionPinned(level)) {
            return false;
        }

        if (!allowPendingActivity && hasPendingActivity(level)) {
            pruneIdleRuntimeState(level);
            return false;
        }

        releaseEntryPreparationTicket(level);
        pruneIdleRuntimeState(level);
        try {
            if (!LiveDimensionInstantiator.unload(level, true, flushSave)) {
                return false;
            }
        } catch (Exception exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not unload dimension {} during {}", level.dimension().location(), reason, exception);
            LAST_ACTIVE_TICKS.put(level.dimension(), level.getGameTime());
            return false;
        }

        clearRuntimeLevelState(level.dimension());
        syncLifecyclePersistence(level.getServer(), false, "dimension-unload");
        VerseWorks.LOGGER.info("VerseWorks unloaded dimension {} during {}", level.dimension().location(), reason);
        return true;
    }

    private static void ensureLifecycleStartupState(MinecraftServer server) {
        String serverKey = serverKey(server);
        if (!STARTUP_LIFECYCLE_SYNCED_SERVERS.add(serverKey)) {
            return;
        }

        scanStartupExpectedPlayerDimensions(server);

        VerseDimensionLifecycleSavedData existingData = VerseDimensionLifecycleSavedData.getIfPresent(server)
            .filter(VerseDimensionLifecycleSavedData::isInitialized)
            .orElse(null);
        if (existingData != null) {
            restoreStartupDimensions(server, existingData);
            return;
        }

        syncLifecyclePersistence(server, true, "startup-init");
        restoreStartupDimensions(server, VerseDimensionLifecycleSavedData.get(server));
    }

    private static void restoreStartupDimensions(MinecraftServer server, VerseDimensionLifecycleSavedData data) {
        try {
            VerseDimensionCatalog.invalidate(server);
            VerseDimensionCatalog.ensureLoaded(server);

            LinkedHashMap<ResourceLocation, VerseDimensionParameters> startupDimensions = new LinkedHashMap<>();
            for (ResourceLocation dimensionId : data.startupDimensions()) {
                VerseDimensionCatalog.get(server, dimensionId).ifPresent(parameters -> startupDimensions.put(dimensionId, parameters));
            }
            for (ResourceLocation dimensionId : startupExpectedPlayerDimensionIds()) {
                VerseDimensionCatalog.get(server, dimensionId).ifPresent(parameters -> startupDimensions.put(dimensionId, parameters));
            }
            List<StartupPreparedDimension> startupPreparedDimensions = new ArrayList<>();
            for (ResourceLocation dimensionId : selectStartupPreparedDimensions(server)) {
                VerseDimensionCatalog.get(server, dimensionId).ifPresent(parameters -> {
                    startupPreparedDimensions.add(new StartupPreparedDimension(dimensionId, parameters));
                    startupDimensions.put(dimensionId, parameters);
                });
            }
            STARTUP_PREPARED_DIMENSIONS.clear();
            STARTUP_PREPARED_DIMENSIONS.addAll(startupPreparedDimensions.stream().map(StartupPreparedDimension::dimensionId).toList());
            STARTUP_PREPARATION_QUEUE.clear();
            activeStartupPreparation = null;
            nextStartupPreparationTick = 0L;
            for (StartupPreparedDimension preparation : startupPreparedDimensions) {
                STARTUP_PREPARATION_QUEUE.addLast(preparation);
            }
            refreshStartupProtectedPlayerDimensions();

            for (Map.Entry<ResourceLocation, VerseDimensionParameters> entry : startupDimensions.entrySet()) {
                GeneratedDimensionPackWriter.writeMetadata(server, entry.getKey(), entry.getValue());
            }
            GeneratedDimensionPackWriter.syncActiveDimensions(server, startupDimensions);
            if (!startupDimensions.isEmpty()) {
                ensureActivePackSelected(server);
            }
            unloadUnselectedStartupPackLevels(server, startupDimensions.keySet(), "startup active-pack cleanup");

            for (Map.Entry<ResourceLocation, VerseDimensionParameters> entry : startupDimensions.entrySet()) {
                if (server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, entry.getKey())) != null) {
                    continue;
                }
                if (LiveDimensionInstantiator.isActivationInProgress(entry.getKey())) {
                    continue;
                }

                String restoreReason = STARTUP_PREPARED_DIMENSIONS.contains(entry.getKey()) ? "startup prepared pool" : "lifecycle manifest";
                VerseWorks.LOGGER.info("VerseWorks restoring startup dimension {} from {}", entry.getKey(), restoreReason);
                LiveDimensionInstantiator.activateAsync(server, entry.getKey(), entry.getValue());
            }

            VerseDimensionCatalog.invalidate(server);
            VerseDimensionCatalog.ensureLoaded(server);
        } catch (java.io.IOException exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not restore startup dimensions from lifecycle manifest", exception);
        }
    }

    private static List<ResourceLocation> selectStartupPreparedDimensions(MinecraftServer server) {
        int limit = Config.startupPreparedDimensionLimit();
        if (limit <= 0) {
            return List.of();
        }

        VerseDimensionUsageSavedData usageData = VerseDimensionUsageSavedData.get(server);
        Comparator<ResourceLocation> ranking = (left, right) -> {
            VerseDimensionUsageSavedData.UsageStats leftUsage = usageData.usage(left);
            VerseDimensionUsageSavedData.UsageStats rightUsage = usageData.usage(right);
            int byVisits = Long.compare(rightUsage.visitCount(), leftUsage.visitCount());
            if (byVisits != 0) {
                return byVisits;
            }

            int byRecency = Long.compare(rightUsage.lastVisitedAtEpochMillis(), leftUsage.lastVisitedAtEpochMillis());
            if (byRecency != 0) {
                return byRecency;
            }

            return left.toString().compareTo(right.toString());
        };

        return VerseDimensionCatalog.knownDimensionIds(server).stream()
            .filter(dimensionId -> VerseWorks.MODID.equals(dimensionId.getNamespace()))
            .filter(dimensionId -> usageData.usage(dimensionId).visitCount() > 0L)
            .sorted(ranking)
            .limit(limit)
            .toList();
    }

    private static void processStartupPreparationQueue(MinecraftServer server, long gameTime) {
        if (activeStartupPreparation != null) {
            ResourceLocation dimensionId = activeStartupPreparation.dimensionId();
            if (HyperBookCollapseHooks.isDimensionCollapsing(dimensionId) || !STARTUP_PREPARED_DIMENSIONS.contains(dimensionId)) {
                finishStartupPreparation(gameTime, false);
                return;
            }

            if (activeStartupPreparation.activationFuture() != null && !activeStartupPreparation.activationFuture().isDone()) {
                return;
            }

            if (activeStartupPreparation.activationFuture() != null && activeStartupPreparation.activationFuture().isCompletedExceptionally()) {
                try {
                    activeStartupPreparation.activationFuture().join();
                } catch (Exception exception) {
                    VerseWorks.LOGGER.warn("VerseWorks could not activate startup-prepared dimension {}", dimensionId, exception);
                }
                finishStartupPreparation(gameTime, false);
                return;
            }

            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) {
                finishStartupPreparation(gameTime, false);
                return;
            }

            ensureEntryPreparationScheduled(server, level, activeStartupPreparation.parameters());
            ensureStartupEntryWarmup(level);
            if (!isStartupPreparationReady(level)) {
                return;
            }

            VerseWorks.LOGGER.info("VerseWorks finished startup preparation for {}", dimensionId);
            finishStartupPreparation(gameTime, true);
            return;
        }

        if (gameTime < nextStartupPreparationTick) {
            return;
        }

        while (!STARTUP_PREPARATION_QUEUE.isEmpty()) {
            StartupPreparedDimension preparation = STARTUP_PREPARATION_QUEUE.pollFirst();
            if (preparation == null) {
                return;
            }

            ResourceLocation dimensionId = preparation.dimensionId();
            if (HyperBookCollapseHooks.isDimensionCollapsing(dimensionId) || !STARTUP_PREPARED_DIMENSIONS.contains(dimensionId)) {
                continue;
            }

            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level != null) {
                VerseWorks.LOGGER.info("VerseWorks preparing startup-selected dimension {}", dimensionId);
                ensureEntryPreparationScheduled(server, level, preparation.parameters());
                ensureStartupEntryWarmup(level);
                if (isStartupPreparationReady(level)) {
                    VerseWorks.LOGGER.info("VerseWorks finished startup preparation for {}", dimensionId);
                    nextStartupPreparationTick = gameTime + STARTUP_PREPARATION_STAGGER_TICKS;
                    return;
                }

                activeStartupPreparation = preparation;
                return;
            }

            VerseWorks.LOGGER.info("VerseWorks activating startup-selected dimension {}", dimensionId);
            activeStartupPreparation = preparation.withActivationFuture(LiveDimensionInstantiator.activateAsync(server, dimensionId, preparation.parameters()));
            return;
        }
    }

    private static void finishStartupPreparation(long gameTime, boolean delayNextAttempt) {
        activeStartupPreparation = null;
        nextStartupPreparationTick = delayNextAttempt ? gameTime + STARTUP_PREPARATION_STAGGER_TICKS : gameTime;
    }

    private static void ensureStartupEntryWarmup(ServerLevel level) {
        if (hasPendingEntryPreparation(level) || preparedEntryArrival(level).isEmpty() || LiveDimensionInstantiator.isReadyForEntry(level)) {
            return;
        }

        LiveDimensionInstantiator.requestEntryWarmup(level);
    }

    private static void syncLifecyclePersistence(MinecraftServer server, boolean conservativeIfUninitialized, String reason) {
        try {
            VerseDimensionCatalog.invalidate(server);
            VerseDimensionCatalog.ensureLoaded(server);

            Set<ResourceLocation> knownDimensions = VerseDimensionCatalog.knownDimensionIds(server);
            LinkedHashMap<ResourceLocation, VerseDimensionLifecycleSavedData.Entry> snapshot = new LinkedHashMap<>();
            LinkedHashMap<ResourceLocation, VerseDimensionParameters> startupDimensions = new LinkedHashMap<>();
            Set<ResourceLocation> playerReferencedDimensions = startupExpectedPlayerDimensionIds(server);
            List<ResourceLocation> startupPreparedDimensions = selectStartupPreparedDimensions(server);

            for (ResourceLocation dimensionId : knownDimensions) {
                Optional<VerseDimensionParameters> parameters = VerseDimensionCatalog.get(server, dimensionId);
                parameters.ifPresent(value -> {
                    try {
                        GeneratedDimensionPackWriter.writeMetadata(server, dimensionId, value);
                    } catch (java.io.IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });

                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
                boolean loadedAtSave = level != null;
                boolean pinnedAtSave = loadedAtSave && isChunkForcedPinned(level);
                boolean activeAtSave = loadedAtSave && (!level.players().isEmpty() || hasPendingActivity(level));
                boolean playerReferencedAtSave = playerReferencedDimensions.contains(dimensionId);
                boolean shouldRestore = playerReferencedAtSave || (loadedAtSave && (pinnedAtSave || activeAtSave));
                String lastState = loadedAtSave
                    ? lifecycleState(level, pinnedAtSave)
                    : (playerReferencedAtSave ? "player_restore_pending" : "unloaded_dormant");

                snapshot.put(dimensionId, new VerseDimensionLifecycleSavedData.Entry(loadedAtSave, pinnedAtSave, shouldRestore, lastState));
                if (shouldRestore) {
                    parameters.ifPresent(value -> startupDimensions.put(dimensionId, value));
                }
            }

            for (ResourceLocation dimensionId : startupPreparedDimensions) {
                VerseDimensionCatalog.get(server, dimensionId).ifPresent(parameters -> startupDimensions.put(dimensionId, parameters));
            }

            VerseDimensionLifecycleSavedData.get(server).replaceEntries(snapshot, true);
            GeneratedDimensionPackWriter.syncActiveDimensions(server, startupDimensions);
            if (!startupDimensions.isEmpty()) {
                ensureActivePackSelected(server);
            }
            unloadUnselectedStartupPackLevels(server, startupDimensions.keySet(), reason + " active-pack cleanup");
            VerseDimensionCatalog.invalidate(server);
            VerseDimensionCatalog.ensureLoaded(server);
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof java.io.IOException ioException) {
                VerseWorks.LOGGER.warn("VerseWorks could not sync lifecycle persistence during {}", reason, ioException);
                return;
            }
            throw exception;
        } catch (java.io.IOException exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not sync lifecycle persistence during {}", reason, exception);
        }
    }

    private static void ensureActivePackSelected(MinecraftServer server) {
        if (server.getPackRepository().getSelectedIds().contains(GeneratedDimensionPackWriter.PACK_ID)) {
            return;
        }

        String serverKey = serverKey(server);
        if (!ACTIVE_PACK_ENABLE_REQUESTED.add(serverKey)) {
            return;
        }

        PackRepository packRepository = server.getPackRepository();
        packRepository.reload();
        if (!packRepository.getSelectedIds().contains(GeneratedDimensionPackWriter.PACK_ID)) {
            packRepository.addPack(GeneratedDimensionPackWriter.PACK_ID);
        }

        server.reloadResources(List.copyOf(packRepository.getSelectedIds())).whenComplete((ignored, throwable) -> {
            ACTIVE_PACK_ENABLE_REQUESTED.remove(serverKey);
            if (throwable != null) {
                VerseWorks.LOGGER.warn("VerseWorks could not enable active dimension pack {}", GeneratedDimensionPackWriter.PACK_ID, throwable);
            }
        });
    }

    private static String lifecycleState(ServerLevel level, boolean pinned) {
        if (!level.players().isEmpty() || hasPendingActivity(level)) {
            return "active";
        }

        if (pinned) {
            return "pinned_sleep";
        }

        if (shouldSleepRuntimeLevel(level)) {
            return "sleeping";
        }

        return "idle_grace";
    }

    private static String serverKey(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();
    }

    private static void scanStartupExpectedPlayerDimensions(MinecraftServer server) {
        String serverKey = serverKey(server);
        if (!STARTUP_PLAYER_DIMENSIONS_SCANNED_SERVERS.add(serverKey)) {
            return;
        }

        STARTUP_EXPECTED_PLAYER_DIMENSIONS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceLocation dimensionId = player.serverLevel().dimension().location();
            if (VerseWorks.MODID.equals(dimensionId.getNamespace())) {
                STARTUP_EXPECTED_PLAYER_DIMENSIONS.put(player.getUUID(), dimensionId);
            }
        }

        Path playerDataDirectory = server.getWorldPath(new net.minecraft.world.level.storage.LevelResource("playerdata"));
        if (!Files.isDirectory(playerDataDirectory)) {
            refreshStartupProtectedPlayerDimensions();
            return;
        }

        try (var playerFiles = Files.list(playerDataDirectory)) {
            for (Path playerFile : playerFiles.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                UUID playerId = parsePlayerDataUuid(playerFile.getFileName().toString());
                if (playerId == null) {
                    continue;
                }

                ResourceLocation dimensionId = readStoredPlayerDimensionId(playerFile);
                if (dimensionId == null || !VerseWorks.MODID.equals(dimensionId.getNamespace())) {
                    continue;
                }

                STARTUP_EXPECTED_PLAYER_DIMENSIONS.put(playerId, dimensionId);
            }
        } catch (IOException exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not scan playerdata for startup dimension protection", exception);
        }

        refreshStartupProtectedPlayerDimensions();
    }

    private static Set<ResourceLocation> startupExpectedPlayerDimensionIds(MinecraftServer server) {
        scanStartupExpectedPlayerDimensions(server);
        return startupExpectedPlayerDimensionIds();
    }

    private static Set<ResourceLocation> startupExpectedPlayerDimensionIds() {
        return Set.copyOf(STARTUP_EXPECTED_PLAYER_DIMENSIONS.values());
    }

    private static void refreshStartupProtectedPlayerDimensions() {
        STARTUP_PROTECTED_PLAYER_DIMENSIONS.clear();
        STARTUP_PROTECTED_PLAYER_DIMENSIONS.addAll(
            STARTUP_EXPECTED_PLAYER_DIMENSIONS.values().stream()
                .filter(dimensionId -> VerseWorks.MODID.equals(dimensionId.getNamespace()))
                .collect(Collectors.toSet())
        );
    }

    private static UUID parsePlayerDataUuid(String fileName) {
        if (!fileName.endsWith(".dat")) {
            return null;
        }

        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 4));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ResourceLocation readStoredPlayerDimensionId(Path playerFile) {
        try (InputStream inputStream = Files.newInputStream(playerFile)) {
            CompoundTag playerTag = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
            if (!playerTag.contains("Dimension", Tag.TAG_STRING)) {
                return null;
            }

            return ResourceLocation.tryParse(playerTag.getString("Dimension"));
        } catch (Exception exception) {
            VerseWorks.LOGGER.debug("VerseWorks could not read stored dimension from playerdata {}", playerFile, exception);
            return null;
        }
    }

    private static void clearRuntimeLevelState(net.minecraft.resources.ResourceKey<Level> levelKey) {
        TIME_RATE_ACCUMULATORS.remove(levelKey);
        PENDING_DECORATION_JOBS.remove(levelKey);
        PENDING_SPAWN_PREPARATIONS.remove(levelKey);
        PREPARED_ENTRY_POINTS.remove(levelKey);
        ENTRY_PREPARATION_TICKETS.remove(levelKey);
        DECORATED_CHUNKS.remove(levelKey);
        PENDING_COMMAND_TELEPORTS.remove(levelKey);
        PENDING_COMMAND_ENTRIES.remove(levelKey);
        PENDING_HYPER_BOOK_TELEPORTS.remove(levelKey);
        PAUSED_POI_VILLAGERS.remove(levelKey);
        NEXT_METEOR_SHOWER_TICKS.remove(levelKey);
        NEXT_WARP_SPAWN_TICKS.remove(levelKey);
        LAST_ACTIVE_TICKS.remove(levelKey);
        LEGACY_WEATHER_WARNING_DIMENSIONS.remove(levelKey);
        BLOCK_ENTITY_GUARD_WARNING_DIMENSIONS.remove(levelKey);
        LEVEL_DATA_WRAP_WARNING_DIMENSIONS.remove(levelKey);
    }

    public static void teleportWithVerseEntryAuthorization(Entity entity, ServerLevel destination, Vec3 target, float yRot, float xRot) {
        AuthorizedVerseEntry authorizedEntry = authorizeVerseEntry(entity, destination);
        try {
            entity.teleportTo(destination, target.x(), target.y(), target.z(), Set.<RelativeMovement>of(), yRot, xRot);
        } finally {
            clearAuthorizedVerseEntry(entity, authorizedEntry);
        }
    }

    private static void unloadUnselectedStartupPackLevels(MinecraftServer server, Set<ResourceLocation> selectedDimensions, String reason) {
        for (ServerLevel level : new ArrayList<>(server.forgeGetWorldMap().values())) {
            if (level == null || !isVerseWorksLevel(level) || !level.players().isEmpty()) {
                continue;
            }

            ResourceLocation dimensionId = level.dimension().location();
            if (selectedDimensions.contains(dimensionId)) {
                continue;
            }

            if (LiveDimensionInstantiator.isRuntimeLevel(level) && !shouldSleepRuntimeLevel(level)) {
                continue;
            }

            if (hasPendingActivity(level) || isChunkForcedPinned(level)) {
                continue;
            }

            releaseEntryPreparationTicket(level);
            pruneIdleRuntimeState(level);
            try {
                if (LiveDimensionInstantiator.unload(level, true, true)) {
                    clearRuntimeLevelState(level.dimension());
                    VerseWorks.LOGGER.info("VerseWorks unloaded unselected dimension {} during {}", dimensionId, reason);
                }
            } catch (Exception exception) {
                VerseWorks.LOGGER.warn("VerseWorks could not unload unselected dimension {} during {}", dimensionId, reason, exception);
                LAST_ACTIVE_TICKS.put(level.dimension(), level.getGameTime());
            }
        }
    }

    private static AuthorizedVerseEntry authorizeVerseEntry(Entity entity, ServerLevel destination) {
        if (!(entity instanceof ServerPlayer player) || !isVerseWorksLevel(destination)) {
            return null;
        }

        AuthorizedVerseEntry authorizedEntry = new AuthorizedVerseEntry(destination.dimension());
        AUTHORIZED_VERSE_ENTRIES.put(player.getUUID(), authorizedEntry);
        return authorizedEntry;
    }

    private static void clearAuthorizedVerseEntry(Entity entity, AuthorizedVerseEntry authorizedEntry) {
        if (authorizedEntry == null || !(entity instanceof ServerPlayer player)) {
            return;
        }

        AUTHORIZED_VERSE_ENTRIES.remove(player.getUUID(), authorizedEntry);
    }

    private static boolean consumeAuthorizedVerseEntry(ServerPlayer player, net.minecraft.resources.ResourceKey<Level> destination) {
        AuthorizedVerseEntry authorizedEntry = AUTHORIZED_VERSE_ENTRIES.get(player.getUUID());
        if (authorizedEntry == null || !authorizedEntry.levelKey().equals(destination)) {
            return false;
        }

        return AUTHORIZED_VERSE_ENTRIES.remove(player.getUUID(), authorizedEntry);
    }

    private static Optional<BlockPos> persistentEntryPoint(ServerLevel level) {
        return VerseDimensionEntryPointSavedData.getIfPresent(level.getServer())
            .flatMap(data -> data.entryPoint(level.dimension().location()));
    }

    private static BlockPos resolvePreparedEntryPoint(ServerLevel level, VerseDimensionParameters parameters, ChunkPos entryChunk) {
        BlockPos cached = PREPARED_ENTRY_POINTS.get(level.dimension());
        if (cached != null) {
            return cached.immutable();
        }

        BlockPos resolved = persistentEntryPoint(level)
            .map(savedEntryPoint -> restorePreparedEntryPoint(level, parameters, savedEntryPoint))
            .orElseGet(() -> prepareEntryArrival(level, parameters, entryChunk));
        rememberPreparedEntryPoint(level, resolved);
        return resolved;
    }

    private static BlockPos restorePreparedEntryPoint(ServerLevel level, VerseDimensionParameters parameters, BlockPos savedEntryPoint) {
        ChunkPos entryChunk = new ChunkPos(savedEntryPoint);
        ensureChunkAvailable(level, entryChunk.x, entryChunk.z, true);
        if (isPreparedArrivalSpot(level, parameters, savedEntryPoint)) {
            clearHeadroom(level, savedEntryPoint, 4);
            return savedEntryPoint.immutable();
        }

        if (parameters.worldType().hasBedrockShell()) {
            return createForcedArrival(level, parameters, entryChunk);
        }

        return createArrivalPocket(level, savedEntryPoint, parameters);
    }

    private static void rememberPreparedEntryPoint(ServerLevel level, BlockPos preparedEntryPoint) {
        BlockPos immutable = preparedEntryPoint.immutable();
        PREPARED_ENTRY_POINTS.put(level.dimension(), immutable);
        VerseDimensionEntryPointSavedData.get(level.getServer()).rememberEntryPoint(level.dimension().location(), immutable);
    }

    private static boolean isStartupPreparationReady(ServerLevel level) {
        return preparedEntryArrival(level).isPresent()
            && !hasPendingEntryPreparation(level)
            && LiveDimensionInstantiator.isReadyForEntry(level);
    }

    private static void releaseWarmupTicket(ServerLevel level, ChunkPos chunkPos, int radius) {
        VerseChunkWarmup.release(level, chunkPos, radius);
    }

    private static void releaseEntryPreparationTicket(ServerLevel level) {
        PreparationWarmupTicket ticket = ENTRY_PREPARATION_TICKETS.remove(level.dimension());
        if (ticket == null) {
            return;
        }

        releaseWarmupTicket(level, ticket.entryChunk(), ticket.radius());
    }

    private static String teleportCoordinateKey(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.6f, %.6f, %.6f", x, y, z);
    }

    private record GuardedTickingBlockEntity(ServerLevel level, TickingBlockEntity delegate) implements TickingBlockEntity {
        @Override
        public void tick() {
            if (!hasBlockEntityTickingChunkRing(level, delegate.getPos(), BLOCK_ENTITY_GUARD_CHUNK_RADIUS)) {
                return;
            }
            delegate.tick();
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public BlockPos getPos() {
            return delegate.getPos();
        }

        @Override
        public String getType() {
            return delegate.getType();
        }
    }

    private record PendingPlayerEntry(net.minecraft.resources.ResourceKey<Level> levelKey, Vec3 arrival, float yRot, float xRot, boolean recordVisitOnArrival) {
    }

    private record AuthorizedVerseEntry(net.minecraft.resources.ResourceKey<Level> levelKey) {
    }

    private static void quiesceShutdownWork(MinecraftServer server) {
        VerseChunkWarmup.releaseAll(server);
        AUTHORIZED_VERSE_ENTRIES.clear();
        PENDING_PLAYER_ENTRIES.clear();
        PENDING_COMMAND_TELEPORTS.clear();
        PENDING_HYPER_BOOK_TELEPORTS.clear();
        PENDING_SPAWN_PREPARATIONS.clear();
        ENTRY_PREPARATION_TICKETS.clear();
        STARTUP_PREPARATION_QUEUE.clear();
        activeStartupPreparation = null;
    }

    private static void logShutdownState(MinecraftServer server) {
        long loadedVerseLevels = server.forgeGetWorldMap().values().stream()
            .filter(level -> level != null && isVerseWorksLevel(level))
            .count();
        VerseWorks.LOGGER.info(
            "VerseWorks shutdown quiesce: loadedLevels={}, pendingPlayerEntries={}, pendingCommandTeleports={}, pendingHyperbookTeleports={}, pendingSpawnPreps={}",
            loadedVerseLevels,
            PENDING_PLAYER_ENTRIES.size(),
            PENDING_COMMAND_TELEPORTS.values().stream().mapToInt(Map::size).sum(),
            PENDING_HYPER_BOOK_TELEPORTS.values().stream().mapToInt(Map::size).sum(),
            PENDING_SPAWN_PREPARATIONS.size()
        );
    }

    private static Optional<VerseDimensionParameters> resolveParameters(ServerLevel level) {
        if (level.getServer() == null) {
            return Optional.empty();
        }

        ResourceLocation dimensionId = level.dimension().location();
        if (!VerseWorks.MODID.equals(dimensionId.getNamespace())) {
            return Optional.empty();
        }

        return VerseDimensionCatalog.get(level.getServer(), dimensionId);
    }

    private static boolean isCorruptionRuleEnabled(VerseDimensionParameters parameters, VerseDimensionCorruption corruption) {
        if (!parameters.corruptions().contains(corruption)) {
            return true;
        }

        return Config.isCorruptionEffectEnabled(corruption);
    }

    private static void applyTimeRules(ServerLevel level, VerseDimensionParameters parameters) {
        if (parameters.effectivePermanentTime()
            && (parameters.forcesPermanentNight() || isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.FIXED_TIME))) {
            long currentDay = Math.floorDiv(level.getDayTime(), 24000L);
            long lockedTime = currentDay * 24000L + parameters.effectiveResolvedTimeOfDay();
            if (level.getDayTime() != lockedTime) {
                level.setDayTime(lockedTime);
            }
            return;
        }

        double extraTime = parameters.dayRate() - 1.0D;
        if (Math.abs(extraTime) < 0.00001D) {
            return;
        }

        var levelKey = level.dimension();
        double accumulator = TIME_RATE_ACCUMULATORS.getOrDefault(levelKey, 0.0D) + extraTime;
        long correction = 0L;
        if (accumulator >= 1.0D) {
            correction = (long) Math.floor(accumulator);
        } else if (accumulator <= -1.0D) {
            correction = (long) Math.ceil(accumulator);
        }

        if (correction != 0L) {
            level.setDayTime(level.getDayTime() + correction);
            accumulator -= correction;
        }
        TIME_RATE_ACCUMULATORS.put(levelKey, accumulator);
    }

    private static void applyWeatherRules(ServerLevel level, VerseDimensionParameters parameters) {
        boolean targetRain = parameters.permanentStorm()
            ? isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_STORM)
            : parameters.permanentRain() && isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_RAIN);
        boolean targetThunder = parameters.permanentStorm()
            ? isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_STORM)
            : parameters.permanentLightning() && isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_LIGHTNING);
        if (!targetRain && !targetThunder) {
            return;
        }

        if (level.getGameTime() % 100L != 0L && level.isRaining() == targetRain && level.isThundering() == targetThunder) {
            return;
        }

        level.setWeatherParameters(0, 6000000, targetRain, targetThunder);
        if (level.getLevelData() instanceof DerivedLevelData && !(level.getLevelData() instanceof MutableDerivedLevelData)
            && LEGACY_WEATHER_WARNING_DIMENSIONS.add(level.dimension())) {
            VerseWorks.LOGGER.warn(
                "Permanent weather is unsupported for already-live legacy VerseWorks dimension {}. Recreate the dimension or restart the world to reload it with current data handling.",
                level.dimension().location()
            );
        }
    }

    private static void applyLightningHazards(ServerLevel level, VerseDimensionParameters parameters) {
        boolean lightningEnabled = (parameters.permanentLightning() && isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_LIGHTNING))
            || (parameters.permanentStorm() && isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.ENDLESS_STORM));
        if (!lightningEnabled || level.players().isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % LIGHTNING_HAZARD_SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        RandomSource random = level.getRandom();
        float ambientChance = parameters.permanentLightning() ? 0.45F : 0.25F;
        boolean directStrikeWindow = gameTime % LIGHTNING_DIRECT_STRIKE_INTERVAL_TICKS == 0L;

        for (ServerPlayer player : level.players()) {
            boolean shouldForceDirectStrike = directStrikeWindow && random.nextFloat() < 0.18F;
            if (!shouldForceDirectStrike && random.nextFloat() > ambientChance) {
                continue;
            }

            BlockPos strikePos = shouldForceDirectStrike
                ? findStrikePosNear(level, BlockPos.containing(player.position()), 0, 10)
                : findStrikePosNear(level, BlockPos.containing(player.position()), LIGHTNING_ARC_MIN_RADIUS, LIGHTNING_ARC_MAX_RADIUS);
            if (strikePos == null) {
                continue;
            }

            spawnLightning(level, strikePos, player, parameters.permanentLightning() ? 8.0F : 6.0F);
        }
    }

    private static BlockPos findStrikePosNear(ServerLevel level, BlockPos origin, int minRadius, int maxRadius) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            int radius = maxRadius <= minRadius ? minRadius : random.nextInt(minRadius, maxRadius + 1);
            int offsetX = radius == 0 ? 0 : random.nextInt(-radius, radius + 1);
            int offsetZ = radius == 0 ? 0 : random.nextInt(-radius, radius + 1);
            BlockPos candidate = origin.offset(offsetX, 0, offsetZ);
            ChunkPos chunkPos = new ChunkPos(candidate);
            if (level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null) {
                continue;
            }

            BlockPos topPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, candidate);
            BlockPos strikePos = topPos.above();
            if (level.getChunkSource().getChunkNow(new ChunkPos(strikePos).x, new ChunkPos(strikePos).z) == null) {
                continue;
            }

            return strikePos;
        }

        return null;
    }

    private static void spawnLightning(ServerLevel level, BlockPos strikePos, ServerPlayer cause, float damage) {
        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) {
            return;
        }

        lightningBolt.moveTo(Vec3.atBottomCenterOf(strikePos));
        lightningBolt.setCause(cause);
        lightningBolt.setDamage(damage);
        level.addFreshEntity(lightningBolt);
    }

    private static void applyMeteorShowers(ServerLevel level, VerseDimensionParameters parameters) {
        if (!parameters.meteorShowers() || !isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.METEORS) || level.players().isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextMeteorTick = NEXT_METEOR_SHOWER_TICKS.computeIfAbsent(level.dimension(), ignored -> scheduleNextMeteorShower(level, gameTime));
        if (gameTime < nextMeteorTick) {
            return;
        }

        Vec3 targetPos = findMeteorShowerTarget(level);
        NEXT_METEOR_SHOWER_TICKS.put(level.dimension(), scheduleNextMeteorShower(level, gameTime));
        if (targetPos == null) {
            return;
        }

        MeteorEntity meteor = MeteorEntity.spawnToward(level, targetPos);
        if (level.addFreshEntity(meteor)) {
            playMeteorEnterCue(level);
        }
    }

    public static void playMeteorEnterCue(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }

        float pitch = 0.95F + level.getRandom().nextFloat() * 0.1F;
        Holder<net.minecraft.sounds.SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(VerseSounds.METEOR_ENTER.get());
        level.players().forEach(player -> player.connection.send(new ClientboundSoundPacket(
            sound,
            SoundSource.AMBIENT,
            player.getX(),
            player.getY(),
            player.getZ(),
            8.0F,
            pitch,
            level.getRandom().nextLong()
        )));
    }

    private static void applyWarpSpawns(ServerLevel level, VerseDimensionParameters parameters) {
        if (!parameters.spawnWarp() || !isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.WARP) || level.players().isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextWarpTick = NEXT_WARP_SPAWN_TICKS.computeIfAbsent(level.dimension(), ignored -> scheduleNextWarpSpawn(level, gameTime));
        if (gameTime < nextWarpTick) {
            return;
        }

        NEXT_WARP_SPAWN_TICKS.put(level.dimension(), scheduleNextWarpSpawn(level, gameTime));
        BlockPos targetPos = findWarpSpawnTarget(level);
        if (targetPos == null) {
            return;
        }

        placeWarpCluster(level, targetPos, level.getRandom(), 3, 5, 1, 2, RUNTIME_WARP_CLUSTER_RADIUS);
    }

    private static long scheduleNextMeteorShower(ServerLevel level, long gameTime) {
        if (METEOR_SHOWER_MAX_INTERVAL_TICKS <= METEOR_SHOWER_MIN_INTERVAL_TICKS) {
            return gameTime + METEOR_SHOWER_MIN_INTERVAL_TICKS;
        }

        int interval = level.getRandom().nextInt(METEOR_SHOWER_MIN_INTERVAL_TICKS, METEOR_SHOWER_MAX_INTERVAL_TICKS + 1);
        return gameTime + interval;
    }

    private static long scheduleNextWarpSpawn(ServerLevel level, long gameTime) {
        if (WARP_SPAWN_MAX_INTERVAL_TICKS <= WARP_SPAWN_MIN_INTERVAL_TICKS) {
            return gameTime + WARP_SPAWN_MIN_INTERVAL_TICKS;
        }

        int interval = level.getRandom().nextInt(WARP_SPAWN_MIN_INTERVAL_TICKS, WARP_SPAWN_MAX_INTERVAL_TICKS + 1);
        return gameTime + interval;
    }

    private static Vec3 findMeteorShowerTarget(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }

        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < METEOR_SHOWER_TARGET_ATTEMPTS; attempt++) {
            ServerPlayer anchor = players.get(random.nextInt(players.size()));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = random.nextInt(METEOR_SHOWER_MIN_RADIUS, METEOR_SHOWER_MAX_RADIUS + 1);
            int targetX = Mth.floor(anchor.getX() + Math.cos(angle) * radius);
            int targetZ = Mth.floor(anchor.getZ() + Math.sin(angle) * radius);
            ChunkPos targetChunk = new ChunkPos(new BlockPos(targetX, 0, targetZ));
            if (level.getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) == null) {
                continue;
            }

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(targetX, 0, targetZ));
            Vec3 impactTarget = Vec3.atBottomCenterOf(surface.above());
            if (!isMeteorTargetFarFromPlayers(players, impactTarget)) {
                continue;
            }

            return impactTarget;
        }

        return null;
    }

    private static boolean isMeteorTargetFarFromPlayers(List<ServerPlayer> players, Vec3 targetPos) {
        double minDistanceSqr = METEOR_SHOWER_MIN_PLAYER_DISTANCE * METEOR_SHOWER_MIN_PLAYER_DISTANCE;
        for (ServerPlayer player : players) {
            if (player.position().distanceToSqr(targetPos) < minDistanceSqr) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos findWarpSpawnTarget(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return null;
        }

        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < WARP_SPAWN_ATTEMPTS; attempt++) {
            ServerPlayer anchor = players.get(random.nextInt(players.size()));
            int chunkX = anchor.chunkPosition().x + random.nextInt(-WARP_SPAWN_CHUNK_RADIUS, WARP_SPAWN_CHUNK_RADIUS + 1);
            int chunkZ = anchor.chunkPosition().z + random.nextInt(-WARP_SPAWN_CHUNK_RADIUS, WARP_SPAWN_CHUNK_RADIUS + 1);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                continue;
            }

            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            BlockPos candidate = surfaceWarpTarget(level, new BlockPos(x, 0, z));
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private static void applyGravity(ServerPlayer player, VerseDimensionParameters parameters) {
        if (Math.abs(parameters.gravityScale() - 1.0D) < 0.00001D || !isCorruptionRuleEnabled(parameters, VerseDimensionCorruption.GRAVITY)) {
            return;
        }

        if (player.onGround() || player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.isSpectator()) {
            return;
        }

        if (player.getFluidHeight(net.minecraft.tags.FluidTags.WATER) > 0.0D || player.getFluidHeight(net.minecraft.tags.FluidTags.LAVA) > 0.0D) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double adjustedY = velocity.y + (1.0D - parameters.gravityScale()) * BASE_GRAVITY_PER_TICK;
        double adjustedX = velocity.x;
        double adjustedZ = velocity.z;
        if (parameters.gravityScale() < 1.0D) {
            double moonFactor = 1.0D - parameters.gravityScale();
            double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (horizontalSpeed > 1.0E-4D) {
                double carryMultiplier = 1.0D + moonFactor * 0.03D;
                double boostedHorizontalSpeed = Math.min(horizontalSpeed * carryMultiplier, horizontalSpeed + 0.018D + moonFactor * 0.03D);
                double speedScale = boostedHorizontalSpeed / horizontalSpeed;
                adjustedX *= speedScale;
                adjustedZ *= speedScale;
            }

            Vec3 knownMovement = player.getKnownMovement();
            Vec3 horizontalIntent = new Vec3(knownMovement.x, 0.0D, knownMovement.z);
            if (horizontalIntent.lengthSqr() > 1.0E-4D) {
                Vec3 normalizedIntent = horizontalIntent.normalize().scale(0.006D + moonFactor * 0.02D);
                adjustedX += normalizedIntent.x;
                adjustedZ += normalizedIntent.z;
            }
        }

        player.setDeltaMovement(adjustedX, adjustedY, adjustedZ);
        player.hurtMarked = true;
    }

    public static RespawnTarget overworldRespawnData(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return new RespawnTarget(overworld.getSharedSpawnPos(), overworld.getSharedSpawnAngle(), 0.0F);
    }

    public record RespawnTarget(BlockPos pos, float yaw, float pitch) {
    }

    private static void scheduleSpawnRegionPreparation(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, ChunkPos entryChunk) {
        VerseWorks.LOGGER.info("Preparing VerseWorks spawn region for {} with {}", level.dimension().location(), parameters.rulesSummary());
        PENDING_DECORATION_JOBS.remove(level.dimension());

        rememberPreparedEntryPoint(level, resolvePreparedEntryPoint(level, parameters, entryChunk));

        Deque<DecorationJob> jobs = new ArrayDeque<>();
        queueChunkDecorations(server, level, parameters, entryChunk, jobs);
        if (parameters.spawnWarp()) {
            createInitialWarpClusterJob(server, level, entryChunk, PREPARED_ENTRY_POINTS.get(level.dimension()))
                .ifPresent(jobs::addLast);
        }

        if (jobs.isEmpty()) {
            releaseEntryPreparationTicket(level);
            VerseWorks.LOGGER.info("Finished VerseWorks spawn region prep for {}", level.dimension().location());
            return;
        }

        PENDING_DECORATION_JOBS.put(level.dimension(), jobs);
        VerseWorks.LOGGER.info("Queued {} VerseWorks initial decoration job(s) for {}", jobs.size(), level.dimension().location());
    }

    private static void processPendingSpawnPreparation(ServerLevel level) {
        PendingSpawnPreparation pending = PENDING_SPAWN_PREPARATIONS.get(level.dimension());
        if (pending == null) {
            return;
        }

        if (pending.warmupFuture().isCompletedExceptionally()) {
            if (PENDING_SPAWN_PREPARATIONS.remove(level.dimension(), pending)) {
                VerseWorks.LOGGER.warn("VerseWorks entry preparation warmup failed for {}; falling back to loaded entry area only", level.dimension().location());
                rememberPreparedEntryPoint(level, resolvePreparedEntryPoint(level, pending.parameters(), pending.entryChunk()));
                releaseEntryPreparationTicket(level);
            }
            return;
        }

        if (!pending.warmupFuture().isDone()) {
            return;
        }

        ChunkPos entryChunk = pending.entryChunk();
        if (!areChunksAvailable(level, entryChunk, pending.searchRadius())) {
            return;
        }

        if (!PENDING_SPAWN_PREPARATIONS.remove(level.dimension(), pending)) {
            return;
        }

        VerseWorks.LOGGER.info("VerseWorks entry preparation warmup completed for {}; scheduling spawn region prep", level.dimension().location());
        scheduleSpawnRegionPreparation(level.getServer(), level, pending.parameters(), entryChunk);
    }

    private static void processDecorationJobs(ServerLevel level) {
        Deque<DecorationJob> jobs = PENDING_DECORATION_JOBS.get(level.dimension());
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        DecorationJob job = jobs.peekFirst();
        job.run(level, job.budgetPerTick());
        if (!job.isDone()) {
            return;
        }

        jobs.removeFirst();
        VerseWorks.LOGGER.info("Completed VerseWorks decoration stage '{}' for {}", job.name(), level.dimension().location());
        if (!jobs.isEmpty()) {
            return;
        }

        PENDING_DECORATION_JOBS.remove(level.dimension());
        resolveParameters(level).ifPresent(parameters -> rememberPreparedEntryPoint(level, resolvePreparedEntryPoint(level, parameters, LiveDimensionInstantiator.getEntryChunk(level))));
        releaseEntryPreparationTicket(level);
        VerseWorks.LOGGER.info("Finished VerseWorks queued decoration batch for {}", level.dimension().location());
    }

    private static void queueNearbyChunkDecorations(ServerPlayer player, VerseDimensionParameters parameters) {
        if (!parameters.hasGlobalChunkDecorators()) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Deque<DecorationJob> jobs = PENDING_DECORATION_JOBS.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        if (jobs.size() >= MAX_PENDING_DECORATION_JOBS) {
            return;
        }

        ChunkPos centerChunk = player.chunkPosition();
        queueChunkDecorations(level.getServer(), level, parameters, centerChunk, jobs);
    }

    private static void queueChunkDecorations(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, ChunkPos chunkPos, Deque<DecorationJob> jobs) {
        ChunkDecorationState decorationState = DECORATED_CHUNKS.computeIfAbsent(level.dimension(), ignored -> new ChunkDecorationState());
        long chunkKey = chunkPos.toLong();

        if (parameters.effectiveOceanLevel() != null && decorationState.oceanChunks().add(chunkKey)) {
            jobs.addLast(new OceanFillJob(parameters, parameters.effectiveOceanLevel(), chunkPos));
        }

        if (!parameters.poolFeatures().isEmpty() && decorationState.lakeChunks().add(chunkKey)) {
            jobs.addAll(createLakeJobs(server, level, parameters, chunkPos));
        }

        if (!parameters.shapeFeatures().isEmpty() && decorationState.sphereChunks().add(chunkKey)) {
            jobs.addAll(createSphereJobs(server, level, parameters, chunkPos));
        }

        if (!parameters.oreMorphFeatures().isEmpty() && !parameters.worldType().isFlat() && decorationState.oreChunks().add(chunkKey)) {
            jobs.addLast(new OrePlacementJob(server, level, parameters, chunkPos));
        }
    }

    private static Optional<DecorationJob> createInitialWarpClusterJob(MinecraftServer server, ServerLevel level, ChunkPos entryChunk, BlockPos preparedEntry) {
        if (preparedEntry == null) {
            return Optional.empty();
        }

        RandomSource random = chunkRandom(server, level, entryChunk, 0x57415250L);
        BlockPos clusterCenter = findInitialWarpClusterCenter(level, preparedEntry, random);
        if (clusterCenter == null) {
            return Optional.empty();
        }

        List<BlockPlacement> placements = new ArrayList<>();
        Set<BlockPos> warpPositions = new HashSet<>();
        collectWarpClusterPlacements(level, clusterCenter, random, 5, 8, 3, 6, 2, warpPositions, placements);
        if (placements.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PlacementJob("initial_warp_cluster", placements, 24));
    }

    private static void placeWarpCluster(ServerLevel level, BlockPos center, RandomSource random, int minWarpCount, int maxWarpCount, int minVineCount, int maxVineCount, int radius) {
        Set<BlockPos> warpPositions = new HashSet<>();
        List<BlockPlacement> placements = new ArrayList<>();
        collectWarpClusterPlacements(level, center, random, minWarpCount, maxWarpCount, minVineCount, maxVineCount, radius, warpPositions, placements);
        for (BlockPlacement placement : placements) {
            placeQueuedBlock(level, placement.position(), placement.state());
            if (placement.state().is(VerseBlocks.WARP.get()) || placement.state().is(VerseBlocks.WARP_VINE.get())) {
                WarpSpreadHelper.playSpreadSound(level, placement.position());
            }
        }
    }

    private static void collectWarpClusterPlacements(
        ServerLevel level,
        BlockPos clusterCenter,
        RandomSource random,
        int minWarpCount,
        int maxWarpCount,
        int minVineCount,
        int maxVineCount,
        int radius,
        Set<BlockPos> warpPositions,
        List<BlockPlacement> placements
    ) {
        int warpCount = minWarpCount + random.nextInt(maxWarpCount - minWarpCount + 1);
        for (int attempt = 0; attempt < 64 && warpPositions.size() < warpCount; attempt++) {
            int offsetX = random.nextInt(-radius, radius + 1);
            int offsetZ = random.nextInt(-radius, radius + 1);
            if (Math.abs(offsetX) + Math.abs(offsetZ) > radius * 2) {
                continue;
            }

            BlockPos surfaceTarget = surfaceWarpTarget(level, clusterCenter.offset(offsetX, 0, offsetZ));
            if (surfaceTarget == null || !warpPositions.add(surfaceTarget.immutable())) {
                continue;
            }

            placements.add(new BlockPlacement(surfaceTarget, VerseBlocks.WARP.get().defaultBlockState()));
        }

        if (warpPositions.isEmpty()) {
            return;
        }

        List<BlockPos> warpList = new ArrayList<>(warpPositions);
        int vineCount = Math.min(warpList.size() + maxVineCount, minVineCount + random.nextInt(maxVineCount - minVineCount + 1));
        for (int index = 0; index < vineCount; index++) {
            BlockPos anchor = warpList.get(random.nextInt(warpList.size()));
            BlockPos vinePos = findInitialWarpVineSpot(level, anchor, random);
            if (vinePos == null) {
                continue;
            }

            BlockState vineState = WarpVineBlock.placementStateForWorldgen(level, vinePos);
            if (vineState != null) {
                placements.add(new BlockPlacement(vinePos, vineState));
            }
        }
    }

    private static BlockPos findInitialWarpClusterCenter(ServerLevel level, BlockPos origin, RandomSource random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int offsetX = random.nextInt(-INITIAL_WARP_CLUSTER_RADIUS_BLOCKS, INITIAL_WARP_CLUSTER_RADIUS_BLOCKS + 1);
            int offsetZ = random.nextInt(-INITIAL_WARP_CLUSTER_RADIUS_BLOCKS, INITIAL_WARP_CLUSTER_RADIUS_BLOCKS + 1);
            if (Math.abs(offsetX) + Math.abs(offsetZ) > INITIAL_WARP_CLUSTER_RADIUS_BLOCKS) {
                continue;
            }

            BlockPos candidate = origin.offset(offsetX, 0, offsetZ);
            BlockPos surfaceTarget = surfaceWarpTarget(level, candidate);
            if (surfaceTarget != null) {
                return surfaceTarget;
            }
        }

        return surfaceWarpTarget(level, origin);
    }

    private static BlockPos surfaceWarpTarget(ServerLevel level, BlockPos targetColumn) {
        if (!isChunkLoaded(level, targetColumn)) {
            return null;
        }

        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetColumn.getX(), 0, targetColumn.getZ()));
        BlockPos target = surface.below();
        if (target.getY() <= level.dimensionType().minY()) {
            return null;
        }

        BlockState targetState = level.getBlockState(target);
        if (!level.getFluidState(target).isEmpty() || targetState.isAir() || targetState.is(Blocks.BEDROCK) || targetState.is(VerseBlocks.WARP.get()) || targetState.is(VerseBlocks.WARP_VINE.get()) || targetState.is(VerseBlocks.STABILIZED_WARP_VINE.get())) {
            return null;
        }

        return target;
    }

    private static BlockPos findInitialWarpVineSpot(ServerLevel level, BlockPos anchor, RandomSource random) {
        List<BlockPos> candidates = List.of(
            anchor.above(),
            anchor.north(),
            anchor.south(),
            anchor.east(),
            anchor.west(),
            anchor.above().north(),
            anchor.above().south(),
            anchor.above().east(),
            anchor.above().west()
        );

        List<BlockPos> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
        for (BlockPos candidate : shuffled) {
            BlockState candidateState = level.getBlockState(candidate);
            if (!level.getFluidState(candidate).isEmpty()) {
                continue;
            }

            if (!candidateState.isAir() && !candidateState.canBeReplaced()) {
                continue;
            }

            if (candidateState.is(VerseBlocks.WARP.get())) {
                continue;
            }

            BlockState vineState = WarpVineBlock.placementStateForWorldgen(level, candidate);
            if (vineState != null) {
                return candidate;
            }
        }

        return null;
    }

    private static BlockPos findExistingSafeSpot(ServerLevel level, ChunkPos entryChunk) {
        int anchorX = entryChunk.getMiddleBlockX();
        int anchorZ = entryChunk.getMiddleBlockZ();
        for (int radius = 0; radius <= ENTRY_SAFE_SEARCH_RADIUS_BLOCKS; radius += ENTRY_SAFE_SEARCH_STEP_BLOCKS) {
            for (int x = anchorX - radius; x <= anchorX + radius; x += ENTRY_SAFE_SEARCH_STEP_BLOCKS) {
                for (int z = anchorZ - radius; z <= anchorZ + radius; z += ENTRY_SAFE_SEARCH_STEP_BLOCKS) {
                    if (radius > 0 && x > anchorX - radius && x < anchorX + radius && z > anchorZ - radius && z < anchorZ + radius) {
                        continue;
                    }

                    if (!isChunkLoaded(level, BlockPos.containing(x, level.dimensionType().minY(), z))) {
                        continue;
                    }

                    BlockPos standPos = findStandPosAt(level, x, z);
                    if (isSafeStandingSpot(level, standPos)) {
                        return standPos;
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos findNearbyChunkSafeSpot(ServerLevel level, ChunkPos entryChunk, int chunkRadius, boolean loadChunks) {
        for (int radius = 1; radius <= chunkRadius; radius++) {
            for (int chunkX = entryChunk.x - radius; chunkX <= entryChunk.x + radius; chunkX++) {
                for (int chunkZ = entryChunk.z - radius; chunkZ <= entryChunk.z + radius; chunkZ++) {
                    if (radius > 0 && chunkX > entryChunk.x - radius && chunkX < entryChunk.x + radius && chunkZ > entryChunk.z - radius && chunkZ < entryChunk.z + radius) {
                        continue;
                    }

                    if (!ensureChunkAvailable(level, chunkX, chunkZ, loadChunks)) {
                        continue;
                    }

                    BlockPos safeSpot = findSafeSpotInChunk(level, new ChunkPos(chunkX, chunkZ));
                    if (safeSpot != null) {
                        return safeSpot;
                    }
                }
            }
        }

        return null;
    }

    private static BlockPos findSafeSpotInChunk(ServerLevel level, ChunkPos chunkPos) {
        int startX = chunkPos.getMinBlockX() + 1;
        int endX = chunkPos.getMaxBlockX() - 1;
        int startZ = chunkPos.getMinBlockZ() + 1;
        int endZ = chunkPos.getMaxBlockZ() - 1;
        for (int x = startX; x <= endX; x += ENTRY_SAFE_SEARCH_STEP_BLOCKS) {
            for (int z = startZ; z <= endZ; z += ENTRY_SAFE_SEARCH_STEP_BLOCKS) {
                BlockPos standPos = findStandPosAt(level, x, z);
                if (isSafeStandingSpot(level, standPos)) {
                    return standPos;
                }
            }
        }
        return null;
    }

    private static BlockPos findStandPosAt(ServerLevel level, int x, int z) {
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (isSafeStandingSpot(level, surfacePos)) {
            return surfacePos.immutable();
        }

        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = maxY - 1; y >= minY + 1; y--) {
            cursor.set(x, y, z);
            if (isSafeStandingSpot(level, cursor)) {
                return cursor.immutable();
            }
        }

        return new BlockPos(x, Math.max(minY + 1, 64), z);
    }

    private static boolean shouldPreferNaturalSpawn(VerseDimensionParameters parameters) {
        return parameters.allowsNaturalSpawnSearch();
    }

    private static BlockPos prepareEntryArrival(ServerLevel level, VerseDimensionParameters parameters, ChunkPos entryChunk) {
        if (shouldPreferNaturalSpawn(parameters)) {
            BlockPos safeSpot = findExistingSafeSpot(level, entryChunk);
            if (safeSpot != null) {
                return finalizePreparedArrival(level, safeSpot, parameters, false);
            }

            BlockPos nearbySafeSpot = findNearbyChunkSafeSpot(level, entryChunk, naturalSpawnSearchRadius(parameters), false);
            if (nearbySafeSpot != null) {
                return finalizePreparedArrival(level, nearbySafeSpot, parameters, false);
            }
        }

        return createForcedArrival(level, parameters, entryChunk);
    }

    private static BlockPos createForcedArrival(ServerLevel level, VerseDimensionParameters parameters, ChunkPos entryChunk) {
        int minBuildHeight = level.dimensionType().minY();
        int anchorY = determinePlatformY(level, parameters, entryChunk);
        if (anchorY < minBuildHeight + 4) {
            anchorY = minBuildHeight + 8;
        }

        BlockPos center = new BlockPos(entryChunk.getMiddleBlockX(), anchorY, entryChunk.getMiddleBlockZ());
        return finalizePreparedArrival(level, center, parameters, true);
    }

    private static BlockPos finalizePreparedArrival(ServerLevel level, BlockPos center, VerseDimensionParameters parameters, boolean forcePocket) {
        BlockPos candidate = center.immutable();
        if (!forcePocket && isPreparedArrivalSpot(level, parameters, candidate)) {
            clearHeadroom(level, candidate, 4);
            return candidate;
        }

        return createArrivalPocket(level, candidate, parameters);
    }

    private static BlockPos createArrivalPocket(ServerLevel level, BlockPos center, VerseDimensionParameters parameters) {
        int minBuildHeight = level.dimensionType().minY();
        int maxBuildHeight = minBuildHeight + level.dimensionType().height() - 1;
        int[] yOffsets = {0, 6, -6, 12, -12};
        for (int yOffset : yOffsets) {
            int candidateY = Mth.clamp(center.getY() + yOffset, minBuildHeight + 8, maxBuildHeight - 8);
            BlockPos candidate = new BlockPos(center.getX(), candidateY, center.getZ());
            ensurePlatform(level, candidate.below(), platformBlock(parameters), platformRadius(parameters));
            clearHeadroom(level, candidate, 4);
            if (isSafeStandingSpot(level, candidate)) {
                return candidate.immutable();
            }
        }

        BlockPos emergency = new BlockPos(center.getX(), Mth.clamp(center.getY(), minBuildHeight + 8, maxBuildHeight - 8), center.getZ());
        ensurePlatform(level, emergency.below(), DEFAULT_SAFETY_PLATFORM_BLOCK, DEFAULT_SAFETY_PLATFORM_RADIUS + 1);
        clearHeadroom(level, emergency, 5);
        return emergency.immutable();
    }

    private static int naturalSpawnSearchRadius(VerseDimensionParameters parameters) {
        return parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND ? 10 : 6;
    }

    private static boolean areChunksAvailable(ServerLevel level, ChunkPos centerChunk, int chunkRadius) {
        for (int chunkX = centerChunk.x - chunkRadius; chunkX <= centerChunk.x + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunk.z - chunkRadius; chunkZ <= centerChunk.z + chunkRadius; chunkZ++) {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int determinePlatformY(ServerLevel level, VerseDimensionParameters parameters, ChunkPos entryChunk) {
        int minBuildHeight = level.dimensionType().minY();
        int maxBuildHeight = minBuildHeight + level.dimensionType().height() - 1;
        if (parameters.worldType() == VerseDimensionWorldType.BEDROCK_SHELL) {
            return Mth.clamp(maxBuildHeight - 26, minBuildHeight + 12, maxBuildHeight - 16);
        }

        if (parameters.worldType().hasCeiling()) {
            return Mth.clamp(minBuildHeight + 96, minBuildHeight + 8, maxBuildHeight - 24);
        }

        Integer oceanLevel = parameters.effectiveOceanLevel();
        if (oceanLevel != null) {
            return Math.max(oceanLevel + 2, minBuildHeight + 8);
        }
        if (parameters.worldType().isFluidWorld()) {
            return Math.max(minBuildHeight + 8, 96);
        }

        BlockPos anchor = new BlockPos(entryChunk.getMiddleBlockX(), 0, entryChunk.getMiddleBlockZ());
        BlockPos standPos = findStandPosAt(level, anchor.getX(), anchor.getZ());
        if (!parameters.worldType().isVoid() && isSafeStandingSpot(level, standPos) && standPos.getY() >= minBuildHeight + 4) {
            return standPos.getY();
        }

        return Math.max(96, minBuildHeight + 8);
    }

    private static boolean ensureChunkAvailable(ServerLevel level, int chunkX, int chunkZ, boolean loadChunks) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            return true;
        }

        if (!loadChunks) {
            return false;
        }

        level.getChunk(chunkX, chunkZ);
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    private static CompletableFuture<?> warmChunks(ServerLevel level, ChunkPos centerChunk, int radius) {
        return VerseChunkWarmup.request(level, centerChunk, radius);
    }

    private static CompletableFuture<?> loadChunks(ServerLevel level, ChunkPos centerChunk, int radius) {
        return VerseChunkWarmup.requestLoaded(level, centerChunk, radius);
    }

    private static void logSlowEntryPreparation(ServerLevel level, long startedAtNanos, String action) {
        long elapsed = System.nanoTime() - startedAtNanos;
        if (elapsed < SLOW_ENTRY_PREPARATION_THRESHOLD_NANOS) {
            return;
        }

        VerseWorks.LOGGER.info("{} for {} took {} ms", action, level.dimension().location(), elapsed / 1_000_000L);
    }

    private static void invokeChunkTicket(ServerLevel level, String methodName, ChunkPos chunkPos, int radius) throws ReflectiveOperationException {
        Object chunkSource = level.getChunkSource();
        for (Method method : chunkSource.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 4) {
                continue;
            }

            method.invoke(chunkSource, TicketType.PLAYER, chunkPos, radius, chunkPos);
            return;
        }
    }

    private static boolean isSafeStandingSpot(ServerLevel level, BlockPos standPos) {
        if (!isChunkLoaded(level, standPos)) {
            return false;
        }

        BlockPos belowPos = standPos.below();
        BlockState belowState = level.getBlockState(belowPos);
        BlockState feetState = level.getBlockState(standPos);
        BlockState headState = level.getBlockState(standPos.above());
        return belowState.isFaceSturdy(level, belowPos, Direction.UP)
            && feetState.getFluidState().isEmpty()
            && headState.getFluidState().isEmpty()
            && feetState.isAir()
            && headState.isAir();
    }

    private static boolean isPreparedArrivalSpot(ServerLevel level, VerseDimensionParameters parameters, BlockPos standPos) {
        if (!isSafeStandingSpot(level, standPos)) {
            return false;
        }

        if (!parameters.worldType().hasBedrockShell()) {
            return true;
        }

        return hasBedrockRoofAbove(level, standPos, 40);
    }

    private static boolean hasBedrockRoofAbove(ServerLevel level, BlockPos standPos, int maxDistance) {
        int maxY = level.dimensionType().minY() + level.dimensionType().height() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int upperBound = Math.min(maxY, standPos.getY() + maxDistance);
        for (int y = standPos.getY() + 2; y <= upperBound; y++) {
            cursor.set(standPos.getX(), y, standPos.getZ());
            if (level.getBlockState(cursor).is(Blocks.BEDROCK)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        return level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) != null;
    }

    private static void ensurePlatform(ServerLevel level, BlockPos center, Block block, int radius) {
        BlockState platform = block.defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                placeQueuedBlock(level, center.offset(dx, 0, dz), platform);
            }
        }
    }

    private static Block platformBlock(VerseDimensionParameters parameters) {
        if (parameters.requiresSafetyPlatformSpawn()) {
            return DEFAULT_SAFETY_PLATFORM_BLOCK;
        }

        return parameters.floorBlock();
    }

    private static int platformRadius(VerseDimensionParameters parameters) {
        if (parameters.requiresSafetyPlatformSpawn()) {
            return DEFAULT_SAFETY_PLATFORM_RADIUS;
        }

        return 4;
    }

    private static void clearHeadroom(ServerLevel level, BlockPos center, int height) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    placeQueuedBlock(level, center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static int sampleOreY(RandomSource random, int minBuildHeight) {
        int[] bands = {minBuildHeight + 8, minBuildHeight + 24, minBuildHeight + 40, minBuildHeight + 64, minBuildHeight + 96, minBuildHeight + 128};
        int base = bands[random.nextInt(bands.length)];
        return base + random.nextInt(24) - 12;
    }

    private static List<DecorationJob> createLakeJobs(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, ChunkPos chunkPos) {
        List<DecorationJob> jobs = new ArrayList<>();
        int minY = level.dimensionType().minY() + 4;
        for (VerseDimensionParameters.PoolFeatureSpec poolSpec : parameters.poolFeatures()) {
            RandomSource random = chunkRandom(server, level, chunkPos, 0x4C414B45L ^ poolSpec.fluidId().hashCode());
            int lakeCount = random.nextDouble() < poolSpec.chancePerChunk() ? 1 : 0;
            for (int index = 0; index < lakeCount; index++) {
                int x = (chunkPos.x << 4) + 3 + random.nextInt(10);
                int z = (chunkPos.z << 4) + 3 + random.nextInt(10);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (surface.getY() <= minY + 3) {
                    continue;
                }

                int basinY = Math.max(minY, surface.getY());
                if (!isTerrainPuddleSite(level, new BlockPos(x, basinY, z))) {
                    continue;
                }

                jobs.add(new LakePlacementJob(resolveFluid(parameters, poolSpec.fluidId()).defaultFluidState().createLegacyBlock(), new BlockPos(x, basinY, z), poolSpec.radius(), poolSpec.depth()));
            }
        }
        return jobs;
    }

    private static boolean isTerrainPuddleSite(ServerLevel level, BlockPos center) {
        BlockPos below = center.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }

        if (level.getBlockState(center).isAir() && level.getBlockState(center.above()).isAir()) {
            return false;
        }

        int exposedSides = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = center.relative(direction);
            if (level.getBlockState(neighbor).isAir() || level.getBlockState(neighbor.below()).isAir()) {
                exposedSides++;
            }
        }
        return exposedSides <= 1;
    }

    private static List<DecorationJob> createSphereJobs(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, ChunkPos chunkPos) {
        List<DecorationJob> jobs = new ArrayList<>();
        for (VerseDimensionParameters.ShapeFeatureSpec shapeSpec : parameters.shapeFeatures()) {
            RandomSource random = chunkRandom(server, level, chunkPos, 0x53504845L ^ shapeSpec.shape().serializedName().hashCode() ^ BuiltInRegistries.BLOCK.getKey(shapeSpec.block()).hashCode());
            int sphereCount = random.nextDouble() < shapeSpec.chancePerChunk() ? 1 : 0;
            if (parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND && random.nextDouble() < 0.35D) {
                sphereCount++;
            }
            if (sphereCount == 0) {
                continue;
            }

            List<int[]> spheres = new ArrayList<>(sphereCount);
            for (int index = 0; index < sphereCount; index++) {
                int x = (chunkPos.x << 4) + random.nextInt(16);
                int z = (chunkPos.z << 4) + random.nextInt(16);
                int y = parameters.sampleHeight(random, shapeSpec.heightDistribution(), level.dimensionType().minY(), level.dimensionType().minY() + level.dimensionType().height());
                if ("surface".equals(shapeSpec.heightDistribution().profile())) {
                    int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY() - 1;
                    y = Math.max(level.dimensionType().minY() + 8, surfaceY + Math.max(2, shapeSpec.maxRadius() / 2));
                    if (shapeSpec.shape() == VerseDimensionParameters.ShapeKind.CUBE && random.nextDouble() < 0.28D) {
                        y = Math.min(level.dimensionType().minY() + level.dimensionType().height() - shapeSpec.maxRadius() - 4, y + 14 + random.nextInt(28));
                    }
                }
                int radius = shapeSpec.minRadius() + random.nextInt(Math.max(1, shapeSpec.maxRadius() - shapeSpec.minRadius() + 1));
                spheres.add(new int[]{x, y, z, radius});
            }
            jobs.add(new SpherePlacementJob(shapeSpec, spheres));
        }
        return jobs;
    }

    private static RandomSource chunkRandom(MinecraftServer server, ServerLevel level, ChunkPos chunkPos, long salt) {
        long seedBase = server.getWorldData().worldGenOptions().seed();
        if (level != null) {
            seedBase ^= resolveParameters(level)
                .map(VerseDimensionParameters::seedOffset)
                .orElse((long) level.dimension().location().hashCode());
        }
        long mixedSeed = seedBase ^ salt ^ (chunkPos.x * 341873128712L) ^ (chunkPos.z * 132897987541L);
        return RandomSource.create(mixedSeed);
    }

    private static void placeQueuedBlock(ServerLevel level, BlockPos position, BlockState state) {
        level.setBlock(position, state, 2);
    }

    private static Fluid resolveFluid(VerseDimensionParameters parameters) {
        ResourceLocation fluidId = parameters.effectiveFluidId();
        return resolveFluid(parameters, fluidId);
    }

    private static Fluid resolveFluid(VerseDimensionParameters parameters, ResourceLocation fluidId) {
        if (fluidId == null) {
            return BuiltInRegistries.FLUID.getOptional(ResourceLocation.withDefaultNamespace("water")).orElse(net.minecraft.world.level.material.Fluids.WATER);
        }

        return BuiltInRegistries.FLUID.getOptional(fluidId)
            .orElseGet(() -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.withDefaultNamespace("water")).orElse(net.minecraft.world.level.material.Fluids.WATER));
    }

    private interface DecorationJob {
        void run(ServerLevel level, int budget);

        boolean isDone();

        String name();

        default int budgetPerTick() {
            return 128;
        }
    }

    private static final class OceanFillJob implements DecorationJob {
        private final BlockState fluidState;
        private final int oceanLevel;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private int x;
        private int z;
        private Integer y;
        private boolean done;

        private OceanFillJob(VerseDimensionParameters parameters, int oceanLevel, ChunkPos chunkPos) {
            this.fluidState = resolveFluid(parameters).defaultFluidState().createLegacyBlock();
            this.oceanLevel = oceanLevel;
            this.minX = chunkPos.getMinBlockX();
            this.maxX = chunkPos.getMaxBlockX();
            this.minZ = chunkPos.getMinBlockZ();
            this.maxZ = chunkPos.getMaxBlockZ();
            this.x = this.minX;
            this.z = this.minZ;
        }

        @Override
        public void run(ServerLevel level, int budget) {
            if (this.fluidState.isAir()) {
                this.done = true;
                return;
            }

            while (budget > 0 && !this.done) {
                if (this.y == null) {
                    BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(this.x, 0, this.z));
                    this.y = Math.min(surface.getY(), this.oceanLevel);
                }

                while (budget > 0 && this.y <= this.oceanLevel) {
                    BlockPos position = new BlockPos(this.x, this.y, this.z);
                    if (level.getBlockState(position).isAir()) {
                        placeQueuedBlock(level, position, this.fluidState);
                    }
                    this.y++;
                    budget--;
                }

                if (this.y <= this.oceanLevel) {
                    continue;
                }

                this.y = null;
                this.z++;
                if (this.z > this.maxZ) {
                    this.z = this.minZ;
                    this.x++;
                }
                if (this.x > this.maxX) {
                    this.done = true;
                }
            }
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

        @Override
        public String name() {
            return "ocean_fill";
        }

        @Override
        public int budgetPerTick() {
            return this.fluidState.getFluidState().is(FluidTags.LAVA) ? 16 : 48;
        }
    }

    private static final class PlacementJob implements DecorationJob {
        private final String name;
        private final List<BlockPlacement> placements;
        private final int budgetPerTick;
        private int index;

        private PlacementJob(String name, List<BlockPlacement> placements, int budgetPerTick) {
            this.name = name;
            this.placements = List.copyOf(placements);
            this.budgetPerTick = budgetPerTick;
        }

        @Override
        public void run(ServerLevel level, int budget) {
            while (budget > 0 && this.index < this.placements.size()) {
                BlockPlacement placement = this.placements.get(this.index++);
                placeQueuedBlock(level, placement.position(), placement.state());
                budget--;
            }
        }

        @Override
        public boolean isDone() {
            return this.index >= this.placements.size();
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public int budgetPerTick() {
            return this.budgetPerTick;
        }
    }

    private static final class LakePlacementJob implements DecorationJob {
        private final BlockState fluidState;
        private final BlockPos center;
        private final int radius;
        private final int depth;
        private int dx;
        private int dz;
        private int offset;
        private int phase;
        private int currentColumnDepth;
        private BlockPos currentSurface;
        private boolean done;

        private LakePlacementJob(BlockState fluidState, BlockPos center, int radius, int depth) {
            this.fluidState = fluidState;
            this.center = center.immutable();
            this.radius = radius;
            this.depth = depth;
            this.dx = -this.radius;
            this.dz = -this.radius;
        }

        @Override
        public void run(ServerLevel level, int budget) {
            if (this.fluidState.isAir()) {
                this.done = true;
                return;
            }

            while (budget > 0 && !this.done) {
                if (this.currentSurface == null) {
                    while (true) {
                        if (this.dx > this.radius) {
                            this.done = true;
                            return;
                        }

                        double distance = Math.sqrt(this.dx * this.dx + this.dz * this.dz);
                        if (distance <= this.radius) {
                            this.currentColumnDepth = Math.max(1, this.depth - (int) Math.floor(distance / 2.5D));
                            this.currentSurface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.center.offset(this.dx, 0, this.dz));
                            this.offset = 0;
                            this.phase = 0;
                            break;
                        }

                        advanceColumn();
                    }
                }

                if (this.phase == 0) {
                    BlockPos target = this.currentSurface.below(this.offset);
                    placeQueuedBlock(level, target, this.fluidState);
                    this.offset++;
                    budget--;
                    if (this.offset > this.currentColumnDepth) {
                        this.phase = 1;
                        this.offset = 1;
                    }
                    continue;
                }

                BlockPos target = this.currentSurface.above(this.offset);
                placeQueuedBlock(level, target, Blocks.AIR.defaultBlockState());
                this.offset++;
                budget--;
                if (this.offset > this.currentColumnDepth + 1) {
                    advanceColumn();
                }
            }
        }

        private void advanceColumn() {
            this.currentSurface = null;
            this.dz++;
            if (this.dz > this.radius) {
                this.dz = -this.radius;
                this.dx++;
            }
        }

        @Override
        public boolean isDone() {
            return this.done;
        }

        @Override
        public String name() {
            return "lake";
        }

        @Override
        public int budgetPerTick() {
            return this.fluidState.getFluidState().is(FluidTags.LAVA) ? 16 : 48;
        }
    }

    private static final class SpherePlacementJob implements DecorationJob {
        private final List<int[]> sphereDefinitions;
        private final BlockState shellState;
        private final BlockState interiorState;
        private final VerseDimensionParameters.ShapeKind shapeKind;
        private final boolean hollow;
        private int sphereIndex;
        private int dx;
        private int dy;
        private int dz;
        private boolean initialized;

        private SpherePlacementJob(VerseDimensionParameters.ShapeFeatureSpec shapeSpec, List<int[]> sphereDefinitions) {
            this.sphereDefinitions = List.copyOf(sphereDefinitions);
            this.shellState = shapeSpec.block().defaultBlockState();
            this.interiorState = shapeSpec.block().defaultBlockState();
            this.shapeKind = shapeSpec.shape();
            this.hollow = shapeSpec.hollow();
        }

        @Override
        public void run(ServerLevel level, int budget) {
            while (budget > 0 && this.sphereIndex < this.sphereDefinitions.size()) {
                int[] sphere = this.sphereDefinitions.get(this.sphereIndex);
                int radius = sphere[3];
                int radiusSquared = radius * radius;
                int innerRadiusSquared = Math.max(1, (radius - 1) * (radius - 1));
                BlockPos center = new BlockPos(sphere[0], sphere[1], sphere[2]);

                if (!this.initialized) {
                    this.dx = -radius;
                    this.dy = -radius;
                    this.dz = -radius;
                    this.initialized = true;
                }

                while (budget > 0 && this.dx <= radius) {
                    int distanceSquared = this.dx * this.dx + this.dy * this.dy + this.dz * this.dz;
                    boolean inside = this.shapeKind == VerseDimensionParameters.ShapeKind.CUBE
                        ? Math.abs(this.dx) <= radius && Math.abs(this.dy) <= radius && Math.abs(this.dz) <= radius
                        : distanceSquared <= radiusSquared;
                    if (inside) {
                        BlockPos position = center.offset(this.dx, this.dy, this.dz);
                        boolean shell = this.shapeKind == VerseDimensionParameters.ShapeKind.CUBE
                            ? Math.abs(this.dx) == radius || Math.abs(this.dy) == radius || Math.abs(this.dz) == radius
                            : distanceSquared >= innerRadiusSquared;
                        if (!this.hollow || shell) {
                            placeQueuedBlock(level, position, shell ? this.shellState : this.interiorState);
                        }
                        budget--;
                    }

                    this.dz++;
                    if (this.dz > radius) {
                        this.dz = -radius;
                        this.dy++;
                        if (this.dy > radius) {
                            this.dy = -radius;
                            this.dx++;
                        }
                    }
                }

                if (this.dx <= radius) {
                    return;
                }

                this.sphereIndex++;
                this.initialized = false;
            }
        }

        @Override
        public boolean isDone() {
            return this.sphereIndex >= this.sphereDefinitions.size();
        }

        @Override
        public String name() {
            return "spheres";
        }

        @Override
        public int budgetPerTick() {
            return 96;
        }
    }

    private static void placeOreCluster(ServerLevel level, BlockPos origin, BlockState oreState, int size, RandomSource random) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int index = 0; index < size; index++) {
            BlockState existingState = level.getBlockState(cursor);
            if (VerseOreProfiles.canReplace(existingState)) {
                placeQueuedBlock(level, cursor, oreState);
            }

            cursor.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        }
    }

    private static final class OrePlacementJob implements DecorationJob {
        private final ChunkPos chunkPos;
        private final RandomSource random;
        private final int minBuildHeight;
        private final List<VerseDimensionParameters.OreMorphSpec> oreSpecs;
        private int currentSpecIndex;
        private int completedVeinsForSpec;
        private boolean started;

        private OrePlacementJob(MinecraftServer server, ServerLevel level, VerseDimensionParameters parameters, ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            this.oreSpecs = List.copyOf(parameters.oreMorphFeatures());
            this.random = chunkRandom(server, level, chunkPos, 0x4F524553L);
            this.minBuildHeight = level.dimensionType().minY();
        }

        @Override
        public void run(ServerLevel level, int budget) {
            if (!this.started) {
                this.started = true;
                VerseWorks.LOGGER.info(
                    "Starting ore enrichment for {} chunk {},{} with {} profile rules",
                    level.dimension().location(),
                    this.chunkPos.x,
                    this.chunkPos.z,
                    this.oreSpecs.size()
                );
            }

            while (budget > 0 && this.currentSpecIndex < this.oreSpecs.size()) {
                VerseDimensionParameters.OreMorphSpec oreSpec = this.oreSpecs.get(this.currentSpecIndex);
                int totalVeins = VerseOreProfiles.veinsPerChunk(oreSpec.profile(), oreSpec.multiplier());
                if (this.completedVeinsForSpec >= totalVeins) {
                    this.currentSpecIndex++;
                    this.completedVeinsForSpec = 0;
                    continue;
                }

                int x = (this.chunkPos.x << 4) + this.random.nextInt(16);
                int y = VerseOreProfiles.sampleY(oreSpec.profile(), this.random, this.minBuildHeight);
                int z = (this.chunkPos.z << 4) + this.random.nextInt(16);
                BlockPos anchor = findOreAnchor(level, x, y, z);
                if (anchor == null) {
                    this.completedVeinsForSpec++;
                    budget--;
                    continue;
                }
                placeOreCluster(level, anchor, oreSpec.replacementBlock().defaultBlockState(), VerseOreProfiles.clusterSize(oreSpec.profile(), this.random, oreSpec.multiplier()), this.random);
                this.completedVeinsForSpec++;
                budget--;
            }
        }

        @Override
        public boolean isDone() {
            return this.currentSpecIndex >= this.oreSpecs.size();
        }

        @Override
        public String name() {
            return "ores";
        }

        @Override
        public int budgetPerTick() {
            return 10;
        }
    }

    private static BlockPos findOreAnchor(ServerLevel level, int x, int sampledY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height() - 1;
        int surfaceY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY() - 1;
        int startY = Mth.clamp(sampledY, minY + 4, Math.min(surfaceY, maxY - 4));
        for (int delta = 0; delta <= 32; delta++) {
            int downY = startY - delta;
            if (downY >= minY) {
                cursor.set(x, downY, z);
                if (VerseOreProfiles.canReplace(level.getBlockState(cursor))) {
                    return cursor.immutable();
                }
            }
            int upY = startY + delta;
            if (delta > 0 && upY <= maxY) {
                cursor.set(x, upY, z);
                if (VerseOreProfiles.canReplace(level.getBlockState(cursor))) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }

    private record ChunkDecorationState(Set<Long> oceanChunks, Set<Long> lakeChunks, Set<Long> sphereChunks, Set<Long> oreChunks) {
        private ChunkDecorationState() {
            this(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    private record PendingSpawnPreparation(
        VerseDimensionParameters parameters,
        ChunkPos entryChunk,
        int searchRadius,
        CompletableFuture<?> warmupFuture
    ) {
    }

    private record PreparationWarmupTicket(ChunkPos entryChunk, int radius) {
    }

    private record BlockPlacement(BlockPos position, BlockState state) {
    }

    private record PendingCommandTeleport(
        Entity entity,
        double targetX,
        double targetY,
        double targetZ,
        float yRot,
        float xRot,
        ChunkPos targetChunk,
        java.util.concurrent.CompletableFuture<?> warmupFuture,
        long queuedAtGameTime,
        String coordinateKey
    ) {
    }

    private record PendingCommandEntry(
        Entity entity,
        long queuedAtGameTime
    ) {
    }

    private record PendingHyperBookTeleport(
        ServerPlayer player,
        ChunkPos targetChunk,
        java.util.concurrent.CompletableFuture<?> warmupFuture,
        long queuedAtGameTime,
        long readyAtGameTime,
        boolean notified,
        boolean notifyWhenReady
    ) {
        private PendingHyperBookTeleport markNotified(long gameTime) {
            return new PendingHyperBookTeleport(player, targetChunk, warmupFuture, queuedAtGameTime, gameTime, true, notifyWhenReady);
        }
    }

    private record StartupPreparedDimension(
        ResourceLocation dimensionId,
        VerseDimensionParameters parameters,
        CompletableFuture<LiveDimensionInstantiator.Result> activationFuture
    ) {
        private StartupPreparedDimension(ResourceLocation dimensionId, VerseDimensionParameters parameters) {
            this(dimensionId, parameters, null);
        }

        private StartupPreparedDimension withActivationFuture(CompletableFuture<LiveDimensionInstantiator.Result> future) {
            return new StartupPreparedDimension(dimensionId, parameters, future);
        }
    }

    public enum EntryPreparationMode {
        DEFERRED,
        FULL
    }
}
