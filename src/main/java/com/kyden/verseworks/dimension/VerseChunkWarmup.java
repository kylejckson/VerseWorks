package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class VerseChunkWarmup {
    private static final int CHUNK_REQUESTS_PER_TICK = 2;
    private static final Map<ResourceKey<Level>, Set<WarmupTicket>> ACTIVE_TICKETS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<WarmupTicket, WarmupRequest>> ACTIVE_REQUESTS = new ConcurrentHashMap<>();

    private VerseChunkWarmup() {
    }

    static CompletableFuture<Void> request(ServerLevel level, ChunkPos centerChunk, int radius) {
        MinecraftServer server = level.getServer();
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (VerseDimensionRuntimeHooks.isShutdownInProgress(server)) {
            result.complete(null);
            return result;
        }

        Runnable scheduleWarmup = () -> {
            WarmupTicket ticket = new WarmupTicket(centerChunk, radius);
            Map<WarmupTicket, WarmupRequest> requests = ACTIVE_REQUESTS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
            WarmupRequest request = requests.computeIfAbsent(ticket, ignored -> {
                try {
                    if (ACTIVE_TICKETS.computeIfAbsent(level.dimension(), ignoredKey -> ConcurrentHashMap.newKeySet()).add(ticket)) {
                        invokeChunkTicket(level.getChunkSource(), "addRegionTicket", centerChunk, radius);
                    }
                } catch (ReflectiveOperationException exception) {
                    VerseWorks.LOGGER.debug("VerseWorks could not add warmup ticket for {}", level.dimension().location(), exception);
                }
                return new WarmupRequest(centerChunk, radius);
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
            requests.remove(ticket);
            if (requests.isEmpty()) {
                ACTIVE_REQUESTS.remove(level.dimension(), requests);
            }
        }

        try {
            invokeChunkTicket(level.getChunkSource(), "removeRegionTicket", centerChunk, radius);
        } catch (ReflectiveOperationException exception) {
            VerseWorks.LOGGER.debug("VerseWorks could not remove warmup ticket for {}", level.dimension().location(), exception);
        }
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
                try {
                    invokeChunkTicket(level.getChunkSource(), "removeRegionTicket", ticket.centerChunk(), ticket.radius());
                } catch (ReflectiveOperationException exception) {
                    VerseWorks.LOGGER.debug("VerseWorks could not remove shutdown warmup ticket for {}", level.dimension().location(), exception);
                }
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

        int remainingBudget = CHUNK_REQUESTS_PER_TICK;
        List<WarmupTicket> completedTickets = new ArrayList<>();
        List<Map.Entry<WarmupTicket, WarmupRequest>> pendingEntries = new ArrayList<>(requests.entrySet());
        pendingEntries.sort(Comparator.comparingInt((Map.Entry<WarmupTicket, WarmupRequest> entry) -> entry.getValue().remainingChunkCount()).reversed());
        for (Map.Entry<WarmupTicket, WarmupRequest> entry : pendingEntries) {
            WarmupRequest request = entry.getValue();
            if (request.result().isDone()) {
                completedTickets.add(entry.getKey());
                continue;
            }

            if (remainingBudget > 0) {
                remainingBudget -= pumpRequest(level, request, remainingBudget);
            }

            if (request.result().isDone()) {
                completedTickets.add(entry.getKey());
            }
        }

        completedTickets.forEach(requests::remove);
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

    private static void invokeChunkTicket(ServerChunkCache chunkSource, String methodName, ChunkPos chunkPos, int radius) throws ReflectiveOperationException {
        for (Method method : chunkSource.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 4) {
                continue;
            }

            method.invoke(chunkSource, TicketType.PLAYER, chunkPos, radius, chunkPos);
            return;
        }
    }

    private static int pumpRequest(ServerLevel level, WarmupRequest request, int budget) {
        if (request.result().isDone() || budget <= 0) {
            return 0;
        }

        ServerChunkCache chunkSource = level.getChunkSource();
        int scheduled = 0;
        while (scheduled < budget && request.hasMoreChunks()) {
            ChunkPos nextChunk = request.nextChunk();
            request.futures().add(chunkSource.getChunkFuture(nextChunk.x, nextChunk.z, ChunkStatus.FULL, true));
            scheduled++;
        }

        if (!request.hasMoreChunks() && !request.completionRegistered()) {
            request.markCompletionRegistered();
            CompletableFuture.allOf(request.futures().toArray(CompletableFuture[]::new))
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        request.result().completeExceptionally(throwable);
                        return;
                    }

                    request.result().complete(null);
                });
        }

        return scheduled;
    }

    private record WarmupTicket(ChunkPos centerChunk, int radius) {
    }

    private static final class WarmupRequest {
        private final ChunkPos centerChunk;
        private final int radius;
        private final CompletableFuture<Void> result = new CompletableFuture<>();
        private final List<CompletableFuture<?>> futures;
        private int nextChunkX;
        private int nextChunkZ;
        private boolean completionRegistered;

        private WarmupRequest(ChunkPos centerChunk, int radius) {
            this.centerChunk = centerChunk;
            this.radius = radius;
            this.futures = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
            this.nextChunkX = centerChunk.x - radius;
            this.nextChunkZ = centerChunk.z - radius;
        }

        private boolean hasMoreChunks() {
            return this.nextChunkX <= this.centerChunk.x + this.radius;
        }

        private ChunkPos nextChunk() {
            ChunkPos chunkPos = new ChunkPos(this.nextChunkX, this.nextChunkZ);
            this.nextChunkZ++;
            if (this.nextChunkZ > this.centerChunk.z + this.radius) {
                this.nextChunkZ = this.centerChunk.z - this.radius;
                this.nextChunkX++;
            }
            return chunkPos;
        }

        private CompletableFuture<Void> result() {
            return this.result;
        }

        private List<CompletableFuture<?>> futures() {
            return this.futures;
        }

        private boolean completionRegistered() {
            return this.completionRegistered;
        }

        private void markCompletionRegistered() {
            this.completionRegistered = true;
        }

        private int remainingChunkCount() {
            int sideLength = this.radius * 2 + 1;
            int totalChunks = sideLength * sideLength;
            return totalChunks - this.futures.size();
        }
    }
}
