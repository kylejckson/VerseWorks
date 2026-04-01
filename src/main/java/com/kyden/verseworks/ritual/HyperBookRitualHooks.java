package com.kyden.verseworks.ritual;

import com.kyden.verseworks.Config;
import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.advancement.VerseWorksAdvancements;
import com.kyden.verseworks.attachment.HyperBookLecternLink;
import com.kyden.verseworks.attachment.VerseAttachments;
import com.kyden.verseworks.dimension.GeneratedDimensionPackWriter;
import com.kyden.verseworks.dimension.LiveDimensionInstantiator;
import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.kyden.verseworks.dimension.VerseDimensionCorruption;
import com.kyden.verseworks.dimension.VerseDimensionOwnershipSavedData;
import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.dimension.VerseDimensionRuntimeHooks;
import com.kyden.verseworks.dimension.VerseDimensionWorldType;
import com.kyden.verseworks.item.HyperBookData;
import com.kyden.verseworks.item.VerseCatalog;
import com.kyden.verseworks.item.VerseData;
import com.kyden.verseworks.item.VerseItems;
import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class HyperBookRitualHooks {
    private static final int CAULDRON_SCAN_INTERVAL_TICKS = 10;
    private static final int CAULDRON_SCAN_RADIUS = 8;
    private static final int CAULDRON_SOUND_INTERVAL_TICKS = 64;
    private static final int LECTERN_SCAN_INTERVAL_TICKS = 20;
    private static final int LECTERN_SCAN_RADIUS = 12;
    private static final int HYPER_BOOK_FINISH_DELAY_TICKS = 20;
    private static final int HYPER_BOOK_READY_DISPLAY_TICKS = 20;
    private static final long CRAFTED_WARMUP_MIN_DELAY_TICKS = 20L * 5L;
    private static final long SLOW_RITUAL_STAGE_THRESHOLD_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(10);
    private static final double CAULDRON_SOUND_HEAR_RANGE = 28.0D;
    private static final double LECTERN_LABEL_VISIBLE_RANGE = 16.0D;
    private static final double LECTERN_TETHER_HEIGHT = 4.8D;
    private static final int HYPER_BOOK_DUST_COST = 16;
    private static final int UNLINKED_HYPER_BOOK_DUST_COST = 32;
    private static final int MAX_PENDING_RITUALS_PER_LEVEL = 4;
    private static final long CAULDRON_ITEM_TIMEOUT_TICKS = 15L * 20L;
    private static final long RITUAL_ACTIVITY_GRACE_TICKS = 20L * 30L;
    private static final int CORRUPTION_WARNING_MIN_RADIUS = 10;
    private static final int CORRUPTION_WARNING_MAX_RADIUS = 20;
    private static final List<Block> CORRUPTION_SPHERE_BLOCKS = List.of(
        Blocks.LAVA,
        Blocks.MAGMA_BLOCK,
        Blocks.GLOWSTONE,
        Blocks.OBSIDIAN,
        Blocks.COBBLESTONE,
        Blocks.INFESTED_STONE
    );
    private static final Pattern NON_DIMENSION_CHARACTERS = Pattern.compile("[^a-z0-9_ ]");
    private static final String LABEL_TAG = "verseworks_hyper_book_label";
    private static final String LABEL_KEY_PREFIX = "verseworks_hyper_book_label:";
    private static final java.util.Map<ResourceKey<Level>, Deque<PendingRitualJob>> PENDING_RITUAL_JOBS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, ActivePendingRitual> ACTIVE_PENDING_RITUALS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, CauldronIngredientState>> CAULDRON_INGREDIENT_STATES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> CAULDRON_SOUND_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> DEBUG_CAULDRONS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> RECENT_RITUAL_ACTIVITY = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> TRACKED_LECTERN_LABELS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, PendingLecternWarmup>> PENDING_LECTERN_WARMUPS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, PendingLecternCompletion>> PENDING_LECTERN_COMPLETIONS = new ConcurrentHashMap<>();
    private static final Deque<PendingCraftedWarmup> PENDING_CRAFTED_WARMUPS = new ArrayDeque<>();
    private static ActiveCraftedWarmup ACTIVE_CRAFTED_WARMUP;
    // Ritual text accepts both snake_case and camelCase aliases because command-created verses
    // and item-authored verses do not all serialize keys the same way.
    private static final Set<String> VALID_PARAMETER_KEYS = Set.of(
        "skycolor", "sky_color",
        "worldtype", "world_type",
        "biomeid", "biome", "biomes",
        "gravityscale", "gravity_scale",
        "dayrate", "day_rate",
        "timeofday", "time_of_day",
        "permanenttime", "permanent_time",
        "permanentrain", "permanent_rain",
        "permanentlightning", "permanent_lightning",
        "permanentstorm", "permanent_storm",
        "meteorshowers", "meteor_showers",
        "spawnwarp", "spawn_warp",
        "oremultiplier", "ore_multiplier",
        "spheres",
        "lakes",
        "structures",
        "oceanlevel", "ocean_level",
        "floorblock", "floor_block",
        "stabilizedrealm", "stabilized_realm",
        "sphereblock", "sphere_block", "spherematerial",
        "fluidid", "fluid"
    );

    private HyperBookRitualHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onLevelTick);
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onLevelSave);
        NeoForge.EVENT_BUS.addListener(HyperBookRitualHooks::onServerStopping);
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        clearRuntimeState();
    }

    private static void clearRuntimeState() {
        PENDING_RITUAL_JOBS.clear();
        ACTIVE_PENDING_RITUALS.clear();
        CAULDRON_INGREDIENT_STATES.clear();
        CAULDRON_SOUND_TIMESTAMPS.clear();
        DEBUG_CAULDRONS.clear();
        RECENT_RITUAL_ACTIVITY.clear();
        TRACKED_LECTERN_LABELS.clear();
        PENDING_LECTERN_WARMUPS.clear();
        PENDING_LECTERN_COMPLETIONS.clear();
        PENDING_CRAFTED_WARMUPS.clear();
        ACTIVE_CRAFTED_WARMUP = null;
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            processNearbyLecterns((ServerLevel) player.level());
        }
    }

    private static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            pruneTrackedLecternLabels(level);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (VerseDimensionRuntimeHooks.isShutdownInProgress(level.getServer())) {
            return;
        }

        long gameTime = level.getGameTime();
        if (level.dimension() == Level.OVERWORLD) {
            processCraftedWarmupCoordinator(level.getServer(), gameTime);
        }
        processPendingLecternWarmups(level, gameTime);
        processPendingLecternCompletions(level, gameTime);
        processActiveLecternVisuals(level);
        if (gameTime % LECTERN_SCAN_INTERVAL_TICKS == 0L) {
            pruneTrackedLecternLabels(level);
        }
        if (level.players().isEmpty()) {
            return;
        }

        processPendingRitualJobs(level, gameTime);
        if (gameTime % CAULDRON_SCAN_INTERVAL_TICKS == 0L) {
            processNearbyCauldrons(level);
        }
        if (gameTime % LECTERN_SCAN_INTERVAL_TICKS == 0L) {
            processNearbyLecterns(level);
        }
    }

    private static void processNearbyCauldrons(ServerLevel level) {
        long gameTime = level.getGameTime();
        Set<BlockPos> scanned = new HashSet<>();
        Set<BlockPos> activeCauldrons = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            for (int dx = -CAULDRON_SCAN_RADIUS; dx <= CAULDRON_SCAN_RADIUS; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -CAULDRON_SCAN_RADIUS; dz <= CAULDRON_SCAN_RADIUS; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (!scanned.add(pos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(pos);
                        if (!isHeatedFullWaterCauldron(state, level, pos)) {
                            continue;
                        }

                        activeCauldrons.add(pos.immutable());
                        spawnCauldronBubbles(level, pos);
                        playCauldronBubbleLoop(level, pos, gameTime);
                        processCauldronTimeout(level, pos);
                        attemptHyperBookRitual(level, pos);
                    }
                }
            }
        }

        pruneInactiveCauldronStates(level, activeCauldrons);
        pruneInactiveCauldronSounds(level, activeCauldrons);
        pruneInactiveDebugCauldrons(level, activeCauldrons);
    }

    private static boolean isHeatedFullWaterCauldron(BlockState state, ServerLevel level, BlockPos pos) {
        if (!(state.getBlock() instanceof LayeredCauldronBlock layeredCauldronBlock) || !state.is(Blocks.WATER_CAULDRON) || !layeredCauldronBlock.isFull(state)) {
            return false;
        }

        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.is(Blocks.FIRE) || belowState.is(Blocks.SOUL_FIRE)) {
            return true;
        }

        return (belowState.is(Blocks.CAMPFIRE) || belowState.is(Blocks.SOUL_CAMPFIRE))
            && belowState.hasProperty(CampfireBlock.LIT)
            && belowState.getValue(CampfireBlock.LIT);
    }

    private static void spawnCauldronBubbles(ServerLevel level, BlockPos pos) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.2D + random.nextDouble() * 0.6D;
        double y = pos.getY() + 0.82D;
        double z = pos.getZ() + 0.2D + random.nextDouble() * 0.6D;
        level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 4, 0.07D, 0.22D, 0.07D, 0.01D);
        level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.18D, z, 2, 0.08D, 0.08D, 0.08D, 0.01D);
        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.32D, z, 2, 0.05D, 0.1D, 0.05D, 0.001D);
    }

    private static void playCauldronBubbleLoop(ServerLevel level, BlockPos pos, long gameTime) {
        if (!hasNearbyCauldronListener(level, pos)) {
            return;
        }

        Map<BlockPos, Long> soundMap = CAULDRON_SOUND_TIMESTAMPS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
        Long lastPlayedAt = soundMap.get(pos);
        if (lastPlayedAt != null && gameTime - lastPlayedAt < CAULDRON_SOUND_INTERVAL_TICKS) {
            return;
        }

        BlockPos storedPos = pos.immutable();
        soundMap.put(storedPos, gameTime);
        Vec3 center = Vec3.atCenterOf(storedPos).add(0.0D, 0.2D, 0.0D);
        level.playSound(null, center.x(), center.y(), center.z(), VerseSounds.BUBBLING_CAULDRON.get(), SoundSource.BLOCKS, 2.25F, 1.0F);
    }

    private static boolean hasNearbyCauldronListener(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        double maxDistanceSqr = CAULDRON_SOUND_HEAR_RANGE * CAULDRON_SOUND_HEAR_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= maxDistanceSqr) {
                return true;
            }
        }
        return false;
    }

    private static void processCauldronTimeout(ServerLevel level, BlockPos cauldronPos) {
        List<ItemEntity> ingredients = level.getEntitiesOfClass(ItemEntity.class, ritualInputBounds(cauldronPos));
        Map<BlockPos, CauldronIngredientState> stateMap = CAULDRON_INGREDIENT_STATES.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());

        if (ingredients.isEmpty()) {
            stateMap.remove(cauldronPos);
            return;
        }

        String signature = ingredientSignature(ingredients);
        long gameTime = level.getGameTime();
        CauldronIngredientState currentState = stateMap.get(cauldronPos);
        if (currentState == null || !currentState.signature().equals(signature)) {
            stateMap.put(cauldronPos.immutable(), new CauldronIngredientState(signature, gameTime));
            markRecentRitualActivity(level);
            return;
        }

        if (gameTime - currentState.lastChangedAt() < CAULDRON_ITEM_TIMEOUT_TICKS) {
            return;
        }

        spitIngredientsOut(level, cauldronPos, ingredients);
        stateMap.remove(cauldronPos);
        markRecentRitualActivity(level);
    }

    private static String ingredientSignature(List<ItemEntity> ingredients) {
        List<String> parts = new ArrayList<>(ingredients.size());
        for (ItemEntity entity : ingredients) {
            ItemStack stack = entity.getItem();
            parts.add(stack.getItemHolder().getRegisteredName() + "@" + stack.getCount() + ":" + VerseData.from(stack).map(VerseData::parameterKey).orElse("-"));
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }

    private static void spitIngredientsOut(ServerLevel level, BlockPos cauldronPos, List<ItemEntity> ingredients) {
        Vec3 center = Vec3.atCenterOf(cauldronPos).add(0.0D, 0.95D, 0.0D);
        for (ItemEntity ingredient : ingredients) {
            double velocityX = (level.random.nextDouble() - 0.5D) * 0.28D;
            double velocityZ = (level.random.nextDouble() - 0.5D) * 0.28D;
            ingredient.setPos(center.x(), center.y(), center.z());
            ingredient.setDeltaMovement(velocityX, 0.34D + level.random.nextDouble() * 0.08D, velocityZ);
            ingredient.setDefaultPickUpDelay();
            ingredient.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.SPLASH, center.x(), center.y(), center.z(), 12, 0.18D, 0.08D, 0.18D, 0.03D);
        level.sendParticles(ParticleTypes.SMOKE, center.x(), center.y() + 0.1D, center.z(), 8, 0.12D, 0.08D, 0.12D, 0.01D);
        level.playSound(null, cauldronPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.05F);
    }

    private static void pruneInactiveCauldronStates(ServerLevel level, Set<BlockPos> activeCauldrons) {
        Map<BlockPos, CauldronIngredientState> stateMap = CAULDRON_INGREDIENT_STATES.get(level.dimension());
        if (stateMap == null || stateMap.isEmpty()) {
            return;
        }

        stateMap.keySet().removeIf(pos -> !activeCauldrons.contains(pos));
        if (stateMap.isEmpty()) {
            CAULDRON_INGREDIENT_STATES.remove(level.dimension(), stateMap);
        }
    }

    private static void pruneInactiveCauldronSounds(ServerLevel level, Set<BlockPos> activeCauldrons) {
        Map<BlockPos, Long> soundMap = CAULDRON_SOUND_TIMESTAMPS.get(level.dimension());
        if (soundMap == null || soundMap.isEmpty()) {
            return;
        }

        soundMap.keySet().removeIf(pos -> !activeCauldrons.contains(pos));
        if (soundMap.isEmpty()) {
            CAULDRON_SOUND_TIMESTAMPS.remove(level.dimension(), soundMap);
        }
    }

    private static void pruneInactiveDebugCauldrons(ServerLevel level, Set<BlockPos> activeCauldrons) {
        Set<BlockPos> debugCauldrons = DEBUG_CAULDRONS.get(level.dimension());
        if (debugCauldrons == null || debugCauldrons.isEmpty()) {
            return;
        }

        debugCauldrons.removeIf(pos -> !activeCauldrons.contains(pos));
        if (debugCauldrons.isEmpty()) {
            DEBUG_CAULDRONS.remove(level.dimension(), debugCauldrons);
        }
    }

    private static void attemptHyperBookRitual(ServerLevel level, BlockPos cauldronPos) {
        List<ItemEntity> ingredients = level.getEntitiesOfClass(ItemEntity.class, ritualInputBounds(cauldronPos));
        if (ingredients.isEmpty()) {
            return;
        }

        List<ItemEntity> verseEntities = new ArrayList<>();
        List<VerseData> verses = new ArrayList<>();
        ItemEntity hyperDustEntity = null;
        ItemEntity paperEntity = null;
        ItemEntity writtenBookEntity = null;
        ItemEntity dyeEntity = null;
        Integer labelColor = null;
        ItemStack writtenBookStack = ItemStack.EMPTY;

        for (ItemEntity entity : ingredients) {
            ItemStack stack = entity.getItem();
            VerseData.from(stack).ifPresent(data -> {
                verseEntities.add(entity);
                verses.add(data);
            });

            if (hyperDustEntity == null && stack.is(VerseItems.HYPER_DUST.get())) {
                hyperDustEntity = entity;
            }

            if (paperEntity == null && stack.is(Items.PAPER)) {
                paperEntity = entity;
            }

            if (writtenBookEntity == null && stack.is(Items.WRITTEN_BOOK) && isNamedSignedBook(stack)) {
                writtenBookEntity = entity;
                writtenBookStack = stack.copyWithCount(1);
            }

            if (dyeEntity == null && stack.getItem() instanceof DyeItem dyeItem) {
                dyeEntity = entity;
                labelColor = dyeColorValue(dyeItem);
            }
        }

        if (tryCraftUnlinkedHyperBook(level, cauldronPos, verseEntities, hyperDustEntity, paperEntity, writtenBookEntity)) {
            return;
        }

        if (hyperDustEntity == null || hyperDustEntity.getItem().getCount() < HYPER_BOOK_DUST_COST || writtenBookEntity == null) {
            return;
        }

        String dimensionName = extractBookTitle(writtenBookStack).orElse(null);
        if (dimensionName == null) {
            return;
        }

        try {
            VerseDimensionParameters parameters = buildParametersFromVerses(level.getServer(), verses, level.random);
            String sanitizedDimensionPath = sanitizeDimensionName(dimensionName);
            if (!queuePendingRitual(level, new PendingRitualJob(
                cauldronPos.immutable(),
                sanitizedDimensionPath,
                dimensionName,
                labelColor,
                parameters,
                computeBookSeedOffset(writtenBookStack),
                writtenBookStack.copy(),
                collectRefundStacks(verseEntities, hyperDustEntity, writtenBookEntity, dyeEntity),
                level.getGameTime()
            ))) {
                findNearestPlayer(level, Vec3.atCenterOf(cauldronPos), 8.0D)
                    .ifPresent(player -> player.sendSystemMessage(Component.literal("The cauldron is already weaving too many Hyper Books. Wait a moment.").withStyle(ChatFormatting.YELLOW)));
                return;
            }
            consumeBookRitualIngredients(verseEntities, hyperDustEntity, writtenBookEntity, dyeEntity);
            consumeCauldronWater(level, cauldronPos);
            level.sendParticles(ParticleTypes.ENCHANT, cauldronPos.getX() + 0.5D, cauldronPos.getY() + 1.0D, cauldronPos.getZ() + 0.5D, 12, 0.15D, 0.15D, 0.15D, 0.08D);
            level.playSound(null, cauldronPos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.55F, 1.35F);
            level.playSound(null, cauldronPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.55F, 1.15F);
        } catch (IllegalArgumentException exception) {
            VerseWorks.LOGGER.warn("Rejected Hyper Book ritual at {} in {}: {}", cauldronPos, level.dimension().location(), exception.getMessage());
            findNearestPlayer(level, Vec3.atCenterOf(cauldronPos), 8.0D)
                .ifPresent(player -> player.sendSystemMessage(Component.literal("The verses reject that dimension: " + exception.getMessage()).withStyle(ChatFormatting.RED)));
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Hyper Book ritual failed at {} in {}", cauldronPos, level.dimension().location(), exception);
            findNearestPlayer(level, Vec3.atCenterOf(cauldronPos), 8.0D)
                .ifPresent(player -> player.sendSystemMessage(Component.literal("The cauldron ritual sputters and fails.").withStyle(ChatFormatting.RED)));
        }
    }

    private static boolean tryCraftUnlinkedHyperBook(ServerLevel level, BlockPos cauldronPos, List<ItemEntity> verseEntities, ItemEntity hyperDustEntity, ItemEntity paperEntity, ItemEntity writtenBookEntity) {
        if (!verseEntities.isEmpty() || writtenBookEntity != null || hyperDustEntity == null || paperEntity == null) {
            return false;
        }

        if (hyperDustEntity.getItem().getCount() < UNLINKED_HYPER_BOOK_DUST_COST) {
            return false;
        }

        consumeItemEntity(hyperDustEntity, UNLINKED_HYPER_BOOK_DUST_COST);
        consumeItemEntity(paperEntity, 1);
        consumeCauldronWater(level, cauldronPos);
        markRecentRitualActivity(level);
        launchCraftedBook(level, cauldronPos, new ItemStack(VerseItems.UNLINKED_HYPER_BOOK.get()));
        return true;
    }

    private static AABB ritualInputBounds(BlockPos pos) {
        return new AABB(
            pos.getX() + 0.15D,
            pos.getY() + 0.25D,
            pos.getZ() + 0.15D,
            pos.getX() + 0.85D,
            pos.getY() + 1.35D,
            pos.getZ() + 0.85D
        );
    }

    private static boolean isNamedSignedBook(ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null && !content.title().raw().isBlank();
    }

    private static Optional<String> extractBookTitle(ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return Optional.empty();
        }

        String title = content.title().raw().trim();
        return title.isEmpty() ? Optional.empty() : Optional.of(title);
    }

    private static long computeBookSeedOffset(ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return 0x4D59535443524146L;
        }

        long hash = 0x9E3779B97F4A7C15L;
        hash = mixSeedString(hash, content.title().raw());
        hash = mixSeedString(hash, content.author());
        for (var page : content.pages()) {
            hash = mixSeedString(hash, page.raw().getString());
            hash = Long.rotateLeft(hash ^ 0x517CC1B727220A95L, 11);
        }
        return hash;
    }

    private static long mixSeedString(long seed, String value) {
        long mixed = seed ^ 0xC2B2AE3D27D4EB4FL;
        for (int index = 0; index < value.length(); index++) {
            mixed ^= value.charAt(index);
            mixed *= 0x100000001B3L;
            mixed = Long.rotateLeft(mixed, 5);
        }
        return mixed;
    }

    private static boolean queuePendingRitual(ServerLevel level, PendingRitualJob job) {
        Deque<PendingRitualJob> jobs = PENDING_RITUAL_JOBS.computeIfAbsent(level.dimension(), ignored -> new ArrayDeque<>());
        if (jobs.size() >= MAX_PENDING_RITUALS_PER_LEVEL) {
            return false;
        }
        jobs.addLast(job);
        return true;
    }

    private static List<ItemStack> collectRefundStacks(List<ItemEntity> verseEntities, ItemEntity hyperDustEntity, ItemEntity writtenBookEntity, ItemEntity dyeEntity) {
        List<ItemStack> refundStacks = new ArrayList<>(verseEntities.size() + 2);
        for (ItemEntity verseEntity : verseEntities) {
            refundStacks.add(verseEntity.getItem().copy());
        }
        refundStacks.add(hyperDustEntity.getItem().copyWithCount(HYPER_BOOK_DUST_COST));
        refundStacks.add(writtenBookEntity.getItem().copyWithCount(1));
        if (dyeEntity != null) {
            refundStacks.add(dyeEntity.getItem().copyWithCount(1));
        }
        return List.copyOf(refundStacks);
    }

    private static void processPendingRitualJobs(ServerLevel level, long gameTime) {
        if (VerseDimensionRuntimeHooks.isShutdownInProgress(level.getServer())) {
            return;
        }

        ActivePendingRitual activeRitual = ACTIVE_PENDING_RITUALS.get(level.dimension());
        if (activeRitual != null) {
            if (!activeRitual.future().isDone()) {
                return;
            }

            ACTIVE_PENDING_RITUALS.remove(level.dimension(), activeRitual);
            try {
                activeRitual.future().join();
                VerseDimensionParameters finalParameters = VerseDimensionCatalog.get(level.getServer(), activeRitual.dimensionId())
                    .orElse(activeRitual.parameters());
                VerseDimensionCatalog.remember(activeRitual.dimensionId(), finalParameters);
                markDimensionPendingWarmup(activeRitual.dimensionId(), finalParameters, gameTime);
                findNearestPlayer(level, Vec3.atCenterOf(activeRitual.job().cauldronPos()), 8.0D)
                    .ifPresent(player -> {
                        VerseWorksAdvancements.award(player, VerseWorksAdvancements.NEW_WORLDS_AWAIT, "crafted_hyperbook");
                    });
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                VerseDimensionCatalog.forget(activeRitual.dimensionId());
                GeneratedDimensionPackWriter.deleteDimension(level.getServer(), activeRitual.dimensionId());
                VerseWorks.LOGGER.error("Hyper Book ritual processing failed at {} in {}", activeRitual.job().cauldronPos(), level.dimension().location(), cause);
                findNearestPlayer(level, Vec3.atCenterOf(activeRitual.job().cauldronPos()), 8.0D)
                    .ifPresent(player -> player.sendSystemMessage(Component.literal("The new Hyperbook's destination failed to stabilize.").withStyle(ChatFormatting.RED)));
            }
            return;
        }

        Deque<PendingRitualJob> jobs = PENDING_RITUAL_JOBS.get(level.dimension());
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        PendingRitualJob job = jobs.peekFirst();
        if (job == null || gameTime <= job.queuedAtGameTime()) {
            return;
        }

        jobs.removeFirst();
        if (jobs.isEmpty()) {
            PENDING_RITUAL_JOBS.remove(level.dimension(), jobs);
        }

        try {
            long ritualStageStartedAt = System.nanoTime();
            Optional<ServerPlayer> ritualOwner = findNearestPlayer(level, Vec3.atCenterOf(job.cauldronPos()), 8.0D);
            Optional<Component> dimensionLimitFailure = validateDimensionCreationLimits(level.getServer(), ritualOwner);
            if (dimensionLimitFailure.isPresent()) {
                refundPendingRitual(level, job, dimensionLimitFailure.get());
                return;
            }

            ResourceLocation dimensionId = allocateDimensionId(level.getServer(), job.dimensionPath());
            VerseDimensionParameters resolvedParameters = job.parameters().withSeedOffset(job.seedOffset());
            VerseDimensionCatalog.remember(dimensionId, resolvedParameters);
            ritualOwner.ifPresent(player -> VerseDimensionOwnershipSavedData.get(level.getServer()).rememberOwner(dimensionId, player.getUUID()));
            ItemStack hyperBook = createLinkedHyperBook(
                job.writtenBookStack(),
                dimensionId,
                job.dimensionName(),
                job.labelColor()
            );
            ejectUnusedRitualItems(level, job.cauldronPos());
            launchCraftedBook(level, job.cauldronPos(), hyperBook);
            logSlowStage("Completed Hyperbook ritual queue stage", dimensionId, ritualStageStartedAt);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long metadataStartedAt = System.nanoTime();
                try {
                    GeneratedDimensionPackWriter.writeDimension(level.getServer(), dimensionId, resolvedParameters);
                    logSlowStage("Persisted Hyperbook metadata", dimensionId, metadataStartedAt);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            });
            ACTIVE_PENDING_RITUALS.put(level.dimension(), new ActivePendingRitual(job, dimensionId, resolvedParameters, future));
            markRecentRitualActivity(level);
        } catch (IllegalArgumentException exception) {
            refundPendingRitual(level, job, Component.literal("The verses reject that dimension: " + exception.getMessage()).withStyle(ChatFormatting.RED));
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Hyper Book ritual processing failed at {} in {}", job.cauldronPos(), level.dimension().location(), exception);
            refundPendingRitual(level, job, Component.literal("The cauldron ritual sputters and returns its ingredients.").withStyle(ChatFormatting.RED));
        }
    }

    private static void refundPendingRitual(ServerLevel level, PendingRitualJob job, Component message) {
        Vec3 center = Vec3.atCenterOf(job.cauldronPos()).add(0.0D, 0.8D, 0.0D);
        for (ItemStack stack : job.refundStacks()) {
            ItemEntity itemEntity = new ItemEntity(level, center.x(), center.y(), center.z(), stack.copy());
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.08D, 0.18D, (level.random.nextDouble() - 0.5D) * 0.08D);
            level.addFreshEntity(itemEntity);
        }

        level.sendParticles(ParticleTypes.SMOKE, center.x(), center.y(), center.z(), 10, 0.15D, 0.15D, 0.15D, 0.01D);
        level.playSound(null, job.cauldronPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.8F);
        findNearestPlayer(level, Vec3.atCenterOf(job.cauldronPos()), 8.0D).ifPresent(player -> player.sendSystemMessage(message));
    }

    private static String sanitizeDimensionName(String input) {
        String cleaned = NON_DIMENSION_CHARACTERS.matcher(input.toLowerCase(Locale.ROOT)).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return cleaned.isBlank() ? "untitled_dimension" : cleaned;
    }

    private static ResourceLocation allocateDimensionId(MinecraftServer server, String basePath) {
        ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, basePath);
        Set<ResourceLocation> knownDimensions = VerseDimensionCatalog.knownDimensionIds(server);
        int suffix = 2;
        while (knownDimensions.contains(candidate) || LiveDimensionInstantiator.findLevel(server, candidate).isPresent()) {
            candidate = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, basePath + suffix++);
        }
        return candidate;
    }

    private static Optional<Component> validateDimensionCreationLimits(MinecraftServer server, Optional<ServerPlayer> ritualOwner) {
        int maxDimensionsPerWorld = Config.MAX_DIMENSIONS_PER_WORLD.get();
        if (!Config.isUncapped(maxDimensionsPerWorld)) {
            long existingDimensionCount = VerseDimensionCatalog.knownDimensionIds(server).stream()
                .filter(dimensionId -> VerseWorks.MODID.equals(dimensionId.getNamespace()))
                .count();
            if (existingDimensionCount >= maxDimensionsPerWorld) {
                return Optional.of(Component.literal("This world has reached its maximum number of VerseWorks dimensions.").withStyle(ChatFormatting.RED));
            }
        }

        int maxDimensionsPerPlayer = Config.MAX_DIMENSIONS_PER_PLAYER.get();
        if (Config.isUncapped(maxDimensionsPerPlayer) || ritualOwner.isEmpty()) {
            return Optional.empty();
        }

        long ownedDimensionCount = VerseDimensionOwnershipSavedData.get(server).countOwnedDimensions(server, ritualOwner.get().getUUID());
        if (ownedDimensionCount >= maxDimensionsPerPlayer) {
            return Optional.of(Component.literal("You have reached your maximum number of created VerseWorks dimensions.").withStyle(ChatFormatting.RED));
        }

        return Optional.empty();
    }

    private static VerseDimensionParameters buildParametersFromVerses(MinecraftServer server, List<VerseData> verses, RandomSource random) {
        List<VerseData> uniqueVerses = uniqueVerses(verses);
        RecipeOptions options = new RecipeOptions();
        for (VerseData verse : uniqueVerses) {
            validateVerseData(verse);
            applyVerseData(options, verse);
            if (verse.secondaryParameterKey() != null && verse.secondaryAmountText() != null) {
                applySecondaryOption(options, verse.secondaryParameterKey(), verse.secondaryAmountText());
            }
        }

        if (!options.structuresExplicitlySet) {
            options.structures = false;
        }
        if (options.worldType.isVoid() && !options.structuresExplicitlySet) {
            options.structures = false;
        }
        if (!options.explicitSkyColor) {
            options.skyColor = VerseCatalog.randomSkyColor(random);
        }
        applyRandomWorldDefaults(server, options, random, uniqueVerses.size());
        applyCorruptions(options, uniqueVerses.size(), random);
        if (options.permanentTime && options.timeOfDay == null) {
            options.timeOfDay = options.explicitFixedTime ? 1000 : random.nextInt(24000);
        }

        validateParameters(server, options);
        return new VerseDimensionParameters(
            options.skyColor,
            options.floorBlock,
            options.sphereBlock,
            options.worldType,
            List.copyOf(options.biomeIds),
            options.gravityScale,
            options.dayRate,
            options.timeOfDay,
            options.permanentTime,
            options.permanentRain,
            options.permanentLightning,
            options.permanentStorm,
            options.meteorShowers,
            options.spawnWarp,
            options.oreMultiplier,
            options.spheres,
            options.fluidId,
            options.lakes,
            options.oceanLevel,
            options.structures,
            List.copyOf(options.corruptions),
            0L
        );
    }

    private static void validateVerseData(VerseData verse) {
        validateParameterKey(verse.parameterKey());
        if ((verse.secondaryParameterKey() == null) != (verse.secondaryAmountText() == null)) {
            throw new IllegalArgumentException("Verse secondary data is incomplete for " + verse.parameterKey());
        }
        if (verse.secondaryParameterKey() != null) {
            validateParameterKey(verse.secondaryParameterKey());
        }
    }

    private static void validateParameterKey(String key) {
        String normalized = normalizeParameterKey(key);
        if (!VALID_PARAMETER_KEYS.contains(normalized)) {
            throw new IllegalArgumentException("Unknown verse parameter " + key);
        }
    }

    private static String normalizeParameterKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    private static void applyVerseData(RecipeOptions options, VerseData verse) {
        switch (verse.parameterKey()) {
            case "skyColor", "sky_color" -> {
                if (verse.intValue() != null) {
                    options.skyColor = verse.intValue();
                    options.explicitSkyColor = true;
                }
            }
            case "worldType", "world_type" -> {
                if (verse.stringValue() != null) {
                    options.worldType = VerseDimensionWorldType.parse(verse.stringValue());
                    options.explicitWorldType = true;
                }
            }
            case "biomeId", "biome", "biomes" -> {
                if (verse.stringValue() != null) {
                    ResourceLocation biomeId = ResourceLocation.tryParse(verse.stringValue());
                    if (biomeId != null) {
                        options.biomeIds.add(biomeId);
                        options.explicitBiomes = true;
                    }
                }
            }
            case "gravityScale", "gravity_scale" -> {
                if (verse.doubleValue() != null) {
                    if (isAntiVerse(verse)) {
                        options.preventGravity = true;
                        options.gravityScale = 1.0D;
                    } else if (!options.preventGravity) {
                        options.explicitGravity = true;
                        options.gravityScale = verse.doubleValue();
                    }
                }
            }
            case "dayRate", "day_rate" -> {
                if (verse.doubleValue() != null) {
                    options.dayRate = verse.doubleValue();
                }
            }
            case "timeOfDay", "time_of_day" -> {
                if (verse.intValue() != null) {
                    options.timeOfDay = verse.intValue();
                    options.explicitFixedTime = true;
                }
            }
            case "permanentTime", "permanent_time",
                "permanentRain", "permanent_rain",
                "permanentLightning", "permanent_lightning",
                "permanentStorm", "permanent_storm",
                "meteorShowers", "meteor_showers",
                "spawnWarp", "spawn_warp" -> {
                if (verse.booleanValue() != null) {
                    applyTemporalToggle(options, canonicalTemporalOptionKey(verse.parameterKey()), verse.booleanValue());
                }
            }
            case "oreMultiplier", "ore_multiplier" -> {
                if (verse.doubleValue() != null) {
                    options.oreMultiplier = verse.doubleValue();
                }
            }
            case "spheres" -> {
                if (verse.booleanValue() != null) {
                    if (!verse.booleanValue() || isAntiVerse(verse)) {
                        options.preventSpheres = true;
                        options.spheres = false;
                        options.sphereBlock = null;
                    } else if (!options.preventSpheres) {
                        options.spheres = true;
                    }
                }
            }
            case "lakes" -> {
                if (verse.booleanValue() != null) {
                    options.lakes = verse.booleanValue();
                }
            }
            case "structures" -> {
                if (verse.booleanValue() != null) {
                    options.structures = verse.booleanValue();
                    options.structuresExplicitlySet = true;
                }
            }
            case "oceanLevel", "ocean_level" -> {
                if (verse.intValue() != null) {
                    options.oceanLevel = verse.intValue();
                }
            }
            case "floorBlock", "floor_block" -> {
                if (verse.stringValue() != null) {
                    options.floorBlock = parseBlock(verse.stringValue());
                }
            }
            case "sphereBlock", "sphere_block", "sphereMaterial" -> {
                if (verse.stringValue() != null && !options.preventSpheres) {
                    options.sphereBlock = parseBlock(verse.stringValue());
                    options.spheres = true;
                }
            }
            case "stabilizedRealm", "stabilized_realm" -> {
                if (Boolean.TRUE.equals(verse.booleanValue())) {
                    options.preventAllCorruptions = true;
                }
            }
            case "fluidId", "fluid" -> {
                if (verse.stringValue() != null) {
                    ResourceLocation fluidId = ResourceLocation.tryParse(verse.stringValue());
                    if (fluidId != null) {
                        options.fluidId = fluidId;
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void applySecondaryOption(RecipeOptions options, String key, String value) {
        String temporalKey = canonicalTemporalOptionKey(key);
        if (temporalKey != null) {
            applyTemporalToggle(options, temporalKey, parseBoolean(value));
        }
    }

    private static String canonicalTemporalOptionKey(String key) {
        return switch (key) {
            case "permanentTime", "permanent_time" -> "permanentTime";
            case "permanentRain", "permanent_rain" -> "permanentRain";
            case "permanentLightning", "permanent_lightning" -> "permanentLightning";
            case "permanentStorm", "permanent_storm" -> "permanentStorm";
            case "meteorShowers", "meteor_showers" -> "meteorShowers";
            case "spawnWarp", "spawn_warp" -> "spawnWarp";
            default -> null;
        };
    }

    private static void applyTemporalToggle(RecipeOptions options, String key, boolean value) {
        switch (key) {
            case "permanentTime" -> {
                if (!value) {
                    options.preventPermanentTime = true;
                    options.permanentTime = false;
                } else if (!options.preventPermanentTime) {
                    options.permanentTime = true;
                }
            }
            case "permanentRain" -> {
                if (!value) {
                    options.preventPermanentRain = true;
                    options.permanentRain = false;
                    options.permanentStorm = false;
                } else if (!options.preventPermanentRain) {
                    options.permanentRain = true;
                }
            }
            case "permanentLightning" -> {
                if (!value) {
                    options.preventPermanentLightning = true;
                    options.permanentLightning = false;
                    options.permanentStorm = false;
                } else if (!options.preventPermanentLightning) {
                    options.permanentLightning = true;
                }
            }
            case "permanentStorm" -> {
                if (!value) {
                    options.preventPermanentStorm = true;
                    options.permanentStorm = false;
                } else if (!options.preventPermanentStorm && !options.preventPermanentRain && !options.preventPermanentLightning) {
                    options.permanentStorm = true;
                    options.permanentRain = true;
                }
            }
            case "meteorShowers" -> {
                if (!value) {
                    options.preventMeteorShowers = true;
                    options.meteorShowers = false;
                } else if (!options.preventMeteorShowers) {
                    options.meteorShowers = true;
                }
            }
            case "spawnWarp" -> {
                if (!value) {
                    options.preventSpawnWarp = true;
                    options.spawnWarp = false;
                } else if (!options.preventSpawnWarp) {
                    options.spawnWarp = true;
                }
            }
            default -> {
            }
        }

        canonicalizeTemporalOptions(options);
    }

    // Anti-verses can disable a broader weather state than the original verse toggled, so every
    // update is normalized back to one internally consistent rule set here.
    private static void canonicalizeTemporalOptions(RecipeOptions options) {
        if (options.preventPermanentStorm) {
            options.permanentStorm = false;
        }
        if (options.preventPermanentTime) {
            options.permanentTime = false;
        }
        if (options.preventPermanentRain) {
            options.permanentRain = false;
            options.permanentStorm = false;
        }
        if (options.preventPermanentLightning) {
            options.permanentLightning = false;
            options.permanentStorm = false;
        }
        if (options.preventMeteorShowers) {
            options.meteorShowers = false;
        }
        if (options.preventSpawnWarp) {
            options.spawnWarp = false;
        }
        if (options.preventGravity) {
            options.gravityScale = 1.0D;
        }
        if (options.preventSpheres) {
            options.spheres = false;
            options.sphereBlock = null;
        }
        if (options.permanentStorm && !options.preventPermanentRain) {
            options.permanentRain = true;
        }
    }

    private static boolean isAntiVerse(VerseData verse) {
        return verse.prefix() != null && verse.prefix().startsWith("Anti-");
    }

    private static List<VerseData> uniqueVerses(List<VerseData> verses) {
        List<VerseData> unique = new ArrayList<>(verses.size());
        Set<String> seenSignatures = new HashSet<>();
        for (VerseData verse : verses) {
            String signature = verseSignature(verse);
            if (seenSignatures.add(signature)) {
                unique.add(verse);
            }
        }
        return List.copyOf(unique);
    }

    private static String verseSignature(VerseData verse) {
        return String.join("|",
            String.valueOf(verse.prefix()),
            String.valueOf(verse.label()),
            String.valueOf(verse.verseType()),
            String.valueOf(verse.parameterKey()),
            String.valueOf(verse.amountText()),
            String.valueOf(verse.stringValue()),
            String.valueOf(verse.intValue()),
            String.valueOf(verse.doubleValue()),
            String.valueOf(verse.booleanValue()),
            String.valueOf(verse.secondaryParameterKey()),
            String.valueOf(verse.secondaryAmountText())
        );
    }

    private static void applyRandomWorldDefaults(MinecraftServer server, RecipeOptions options, RandomSource random, int uniqueVerseCount) {
        if (options.explicitWorldType || options.explicitBiomes || !options.biomeIds.isEmpty()) {
            return;
        }

        VerseDimensionWorldType[] worldTypes = {
            VerseDimensionWorldType.NORMAL,
            VerseDimensionWorldType.AMPLIFIED,
            VerseDimensionWorldType.SKY_ISLAND,
            VerseDimensionWorldType.ISLAND
        };
        options.worldType = worldTypes[random.nextInt(worldTypes.length)];

        List<ResourceLocation> availableBiomes = server.registryAccess().lookupOrThrow(Registries.BIOME)
            .listElements()
            .map(holder -> holder.key().location())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (availableBiomes.isEmpty()) {
            throw new IllegalArgumentException("No biomes are available for ritual world generation");
        }

        int minBiomeCount = randomBiomeMinCount(uniqueVerseCount);
        int maxBiomeCount = randomBiomeMaxCount(uniqueVerseCount, availableBiomes.size());
        int biomeCount = randomBetweenInclusive(random, minBiomeCount, maxBiomeCount);
        biomeCount = Math.min(biomeCount, availableBiomes.size());
        for (int index = 0; index < biomeCount; index++) {
            int selectedIndex = random.nextInt(availableBiomes.size());
            options.biomeIds.add(availableBiomes.remove(selectedIndex));
        }
    }

    private static void applyCorruptions(RecipeOptions options, int verseCount, RandomSource random) {
        if (options.preventAllCorruptions) {
            return;
        }

        List<VerseDimensionCorruption> eligible = eligibleCorruptions(options);
        if (eligible.isEmpty()) {
            return;
        }

        int targetCorruptions = targetCorruptionCount(verseCount, eligible.size(), random);
        for (int index = 0; index < targetCorruptions && !eligible.isEmpty(); index++) {
            applyRandomCorruption(options, eligible, random);
        }
    }

    private static int targetCorruptionCount(int uniqueVerseCount, int eligibleCount, RandomSource random) {
        int maxCorruptions = Math.max(1, eligibleCount - Math.min(uniqueVerseCount, Math.max(0, eligibleCount - 1)));
        int minCorruptions = uniqueVerseCount <= 1 ? 3 : uniqueVerseCount <= 3 ? 2 : 1;
        minCorruptions = Math.min(minCorruptions, eligibleCount);
        maxCorruptions = Math.max(minCorruptions, maxCorruptions);
        return randomBetweenInclusive(random, minCorruptions, maxCorruptions);
    }

    private static List<VerseDimensionCorruption> eligibleCorruptions(RecipeOptions options) {
        List<VerseDimensionCorruption> corruptions = new ArrayList<>();
        if (!options.preventPermanentRain) {
            corruptions.add(VerseDimensionCorruption.ENDLESS_RAIN);
        }
        if (!options.preventPermanentStorm && !options.preventPermanentRain && !options.preventPermanentLightning) {
            corruptions.add(VerseDimensionCorruption.ENDLESS_STORM);
        }
        if (!options.preventPermanentLightning) {
            corruptions.add(VerseDimensionCorruption.ENDLESS_LIGHTNING);
        }
        if (!options.preventMeteorShowers) {
            corruptions.add(VerseDimensionCorruption.METEORS);
        }
        if (!options.preventSpawnWarp) {
            corruptions.add(VerseDimensionCorruption.WARP);
        }
        if (!options.preventPermanentTime) {
            corruptions.add(VerseDimensionCorruption.FIXED_TIME);
        }
        if (!options.preventGravity && !options.explicitGravity) {
            corruptions.add(VerseDimensionCorruption.GRAVITY);
        }
        if (!options.preventSpheres) {
            corruptions.add(VerseDimensionCorruption.SPHERES);
        }
        return corruptions;
    }

    private static void applyRandomCorruption(RecipeOptions options, List<VerseDimensionCorruption> eligible, RandomSource random) {
        VerseDimensionCorruption corruption = eligible.remove(random.nextInt(eligible.size()));
        if (!options.corruptions.contains(corruption)) {
            options.corruptions.add(corruption);
        }

        switch (corruption) {
            case ENDLESS_RAIN -> applyTemporalToggle(options, "permanentRain", true);
            case ENDLESS_STORM -> applyTemporalToggle(options, "permanentStorm", true);
            case ENDLESS_LIGHTNING -> applyTemporalToggle(options, "permanentLightning", true);
            case METEORS -> applyTemporalToggle(options, "meteorShowers", true);
            case WARP -> applyTemporalToggle(options, "spawnWarp", true);
            case FIXED_TIME -> {
                applyTemporalToggle(options, "permanentTime", true);
                options.timeOfDay = random.nextInt(24000);
            }
            case GRAVITY -> options.gravityScale = random.nextBoolean() ? 0.25D : 4.0D;
            case SPHERES -> {
                if (options.sphereBlock == null) {
                    options.spheres = true;
                    options.sphereBlock = randomCorruptionSphereBlock(random);
                }
            }
        }

        switch (corruption) {
            case ENDLESS_RAIN, ENDLESS_LIGHTNING -> eligible.remove(VerseDimensionCorruption.ENDLESS_STORM);
            case ENDLESS_STORM -> {
                eligible.remove(VerseDimensionCorruption.ENDLESS_RAIN);
                eligible.remove(VerseDimensionCorruption.ENDLESS_LIGHTNING);
            }
            default -> {
            }
        }
    }

    private static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            default -> false;
        };
    }

    private static int randomBetweenInclusive(RandomSource random, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private static int randomBiomeMinCount(int uniqueVerseCount) {
        if (uniqueVerseCount <= 1) {
            return 2;
        }
        if (uniqueVerseCount <= 3) {
            return 2;
        }
        return 1;
    }

    private static int randomBiomeMaxCount(int uniqueVerseCount, int availableBiomeCount) {
        int max = uniqueVerseCount <= 0 ? 5 : uniqueVerseCount == 1 ? 4 : uniqueVerseCount <= 3 ? 3 : 2;
        return Math.max(randomBiomeMinCount(uniqueVerseCount), Math.min(max, availableBiomeCount));
    }

    private static Block randomCorruptionSphereBlock(RandomSource random) {
        return CORRUPTION_SPHERE_BLOCKS.get(random.nextInt(CORRUPTION_SPHERE_BLOCKS.size()));
    }

    private static void validateParameters(MinecraftServer server, RecipeOptions options) {
        if (options.worldType.isFlat() && options.biomeIds.size() > 1) {
            throw new IllegalArgumentException("flat and void worlds support at most one biome");
        }

        var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        for (ResourceLocation biomeId : options.biomeIds) {
            ResourceKey<net.minecraft.world.level.biome.Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
            if (biomeRegistry.get(biomeKey).isEmpty()) {
                throw new IllegalArgumentException("Unknown biome " + biomeId);
            }
        }

        if (options.fluidId != null) {
            var fluid = BuiltInRegistries.FLUID.getOptional(options.fluidId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fluid " + options.fluidId));
            if (fluid.defaultFluidState().createLegacyBlock().isAir()) {
                throw new IllegalArgumentException("Fluid " + options.fluidId + " cannot be placed as a world block");
            }
        }
    }

    private static Block parseBlock(String value) {
        ResourceLocation blockId = ResourceLocation.tryParse(value);
        if (blockId == null) {
            throw new IllegalArgumentException("Invalid block id " + value);
        }
        return BuiltInRegistries.BLOCK.getOptional(blockId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown block id " + value));
    }

    public static boolean hasRecentRitualActivity(ServerLevel level) {
        if (ACTIVE_PENDING_RITUALS.containsKey(level.dimension())) {
            return true;
        }

        Long lastActivity = RECENT_RITUAL_ACTIVITY.get(level.dimension());
        return lastActivity != null && level.getGameTime() - lastActivity <= RITUAL_ACTIVITY_GRACE_TICKS;
    }

    private static void markRecentRitualActivity(ServerLevel level) {
        RECENT_RITUAL_ACTIVITY.put(level.dimension(), level.getGameTime());
    }

    private static void consumeBookRitualIngredients(List<ItemEntity> verseEntities, ItemEntity hyperDustEntity, ItemEntity writtenBookEntity, ItemEntity dyeEntity) {
        for (ItemEntity verseEntity : verseEntities) {
            consumeItemEntity(verseEntity, verseEntity.getItem().getCount());
        }
        consumeItemEntity(hyperDustEntity, HYPER_BOOK_DUST_COST);
        consumeItemEntity(writtenBookEntity, 1);
        if (dyeEntity != null) {
            consumeItemEntity(dyeEntity, 1);
        }
    }

    private static void consumeItemEntity(ItemEntity entity, int amount) {
        ItemStack stack = entity.getItem();
        stack.shrink(amount);
        if (stack.isEmpty()) {
            entity.discard();
            return;
        }
        entity.setItem(stack);
    }

    private static void consumeCauldronWater(ServerLevel level, BlockPos cauldronPos) {
        if (!isDebugCauldron(level, cauldronPos)) {
            level.setBlock(cauldronPos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, cauldronPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.85F, 0.9F);
        }
        level.sendParticles(ParticleTypes.SPLASH, cauldronPos.getX() + 0.5D, cauldronPos.getY() + 0.95D, cauldronPos.getZ() + 0.5D, 10, 0.18D, 0.08D, 0.18D, 0.03D);
        Map<BlockPos, CauldronIngredientState> stateMap = CAULDRON_INGREDIENT_STATES.get(level.dimension());
        if (stateMap != null) {
            stateMap.remove(cauldronPos);
        }
    }

    public static void createDebugCauldron(ServerLevel level, BlockPos cauldronPos) {
        level.setBlock(cauldronPos.below(2), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cauldronPos.below(), Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cauldronPos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), Block.UPDATE_ALL);
        DEBUG_CAULDRONS.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet()).add(cauldronPos.immutable());
        Map<BlockPos, Long> soundMap = CAULDRON_SOUND_TIMESTAMPS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
        soundMap.remove(cauldronPos);
    }

    private static boolean isDebugCauldron(ServerLevel level, BlockPos cauldronPos) {
        Set<BlockPos> debugCauldrons = DEBUG_CAULDRONS.get(level.dimension());
        return debugCauldrons != null && debugCauldrons.contains(cauldronPos);
    }

    private static ItemStack createLinkedHyperBook(ItemStack writtenBook, ResourceLocation dimensionId, String dimensionName, Integer labelColor) {
        ItemStack hyperBook = new ItemStack(VerseItems.HYPER_BOOK.get());
        WrittenBookContent writtenBookContent = writtenBook.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (writtenBookContent != null) {
            hyperBook.set(DataComponents.WRITTEN_BOOK_CONTENT, writtenBookContent);
        }
        hyperBook.set(DataComponents.CUSTOM_NAME, hyperBookName(dimensionName, labelColor));
        return new HyperBookData(dimensionId, dimensionName, labelColor).apply(hyperBook);
    }

    private static Component hyperBookName(String dimensionName, Integer labelColor) {
        int resolvedColor = labelColor != null ? labelColor : 0xFF55FF;
        return Component.literal("Hyperbook - " + dimensionName).withStyle(style -> style.withColor(resolvedColor));
    }

    private static int dyeColorValue(DyeItem dyeItem) {
        return switch (dyeItem.getDyeColor()) {
            case WHITE -> 0xF9FFFE;
            case ORANGE -> 0xF9801D;
            case MAGENTA -> 0xC74EBD;
            case LIGHT_BLUE -> 0x3AB3DA;
            case YELLOW -> 0xFED83D;
            case LIME -> 0x80C71F;
            case PINK -> 0xF38BAA;
            case GRAY -> 0x474F52;
            case LIGHT_GRAY -> 0x9D9D97;
            case CYAN -> 0x169C9C;
            case PURPLE -> 0x8932B8;
            case BLUE -> 0x3C44AA;
            case BROWN -> 0x835432;
            case GREEN -> 0x5E7C16;
            case RED -> 0xB02E26;
            case BLACK -> 0x1D1D21;
        };
    }

    private static void launchCraftedBook(ServerLevel level, BlockPos cauldronPos, ItemStack hyperBook) {
        Vec3 start = Vec3.atCenterOf(cauldronPos).add(0.0D, 0.85D, 0.0D);
        Optional<ServerPlayer> nearestPlayer = findNearestPlayer(level, start, 8.0D);
        ItemEntity itemEntity = new ItemEntity(level, start.x(), start.y(), start.z(), hyperBook);

        Vec3 launchVelocity = new Vec3(0.0D, 0.42D, 0.0D);
        if (nearestPlayer.isPresent()) {
            Vec3 playerOffset = nearestPlayer.get().position().subtract(start);
            Vec3 horizontalFacing = new Vec3(playerOffset.x, 0.0D, playerOffset.z);
            if (horizontalFacing.lengthSqr() > 1.0E-4D) {
                double horizontalSpeed = Math.min(0.36D, 0.18D + Math.sqrt(horizontalFacing.lengthSqr()) * 0.04D);
                launchVelocity = horizontalFacing.normalize().scale(horizontalSpeed).add(0.0D, 0.42D, 0.0D);
            }
        }

        itemEntity.setDeltaMovement(launchVelocity);
        itemEntity.hurtMarked = true;
        level.addFreshEntity(itemEntity);
        level.sendParticles(ParticleTypes.ENCHANT, start.x(), start.y() + 0.15D, start.z(), 20, 0.18D, 0.18D, 0.18D, 0.15D);
        level.sendParticles(ParticleTypes.POOF, start.x(), start.y() + 0.1D, start.z(), 10, 0.12D, 0.08D, 0.12D, 0.02D);
        level.playSound(null, cauldronPos, VerseSounds.RITUAL_COMPLETE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void markDimensionPendingWarmup(ResourceLocation dimensionId, VerseDimensionParameters parameters, long gameTime) {
        PENDING_CRAFTED_WARMUPS.removeIf(pending -> pending.dimensionId().equals(dimensionId));
        PENDING_CRAFTED_WARMUPS.addLast(new PendingCraftedWarmup(dimensionId, parameters, gameTime + CRAFTED_WARMUP_MIN_DELAY_TICKS));
    }

    private static void processCraftedWarmupCoordinator(MinecraftServer server, long gameTime) {
        ActiveCraftedWarmup activeWarmup = ACTIVE_CRAFTED_WARMUP;
        if (activeWarmup != null) {
            if (!activeWarmup.future().isDone()) {
                return;
            }

            ACTIVE_CRAFTED_WARMUP = null;
            try {
                LiveDimensionInstantiator.Result result = activeWarmup.future().join();
                VerseWorks.LOGGER.info("Background Hyperbook warmup started for {} ({})", activeWarmup.dimensionId(), result.detail());
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                VerseWorks.LOGGER.warn("Background Hyperbook warmup failed for {}", activeWarmup.dimensionId(), cause);
            }
        }

        if (PENDING_CRAFTED_WARMUPS.isEmpty()) {
            return;
        }

        if (VerseDimensionRuntimeHooks.isShutdownInProgress(server)
            || LiveDimensionInstantiator.isAnyActivationInProgress()
            || VerseDimensionRuntimeHooks.hasPendingTeleportWork()) {
            return;
        }

        PendingCraftedWarmup pendingWarmup = PENDING_CRAFTED_WARMUPS.peekFirst();
        if (pendingWarmup == null || gameTime < pendingWarmup.readyAtGameTime()) {
            return;
        }

        if (LiveDimensionInstantiator.findLevel(server, pendingWarmup.dimensionId()).isPresent()) {
            PENDING_CRAFTED_WARMUPS.removeFirst();
            return;
        }

        PENDING_CRAFTED_WARMUPS.removeFirst();
        long enqueueStartedAt = System.nanoTime();
        CompletableFuture<LiveDimensionInstantiator.Result> future = LiveDimensionInstantiator.activateAsync(
            server,
            pendingWarmup.dimensionId(),
            pendingWarmup.parameters(),
            LiveDimensionInstantiator.ActivationMode.CRAFT_BACKGROUND
        );
        logSlowStage("Enqueued crafted Hyperbook warmup", pendingWarmup.dimensionId(), enqueueStartedAt);
        ACTIVE_CRAFTED_WARMUP = new ActiveCraftedWarmup(pendingWarmup.dimensionId(), future);
    }

    private static void ejectUnusedRitualItems(ServerLevel level, BlockPos cauldronPos) {
        Vec3 start = Vec3.atCenterOf(cauldronPos).add(0.0D, 0.85D, 0.0D);
        Optional<ServerPlayer> nearestPlayer = findNearestPlayer(level, start, 8.0D);
        List<ItemEntity> unusedItems = level.getEntitiesOfClass(ItemEntity.class, ritualInputBounds(cauldronPos));
        for (ItemEntity itemEntity : unusedItems) {
            if (!itemEntity.isAlive()) {
                continue;
            }

            itemEntity.setDefaultPickUpDelay();
            Vec3 launchVelocity = new Vec3(
                (level.random.nextDouble() - 0.5D) * 0.08D,
                0.24D,
                (level.random.nextDouble() - 0.5D) * 0.08D
            );
            if (nearestPlayer.isPresent()) {
                Vec3 playerOffset = nearestPlayer.get().position().subtract(itemEntity.position());
                Vec3 horizontalFacing = new Vec3(playerOffset.x, 0.0D, playerOffset.z);
                if (horizontalFacing.lengthSqr() > 1.0E-4D) {
                    double horizontalSpeed = Math.min(0.32D, 0.14D + Math.sqrt(horizontalFacing.lengthSqr()) * 0.04D);
                    launchVelocity = horizontalFacing.normalize().scale(horizontalSpeed).add(0.0D, 0.24D, 0.0D);
                }
            }

            itemEntity.setDeltaMovement(launchVelocity);
            itemEntity.hurtMarked = true;
        }
    }

    private static Optional<ServerPlayer> findNearestPlayer(ServerLevel level, Vec3 origin, double maxDistance) {
        return level.players().stream()
            .filter(player -> player.position().distanceToSqr(origin) <= maxDistance * maxDistance)
            .min(Comparator.comparingDouble(player -> player.position().distanceToSqr(origin)));
    }

    private static void processNearbyLecterns(ServerLevel level) {
        Set<BlockPos> scanned = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            for (int dx = -LECTERN_SCAN_RADIUS; dx <= LECTERN_SCAN_RADIUS; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -LECTERN_SCAN_RADIUS; dz <= LECTERN_SCAN_RADIUS; dz++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (!scanned.add(pos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(pos);
                        if (!state.is(Blocks.LECTERN)) {
                            continue;
                        }

                        LecternBlockEntity lectern = level.getBlockEntity(pos) instanceof LecternBlockEntity found ? found : null;
                        if (lectern == null || !lecternHasStoredBook(lectern)) {
                            logLecternLabelRemoval(level, pos, "lectern missing book during scan");
                            removeLecternLabel(level, pos);
                            continue;
                        }

                        Optional<HyperBookData> data = getLecternHyperBookData(lectern);
                        if (data.isEmpty()) {
                            logLecternLabelRemoval(level, pos, "lectern book is not recognized as a hyperbook during scan");
                            removeLecternLabel(level, pos);
                            continue;
                        }

                        spawnLecternParticles(level, pos, data.get().dimensionId());
                        ensureLecternLabel(level, pos, data.get());
                    }
                }
            }
        }
    }

    private static void processActiveLecternVisuals(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;
        }

        Set<BlockPos> activeLecterns = new HashSet<>();
        Map<BlockPos, PendingLecternWarmup> pendingWarmups = PENDING_LECTERN_WARMUPS.get(level.dimension());
        if (pendingWarmups != null && !pendingWarmups.isEmpty()) {
            activeLecterns.addAll(pendingWarmups.keySet());
        }

        Map<BlockPos, PendingLecternCompletion> pendingCompletions = PENDING_LECTERN_COMPLETIONS.get(level.dimension());
        if (pendingCompletions != null && !pendingCompletions.isEmpty()) {
            activeLecterns.addAll(pendingCompletions.keySet());
        }

        for (BlockPos lecternPos : activeLecterns) {
            if (!hasNearbyLabelViewer(level, lecternPos)) {
                continue;
            }

            if (!(level.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) || !lecternHasStoredBook(lectern)) {
                continue;
            }

            Optional<HyperBookData> data = getLecternHyperBookData(lectern);
            if (data.isEmpty()) {
                continue;
            }

            spawnLecternParticles(level, lecternPos, data.get().dimensionId());
        }
    }

    private static void processPendingLecternWarmups(ServerLevel level, long gameTime) {
        Map<BlockPos, PendingLecternWarmup> pendingWarmups = PENDING_LECTERN_WARMUPS.get(level.dimension());
        if (pendingWarmups == null || pendingWarmups.isEmpty()) {
            return;
        }

        List<BlockPos> completedLecterns = new ArrayList<>();
        List<BlockPos> cancelledLecterns = new ArrayList<>();
        for (Map.Entry<BlockPos, PendingLecternWarmup> entry : pendingWarmups.entrySet()) {
            BlockPos lecternPos = entry.getKey();
            PendingLecternWarmup pending = entry.getValue();
            if (!isMatchingHyperBookLectern(level, lecternPos, pending.destinationId())) {
                cancelledLecterns.add(lecternPos);
                continue;
            }

            ServerLevel destination = findExistingDestination(level.getServer(), pending.destinationId());
            if (destination == null) {
                cancelledLecterns.add(lecternPos);
                continue;
            }

            VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(level.getServer(), destination);
            if (!isHyperBookDestinationReady(level, lecternPos, destination, pending.destinationId())) {
                continue;
            }

            BlockPos soundPos = lecternPos.immutable();
            level.playSound(null, soundPos, VerseSounds.DIMENSION_PREPARED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            sendLecternCompletionFeedback(level, soundPos, List.copyOf(pending.playerIds()));
            PENDING_LECTERN_COMPLETIONS
                .computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(soundPos, new PendingLecternCompletion(pending.destinationId(), List.copyOf(pending.playerIds()), gameTime + HYPER_BOOK_READY_DISPLAY_TICKS, true));
            completedLecterns.add(lecternPos);
        }

        completedLecterns.forEach(pendingWarmups::remove);
        cancelledLecterns.forEach(pendingWarmups::remove);
        if (pendingWarmups.isEmpty()) {
            PENDING_LECTERN_WARMUPS.remove(level.dimension(), pendingWarmups);
        }
    }

    private static void processPendingLecternCompletions(ServerLevel level, long gameTime) {
        Map<BlockPos, PendingLecternCompletion> pendingCompletions = PENDING_LECTERN_COMPLETIONS.get(level.dimension());
        if (pendingCompletions == null || pendingCompletions.isEmpty()) {
            return;
        }

        List<BlockPos> finishedLecterns = new ArrayList<>();
        for (Map.Entry<BlockPos, PendingLecternCompletion> entry : pendingCompletions.entrySet()) {
            BlockPos lecternPos = entry.getKey();
            PendingLecternCompletion pending = entry.getValue();
            if (!isMatchingHyperBookLectern(level, lecternPos, pending.destinationId())) {
                finishedLecterns.add(lecternPos);
                continue;
            }

            if (gameTime < pending.finishAtGameTime()) {
                continue;
            }

            if (!pending.notified()) {
                sendLecternCompletionFeedback(level, lecternPos, pending.playerIds());
                pendingCompletions.put(lecternPos, pending.withState(gameTime + HYPER_BOOK_READY_DISPLAY_TICKS, true));
                continue;
            }

            finishedLecterns.add(lecternPos);
        }

        finishedLecterns.forEach(pendingCompletions::remove);
        if (pendingCompletions.isEmpty()) {
            PENDING_LECTERN_COMPLETIONS.remove(level.dimension(), pendingCompletions);
        }
    }

    private static void sendLecternCompletionFeedback(ServerLevel level, BlockPos lecternPos, List<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("The Hyperbook has finished tunneling.").withStyle(ChatFormatting.AQUA));
            }
        }

        Vec3 center = Vec3.atCenterOf(lecternPos).add(0.0D, 1.02D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 14, 0.12D, 0.12D, 0.12D, 0.02D);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.05D, center.z, 8, 0.08D, 0.08D, 0.08D, 0.02D);
        spawnLecternCompletionBurst(level, center);
    }

    private static boolean isMatchingHyperBookLectern(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId) {
        if (!level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
            return false;
        }

        if (!(level.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) || !lecternHasStoredBook(lectern)) {
            return false;
        }

        return getLecternHyperBookData(lectern)
            .map(data -> data.dimensionId().equals(destinationId))
            .orElse(false);
    }

    private static boolean isHyperBookDestinationReady(ServerLevel sourceLevel, BlockPos lecternPos, ServerLevel destination, ResourceLocation destinationId) {
        if (!(sourceLevel.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) || !lecternHasStoredBook(lectern)) {
            return false;
        }

        Optional<HyperBookData> data = getLecternHyperBookData(lectern);
        if (data.isEmpty() || !data.get().dimensionId().equals(destinationId)) {
            return false;
        }

        Optional<Vec3> savedTarget = data.get().targetPosition();
        Optional<Vec3> preparedTarget = savedTarget.isPresent() ? savedTarget : VerseDimensionRuntimeHooks.preparedEntryArrival(destination);
        boolean waitingOnEntryWarmup = preparedTarget.isEmpty()
            && LiveDimensionInstantiator.isRuntimeLevel(destination)
            && !LiveDimensionInstantiator.isReadyForEntry(destination);
        boolean waitingOnPreparedEntry = !savedTarget.isPresent() && !VerseDimensionRuntimeHooks.isPreparedEntryReady(destination);
        return !waitingOnEntryWarmup && !waitingOnPreparedEntry && preparedTarget.isPresent();
    }

    private static ServerLevel findExistingDestination(MinecraftServer server, ResourceLocation dimensionId) {
        ServerLevel liveLevel = LiveDimensionInstantiator.findLevel(server, dimensionId).orElse(null);
        if (liveLevel != null) {
            return liveLevel;
        }

        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
    }

    private static void queueLecternWarmupNotification(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId, ServerPlayer player) {
        Map<BlockPos, PendingLecternWarmup> pendingWarmups = PENDING_LECTERN_WARMUPS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
        BlockPos storedPos = lecternPos.immutable();
        Map<BlockPos, PendingLecternCompletion> pendingCompletions = PENDING_LECTERN_COMPLETIONS.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>());
        PendingLecternCompletion existingCompletion = pendingCompletions.get(storedPos);
        if (existingCompletion != null && existingCompletion.destinationId().equals(destinationId)) {
            if (!existingCompletion.playerIds().contains(player.getUUID())) {
                List<UUID> updatedPlayerIds = new ArrayList<>(existingCompletion.playerIds());
                updatedPlayerIds.add(player.getUUID());
                pendingCompletions.put(storedPos, new PendingLecternCompletion(destinationId, List.copyOf(updatedPlayerIds), existingCompletion.finishAtGameTime(), existingCompletion.notified()));
            }
            return;
        }

        PendingLecternWarmup existing = pendingWarmups.get(storedPos);
        if (existing == null || !existing.destinationId().equals(destinationId)) {
            Set<UUID> playerIds = ConcurrentHashMap.newKeySet();
            playerIds.add(player.getUUID());
            pendingWarmups.put(storedPos, new PendingLecternWarmup(destinationId, playerIds));
            pendingCompletions.remove(storedPos);
            return;
        }

        existing.playerIds().add(player.getUUID());
    }

    private static boolean hasNearbyLabelViewer(ServerLevel level, BlockPos lecternPos) {
        Vec3 labelCenter = new Vec3(lecternPos.getX() + 0.5D, lecternPos.getY() + 0.5D, lecternPos.getZ() + 0.5D);
        double maxDistanceSqr = LECTERN_LABEL_VISIBLE_RANGE * LECTERN_LABEL_VISIBLE_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(labelCenter) <= maxDistanceSqr) {
                return true;
            }
        }
        return false;
    }

    private static void spawnLecternParticles(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId) {
        LecternVisualState visualState = lecternVisualState(level, lecternPos, destinationId);
        if (visualState == LecternVisualState.TUNNELING) {
            spawnLecternTetherParticles(level, lecternPos);
            return;
        }

        if (visualState == LecternVisualState.READY) {
            spawnLecternReadyParticles(level, lecternPos);
            return;
        }

        RandomSource random = level.getRandom();
        double x = lecternPos.getX() + 0.35D + random.nextDouble() * 0.3D;
        double y = lecternPos.getY() + 1.02D + random.nextDouble() * 0.25D;
        double z = lecternPos.getZ() + 0.35D + random.nextDouble() * 0.3D;
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 6, 0.16D, 0.22D, 0.16D, 0.3D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.18D, z, 2, 0.08D, 0.16D, 0.08D, 0.015D);
    }

    private static void spawnLecternTetherParticles(ServerLevel level, BlockPos lecternPos) {
        RandomSource random = level.getRandom();
        double centerX = lecternPos.getX() + 0.5D;
        double centerY = lecternPos.getY() + 1.02D;
        double centerZ = lecternPos.getZ() + 0.5D;
        double time = level.getGameTime() * 0.12D;
        for (int index = 0; index < 9; index++) {
            double progress = index / 8.0D;
            double y = centerY + progress * LECTERN_TETHER_HEIGHT;
            double zigzag = (index % 2 == 0 ? -1.0D : 1.0D) * (0.05D + progress * 0.1D);
            double sway = Math.sin(time + progress * 6.0D) * 0.035D;
            double depth = Math.cos(time * 0.75D + progress * 8.0D) * 0.025D;
            double x = centerX + zigzag + sway + (random.nextDouble() - 0.5D) * 0.01D;
            double z = centerZ + depth + (random.nextDouble() - 0.5D) * 0.01D;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 2, 0.015D, 0.03D, 0.015D, 0.01D);
            if ((index & 1) == 0) {
                level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
            if (index > 0 && index < 8 && random.nextBoolean()) {
                double edgeDrift = 0.1D + progress * 0.12D;
                level.sendParticles(ParticleTypes.PORTAL, x - edgeDrift, y, z, 1, 0.015D, 0.03D, 0.02D, 0.01D);
                level.sendParticles(ParticleTypes.PORTAL, x + edgeDrift, y, z, 1, 0.015D, 0.03D, 0.02D, 0.01D);
            }
        }

        double topY = centerY + LECTERN_TETHER_HEIGHT;
        level.sendParticles(ParticleTypes.ENCHANT, centerX, centerY + 1.4D, centerZ, 6, 0.08D, 0.6D, 0.08D, 0.08D);
        level.sendParticles(ParticleTypes.END_ROD, centerX, topY + 0.15D, centerZ, 4, 0.04D, 0.12D, 0.04D, 0.03D);
        level.sendParticles(ParticleTypes.PORTAL, centerX, topY + 0.28D, centerZ, 6, 0.05D, 0.18D, 0.05D, 0.04D);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, centerX, topY + 0.4D, centerZ, 2, 0.03D, 0.08D, 0.03D, 0.01D);
    }

    private static void spawnLecternReadyParticles(ServerLevel level, BlockPos lecternPos) {
        RandomSource random = level.getRandom();
        double centerX = lecternPos.getX() + 0.5D;
        double centerY = lecternPos.getY() + 1.08D;
        double centerZ = lecternPos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, centerX, centerY, centerZ, 6, 0.1D, 0.1D, 0.1D, 0.01D);
        level.sendParticles(ParticleTypes.PORTAL, centerX, centerY + 0.04D, centerZ, 5, 0.12D, 0.12D, 0.12D, 0.015D);
        if (random.nextBoolean()) {
            level.sendParticles(ParticleTypes.DRAGON_BREATH, centerX, centerY + 0.02D, centerZ, 1, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    private static void spawnLecternCompletionBurst(ServerLevel level, Vec3 center) {
        for (int index = 0; index < 6; index++) {
            double spread = 0.16D + index * 0.12D;
            double y = center.y + 0.05D + index * 0.08D;
            double zOffset = (index % 2 == 0 ? 1.0D : -1.0D) * 0.05D;
            level.sendParticles(ParticleTypes.END_ROD, center.x - spread, y, center.z + zOffset, 2, 0.03D, 0.02D, 0.04D, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, center.x + spread, y, center.z - zOffset, 2, 0.03D, 0.02D, 0.04D, 0.03D);
            level.sendParticles(ParticleTypes.PORTAL, center.x - spread, y, center.z + zOffset, 3, 0.04D, 0.03D, 0.05D, 0.035D);
            level.sendParticles(ParticleTypes.PORTAL, center.x + spread, y, center.z - zOffset, 3, 0.04D, 0.03D, 0.05D, 0.035D);
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.12D, center.z, 6, 0.08D, 0.06D, 0.08D, 0.02D);
    }

    private static void ensureLecternLabel(ServerLevel level, BlockPos lecternPos, HyperBookData data) {
        List<ArmorStand> labels = findLecternLabels(level, lecternPos);
        ArmorStand label = labels.isEmpty() ? null : labels.getFirst();
        if (label == null) {
            label = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
            if (label == null) {
                return;
            }

            label.addTag(LABEL_TAG);
            label.addTag(labelKey(lecternPos));
            label.setInvisible(true);
            label.setNoGravity(true);
            label.setSilent(true);
            label.setInvulnerable(true);
            setArmorStandMarker(label, true);
            label.setNoBasePlate(true);
            label.setCustomNameVisible(true);
            level.addFreshEntity(label);
        }

        label.setPos(lecternPos.getX() + 0.5D, lecternPos.getY() + 0.98D, lecternPos.getZ() + 0.5D);
        label.setCustomName(buildLecternLabelText(level, lecternPos, data));
        trackLecternLabel(level, lecternPos);

        for (int index = 1; index < labels.size(); index++) {
            labels.get(index).discard();
        }
    }

    private static void removeLecternLabel(ServerLevel level, BlockPos lecternPos) {
        for (ArmorStand label : findLecternLabels(level, lecternPos)) {
            label.discard();
        }
        untrackLecternLabel(level, lecternPos);
    }

    private static void discardNearbyLecternLabels(ServerLevel level, BlockPos center, int radius) {
        AABB bounds = new AABB(center).inflate(radius, 8.0D, radius);
        for (ArmorStand label : level.getEntitiesOfClass(ArmorStand.class, bounds, entity -> entity.getTags().contains(LABEL_TAG))) {
            label.discard();
        }
    }

    private static List<ArmorStand> findLecternLabels(ServerLevel level, BlockPos lecternPos) {
        String labelKey = labelKey(lecternPos);
        AABB bounds = new AABB(
            lecternPos.getX() + 0.15D,
            lecternPos.getY() - 0.25D,
            lecternPos.getZ() + 0.15D,
            lecternPos.getX() + 0.85D,
            lecternPos.getY() + 1.6D,
            lecternPos.getZ() + 0.85D
        );
        return level.getEntitiesOfClass(ArmorStand.class, bounds, entity -> entity.getTags().contains(LABEL_TAG) && entity.getTags().contains(labelKey));
    }

    private static void pruneTrackedLecternLabels(ServerLevel level) {
        Set<BlockPos> trackedLecterns = TRACKED_LECTERN_LABELS.get(level.dimension());
        if (trackedLecterns == null || trackedLecterns.isEmpty()) {
            return;
        }

        for (BlockPos lecternPos : new ArrayList<>(trackedLecterns)) {
            if (!level.isLoaded(lecternPos)) {
                continue;
            }

            if (!(level.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) || !lecternHasStoredBook(lectern)) {
                logLecternLabelRemoval(level, lecternPos, "tracked lectern no longer has a book");
                removeLecternLabel(level, lecternPos);
                continue;
            }

            Optional<HyperBookData> data = getLecternHyperBookData(lectern);
            if (data.isEmpty()) {
                logLecternLabelRemoval(level, lecternPos, "tracked lectern book no longer resolves to hyperbook data");
                removeLecternLabel(level, lecternPos);
                continue;
            }

            ensureLecternLabel(level, lecternPos, data.get());
        }
    }

    private static void trackLecternLabel(ServerLevel level, BlockPos lecternPos) {
        TRACKED_LECTERN_LABELS.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet()).add(lecternPos.immutable());
    }

    private static Optional<HyperBookData> getLecternHyperBookData(LecternBlockEntity lectern) {
        Optional<HyperBookData> attachedData = lectern.getExistingData(VerseAttachments.HYPER_BOOK_LECTERN_LINK)
            .flatMap(HyperBookLecternLink::data);
        if (attachedData.isPresent()) {
            return attachedData;
        }

        Optional<HyperBookData> bookData = HyperBookData.from(lectern.getBook());
        bookData.ifPresent(data -> {
            VerseWorks.LOGGER.info("Recovered lectern hyperbook attachment from stored item: item={}, dimension={}", itemId(lectern.getBook()), data.dimensionId());
            setLecternHyperBookData(lectern, data);
            lectern.setChanged();
        });
        return bookData;
    }

    private static boolean isHyperBookLectern(LecternBlockEntity lectern) {
        return lectern.getBook().is(VerseItems.HYPER_BOOK.get()) || getLecternHyperBookData(lectern).isPresent();
    }

    private static void setLecternHyperBookData(LecternBlockEntity lectern, HyperBookData data) {
        HyperBookLecternLink link = lectern.getData(VerseAttachments.HYPER_BOOK_LECTERN_LINK);
        link.set(data);
    }

    private static void untrackLecternLabel(ServerLevel level, BlockPos lecternPos) {
        Set<BlockPos> trackedLecterns = TRACKED_LECTERN_LABELS.get(level.dimension());
        if (trackedLecterns == null) {
            return;
        }

        trackedLecterns.remove(lecternPos);
        if (trackedLecterns.isEmpty()) {
            TRACKED_LECTERN_LABELS.remove(level.dimension(), trackedLecterns);
        }
    }

    private static void setArmorStandMarker(ArmorStand armorStand, boolean marker) {
        byte flags = armorStand.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS);
        if (marker) {
            flags |= ArmorStand.CLIENT_FLAG_MARKER;
        } else {
            flags &= (byte) ~ArmorStand.CLIENT_FLAG_MARKER;
        }
        armorStand.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, flags);
        armorStand.refreshDimensions();
    }

    private static String labelKey(BlockPos lecternPos) {
        return LABEL_KEY_PREFIX + lecternPos.getX() + ":" + lecternPos.getY() + ":" + lecternPos.getZ();
    }

    private static Component buildLecternLabelText(ServerLevel level, BlockPos lecternPos, HyperBookData data) {
        Component base = Component.literal(data.dimensionName()).withStyle(style -> style.withColor(data.labelColorValue()));
        LecternVisualState visualState = lecternVisualState(level, lecternPos, data.dimensionId());
        if (visualState == LecternVisualState.TUNNELING) {
            return base.copy().append(Component.literal(" [Tunneling]").withStyle(ChatFormatting.YELLOW));
        }
        return base;
    }

    private static LecternVisualState lecternVisualState(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId) {
        PendingLecternCompletion completion = PENDING_LECTERN_COMPLETIONS
            .getOrDefault(level.dimension(), Map.of())
            .get(lecternPos);
        if (completion != null && completion.destinationId().equals(destinationId) && completion.notified()) {
            return LecternVisualState.READY;
        }

        PendingLecternWarmup warmup = PENDING_LECTERN_WARMUPS
            .getOrDefault(level.dimension(), Map.of())
            .get(lecternPos);
        if (warmup != null && warmup.destinationId().equals(destinationId)) {
            return LecternVisualState.TUNNELING;
        }

        if (completion != null && completion.destinationId().equals(destinationId)) {
            return LecternVisualState.TUNNELING;
        }

        return LecternVisualState.IDLE;
    }

    private static boolean isLecternTeleportReady(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId) {
        return lecternVisualState(level, lecternPos, destinationId) != LecternVisualState.TUNNELING;
    }

    private static void clearLecternTunnelState(ServerLevel level, BlockPos lecternPos, ResourceLocation destinationId) {
        Map<BlockPos, PendingLecternWarmup> pendingWarmups = PENDING_LECTERN_WARMUPS.get(level.dimension());
        if (pendingWarmups != null) {
            PendingLecternWarmup warmup = pendingWarmups.get(lecternPos);
            if (warmup != null && warmup.destinationId().equals(destinationId)) {
                pendingWarmups.remove(lecternPos);
                if (pendingWarmups.isEmpty()) {
                    PENDING_LECTERN_WARMUPS.remove(level.dimension(), pendingWarmups);
                }
            }
        }

        Map<BlockPos, PendingLecternCompletion> pendingCompletions = PENDING_LECTERN_COMPLETIONS.get(level.dimension());
        if (pendingCompletions != null) {
            PendingLecternCompletion completion = pendingCompletions.get(lecternPos);
            if (completion != null && completion.destinationId().equals(destinationId)) {
                pendingCompletions.remove(lecternPos);
                if (pendingCompletions.isEmpty()) {
                    PENDING_LECTERN_COMPLETIONS.remove(level.dimension(), pendingCompletions);
                }
            }
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryPlaceHyperBookOnLectern(event)) {
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern) || !lecternHasStoredBook(lectern)) {
            return;
        }

        if (!isHyperBookLectern(lectern)) {
            if (!event.getLevel().isClientSide()) {
                VerseWorks.LOGGER.info("Ignoring lectern interaction at {} because stored book is not recognized as a hyperbook. item={}, storedBookPresent={}", event.getPos(), itemId(lectern.getBook()), lecternHasStoredBook(lectern));
            }
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }

        VerseWorks.LOGGER.info("Intercepting hyperbook lectern interaction at {}. item={}, player={}", event.getPos(), itemId(lectern.getBook()), event.getEntity().getName().getString());

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Optional<HyperBookData> data = getLecternHyperBookData(lectern);
        if (data.isEmpty()) {
            VerseWorks.LOGGER.info("Lectern interaction at {} aborted because no HyperBookData was available after recognition", event.getPos());
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        tryUseHyperBook(player, ItemStack.EMPTY, data.get(), event.getPos());
    }

    public static boolean useHeldHyperBook(ServerPlayer player, ItemStack stack, HyperBookData data) {
        return tryUseHyperBook(player, stack, data, null);
    }

    private static boolean tryUseHyperBook(ServerPlayer player, ItemStack heldStack, HyperBookData data, BlockPos lecternPos) {
        ServerLevel sourceLevel = (ServerLevel) player.level();
        ServerLevel destination = resolveDestination(sourceLevel.getServer(), data.dimensionId(), player, lecternPos);
        if (destination == null) {
            VerseWorks.LOGGER.info("Hyperbook use for {} deferred because destination {} is not currently available", player.getGameProfile().getName(), data.dimensionId());
            return true;
        }

        Optional<Vec3> savedTarget = data.targetPosition();
        Optional<Vec3> preparedTarget = savedTarget.isPresent() ? savedTarget : VerseDimensionRuntimeHooks.preparedEntryArrival(destination);
        boolean waitingOnEntryWarmup = preparedTarget.isEmpty()
            && LiveDimensionInstantiator.isRuntimeLevel(destination)
            && !LiveDimensionInstantiator.isReadyForEntry(destination);
        boolean waitingOnPreparedEntry = !savedTarget.isPresent() && !VerseDimensionRuntimeHooks.isPreparedEntryReady(destination);
        boolean lecternWaitingForReadyClick = lecternPos != null && !isLecternTeleportReady(sourceLevel, lecternPos, data.dimensionId());
        if (waitingOnEntryWarmup || waitingOnPreparedEntry || !preparedTarget.isPresent() || lecternWaitingForReadyClick) {
            VerseWorks.LOGGER.info(
                "Hyperbook use for {} is waiting. destination={}, lecternPos={}, waitingOnEntryWarmup={}, waitingOnPreparedEntry={}, hasPreparedTarget={}, lecternWaitingForReadyClick={}",
                player.getGameProfile().getName(),
                data.dimensionId(),
                lecternPos,
                waitingOnEntryWarmup,
                waitingOnPreparedEntry,
                preparedTarget.isPresent(),
                lecternWaitingForReadyClick
            );
            if (lecternPos != null) {
                startLecternHyperBookTunneling(sourceLevel, player, lecternPos, data.dimensionId());
            } else {
                player.sendSystemMessage(Component.literal("The Hyperbook is currently tunneling through space-time, try again soon.").withStyle(ChatFormatting.YELLOW));
            }
            if (LiveDimensionInstantiator.isRuntimeLevel(destination) && !VerseDimensionRuntimeHooks.hasPendingEntryPreparation(destination)) {
                LiveDimensionInstantiator.requestEntryWarmup(destination);
            }
            return true;
        }

        if (preparedTarget.isPresent()) {
            Vec3 target = preparedTarget.get();
            float targetYRot = data.targetYRot() != null ? data.targetYRot() : player.getYRot();
            float targetXRot = data.targetXRot() != null ? data.targetXRot() : player.getXRot();
            if (lecternPos != null) {
                player.sendSystemMessage(Component.literal("Teleporting to " + data.dimensionName() + "...").withStyle(ChatFormatting.AQUA));
            }
            if (lecternPos != null) {
                clearLecternTunnelState(sourceLevel, lecternPos, data.dimensionId());
            }
            if (savedTarget.isEmpty()) {
                data = persistResolvedTarget(sourceLevel, lecternPos, heldStack, data, target, targetYRot, targetXRot);
            }
            VerseWorks.LOGGER.info("Hyperbook use for {} resolved destination {} with target {}", player.getGameProfile().getName(), data.dimensionId(), target);
            if (lecternPos == null && VerseDimensionRuntimeHooks.queueHyperBookTeleport(player, destination, target, targetYRot, targetXRot)) {
                startQueuedHyperBookWarmup(sourceLevel, player, null, data.dimensionId());
                VerseWorks.LOGGER.info("Queued warmup-based Hyperbook teleport for {} to {}", player.getGameProfile().getName(), data.dimensionId());
                return true;
            }

            if (lecternPos == null) {
                player.sendSystemMessage(Component.literal("Teleporting to " + data.dimensionName() + "...").withStyle(ChatFormatting.AQUA));
            }
            Vec3 departure = player.position();
            playTeleportDeparture(sourceLevel, departure);
            VerseDimensionRuntimeHooks.performHyperBookTeleport(player, destination, target, targetYRot, targetXRot);
            playTeleportArrival(destination, target);
            VerseWorks.LOGGER.info("Performed immediate Hyperbook teleport for {} to {}", player.getGameProfile().getName(), data.dimensionId());
            return true;
        }

        return true;
    }

    private static HyperBookData persistResolvedTarget(ServerLevel sourceLevel, BlockPos lecternPos, ItemStack heldStack, HyperBookData data, Vec3 target, float targetYRot, float targetXRot) {
        HyperBookData resolvedData = data.withResolvedTarget(target, targetYRot, targetXRot);

        if (lecternPos != null && sourceLevel.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern && lecternHasStoredBook(lectern)) {
            resolvedData.apply(lectern.getBook());
            setLecternHyperBookData(lectern, resolvedData);
            lectern.setChanged();
            return resolvedData;
        }

        if (heldStack != null && !heldStack.isEmpty()) {
            resolvedData.apply(heldStack);
        }

        return resolvedData;
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Set<BlockPos> debugCauldrons = DEBUG_CAULDRONS.get(level.dimension());
        if (debugCauldrons != null) {
            debugCauldrons.remove(event.getPos());
        }

        if (!level.getBlockState(event.getPos()).is(Blocks.LECTERN)) {
            return;
        }

        removeLecternLabel(level, event.getPos());
        Map<BlockPos, PendingLecternWarmup> pendingWarmups = PENDING_LECTERN_WARMUPS.get(level.dimension());
        if (pendingWarmups != null) {
            pendingWarmups.remove(event.getPos());
            if (pendingWarmups.isEmpty()) {
                PENDING_LECTERN_WARMUPS.remove(level.dimension(), pendingWarmups);
            }
        }

        Map<BlockPos, PendingLecternCompletion> pendingCompletions = PENDING_LECTERN_COMPLETIONS.get(level.dimension());
        if (pendingCompletions != null) {
            pendingCompletions.remove(event.getPos());
            if (pendingCompletions.isEmpty()) {
                PENDING_LECTERN_COMPLETIONS.remove(level.dimension(), pendingCompletions);
            }
        }
    }

    private static boolean tryPlaceHyperBookOnLectern(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.LECTERN)) {
            return false;
        }

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern) || lecternHasStoredBook(lectern)) {
            return false;
        }

        ItemStack heldStack = event.getItemStack();
        if (!heldStack.is(VerseItems.HYPER_BOOK.get())) {
            return false;
        }

        if (!event.getLevel().isClientSide()) {
            VerseWorks.LOGGER.info("Allowing vanilla lectern placement for hyperbook at {}. heldItem={}, player={}", event.getPos(), itemId(heldStack), event.getEntity().getName().getString());
        }

        return true;
    }

    private static void logLecternLabelRemoval(ServerLevel level, BlockPos lecternPos, String reason) {
        if (!TRACKED_LECTERN_LABELS.getOrDefault(level.dimension(), Set.of()).contains(lecternPos)) {
            return;
        }

        VerseWorks.LOGGER.info("Removing lectern label at {} in {} because {}", lecternPos, level.dimension().location(), reason);
    }

    private static boolean lecternHasStoredBook(LecternBlockEntity lectern) {
        return !lectern.getBook().isEmpty();
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static void triggerCorruptionPlacementWarning(ServerLevel level, ServerPlayer player, HyperBookData data) {
        Optional<VerseDimensionParameters> parameters = VerseDimensionCatalog.get(level.getServer(), data.dimensionId());
        if (parameters.isEmpty() || !parameters.get().hasCorruptions()) {
            return;
        }

        BlockPos strikePos = findStrikePosNear(level, player.blockPosition(), CORRUPTION_WARNING_MIN_RADIUS, CORRUPTION_WARNING_MAX_RADIUS);
        if (strikePos == null) {
            return;
        }

        LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(level);
        if (lightningBolt == null) {
            return;
        }

        lightningBolt.moveTo(Vec3.atBottomCenterOf(strikePos));
        lightningBolt.setCause(player);
        lightningBolt.setDamage(0.0F);
        level.addFreshEntity(lightningBolt);
    }

    private static BlockPos findStrikePosNear(ServerLevel level, BlockPos origin, int minRadius, int maxRadius) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 12; attempt++) {
            int radius = maxRadius <= minRadius ? minRadius : random.nextInt(minRadius, maxRadius + 1);
            int offsetX = random.nextInt(-radius, radius + 1);
            int offsetZ = random.nextInt(-radius, radius + 1);
            if (Math.abs(offsetX) < minRadius && Math.abs(offsetZ) < minRadius) {
                continue;
            }

            BlockPos candidate = origin.offset(offsetX, 0, offsetZ);
            BlockPos topPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, candidate);
            BlockPos strikePos = topPos.above();
            if (level.getChunkSource().getChunkNow(new net.minecraft.world.level.ChunkPos(strikePos).x, new net.minecraft.world.level.ChunkPos(strikePos).z) == null) {
                continue;
            }

            return strikePos;
        }

        return null;
    }

    private static ServerLevel resolveDestination(MinecraftServer server, ResourceLocation dimensionId, ServerPlayer player, BlockPos lecternPos) {
        return resolveDestination(server, dimensionId, player, lecternPos, true);
    }

    private static ServerLevel resolveDestination(MinecraftServer server, ResourceLocation dimensionId, ServerPlayer player, BlockPos lecternPos, boolean notifyPlayer) {
        long activationStartedAt = System.nanoTime();
        ServerLevel liveLevel = LiveDimensionInstantiator.findLevel(server, dimensionId).orElse(null);
        if (liveLevel != null) {
            if (lecternPos == null) {
                VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, liveLevel);
            }
            return liveLevel;
        }

        ServerLevel loadedLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (loadedLevel != null) {
            if (lecternPos == null) {
                VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, loadedLevel);
            }
            return loadedLevel;
        }

        Optional<VerseDimensionParameters> parameters = VerseDimensionCatalog.get(server, dimensionId);
        if (parameters.isEmpty()) {
            if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("That Hyper Book is no longer linked to an available dimension.").withStyle(ChatFormatting.RED));
            }
            return null;
        }

        if (!LiveDimensionInstantiator.isActivationInProgress(dimensionId)) {
            if (lecternPos != null) {
                startLecternHyperBookTunneling((ServerLevel) player.level(), player, lecternPos, dimensionId);
            } else if (notifyPlayer) {
                player.sendSystemMessage(Component.literal("The Hyperbook is currently tunneling through space-time, try again soon.").withStyle(ChatFormatting.YELLOW));
            }
            PENDING_CRAFTED_WARMUPS.removeIf(pending -> pending.dimensionId().equals(dimensionId));
            LiveDimensionInstantiator.activateAsync(server, dimensionId, parameters.get(), LiveDimensionInstantiator.ActivationMode.FIRST_USE_BLOCKING);
            logSlowStage("Started first-use Hyperbook activation", dimensionId, activationStartedAt);
            return null;
        }

        if (lecternPos != null) {
            startLecternHyperBookTunneling((ServerLevel) player.level(), player, lecternPos, dimensionId);
        } else if (notifyPlayer) {
            player.sendSystemMessage(Component.literal("The Hyperbook is currently tunneling through space-time, try again soon.").withStyle(ChatFormatting.YELLOW));
        }
        return null;
    }

    private static void playTeleportDeparture(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.9D, pos.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.9D, pos.z, 12, 0.2D, 0.2D, 0.2D, 0.03D);
        level.playSound(null, BlockPos.containing(pos), VerseSounds.ENTER_DIMENSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void startLecternHyperBookTunneling(ServerLevel level, ServerPlayer player, BlockPos lecternPos, ResourceLocation destinationId) {
        boolean alreadyTunneling = lecternVisualState(level, lecternPos, destinationId) == LecternVisualState.TUNNELING;
        queueLecternWarmupNotification(level, lecternPos, destinationId, player);
        if (!alreadyTunneling) {
            playLecternTunnelStartEffects(level, lecternPos);
        }
        player.sendSystemMessage(Component.literal("The Hyperbook is currently tunneling through space-time. You will be notified when it's broken through.").withStyle(ChatFormatting.YELLOW));
    }

    private static void playLecternTunnelStartEffects(ServerLevel level, BlockPos lecternPos) {
        Vec3 center = Vec3.atCenterOf(lecternPos).add(0.0D, 0.95D, 0.0D);
        level.playSound(null, lecternPos, VerseSounds.ENTER_DIMENSION.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 6, 0.1D, 0.1D, 0.1D, 0.01D);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 5, 0.08D, 0.1D, 0.08D, 0.015D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.18D, center.z, 7, 0.08D, 0.18D, 0.08D, 0.035D);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.24D, center.z, 3, 0.05D, 0.12D, 0.05D, 0.015D);
    }

    private static void startQueuedHyperBookWarmup(ServerLevel sourceLevel, ServerPlayer player, BlockPos lecternPos, ResourceLocation destinationId) {
        if (lecternPos != null) {
            queueLecternWarmupNotification(sourceLevel, lecternPos, destinationId, player);
        }

        Vec3 pos = player.position();
        sourceLevel.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.9D, pos.z, 12, 0.18D, 0.28D, 0.18D, 0.01D);
        sourceLevel.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.9D, pos.z, 10, 0.14D, 0.18D, 0.14D, 0.02D);
        player.sendSystemMessage(Component.literal("The Hyperbook is currently tunneling through space-time.").withStyle(ChatFormatting.YELLOW));
    }

    private static void playTeleportArrival(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.9D, pos.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.9D, pos.z, 12, 0.2D, 0.2D, 0.2D, 0.03D);
        level.playSound(null, BlockPos.containing(pos), VerseSounds.ENTERED_DIMENSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private record PendingRitualJob(
        BlockPos cauldronPos,
        String dimensionPath,
        String dimensionName,
        Integer labelColor,
        VerseDimensionParameters parameters,
        long seedOffset,
        ItemStack writtenBookStack,
        List<ItemStack> refundStacks,
        long queuedAtGameTime
    ) {
    }

    private record ActivePendingRitual(
        PendingRitualJob job,
        ResourceLocation dimensionId,
        VerseDimensionParameters parameters,
        CompletableFuture<Void> future
    ) {
    }

    private record CauldronIngredientState(String signature, long lastChangedAt) {
    }

    private record PendingLecternWarmup(ResourceLocation destinationId, Set<UUID> playerIds) {
    }

    private record PendingLecternCompletion(ResourceLocation destinationId, List<UUID> playerIds, long finishAtGameTime, boolean notified) {
        private PendingLecternCompletion withState(long finishAtGameTime, boolean notified) {
            return new PendingLecternCompletion(this.destinationId, this.playerIds, finishAtGameTime, notified);
        }
    }

    private record PendingCraftedWarmup(ResourceLocation dimensionId, VerseDimensionParameters parameters, long readyAtGameTime) {
    }

    private record ActiveCraftedWarmup(
        ResourceLocation dimensionId,
        CompletableFuture<LiveDimensionInstantiator.Result> future
    ) {
    }

    private enum LecternVisualState {
        IDLE,
        TUNNELING,
        READY
    }

    private static void logSlowStage(String action, ResourceLocation dimensionId, long startedAtNanos) {
        long elapsed = System.nanoTime() - startedAtNanos;
        if (elapsed < SLOW_RITUAL_STAGE_THRESHOLD_NANOS) {
            return;
        }

        VerseWorks.LOGGER.info("{} for {} took {} ms", action, dimensionId, elapsed / 1_000_000L);
    }

    private static final class RecipeOptions {
        private int skyColor = 0xFF73C2FB;
        private Block floorBlock = Blocks.GRASS_BLOCK;
        private Block sphereBlock;
        private VerseDimensionWorldType worldType = VerseDimensionWorldType.NORMAL;
        private final List<ResourceLocation> biomeIds = new ArrayList<>();
        private boolean explicitWorldType;
        private boolean explicitBiomes;
        private double gravityScale = 1.0D;
        private double dayRate = 1.0D;
        private Integer timeOfDay;
        private boolean explicitSkyColor;
        private boolean explicitFixedTime;
        private boolean permanentTime;
        private boolean permanentRain;
        private boolean permanentLightning;
        private boolean permanentStorm;
        private boolean meteorShowers;
        private boolean spawnWarp;
        private boolean preventPermanentTime;
        private boolean preventPermanentRain;
        private boolean preventPermanentLightning;
        private boolean preventPermanentStorm;
        private boolean preventMeteorShowers;
        private boolean preventSpawnWarp;
        private boolean preventGravity;
        private boolean preventSpheres;
        private boolean preventAllCorruptions;
        private boolean explicitGravity;
        private double oreMultiplier = 1.0D;
        private boolean spheres;
        private ResourceLocation fluidId;
        private boolean lakes;
        private Integer oceanLevel;
        private boolean structures;
        private boolean structuresExplicitlySet;
        private final List<VerseDimensionCorruption> corruptions = new ArrayList<>();
    }
}
