package com.kyden.verseworks.command;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.dimension.GeneratedDimensionPackWriter;
import com.kyden.verseworks.dimension.LiveDimensionInstantiator;
import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.dimension.VerseDimensionRuntimeHooks;
import com.kyden.verseworks.dimension.VerseDimensionWorldType;
import com.kyden.verseworks.entity.MeteorEntity;
import com.kyden.verseworks.item.HyperBookData;
import com.kyden.verseworks.item.VerseItems;
import com.kyden.verseworks.ritual.HyperBookRitualHooks;
import com.kyden.verseworks.util.VerseText;
import com.kyden.verseworks.worldgen.MysticRuinStructure;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class VerseWorksCommands {
    private static final int DEFAULT_SKY_COLOR = 0xFF73C2FB;
    private static final Object GENERATED_PACK_RELOAD_LOCK = new Object();
    private static volatile CompletableFuture<Void> generatedPackReloadFuture;
    private static final DynamicCommandExceptionType INVALID_DIMENSION_NAME = new DynamicCommandExceptionType(
        name -> Component.literal("Dimension names must resolve to a verseworks namespace id: " + name)
    );
    private static final DynamicCommandExceptionType DIMENSION_WRITE_FAILED = new DynamicCommandExceptionType(
        reason -> Component.literal("Failed to write dimension datapack: " + reason)
    );
    private static final DynamicCommandExceptionType LIVE_INSTANTIATION_FAILED = new DynamicCommandExceptionType(
        reason -> Component.literal("Live dimension instantiation failed: " + reason)
    );
    private static final DynamicCommandExceptionType LIVE_DIMENSION_NOT_READY = new DynamicCommandExceptionType(
        reason -> Component.literal("Live dimension is not ready yet: " + reason)
    );
    private static final DynamicCommandExceptionType DIMENSION_ENTRY_FAILED = new DynamicCommandExceptionType(
        reason -> Component.literal("Dimension entry failed: " + reason)
    );
    private static final DynamicCommandExceptionType INVALID_WORLD_TYPE = new DynamicCommandExceptionType(
        value -> Component.literal("Unknown world type: " + value + ". Use one of: " + String.join(", ", VerseDimensionWorldType.commandNames()))
    );
    private static final DynamicCommandExceptionType INVALID_BIOME = new DynamicCommandExceptionType(
        value -> Component.literal("Unknown biome: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_BIOME_CONFIGURATION = new DynamicCommandExceptionType(
        value -> Component.literal("Invalid biome configuration: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_DIMENSION = new DynamicCommandExceptionType(
        value -> Component.literal("Unknown dimension '" + value + "'")
    );
    private static final DynamicCommandExceptionType INVALID_OPTION = new DynamicCommandExceptionType(
        value -> Component.literal("Invalid create option: " + value)
    );
    private static final DynamicCommandExceptionType DIMENSION_ALREADY_EXISTS = new DynamicCommandExceptionType(
        value -> Component.literal("Dimension already exists: " + value)
    );
    private static final DynamicCommandExceptionType DIMENSION_CREATION_FAILED = new DynamicCommandExceptionType(
        value -> Component.literal("Dimension creation failed: " + value)
    );
    private static final DynamicCommandExceptionType CREATIVE_ONLY = new DynamicCommandExceptionType(
        value -> Component.literal("Creative flying speed can only be changed for creative-mode players: " + value)
    );
    private static final DynamicCommandExceptionType METEOR_SPAWN_FAILED = new DynamicCommandExceptionType(
        value -> Component.literal("Failed to spawn meteor: " + value)
    );
    private static final Set<String> CREATE_OPTION_KEYS = Set.of(
        "skycolor", "sky_color", "type", "world_type", "void", "floor", "floor_block", "biomes", "biome",
        "gravity", "gravity_scale", "dayrate", "day_rate", "time", "timeofday", "time_of_day",
        "permaday", "permanentday", "permanent_time", "fixedtime", "endlessrain", "endless_rain", "permanent_rain",
        "endlesslightning", "endless_lightning", "permanent_lightning",
        "endlessstorm", "endless_storm", "permanent_storm", "meteorshowers", "meteor_showers",
        "spawnwarp", "spawn_warp",
        "oremult", "ore_multiplier", "ores",
        "spheres", "sphereblock", "sphere_block", "sphere_material", "fluid", "lakes", "ocean", "oceanlevel", "ocean_level", "structures"
    );

    private VerseWorksCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("verseworks")
                .requires(source -> source.hasPermission(2))
                .then(createCommand(event))
                .then(
                    Commands.literal("enter")
                        .then(
                            Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(VerseWorksCommands::suggestKnownDimensions)
                                .executes(
                                    context -> enterDimension(
                                        context,
                                        java.util.List.of(context.getSource().getEntityOrException()),
                                        resolveDestination(context.getSource(), ResourceLocationArgument.getId(context, "dimension"))
                                    )
                                )
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .executes(
                                            context -> enterDimension(
                                                context,
                                                EntityArgument.getEntities(context, "targets"),
                                                resolveDestination(context.getSource(), ResourceLocationArgument.getId(context, "dimension"))
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("overworld")
                        .executes(context -> teleportToOverworld(context, java.util.List.of(context.getSource().getEntityOrException())))
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> teleportToOverworld(context, EntityArgument.getEntities(context, "targets")))
                        )
                )
                .then(
                    Commands.literal("checkbiome")
                        .executes(context -> checkBiome(context, java.util.List.of(context.getSource().getEntityOrException())))
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> checkBiome(context, EntityArgument.getEntities(context, "targets")))
                        )
                )
                .then(
                    Commands.literal("checkweather")
                        .executes(VerseWorksCommands::checkWeather)
                )
                .then(
                    Commands.literal("meteor")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> spawnMeteor(context, java.util.List.of(context.getSource().getEntityOrException())))
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> spawnMeteor(context, EntityArgument.getEntities(context, "targets")))
                        )
                )
                .then(
                    Commands.literal("speed")
                        .then(
                            Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0D))
                                .executes(context -> setCreativeFlightSpeed(
                                    context,
                                    java.util.List.of(context.getSource().getPlayerOrException()),
                                    DoubleArgumentType.getDouble(context, "multiplier")
                                ))
                                .then(
                                    Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> setCreativeFlightSpeed(
                                            context,
                                            EntityArgument.getPlayers(context, "targets"),
                                            DoubleArgumentType.getDouble(context, "multiplier")
                                        ))
                                )
                        )
                )
                .then(
                    Commands.literal("givebook")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(VerseWorksCommands::suggestKnownDimensions)
                                .executes(VerseWorksCommands::giveHyperBook)
                        )
                )
                .then(debugCommands())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommands() {
        return Commands.literal("debug")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("books").executes(VerseWorksCommands::giveDebugHyperBooks))
            .then(Commands.literal("cauldron").executes(VerseWorksCommands::spawnDebugCauldron))
            .then(Commands.literal("ruin").executes(VerseWorksCommands::spawnDebugRuin))
            .then(Commands.literal("loaded-dimensions").executes(VerseWorksCommands::debugLoadedDimensions))
            .then(Commands.literal("known-dimensions").executes(VerseWorksCommands::debugKnownDimensions));
    }

    private static int spawnDebugCauldron(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        BlockPos cauldronPos = player.blockPosition().below();
        HyperBookRitualHooks.createDebugCauldron(level, cauldronPos);
        context.getSource().sendSuccess(
            () -> Component.literal("Spawned a debug bubbling cauldron at " + cauldronPos.getX() + ", " + cauldronPos.getY() + ", " + cauldronPos.getZ()),
            true
        );
        return 1;
    }

    private static int spawnDebugRuin(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        BlockPos placedAt = MysticRuinStructure.placeDebug(level, player.blockPosition());
        context.getSource().sendSuccess(
            () -> Component.literal("Placed mystic ruin at " + placedAt.getX() + ", " + placedAt.getY() + ", " + placedAt.getZ()),
            true
        );
        return 1;
    }

    private static int debugLoadedDimensions(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<ServerLevel> loadedLevels = new ArrayList<>(server.forgeGetWorldMap().values().stream()
            .filter(VerseWorksCommands::isVerseWorksLevel)
            .toList());
        loadedLevels.sort(java.util.Comparator.comparing(level -> level.dimension().location().toString()));

        if (loadedLevels.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No VerseWorks dimensions are currently loaded."), false);
            return 0;
        }

        context.getSource().sendSuccess(
            () -> Component.literal("Loaded VerseWorks dimensions: " + loadedLevels.size()),
            false
        );

        for (ServerLevel level : loadedLevels) {
            context.getSource().sendSuccess(() -> describeLoadedDimension(level), false);
        }

        return loadedLevels.size();
    }

    private static int debugKnownDimensions(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        List<ResourceLocation> knownDimensions = new ArrayList<>(VerseDimensionCatalog.knownDimensionIds(server));
        knownDimensions.sort(java.util.Comparator.comparing(ResourceLocation::toString));

        if (knownDimensions.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No VerseWorks dimensions are currently known."), false);
            return 0;
        }

        context.getSource().sendSuccess(
            () -> Component.literal("Known VerseWorks dimensions: " + knownDimensions.size()),
            false
        );

        for (ResourceLocation dimensionId : knownDimensions) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            boolean loaded = level != null;
            boolean runtime = loaded && LiveDimensionInstantiator.isRuntimeLevel(level);
            int forcedChunks = loaded ? level.getForcedChunks().size() : 0;
                boolean restoreOnStartup = VerseDimensionRuntimeHooks.restoresOnStartup(server, dimensionId);
            context.getSource().sendSuccess(
                () -> Component.literal(
                    dimensionId
                        + " | loaded=" + loaded
                        + " | mode=" + (runtime ? "runtime" : (loaded ? "startup/datapack" : "dormant/metadata"))
                        + " | forcedChunks=" + forcedChunks
                            + " | restoreOnStartup=" + restoreOnStartup
                ),
                false
            );
        }

        return knownDimensions.size();
    }

    private static int giveDebugHyperBooks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        int givenBooks = 0;
        for (DebugDimensionDefinition definition : debugDimensionDefinitions()) {
            ensureDebugDimension(server, definition);
            ItemStack book = createDebugHyperBook(definition.dimensionId(), definition.label());
            if (!player.addItem(book)) {
                VerseWorks.LOGGER.info("Debug Hyperbook for {} did not fit in inventory, dropping near player {}", definition.dimensionId(), player.getGameProfile().getName());
                player.drop(book, false);
            } else {
                VerseWorks.LOGGER.info("Granted debug Hyperbook for {} to {}", definition.dimensionId(), player.getGameProfile().getName());
            }
            givenBooks++;
        }

        int createdBooks = givenBooks;
        context.getSource().sendSuccess(() -> Component.literal("Spawned " + createdBooks + " debug Hyperbooks in your inventory."), false);
        return createdBooks;
    }

    private static int giveHyperBook(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = context.getSource().getServer();
        ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
        VerseDimensionCatalog.get(server, dimensionId)
            .orElseThrow(() -> UNKNOWN_DIMENSION.create(dimensionId));
        String dimensionName = VerseText.displayDimensionName(dimensionId);
        ItemStack book = createDebugHyperBook(dimensionId, dimensionName);
        if (!player.addItem(book)) {
            VerseWorks.LOGGER.info("Hyperbook for {} did not fit in inventory, dropping near player {}", dimensionId, player.getGameProfile().getName());
            player.drop(book, false);
        } else {
            VerseWorks.LOGGER.info("Granted Hyperbook for {} to {}", dimensionId, player.getGameProfile().getName());
        }

        context.getSource().sendSuccess(() -> Component.literal("Gave Hyperbook for " + dimensionId + "."), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createCommand(RegisterCommandsEvent event) {
        return Commands.literal("create")
            .then(
                Commands.argument("name", StringArgumentType.word())
                    .executes(VerseWorksCommands::createDimension)
                    .then(Commands.argument("options", StringArgumentType.greedyString()).executes(VerseWorksCommands::createDimension))
            );
    }

    private static int createDimension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation dimensionId = parseVerseWorksDimensionId(StringArgumentType.getString(context, "name"));
        MinecraftServer server = context.getSource().getServer();
        ensureDimensionDoesNotExist(server, dimensionId);
        VerseDimensionParameters parameters = readParameters(context, server).withSeedOffset(GeneratedDimensionPackWriter.defaultSeedOffset(dimensionId));

        try {
            GeneratedDimensionPackWriter.WriteResult result = GeneratedDimensionPackWriter.writeDimension(server, dimensionId, parameters);
            VerseDimensionCatalog.remember(dimensionId, parameters);

            context.getSource().sendSuccess(
                () -> Component.literal(
                    "Created dimension " + dimensionId
                            + " with persisted metadata in " + GeneratedDimensionPackWriter.METADATA_PACK_ID
                        + " using " + parameters.rulesSummary()
                        + " with biomes " + parameters.biomeSummary()
                        + (parameters.worldType().isFlat() ? " and floor " + GeneratedDimensionPackWriter.floorBlockId(parameters.floorBlock()) : "")
                            + ". It will only return to the startup-active pack after a save while still loaded. Use /verseworks enter or a Hyperbook to activate it when needed."
                ),
                true
            );
            VerseWorks.LOGGER.info("Generated dormant dimension {} at {}", dimensionId, result.packRoot());
        } catch (IOException exception) {
            throw DIMENSION_WRITE_FAILED.create(exception.getMessage());
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Unexpected VerseWorks dimension creation failure for {}", dimensionId, exception);
            throw DIMENSION_CREATION_FAILED.create(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }

        return 1;
    }

    private static void ensureDimensionDoesNotExist(MinecraftServer server, ResourceLocation dimensionId) throws CommandSyntaxException {
        if (LiveDimensionInstantiator.findLevel(server, dimensionId).isPresent()
            || VerseDimensionCatalog.knownDimensionIds(server).contains(dimensionId)) {
            throw DIMENSION_ALREADY_EXISTS.create(dimensionId);
        }
    }

    private static boolean isGeneratedPackSelected(MinecraftServer server) {
        return server.getPackRepository().getSelectedIds().contains(GeneratedDimensionPackWriter.PACK_ID);
    }

    private static void enableGeneratedPack(MinecraftServer server) {
        PackRepository packRepository = server.getPackRepository();
        packRepository.reload();

        boolean changed = packRepository.addPack(GeneratedDimensionPackWriter.PACK_ID);
        if (changed) {
            server.reloadResources(packRepository.getSelectedIds()).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    VerseWorks.LOGGER.error("Failed to enable generated dimension pack {}", GeneratedDimensionPackWriter.PACK_ID, throwable);
                }
            });
        }
    }

    private static CompletableFuture<Void> queueGeneratedPackReload(MinecraftServer server, CommandSourceStack source, ResourceLocation focusDimension) {
        synchronized (GENERATED_PACK_RELOAD_LOCK) {
            if (generatedPackReloadFuture != null && !generatedPackReloadFuture.isDone()) {
                return generatedPackReloadFuture;
            }

            PackRepository packRepository = server.getPackRepository();
            packRepository.reload();
            if (!packRepository.getSelectedIds().contains(GeneratedDimensionPackWriter.PACK_ID)) {
                packRepository.addPack(GeneratedDimensionPackWriter.PACK_ID);
            }

            CompletableFuture<Void> future = server.reloadResources(List.copyOf(packRepository.getSelectedIds()));
            generatedPackReloadFuture = future;
            future.whenComplete((ignored, throwable) -> {
                synchronized (GENERATED_PACK_RELOAD_LOCK) {
                    if (generatedPackReloadFuture == future) {
                        generatedPackReloadFuture = null;
                    }
                }

                server.execute(() -> {
                    if (throwable != null) {
                        VerseWorks.LOGGER.error("Failed to reload generated dimension pack {}", GeneratedDimensionPackWriter.PACK_ID, throwable);
                        source.sendFailure(Component.literal("Generated dimension reload failed: " + throwable.getMessage()));
                        return;
                    }

                    VerseDimensionCatalog.ensureLoaded(server);
                    if (focusDimension != null) {
                        boolean levelLoaded = server.getLevel(ResourceKey.create(Registries.DIMENSION, focusDimension)) != null;
                        boolean stemRegistered = LiveDimensionInstantiator.hasRegisteredLevelStem(server, focusDimension);
                        if (levelLoaded || stemRegistered) {
                            source.sendSuccess(() -> Component.literal("Reload complete. " + focusDimension + " is registered; run /verseworks enter again."), false);
                        } else {
                            source.sendFailure(Component.literal("Reload complete, but NeoForge did not register " + focusDimension + " into the running server. Re-entering this world or restarting the server is required for that new dimension."));
                        }
                    } else {
                        source.sendSuccess(() -> Component.literal("Reload complete. Generated VerseWorks dimensions are now refreshed."), false);
                    }
                });
            });
            return future;
        }
    }

    private static boolean isGeneratedPackReloadInProgress() {
        CompletableFuture<Void> future = generatedPackReloadFuture;
        return future != null && !future.isDone();
    }

    private static LiveDimensionInstantiator.Result tryLiveInstantiation(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws CommandSyntaxException {
        try {
            return LiveDimensionInstantiator.instantiate(server, dimensionId, parameters);
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Live instantiation prototype failed for {}", dimensionId, exception);
            throw LIVE_INSTANTIATION_FAILED.create(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private static VerseDimensionParameters readParameters(CommandContext<CommandSourceStack> context, MinecraftServer server) throws CommandSyntaxException {
        CreateOptions options = new CreateOptions();
        if (hasArgument(context, "options", String.class)) {
            applyOptions(options, StringArgumentType.getString(context, "options"));
        }

        if (!options.seenKeys.contains("structures")) {
            options.structures = false;
        }

        if (options.worldType.isVoid() && !options.seenKeys.contains("structures")) {
            options.structures = false;
        }

        if (options.permanentTime && options.timeOfDay == null) {
            options.timeOfDay = 1000;
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
            List.of(),
            0L
        );
    }

    private static VerseDimensionParameters ensureDebugDimension(MinecraftServer server, DebugDimensionDefinition definition) throws CommandSyntaxException {
        VerseDimensionParameters existing = VerseDimensionCatalog.get(server, definition.dimensionId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        VerseDimensionParameters parameters = new VerseDimensionParameters(
            DEFAULT_SKY_COLOR,
            Blocks.GRASS_BLOCK,
            null,
            definition.worldType(),
            List.of(ResourceLocation.withDefaultNamespace("plains")),
            1.0D,
            1.0D,
            null,
            false,
            false,
            false,
            false,
            false,
            false,
            1.0D,
            false,
            null,
            false,
            null,
            false,
            List.of(),
            GeneratedDimensionPackWriter.defaultSeedOffset(definition.dimensionId())
        );

        try {
            GeneratedDimensionPackWriter.writeDimension(server, definition.dimensionId(), parameters);
            VerseDimensionCatalog.remember(definition.dimensionId(), parameters);
            return parameters;
        } catch (IOException exception) {
            throw DIMENSION_WRITE_FAILED.create(exception.getMessage());
        }
    }

    private static List<DebugDimensionDefinition> debugDimensionDefinitions() {
        return List.of(
            new DebugDimensionDefinition(ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "debug_flat"), "Debug Flat", VerseDimensionWorldType.FLAT),
            new DebugDimensionDefinition(ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "debug_void"), "Debug Void", VerseDimensionWorldType.VOID),
            new DebugDimensionDefinition(ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "debug_sky"), "Debug Sky", VerseDimensionWorldType.SKY_ISLAND),
            new DebugDimensionDefinition(ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "debug_normal"), "Debug Normal", VerseDimensionWorldType.NORMAL),
            new DebugDimensionDefinition(ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "debug_amplified"), "Debug Amplified", VerseDimensionWorldType.AMPLIFIED)
        );
    }

    private static ItemStack createDebugHyperBook(ResourceLocation dimensionId, String dimensionName) {
        ItemStack hyperBook = new ItemStack(VerseItems.HYPER_BOOK.get());
        hyperBook.set(DataComponents.WRITTEN_BOOK_CONTENT, createDebugWrittenBookContent(dimensionName, dimensionId));
        hyperBook.set(DataComponents.CUSTOM_NAME, Component.literal("Hyperbook - " + dimensionName).withStyle(ChatFormatting.LIGHT_PURPLE));
        return new HyperBookData(dimensionId, dimensionName).apply(hyperBook);
    }

    private static WrittenBookContent createDebugWrittenBookContent(String dimensionName, ResourceLocation dimensionId) {
        String title = dimensionName.length() > 32 ? dimensionName.substring(0, 32) : dimensionName;
        String pageText = "Debug link to " + dimensionName + " (" + dimensionId + ")";
        return new WrittenBookContent(
            Filterable.passThrough(title),
            "VerseWorks",
            0,
            List.of(Filterable.passThrough(Component.literal(pageText))),
            true
        );
    }

    private static void applyOptions(CreateOptions options, String optionString) throws CommandSyntaxException {
        List<String> tokens = Arrays.stream(optionString.trim().split("\\s+"))
            .filter(token -> !token.isBlank())
            .toList();
        for (int index = 0; index < tokens.size();) {
            String token = tokens.get(index++);
            String key;
            String value;
            int separator = token.indexOf('=');
            if (separator > 0) {
                key = token.substring(0, separator);
                value = token.substring(separator + 1);
                if (value.isBlank()) {
                    throw INVALID_OPTION.create("Missing value for '" + key + "'");
                }
            } else {
                key = token;
                if (index >= tokens.size()) {
                    throw INVALID_OPTION.create("Missing value for '" + key + "'");
                }
                value = tokens.get(index++);
            }

            applyOption(options, key, value);
        }
    }

    private static void applyOption(CreateOptions options, String rawKey, String value) throws CommandSyntaxException {
        String key = rawKey.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!options.seenKeys.add(key)) {
            throw INVALID_OPTION.create("Duplicate option '" + rawKey + "'");
        }
        switch (key) {
            case "skycolor", "sky_color" -> options.skyColor = parseHexColor(value);
            case "type", "world_type" -> options.worldType = parseWorldType(value);
            case "void" -> {
                if (parseBoolean(value)) {
                    options.worldType = VerseDimensionWorldType.VOID;
                }
            }
            case "floor", "floor_block" -> options.floorBlock = parseBlock(value);
            case "biomes", "biome" -> options.biomeIds = parseBiomes(value);
            case "gravity", "gravity_scale" -> options.gravityScale = parseDouble(value, 0.05D, 4.0D, "gravity");
            case "dayrate", "day_rate" -> options.dayRate = parseDouble(value, 0.0D, 64.0D, "dayrate");
            case "time", "timeofday", "time_of_day" -> options.timeOfDay = parseInt(value, 0, 23999, "time");
            case "permaday", "permanentday", "permanent_time", "fixedtime" -> options.permanentTime = parseBoolean(value);
            case "endlessrain", "endless_rain", "permanent_rain" -> options.permanentRain = parseBoolean(value);
            case "endlesslightning", "endless_lightning", "permanent_lightning" -> options.permanentLightning = parseBoolean(value);
            case "endlessstorm", "endless_storm", "permanent_storm" -> options.permanentStorm = parseBoolean(value);
            case "meteorshowers", "meteor_showers" -> options.meteorShowers = parseBoolean(value);
            case "spawnwarp", "spawn_warp" -> options.spawnWarp = parseBoolean(value);
            case "oremult", "ore_multiplier", "ores" -> options.oreMultiplier = parseDouble(value, 0.0D, 8.0D, "ore multiplier");
            case "spheres" -> options.spheres = parseBoolean(value);
            case "sphereblock", "sphere_block", "sphere_material" -> {
                options.sphereBlock = parseBlock(value);
                options.spheres = true;
            }
            case "fluid" -> options.fluidId = parseResourceLocation(value, "fluid");
            case "lakes" -> options.lakes = parseBoolean(value);
            case "ocean", "oceanlevel", "ocean_level" -> options.oceanLevel = parseInt(value, -64, 320, "ocean level");
            case "structures" -> options.structures = parseBoolean(value);
            default -> throw INVALID_OPTION.create("Unknown option '" + rawKey + "'");
        }

        if (options.permanentStorm) {
            options.permanentRain = true;
        }
    }

    private static void validateParameters(MinecraftServer server, CreateOptions options) throws CommandSyntaxException {
        if (options.worldType.isFlat() && options.biomeIds.size() > 1) {
            throw INVALID_BIOME_CONFIGURATION.create("flat and void worlds support at most one biome");
        }

        var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        for (ResourceLocation biomeId : options.biomeIds) {
            ResourceKey<net.minecraft.world.level.biome.Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
            if (biomeRegistry.get(biomeKey).isEmpty()) {
                throw INVALID_BIOME.create(biomeId);
            }
        }

        if (options.fluidId != null) {
            var fluid = BuiltInRegistries.FLUID.getOptional(options.fluidId)
                .orElseThrow(() -> INVALID_OPTION.create("Unknown fluid '" + options.fluidId + "'"));
            if (fluid.defaultFluidState().createLegacyBlock().isAir()) {
                throw INVALID_OPTION.create("Fluid '" + options.fluidId + "' cannot be placed as a world block");
            }
        }

        if (!options.structures && !options.worldType.isFlat()) {
            VerseWorks.LOGGER.info("VerseWorks live dimensions will suppress structures unless explicitly enabled with structures=true");
        }
    }

    private static <T> boolean hasArgument(CommandContext<CommandSourceStack> context, String name, Class<T> type) {
        try {
            context.getArgument(name, type);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static CompletableFuture<Suggestions> suggestKnownDimensions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            VerseDimensionCatalog.knownDimensionIds(context.getSource().getServer()).stream().map(ResourceLocation::toString),
            builder
        );
    }

    private static ResourceLocation parseVerseWorksDimensionId(String requestedName) throws CommandSyntaxException {
        ResourceLocation dimensionId = ResourceLocation.tryParse(VerseWorks.MODID + ":" + requestedName);
        if (dimensionId == null || !VerseWorks.MODID.equals(dimensionId.getNamespace()) || StringUtil.isBlank(dimensionId.getPath())) {
            throw INVALID_DIMENSION_NAME.create(requestedName);
        }
        return dimensionId;
    }

    private static ServerLevel resolveDestination(CommandSourceStack source, ResourceLocation dimensionId) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        ServerLevel loadedLevel = LiveDimensionInstantiator.findLevel(server, dimensionId).orElse(null);
        if (loadedLevel != null) {
            VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, loadedLevel);
            return loadedLevel;
        }

        loadedLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (loadedLevel != null) {
            VerseDimensionRuntimeHooks.ensureEntryPreparationScheduled(server, loadedLevel);
            return loadedLevel;
        }

        if (VerseDimensionCatalog.get(server, dimensionId).isEmpty()) {
            throw UNKNOWN_DIMENSION.create(dimensionId);
        }

        VerseDimensionParameters parameters = VerseDimensionCatalog.get(server, dimensionId)
            .orElseThrow(() -> UNKNOWN_DIMENSION.create(dimensionId));

        if (!LiveDimensionInstantiator.isActivationInProgress(dimensionId)) {
            LiveDimensionInstantiator.activateAsync(server, dimensionId, parameters);
            throw LIVE_DIMENSION_NOT_READY.create(
                dimensionId + " is activating in the background. Run /verseworks enter again in a moment"
            );
        }

        throw LIVE_DIMENSION_NOT_READY.create(
            dimensionId + " is still activating in the background. Run /verseworks enter again in a moment"
        );
    }

    private static String syntaxReason(CommandSyntaxException exception) {
        return exception.getRawMessage() != null ? exception.getRawMessage().getString() : exception.getMessage();
    }

    private static int enterDimension(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets, ServerLevel destination) throws CommandSyntaxException {
        if (LiveDimensionInstantiator.isRuntimeLevel(destination) && !VerseDimensionRuntimeHooks.hasPendingEntryPreparation(destination)) {
            LiveDimensionInstantiator.requestEntryWarmup(destination);
        }

        if (LiveDimensionInstantiator.isRuntimeLevel(destination) && !LiveDimensionInstantiator.isReadyForEntry(destination)) {
            throw LIVE_DIMENSION_NOT_READY.create(
                destination.dimension().location() + " is still warming its entry area; "
                    + LiveDimensionInstantiator.warmupDetail(destination.dimension())
                    + ". Try /verseworks enter again in a moment"
            );
        }

        int moved = 0;
        try {
            for (Entity target : targets) {
                teleport(target, destination);
                moved++;
            }
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Failed to enter dimension {}", destination.dimension().location(), exception);
            throw DIMENSION_ENTRY_FAILED.create(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }

        final int movedCount = moved;
        String dimensionName = destination.dimension().location().toString();
        if (movedCount == 1) {
            Entity target = targets.iterator().next();
            context.getSource().sendSuccess(() -> Component.literal("Teleported " + target.getName().getString() + " to " + dimensionName), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Teleported %d entities to %s", movedCount, dimensionName)), true);
        }
        return movedCount;
    }

    private static int checkBiome(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) {
        int reported = 0;
        for (Entity target : targets) {
            BlockPos position = BlockPos.containing(target.position());
            ResourceLocation biomeId = target.level()
                .getBiome(position)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.withDefaultNamespace("unknown"));

            context.getSource().sendSuccess(
                () -> Component.literal(
                    target.getName().getString()
                        + " is in biome " + biomeId
                        + " at " + position.getX() + ", " + position.getY() + ", " + position.getZ()
                        + " in " + target.level().dimension().location()
                ),
                false
            );
            reported++;
        }

        return reported;
    }

    private static int checkWeather(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        VerseDimensionParameters parameters = VerseDimensionCatalog.get(level.getServer(), level.dimension().location()).orElse(null);
        context.getSource().sendSuccess(
            () -> Component.literal(
                "Weather in " + level.dimension().location()
                    + ": raining=" + level.isRaining()
                    + ", thundering=" + level.isThundering()
                    + ", rainLevel=" + String.format(Locale.ROOT, "%.2f", level.getRainLevel(1.0F))
                    + ", thunderLevel=" + String.format(Locale.ROOT, "%.2f", level.getThunderLevel(1.0F))
                    + (parameters == null ? "" : ", configuredRain=" + parameters.permanentRain() + ", configuredLightning=" + parameters.permanentLightning() + ", configuredStorm=" + parameters.permanentStorm() + ", meteorShowers=" + parameters.meteorShowers() + ", spawnWarp=" + parameters.spawnWarp())
            ),
            false
        );
        return 1;
    }

    private static int setCreativeFlightSpeed(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, double multiplier) throws CommandSyntaxException {
        float flyingSpeed = (float) (0.05D * multiplier);
        int updated = 0;

        for (ServerPlayer player : targets) {
            if (!player.isCreative()) {
                throw CREATIVE_ONLY.create(player.getName().getString());
            }

            player.getAbilities().setFlyingSpeed(flyingSpeed);
            player.onUpdateAbilities();
            updated++;
        }

        final int updatedCount = updated;
        final String multiplierText = Double.toString(multiplier);
        final String flyingSpeedText = String.format(Locale.ROOT, "%.3f", flyingSpeed);
        if (updatedCount == 1) {
            ServerPlayer player = targets.iterator().next();
            context.getSource().sendSuccess(
                () -> Component.literal("Set creative flight speed for " + player.getName().getString() + " to x" + multiplierText + " (" + flyingSpeedText + ")"),
                true
            );
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Set creative flight speed for " + updatedCount + " players to x" + multiplierText + " (" + flyingSpeedText + ")"),
                true
            );
        }

        return updatedCount;
    }

    private static int teleportToOverworld(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) throws CommandSyntaxException {
        ServerLevel overworld = context.getSource().getServer().overworld();
        VerseDimensionRuntimeHooks.RespawnTarget respawnData = VerseDimensionRuntimeHooks.overworldRespawnData(context.getSource().getServer());
        Vec3 destination = Vec3.atBottomCenterOf(respawnData.pos());
        int moved = 0;

        try {
            for (Entity target : targets) {
                target.teleportTo(overworld, destination.x(), destination.y(), destination.z(), Set.<RelativeMovement>of(), respawnData.yaw(), respawnData.pitch());
                moved++;
            }
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Failed to teleport entities to the overworld", exception);
            throw DIMENSION_ENTRY_FAILED.create(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }

        final int movedCount = moved;
        if (movedCount == 1) {
            Entity target = targets.iterator().next();
            context.getSource().sendSuccess(() -> Component.literal("Teleported " + target.getName().getString() + " to the overworld"), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Teleported " + movedCount + " entities to the overworld"), true);
        }
        return movedCount;
    }

    private static int spawnMeteor(CommandContext<CommandSourceStack> context, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int spawned = 0;

        for (Entity target : targets) {
            if (!(target.level() instanceof ServerLevel serverLevel)) {
                throw METEOR_SPAWN_FAILED.create("target is not in a server level");
            }

            MeteorEntity meteor = MeteorEntity.spawnToward(serverLevel, target.position());
            if (!serverLevel.addFreshEntity(meteor)) {
                throw METEOR_SPAWN_FAILED.create("server rejected meteor for " + target.getName().getString());
            }
            VerseDimensionRuntimeHooks.playMeteorEnterCue(serverLevel);
            spawned++;
        }

        final int spawnCount = spawned;
        if (spawnCount == 1) {
            Entity target = targets.iterator().next();
            context.getSource().sendSuccess(
                () -> Component.literal("Summoned a meteor above " + target.getName().getString()),
                true
            );
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Summoned meteors above " + spawnCount + " targets"),
                true
            );
        }

        return spawnCount;
    }

    private static void teleport(Entity target, ServerLevel destination) {
        Vec3 arrival = VerseDimensionRuntimeHooks.findSafeArrival(destination);
        target.teleportTo(destination, arrival.x(), arrival.y(), arrival.z(), Set.<RelativeMovement>of(), target.getYRot(), target.getXRot());
        if (target instanceof net.minecraft.server.level.ServerPlayer player) {
            VerseDimensionRuntimeHooks.registerPendingPlayerEntry(player, destination, arrival);
        }
    }

    private static Component describeLoadedDimension(ServerLevel level) {
        boolean runtime = LiveDimensionInstantiator.isRuntimeLevel(level);
        boolean sleeping = VerseDimensionRuntimeHooks.isDimensionSleeping(level);
            boolean pinned = VerseDimensionRuntimeHooks.isDimensionPinned(level);
        boolean pending = VerseDimensionRuntimeHooks.hasPendingActivity(level);
        boolean prepared = VerseDimensionRuntimeHooks.preparedEntryArrival(level).isPresent();
        boolean entryReady = VerseDimensionRuntimeHooks.isPreparedEntryReady(level);
        long idleTicks = VerseDimensionRuntimeHooks.idleTicks(level);
        int forcedChunks = level.getForcedChunks().size();
            boolean restoreOnStartup = VerseDimensionRuntimeHooks.restoresOnStartup(level.getServer(), level.dimension().location());
        String warmup = runtime ? LiveDimensionInstantiator.warmupDetail(level.dimension()) : "startup/datapack";
        return Component.literal(
            level.dimension().location()
                + " | mode=" + (runtime ? "runtime" : "startup/datapack")
                + " | sleeping=" + sleeping
                    + " | pinned=" + pinned
                + " | players=" + level.players().size()
                + " | forcedChunks=" + forcedChunks
                + " | pending=" + pending
                + " | prepared=" + prepared
                + " | entryReady=" + entryReady
                + " | idleTicks=" + idleTicks
                    + " | restoreOnStartup=" + restoreOnStartup
                + " | warmup=" + warmup
        );
    }

    private static boolean isVerseWorksLevel(ServerLevel level) {
        return VerseWorks.MODID.equals(level.dimension().location().getNamespace());
    }

    private static int parseHexColor(String input) throws CommandSyntaxException {
        String normalized = input.startsWith("#") ? input.substring(1) : input;
        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException exception) {
            throw INVALID_OPTION.create("Invalid hex color '" + input + "'");
        }
    }

    private static VerseDimensionWorldType parseWorldType(String input) throws CommandSyntaxException {
        try {
            return VerseDimensionWorldType.parse(input);
        } catch (IllegalArgumentException exception) {
            throw INVALID_WORLD_TYPE.create(input);
        }
    }

    private static Block parseBlock(String input) throws CommandSyntaxException {
        ResourceLocation blockId = parseResourceLocation(input, "block");
        return BuiltInRegistries.BLOCK.getOptional(blockId)
            .orElseThrow(() -> INVALID_OPTION.create("Unknown block '" + input + "'"));
    }

    private static List<ResourceLocation> parseBiomes(String input) throws CommandSyntaxException {
        if (input.isBlank()) {
            return List.of();
        }

        List<ResourceLocation> biomeIds = new ArrayList<>();
        for (String token : input.split(",")) {
            String trimmed = token.trim();
            ResourceLocation biomeId = ResourceLocation.tryParse(trimmed);
            if (biomeId == null) {
                throw INVALID_BIOME.create(trimmed);
            }
            biomeIds.add(biomeId);
        }
        return List.copyOf(biomeIds);
    }

    private static ResourceLocation parseResourceLocation(String input, String label) throws CommandSyntaxException {
        ResourceLocation identifier = ResourceLocation.tryParse(input);
        if (identifier == null) {
            throw INVALID_OPTION.create("Invalid " + label + " id '" + input + "'");
        }
        return identifier;
    }

    private static boolean parseBoolean(String input) throws CommandSyntaxException {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw INVALID_OPTION.create("Invalid boolean '" + input + "'");
        };
    }

    private static double parseDouble(String input, double min, double max, String label) throws CommandSyntaxException {
        try {
            double value = Double.parseDouble(input);
            if (value < min || value > max) {
                throw INVALID_OPTION.create(label + " must be between " + min + " and " + max);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw INVALID_OPTION.create("Invalid number for " + label + ": '" + input + "'");
        }
    }

    private static int parseInt(String input, int min, int max, String label) throws CommandSyntaxException {
        try {
            int value = Integer.parseInt(input);
            if (value < min || value > max) {
                throw INVALID_OPTION.create(label + " must be between " + min + " and " + max);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw INVALID_OPTION.create("Invalid integer for " + label + ": '" + input + "'");
        }
    }

    private static final class CreateOptions {
        private int skyColor = DEFAULT_SKY_COLOR;
        private Block floorBlock = Blocks.GRASS_BLOCK;
        private Block sphereBlock;
        private VerseDimensionWorldType worldType = VerseDimensionWorldType.NORMAL;
        private List<ResourceLocation> biomeIds = List.of();
        private double gravityScale = 1.0D;
        private double dayRate = 1.0D;
        private Integer timeOfDay;
        private boolean permanentTime;
        private boolean permanentRain;
        private boolean permanentLightning;
        private boolean permanentStorm;
        private boolean meteorShowers;
        private boolean spawnWarp;
        private double oreMultiplier = 1.0D;
        private boolean spheres;
        private ResourceLocation fluidId;
        private boolean lakes;
        private Integer oceanLevel;
        private boolean structures;
        private final Set<String> seenKeys = new HashSet<>();
    }

    private record DebugDimensionDefinition(ResourceLocation dimensionId, String label, VerseDimensionWorldType worldType) {
    }
}
