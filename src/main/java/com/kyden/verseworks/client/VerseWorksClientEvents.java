package com.kyden.verseworks.client;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.dimension.VerseDimensionRuntimeHooks;
import com.kyden.verseworks.dimension.VerseDimensionVisuals;
import com.kyden.verseworks.dimension.VerseDimensionWorldType;
import com.kyden.verseworks.item.VerseItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = VerseWorks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class VerseWorksClientEvents {
    private static final double VERSE_WHISPER_SCAN_RANGE = 24.0D;
    private static final double VERSE_WHISPER_VERTICAL_RANGE = 12.0D;
    private static final double VERSE_WHISPER_MERGE_RADIUS = 1.5D;
    private static final float VERSE_WHISPER_VOLUME = 0.65F;
    private static final int VERSE_WHISPER_ATTENUATION_DISTANCE = 20;
    private static final int VERSE_WHISPER_MIN_ENTITY_AGE_TICKS = 10;
    private static final int VERSE_WHISPER_SCAN_INTERVAL_TICKS = 20;
    private static final RandomSource WHISPER_RANDOM = RandomSource.create();
    private static final Map<Integer, VerseWhisperSoundInstance> ACTIVE_VERSE_WHISPERS = new LinkedHashMap<>();
    private static List<ResourceLocation> cachedWhisperVariants = List.of();
    private static int cachedWhisperManagerIdentity;
    private static long nextWhisperScanTick;

    private VerseWorksClientEvents() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        resolveDimensionParameters(minecraft).ifPresent(parameters -> {
            int skyColor = VerseDimensionVisuals.resolvedColoredSkyColor(parameters);
            event.setRed(((skyColor >> 16) & 0xFF) / 255.0F);
            event.setGreen(((skyColor >> 8) & 0xFF) / 255.0F);
            event.setBlue((skyColor & 0xFF) / 255.0F);
        });
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        resolveDimensionParameters(minecraft).ifPresent(parameters -> applyFogProfile(event, parameters));
    }

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        VerseDimensionRuntimeHooks.rewritePendingTeleportSystemMessage(event.getMessage())
            .ifPresent(event::setMessage);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            stopAllVerseWhispers();
            nextWhisperScanTick = 0L;
            return;
        }

        pruneFinishedVerseWhispers(minecraft);
        if (minecraft.level.getGameTime() < nextWhisperScanTick) {
            return;
        }

        nextWhisperScanTick = minecraft.level.getGameTime() + VERSE_WHISPER_SCAN_INTERVAL_TICKS;
        spawnMissingVerseWhispers(minecraft);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAllVerseWhispers();
        nextWhisperScanTick = 0L;
    }

    private static Optional<VerseDimensionParameters> resolveDimensionParameters(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Optional.empty();
        }

        ResourceLocation dimensionId = minecraft.level.dimension().location();
        if (!VerseWorks.MODID.equals(dimensionId.getNamespace())) {
            return Optional.empty();
        }

        return VerseDimensionCatalog.getCached(dimensionId);
    }

    private static void applyFogProfile(ViewportEvent.RenderFog event, VerseDimensionParameters parameters) {
        float originalFar = event.getFarPlaneDistance();
        if (originalFar <= 0.0F) {
            return;
        }
        float fogDensityScale = VerseDimensionVisuals.fogDensityScale(parameters);

        if (usesBlackVoidSky(parameters)) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.min(originalFar * 0.25F, 24.0F) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (parameters.worldType().isVoid()) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.min(originalFar * 0.35F, 40.0F) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (isEndLikeDimension(parameters)) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.max(24.0F, originalFar * 0.6F) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (usesAmbientCeilingFog(parameters)) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.max(18.0F, Math.min(72.0F, originalFar * 0.42F)) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (parameters.worldType() == VerseDimensionWorldType.FLAT) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.max(48.0F, originalFar * 0.95F) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND) {
            event.setNearPlaneDistance(0.0F);
            event.setFarPlaneDistance(Math.max(32.0F, originalFar * 0.8F) / fogDensityScale);
            event.setCanceled(true);
            return;
        }

        if (fogDensityScale != 1.0F) {
            float forcedFar = Math.max(12.0F, originalFar / (fogDensityScale * fogDensityScale));
            event.setNearPlaneDistance(Math.max(0.0F, forcedFar * 0.05F));
            event.setFarPlaneDistance(forcedFar);
            event.setCanceled(true);
        }
    }

    private static boolean usesBlackVoidSky(VerseDimensionParameters parameters) {
        return VerseDimensionVisuals.usesBlackVoidSky(parameters);
    }

    private static boolean isEndLikeDimension(VerseDimensionParameters parameters) {
        return VerseDimensionVisuals.isEndLikeDimension(parameters);
    }

    private static boolean usesAmbientCeilingFog(VerseDimensionParameters parameters) {
        return VerseDimensionVisuals.usesAmbientCeilingFog(parameters);
    }

    private static void pruneFinishedVerseWhispers(Minecraft minecraft) {
        ACTIVE_VERSE_WHISPERS.entrySet().removeIf(entry -> {
            VerseWhisperSoundInstance sound = entry.getValue();
            if (!sound.isFinished()) {
                return false;
            }

            if (minecraft.getSoundManager().isActive(sound)) {
                minecraft.getSoundManager().stop(sound);
            }
            return true;
        });
    }

    private static void spawnMissingVerseWhispers(Minecraft minecraft) {
        if (minecraft.options.getSoundSourceVolume(SoundSource.AMBIENT) <= 0.0F) {
            if (!ACTIVE_VERSE_WHISPERS.isEmpty()) {
                stopAllVerseWhispers();
            }
            return;
        }

        List<ResourceLocation> whisperVariants = whisperVariants(minecraft.getResourceManager());
        if (whisperVariants.isEmpty()) {
            return;
        }

        AABB scanBounds = minecraft.player.getBoundingBox().inflate(VERSE_WHISPER_SCAN_RANGE, VERSE_WHISPER_VERTICAL_RANGE, VERSE_WHISPER_SCAN_RANGE);
        List<ItemEntity> verseItems = minecraft.level.getEntitiesOfClass(ItemEntity.class, scanBounds, VerseWorksClientEvents::isAudibleVerseEntity);
        if (verseItems.size() > 1) {
            verseItems.sort(Comparator.comparingInt(ItemEntity::getId));
        }
        for (ItemEntity verseItem : verseItems) {
            if (ACTIVE_VERSE_WHISPERS.containsKey(verseItem.getId())) {
                continue;
            }
            if (hasNearbyActiveWhisper(verseItem)) {
                continue;
            }

            VerseWhisperSoundInstance sound = new VerseWhisperSoundInstance(verseItem, pickWhisperVariant(whisperVariants));
            ACTIVE_VERSE_WHISPERS.put(verseItem.getId(), sound);
            minecraft.getSoundManager().queueTickingSound(sound);
        }
    }

    private static boolean isAudibleVerseEntity(ItemEntity entity) {
        return entity.isAlive()
            && entity.tickCount >= VERSE_WHISPER_MIN_ENTITY_AGE_TICKS
            && entity.getItem().is(VerseItems.VERSE.get());
    }

    private static boolean hasNearbyActiveWhisper(ItemEntity verseItem) {
        Vec3 versePos = verseItem.position();
        double mergeRadiusSqr = VERSE_WHISPER_MERGE_RADIUS * VERSE_WHISPER_MERGE_RADIUS;
        for (VerseWhisperSoundInstance sound : ACTIVE_VERSE_WHISPERS.values()) {
            if (sound.isFinished()) {
                continue;
            }
            if (sound.level() != verseItem.level()) {
                continue;
            }
            if (sound.position().distanceToSqr(versePos) <= mergeRadiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static List<ResourceLocation> whisperVariants(ResourceManager resourceManager) {
        int resourceManagerIdentity = System.identityHashCode(resourceManager);
        if (resourceManagerIdentity != cachedWhisperManagerIdentity) {
            cachedWhisperVariants = discoverWhisperVariants(resourceManager);
            cachedWhisperManagerIdentity = resourceManagerIdentity;
        }

        return cachedWhisperVariants;
    }

    private static List<ResourceLocation> discoverWhisperVariants(ResourceManager resourceManager) {
        return resourceManager.listResources(
                "sounds",
                location -> VerseWorks.MODID.equals(location.getNamespace())
                    && location.getPath().startsWith("sounds/whisper_")
                    && location.getPath().endsWith(".ogg")
            ).keySet().stream()
            .map(location -> ResourceLocation.fromNamespaceAndPath(
                location.getNamespace(),
                location.getPath().substring("sounds/".length(), location.getPath().length() - ".ogg".length())
            ))
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();
    }

    private static ResourceLocation pickWhisperVariant(List<ResourceLocation> whisperVariants) {
        return whisperVariants.get(WHISPER_RANDOM.nextInt(whisperVariants.size()));
    }

    private static void stopAllVerseWhispers() {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        for (VerseWhisperSoundInstance sound : ACTIVE_VERSE_WHISPERS.values()) {
            sound.markFinished();
            soundManager.stop(sound);
        }
        ACTIVE_VERSE_WHISPERS.clear();
    }

    private static final class VerseWhisperSoundInstance extends AbstractTickableSoundInstance {
        private final ItemEntity verseItem;
        private final ResourceLocation soundId;
        private final Level level;
        private boolean finished;

        private VerseWhisperSoundInstance(ItemEntity verseItem, ResourceLocation soundId) {
            super(SoundEvent.createVariableRangeEvent(soundId), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.verseItem = verseItem;
            this.soundId = soundId;
            this.level = verseItem.level();
            this.volume = VERSE_WHISPER_VOLUME;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.looping = false;
            this.delay = 0;
            syncToVerse();
        }

        @Override
        public void tick() {
            if (this.finished || !this.verseItem.isAlive() || !this.verseItem.getItem().is(VerseItems.VERSE.get())) {
                markFinished();
                return;
            }

            syncToVerse();
        }

        @Override
        public boolean canPlaySound() {
            return !this.finished && this.verseItem.isAlive();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents event = new WeighedSoundEvents(this.soundId, null);
            event.addSound(new Sound(
                this.soundId,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                false,
                false,
                VERSE_WHISPER_ATTENUATION_DISTANCE
            ));
            this.sound = event.getSound(this.random);
            return event;
        }

        private void syncToVerse() {
            Vec3 pos = this.verseItem.position();
            this.x = pos.x;
            this.y = pos.y + 0.1D;
            this.z = pos.z;
        }

        private boolean isFinished() {
            return this.finished || this.isStopped();
        }

        private void markFinished() {
            if (this.finished) {
                return;
            }

            this.finished = true;
            this.stop();
        }

        private Vec3 position() {
            return new Vec3(this.x, this.y, this.z);
        }

        private Level level() {
            return this.level;
        }
    }
}
