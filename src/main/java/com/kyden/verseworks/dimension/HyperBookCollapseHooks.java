package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.item.HyperBookData;
import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HyperBookCollapseHooks {
    private static final long TOSSED_BOOK_EXPIRY_TICKS = 20L * 30L;
    private static final long COLLAPSE_RETRY_DELAY_TICKS = 5L;
    private static final double PORTAL_REJECTION_LIFT = 0.35D;
    private static final Component COLLAPSE_SUCCESS_MESSAGE = Component.literal("Dimension Permenantly Collapsed").withStyle(ChatFormatting.LIGHT_PURPLE);
    private static final Component COLLAPSE_AUTH_FAILURE_MESSAGE = Component.literal("Only the dimension's creator can collapse it.").withStyle(ChatFormatting.RED);
    private static final Component COLLAPSE_UNAVAILABLE_MESSAGE = Component.literal("That Hyperbook is no longer linked to an available dimension.").withStyle(ChatFormatting.RED);
    private static final Map<UUID, TossedHyperBook> TOSSED_HYPERBOOKS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, PendingCollapse> PENDING_COLLAPSES = new ConcurrentHashMap<>();

    private HyperBookCollapseHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HyperBookCollapseHooks::onItemToss);
        NeoForge.EVENT_BUS.addListener(HyperBookCollapseHooks::onEntityTravelToDimension);
        NeoForge.EVENT_BUS.addListener(HyperBookCollapseHooks::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(HyperBookCollapseHooks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(HyperBookCollapseHooks::onServerStopping);
    }

    public static boolean isDimensionCollapsing(ResourceLocation dimensionId) {
        return PENDING_COLLAPSES.containsKey(dimensionId);
    }

    private static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        HyperBookData data = HyperBookData.from(event.getEntity().getItem()).orElse(null);
        if (data == null || !VerseWorks.MODID.equals(data.dimensionId().getNamespace())) {
            return;
        }

        ServerLevel level = player.serverLevel();
        TOSSED_HYPERBOOKS.put(event.getEntity().getUUID(), new TossedHyperBook(player.getUUID(), level.getGameTime() + TOSSED_BOOK_EXPIRY_TICKS));
    }

    private static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity) || event.getDimension() != Level.END) {
            return;
        }

        if (!(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }

        if (!attemptCollapse(level, itemEntity, true)) {
            return;
        }

        event.setCanceled(true);
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity) || !(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }

        TossedHyperBook tossed = TOSSED_HYPERBOOKS.get(itemEntity.getUUID());
        if (tossed == null || level.getGameTime() > tossed.expiresAtGameTime()) {
            TOSSED_HYPERBOOKS.remove(itemEntity.getUUID(), tossed);
            return;
        }

        if (!isInsideEndPortal(level, itemEntity)) {
            return;
        }

        attemptCollapse(level, itemEntity, false);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }

        pruneExpiredTossRecords(level.getGameTime());
        processPendingCollapses(level.getServer(), level.getGameTime());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TOSSED_HYPERBOOKS.clear();
        PENDING_COLLAPSES.clear();
    }

    private static boolean attemptCollapse(ServerLevel sourceLevel, ItemEntity itemEntity, boolean rejectPortalTravel) {
        HyperBookData data = HyperBookData.from(itemEntity.getItem()).orElse(null);
        if (data == null || !VerseWorks.MODID.equals(data.dimensionId().getNamespace())) {
            return false;
        }

        TossedHyperBook tossed = TOSSED_HYPERBOOKS.get(itemEntity.getUUID());
        if (tossed == null) {
            return false;
        }

        MinecraftServer server = sourceLevel.getServer();
        if (server == null) {
            return false;
        }

        if (PENDING_COLLAPSES.containsKey(data.dimensionId())) {
            Player player = server.getPlayerList().getPlayer(tossed.playerId());
            rejectCollapseItem(itemEntity, player, Component.literal("That dimension is already collapsing.").withStyle(ChatFormatting.YELLOW), rejectPortalTravel);
            return true;
        }

        if (VerseDimensionCatalog.get(server, data.dimensionId()).isEmpty()) {
            Player player = server.getPlayerList().getPlayer(tossed.playerId());
            rejectCollapseItem(itemEntity, player, COLLAPSE_UNAVAILABLE_MESSAGE, rejectPortalTravel);
            return true;
        }

        UUID ownerId = VerseDimensionOwnershipSavedData.get(server).owner(data.dimensionId()).orElse(null);
        boolean creatorMatches = data.creatorId() == null || tossed.playerId().equals(data.creatorId());
        if (ownerId == null || !ownerId.equals(tossed.playerId()) || !creatorMatches) {
            Player player = server.getPlayerList().getPlayer(tossed.playerId());
            rejectCollapseItem(itemEntity, player, COLLAPSE_AUTH_FAILURE_MESSAGE, rejectPortalTravel);
            return true;
        }

        startCollapse(sourceLevel, itemEntity, data, tossed);
        return true;
    }

    private static void startCollapse(ServerLevel sourceLevel, ItemEntity itemEntity, HyperBookData data, TossedHyperBook tossed) {
        itemEntity.discard();
        TOSSED_HYPERBOOKS.remove(itemEntity.getUUID());

        Vec3 center = itemEntity.position();
        playCollapseRiftBurst(sourceLevel, center);

        Player player = sourceLevel.getServer().getPlayerList().getPlayer(tossed.playerId());
        if (player != null) {
            player.sendSystemMessage(COLLAPSE_SUCCESS_MESSAGE);
        }

        playGlobalCollapseSound(sourceLevel.getServer());
        PENDING_COLLAPSES.putIfAbsent(data.dimensionId(), new PendingCollapse(data.dimensionId(), data.dimensionName(), tossed.playerId(), sourceLevel.getGameTime()));
    }

    private static void processPendingCollapses(MinecraftServer server, long gameTime) {
        for (PendingCollapse collapse : new ArrayList<>(PENDING_COLLAPSES.values())) {
            if (gameTime < collapse.nextAttemptTick) {
                continue;
            }

            try {
                if (!collapse.offlinePlayersRelocated) {
                    relocateOfflinePlayers(server, collapse.dimensionId);
                    VerseDimensionRuntimeHooks.forgetStartupDimension(collapse.dimensionId);
                    collapse.offlinePlayersRelocated = true;
                }

                VerseDimensionRuntimeHooks.cancelDimensionWork(server, collapse.dimensionId);
                evacuateOnlinePlayers(server, collapse.dimensionId);

                ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, collapse.dimensionId));
                if (level != null && !level.players().isEmpty()) {
                    collapse.nextAttemptTick = gameTime + COLLAPSE_RETRY_DELAY_TICKS;
                    continue;
                }

                if (level != null && !VerseDimensionRuntimeHooks.unloadRuntimeLevelForCollapse(level)) {
                    collapse.nextAttemptTick = gameTime + COLLAPSE_RETRY_DELAY_TICKS;
                    continue;
                }

                if (LiveDimensionInstantiator.hasRegisteredLevelStem(server, collapse.dimensionId)) {
                    LiveDimensionInstantiator.unregisterLevelStem(server, collapse.dimensionId);
                }

                GeneratedDimensionPackWriter.deleteDimension(server, collapse.dimensionId);
                VerseDimensionCatalog.forget(collapse.dimensionId);
                VerseDimensionEntryPointSavedData.get(server).forgetDimension(collapse.dimensionId);
                VerseDimensionOwnershipSavedData.get(server).forgetOwner(collapse.dimensionId);
                VerseDimensionLifecycleSavedData.get(server).forgetDimension(collapse.dimensionId);
                VerseDimensionUsageSavedData.get(server).forgetDimension(collapse.dimensionId);
                VerseDimensionRuntimeHooks.forgetStartupDimension(collapse.dimensionId);
                VerseDimensionRuntimeHooks.syncLifecycleState(server, "dimension-collapse");
                PENDING_COLLAPSES.remove(collapse.dimensionId, collapse);
                VerseWorks.LOGGER.info("VerseWorks permanently collapsed dimension {}", collapse.dimensionId);
            } catch (Exception exception) {
                collapse.nextAttemptTick = gameTime + COLLAPSE_RETRY_DELAY_TICKS;
                VerseWorks.LOGGER.warn("VerseWorks could not finish collapsing dimension {}", collapse.dimensionId, exception);
            }
        }
    }

    private static void evacuateOnlinePlayers(MinecraftServer server, ResourceLocation dimensionId) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        VerseDimensionRuntimeHooks.RespawnTarget respawn = VerseDimensionRuntimeHooks.overworldRespawnData(server);
        Vec3 destination = Vec3.atBottomCenterOf(respawn.pos());
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (!dimensionId.equals(player.serverLevel().dimension().location())) {
                continue;
            }

            player.teleportTo(overworld, destination.x(), destination.y(), destination.z(), Set.<RelativeMovement>of(), respawn.yaw(), respawn.pitch());
        }
    }

    private static void relocateOfflinePlayers(MinecraftServer server, ResourceLocation dimensionId) {
        Path playerDataDirectory = server.getWorldPath(new LevelResource("playerdata"));
        if (!Files.isDirectory(playerDataDirectory)) {
            return;
        }

        VerseDimensionRuntimeHooks.RespawnTarget respawn = VerseDimensionRuntimeHooks.overworldRespawnData(server);
        Vec3 destination = Vec3.atBottomCenterOf(respawn.pos());

        try (var playerFiles = Files.list(playerDataDirectory)) {
            for (Path playerFile : playerFiles.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                rewriteOfflinePlayerData(playerFile, dimensionId, destination, respawn);
            }
        } catch (IOException exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not relocate offline players for collapsed dimension {}", dimensionId, exception);
        }
    }

    private static void rewriteOfflinePlayerData(Path playerFile, ResourceLocation dimensionId, Vec3 destination, VerseDimensionRuntimeHooks.RespawnTarget respawn) {
        try (InputStream inputStream = Files.newInputStream(playerFile)) {
            CompoundTag playerTag = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
            if (!playerTag.contains("Dimension", Tag.TAG_STRING) || !dimensionId.toString().equals(playerTag.getString("Dimension"))) {
                return;
            }

            playerTag.putString("Dimension", Level.OVERWORLD.location().toString());
            playerTag.put("Pos", doubleList(destination.x(), destination.y(), destination.z()));
            playerTag.put("Rotation", floatList(respawn.yaw(), respawn.pitch()));
            NbtIo.writeCompressed(playerTag, playerFile);
        } catch (Exception exception) {
            VerseWorks.LOGGER.warn("VerseWorks could not rewrite offline playerdata {}", playerFile, exception);
        }
    }

    private static ListTag doubleList(double x, double y, double z) {
        ListTag tag = new ListTag();
        tag.add(DoubleTag.valueOf(x));
        tag.add(DoubleTag.valueOf(y));
        tag.add(DoubleTag.valueOf(z));
        return tag;
    }

    private static ListTag floatList(float yaw, float pitch) {
        ListTag tag = new ListTag();
        tag.add(FloatTag.valueOf(yaw));
        tag.add(FloatTag.valueOf(pitch));
        return tag;
    }

    private static void playGlobalCollapseSound(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        Holder<net.minecraft.sounds.SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(VerseSounds.COLLAPSE.get());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(
                sound,
                SoundSource.AMBIENT,
                player.getX(),
                player.getY(),
                player.getZ(),
                3.5F,
                0.92F,
                overworld.getRandom().nextLong()
            ));
        }
    }

    private static void playCollapseRiftBurst(ServerLevel level, Vec3 center) {
        double x = center.x();
        double y = center.y() + 0.1D;
        double z = center.z();
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 36, 0.22D, 0.45D, 0.22D, 0.18D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.1D, z, 18, 0.16D, 0.32D, 0.16D, 0.05D);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y + 0.18D, z, 12, 0.14D, 0.22D, 0.14D, 0.02D);
        for (int index = 0; index < 8; index++) {
            double angle = (Math.PI * 2.0D * index) / 8.0D;
            double ringX = x + Math.cos(angle) * 0.55D;
            double ringZ = z + Math.sin(angle) * 0.55D;
            level.sendParticles(ParticleTypes.PORTAL, ringX, y + 0.12D, ringZ, 4, 0.05D, 0.08D, 0.05D, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, ringX, y + 0.2D, ringZ, 2, 0.03D, 0.06D, 0.03D, 0.015D);
        }
    }

    private static void rejectCollapseItem(ItemEntity itemEntity, Player player, Component message, boolean rejectPortalTravel) {
        TOSSED_HYPERBOOKS.remove(itemEntity.getUUID());
        if (player != null) {
            player.sendSystemMessage(message);
        }
        itemEntity.setPortalCooldown();
        itemEntity.setPos(itemEntity.getX(), itemEntity.getY() + 0.15D, itemEntity.getZ());
        itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().add(0.0D, PORTAL_REJECTION_LIFT, 0.0D));
        itemEntity.hurtMarked = true;
    }

    private static void pruneExpiredTossRecords(long gameTime) {
        TOSSED_HYPERBOOKS.entrySet().removeIf(entry -> entry.getValue().expiresAtGameTime() < gameTime);
    }

    private static boolean isInsideEndPortal(ServerLevel level, ItemEntity itemEntity) {
        AABB bounds = itemEntity.getBoundingBox().inflate(0.01D);
        int minX = net.minecraft.util.Mth.floor(bounds.minX);
        int maxX = net.minecraft.util.Mth.floor(bounds.maxX);
        int minY = net.minecraft.util.Mth.floor(bounds.minY);
        int maxY = net.minecraft.util.Mth.floor(bounds.maxY);
        int minZ = net.minecraft.util.Mth.floor(bounds.minZ);
        int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.END_PORTAL)) {
                        return true;
                    }
                }
            }
        }

        return level.getBlockState(itemEntity.blockPosition()).is(Blocks.END_PORTAL);
    }

    private record TossedHyperBook(UUID playerId, long expiresAtGameTime) {
    }

    private static final class PendingCollapse {
        private final ResourceLocation dimensionId;
        private final String dimensionName;
        private final UUID initiatorId;
        private long nextAttemptTick;
        private boolean offlinePlayersRelocated;

        private PendingCollapse(ResourceLocation dimensionId, String dimensionName, UUID initiatorId, long startedAtGameTime) {
            this.dimensionId = dimensionId;
            this.dimensionName = dimensionName;
            this.initiatorId = initiatorId;
            this.nextAttemptTick = startedAtGameTime;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s (%s by %s)", dimensionName, dimensionId, initiatorId);
        }
    }
}
