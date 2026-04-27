package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class VerseChunkWarmup {
    private static final Map<ResourceKey<Level>, Set<WarmupTicket>> ACTIVE_TICKETS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<WarmupTicket, WarmupRequest>> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

    private VerseChunkWarmup() {
    }

    static CompletableFuture<Void> request(ServerLevel level, ChunkPos centerChunk, int radius) {
        return request(level, centerChunk, radius, WarmupMode.ENTITY_TICKING);
    }

    static CompletableFuture<Void> requestLoaded(ServerLevel level, ChunkPos centerChunk, int radius) {
        return request(level, centerChunk, radius, WarmupMode.LOADED);
    }

    private static CompletableFuture<Void> request(ServerLevel level, ChunkPos centerChunk, int radius, WarmupMode mode) {
        MinecraftServer server = level.getServer();
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (VerseDimensionRuntimeHooks.isShutdownInProgress(server)) {
            result.complete(null);
            return result;
        }

        Runnable scheduleWarmup = () -> {
            WarmupTicket ticket = new WarmupTicket(centerChunk, radius);
            Map<WarmupTicket, WarmupRequest> requests = ACTIVE_REQUESTS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
            WarmupRequest request = requests.compute(ticket, (ignored, existing) -> {
                if (ACTIVE_TICKETS.computeIfAbsent(level.dimension(), ignoredKey -> ConcurrentHashMap.newKeySet()).add(ticket)) {
                    level.getChunkSource().addRegionTicket(TicketType.PLAYER, centerChunk, radius, centerChunk, true);
                }
                if (existing == null) {
                    return new WarmupRequest(centerChunk, radius, mode);
                }
                existing.requireAtLeast(mode);
                return existing;
            });
            request.result().whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                    return;
                }

                result.complete(null);
            });
        };

        if (server.isSameThread()) {
            scheduleWarmup.run();
        } else {
            server.execute(scheduleWarmup);
        }

        return result;
    }

    static void release(ServerLevel level, ChunkPos centerChunk, int radius) {
        Set<WarmupTicket> tickets = ACTIVE_TICKETS.get(level.dimension());
        if (tickets == null) {
            return;
        }

        WarmupTicket ticket = new WarmupTicket(centerChunk, radius);
        if (!tickets.remove(ticket)) {
            return;
        }
        if (tickets.isEmpty()) {
            ACTIVE_TICKETS.remove(level.dimension(), tickets);
        }
        Map<WarmupTicket, WarmupRequest> requests = ACTIVE_REQUESTS.get(level.dimension());
        if (requests != null) {
            WarmupRequest request = requests.remove(ticket);
            if (request != null) {
                request.result().complete(null);
            }
            if (requests.isEmpty()) {
                ACTIVE_REQUESTS.remove(level.dimension(), requests);
            }
        }

        level.getChunkSource().removeRegionTicket(TicketType.PLAYER, centerChunk, radius, centerChunk, true);
    }

    static void releaseAll(MinecraftServer server) {
        for (ServerLevel level : server.forgeGetWorldMap().values()) {
            if (level == null) {
                continue;
            }

            Set<WarmupTicket> tickets = ACTIVE_TICKETS.remove(level.dimension());
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }

            for (WarmupTicket ticket : tickets) {
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, ticket.centerChunk(), ticket.radius(), ticket.centerChunk(), true);
            }

            Map<WarmupTicket, WarmupRequest> requests = ACTIVE_REQUESTS.remove(level.dimension());
            if (requests != null) {
                requests.values().forEach(request -> request.result().complete(null));
            }
        }
    }

    static void clearRuntimeState() {
        ACTIVE_TICKETS.clear();
        ACTIVE_REQUESTS.clear();
    }

    static void tick(ServerLevel level) {
        Map<WarmupTicket, WarmupRequest> requests = ACTIVE_REQUESTS.get(level.dimension());
        if (requests == null || requests.isEmpty()) {
            return;
        }

        requests.entrySet().removeIf(entry -> {
            WarmupRequest request = entry.getValue();
            if (!request.result().isDone() && request.mode().isSatisfied(level, request.centerChunk(), request.radius())) {
                request.result().complete(null);
            }
            return request.result().isDone();
        });
        if (requests.isEmpty()) {
            ACTIVE_REQUESTS.remove(level.dimension(), requests);
        }
    }

    static void tickAll(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, Map<WarmupTicket, WarmupRequest>> entry : ACTIVE_REQUESTS.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }

            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }

            tick(level);
        }
    }

    private static boolean areChunksEntityTicking(ServerLevel level, ChunkPos centerChunk, int radius) {
        ServerChunkCache chunkSource = level.getChunkSource();
        for (int chunkX = centerChunk.x - radius; chunkX <= centerChunk.x + radius; chunkX++) {
            for (int chunkZ = centerChunk.z - radius; chunkZ <= centerChunk.z + radius; chunkZ++) {
                if (!chunkSource.isPositionTicking(ChunkPos.asLong(chunkX, chunkZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    private enum WarmupMode {
        LOADED {
            @Override
            boolean isSatisfied(ServerLevel level, ChunkPos centerChunk, int radius) {
                ServerChunkCache chunkSource = level.getChunkSource();
                for (int chunkX = centerChunk.x - radius; chunkX <= centerChunk.x + radius; chunkX++) {
                    for (int chunkZ = centerChunk.z - radius; chunkZ <= centerChunk.z + radius; chunkZ++) {
                        if (chunkSource.getChunkNow(chunkX, chunkZ) == null) {
                            return false;
                        }
                    }
                }
                return true;
            }
        },
        ENTITY_TICKING {
            @Override
            boolean isSatisfied(ServerLevel level, ChunkPos centerChunk, int radius) {
                return areChunksEntityTicking(level, centerChunk, radius);
            }
        };

        abstract boolean isSatisfied(ServerLevel level, ChunkPos centerChunk, int radius);

        WarmupMode max(WarmupMode other) {
            return this.ordinal() >= other.ordinal() ? this : other;
        }
    }

    private record WarmupTicket(ChunkPos centerChunk, int radius) {
    }

    private static final class WarmupRequest {
        private final ChunkPos centerChunk;
        private final int radius;
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private WarmupMode mode;

        private WarmupRequest(ChunkPos centerChunk, int radius, WarmupMode mode) {
            this.centerChunk = centerChunk;
            this.radius = radius;
            this.mode = mode;
        }

        private CompletableFuture<Void> result() {
            return this.result;
        }

        private WarmupMode mode() {
            return this.mode;
        }

        private void requireAtLeast(WarmupMode mode) {
            this.mode = this.mode.max(mode);
        }

        private ChunkPos centerChunk() {
            return this.centerChunk;
        }

        private int radius() {
            return this.radius;
        }
    }
}
