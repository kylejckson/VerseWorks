package com.kyden.verseworks.dimension;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kyden.verseworks.VerseWorks;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class GeneratedDimensionPackWriter {
    // Dimension parameters live in a metadata-only pack so worlds can remember authored settings
    // without forcing every generated dimension to stay active across restarts.
    public static final String PACK_DIRECTORY_NAME = "verseworks_generated_dimensions";
    public static final String PACK_ID = "file/" + PACK_DIRECTORY_NAME;
    public static final String METADATA_PACK_DIRECTORY_NAME = "verseworks_dimension_metadata";
    public static final String METADATA_PACK_ID = "file/" + METADATA_PACK_DIRECTORY_NAME;
    public static final String DEFAULT_FLOOR_BLOCK_ID = "minecraft:grass_block";
    public static final ResourceLocation TERRAIN_GENERATOR_ID = ResourceLocation.tryParse(VerseWorks.MODID + ":terrain");
    private static final String PARAMETER_DIRECTORY = "verseworks_dimension";
    private static final int DATA_PACK_FORMAT = 75;
    private static final int DATA_PACK_FORMAT_MAX = 81;

    private GeneratedDimensionPackWriter() {
    }

    public static WriteResult writeDimension(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws IOException {
        return writeDimension(server, dimensionId, parameters, false);
    }

    public static WriteResult writeDimension(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters, boolean restoreOnStartup) throws IOException {
        VerseDimensionParameters effectiveParameters = sanitizeParameters(server, dimensionId, parameters);
        Path metadataPackRoot = metadataPackRoot(server);
        Path parameterPath = metadataParameterPath(server, dimensionId);
        Path dimensionTypePath = activeDimensionTypePath(server, dimensionId);
        Path dimensionPath = activeDimensionPath(server, dimensionId);

        Files.createDirectories(parameterPath.getParent());
        writeUtf8(metadataPackRoot.resolve("pack.mcmeta"), metadataPackMeta());
        writeUtf8(parameterPath, parametersJson(effectiveParameters));

        if (restoreOnStartup) {
            writeActiveDimension(server, dimensionId, effectiveParameters);
        } else {
            deleteActiveDimension(server, dimensionId);
        }

        return new WriteResult(metadataPackRoot, dimensionTypePath, dimensionPath, parameterPath);
    }

    public static void writeMetadata(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws IOException {
        writeDimension(server, dimensionId, parameters, false);
    }

    public static void writeActiveDimension(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) throws IOException {
        VerseDimensionParameters effectiveParameters = sanitizeParameters(server, dimensionId, parameters);
        Path packRoot = activePackRoot(server);
        Path dimensionTypePath = activeDimensionTypePath(server, dimensionId);
        Path dimensionPath = activeDimensionPath(server, dimensionId);

        Files.createDirectories(dimensionTypePath.getParent());
        Files.createDirectories(dimensionPath.getParent());
        writeUtf8(packRoot.resolve("pack.mcmeta"), activePackMeta());
        writeUtf8(dimensionTypePath, dimensionTypeJson(effectiveParameters));
        writeUtf8(dimensionPath, dimensionJson(server, dimensionId, effectiveParameters));
    }

    public static void deleteActiveDimension(MinecraftServer server, ResourceLocation dimensionId) {
        deleteIfExists(activeDimensionPath(server, dimensionId));
        deleteIfExists(activeDimensionTypePath(server, dimensionId));
        deleteIfExists(legacyParameterPath(server, dimensionId));
    }

    public static void syncActiveDimensions(MinecraftServer server, java.util.Map<ResourceLocation, VerseDimensionParameters> startupDimensions) throws IOException {
        Path packRoot = activePackRoot(server);
        Files.createDirectories(packRoot);
        writeUtf8(packRoot.resolve("pack.mcmeta"), activePackMeta());

        LinkedHashSet<ResourceLocation> staleDimensions = new LinkedHashSet<>(listActiveDimensions(server));
        for (java.util.Map.Entry<ResourceLocation, VerseDimensionParameters> entry : startupDimensions.entrySet()) {
            writeActiveDimension(server, entry.getKey(), entry.getValue());
            staleDimensions.remove(entry.getKey());
        }

        for (ResourceLocation dimensionId : staleDimensions) {
            deleteActiveDimension(server, dimensionId);
        }
    }

    public static void deleteDimension(MinecraftServer server, ResourceLocation dimensionId) {
        deleteIfExists(metadataParameterPath(server, dimensionId));
        deleteActiveDimension(server, dimensionId);
    }

    public static List<ResourceLocation> listGeneratedDimensions(MinecraftServer server) throws IOException {
        LinkedHashSet<ResourceLocation> dimensionIds = new LinkedHashSet<>();
        dimensionIds.addAll(listPackDimensionIds(server, METADATA_PACK_DIRECTORY_NAME, PARAMETER_DIRECTORY));
        dimensionIds.addAll(listPackDimensionIds(server, PACK_DIRECTORY_NAME, PARAMETER_DIRECTORY));
        dimensionIds.addAll(listPackDimensionIds(server, PACK_DIRECTORY_NAME, "dimension"));
        return dimensionIds.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    public static List<ResourceLocation> listActiveDimensions(MinecraftServer server) throws IOException {
        return listPackDimensionIds(server, PACK_DIRECTORY_NAME, "dimension");
    }

    public static Optional<VerseDimensionParameters> readParameters(MinecraftServer server, ResourceLocation dimensionId) {
        Path parameterPath = resolveStoredParameterPath(server, dimensionId);
        if (parameterPath == null) {
            return Optional.empty();
        }

        try {
            JsonObject root = readJsonObject(parameterPath);
            JsonObject temporal = root.has("temporal") && root.get("temporal").isJsonObject() ? root.getAsJsonObject("temporal") : root;
            Block floorBlock = resolveBlock(root.has("floor_block") ? root.get("floor_block").getAsString() : DEFAULT_FLOOR_BLOCK_ID);
            Block sphereBlock = root.has("sphere_block") && !root.get("sphere_block").isJsonNull()
                ? resolveBlock(root.get("sphere_block").getAsString())
                : null;
            VerseDimensionWorldType worldType = root.has("world_type")
                ? VerseDimensionWorldType.parse(root.get("world_type").getAsString())
                : VerseDimensionWorldType.NORMAL;
            List<ResourceLocation> biomeIds = root.has("biomes") ? parseBiomeIds(root.getAsJsonArray("biomes")) : List.of();
            ResourceLocation fluidId = root.has("fluid") && !root.get("fluid").isJsonNull()
                ? ResourceLocation.tryParse(root.get("fluid").getAsString())
                : null;

            VerseDimensionParameters parameters = new VerseDimensionParameters(
                root.has("sky_color") ? root.get("sky_color").getAsInt() : 0,
                floorBlock,
                sphereBlock,
                worldType,
                biomeIds,
                root.has("gravity_scale") ? root.get("gravity_scale").getAsDouble() : 1.0D,
                temporal.has("day_rate") ? temporal.get("day_rate").getAsDouble() : 1.0D,
                temporal.has("time_of_day") && !temporal.get("time_of_day").isJsonNull() ? temporal.get("time_of_day").getAsInt() : null,
                temporal.has("permanent_time") && temporal.get("permanent_time").getAsBoolean(),
                temporal.has("permanent_rain") && temporal.get("permanent_rain").getAsBoolean(),
                temporal.has("permanent_lightning") && temporal.get("permanent_lightning").getAsBoolean(),
                temporal.has("permanent_storm") && temporal.get("permanent_storm").getAsBoolean(),
                temporal.has("meteor_showers") && temporal.get("meteor_showers").getAsBoolean(),
                temporal.has("spawn_warp") && temporal.get("spawn_warp").getAsBoolean(),
                root.has("ore_multiplier") ? root.get("ore_multiplier").getAsDouble() : 1.0D,
                root.has("spheres") && root.get("spheres").getAsBoolean(),
                fluidId,
                root.has("lakes") && root.get("lakes").getAsBoolean(),
                root.has("ocean_level") && !root.get("ocean_level").isJsonNull() ? root.get("ocean_level").getAsInt() : null,
                !root.has("structures") || root.get("structures").getAsBoolean(),
                root.has("mob_spawn_multiplier") ? root.get("mob_spawn_multiplier").getAsDouble() : 1.0D,
                !root.has("caves_enabled") || root.get("caves_enabled").getAsBoolean(),
                !root.has("chasms_enabled") || root.get("chasms_enabled").getAsBoolean(),
                root.has("corruptions") ? parseCorruptions(root.getAsJsonArray("corruptions")) : List.of(),
                root.has("seed_offset") ? root.get("seed_offset").getAsLong() : defaultSeedOffset(dimensionId)
            );
            return Optional.of(sanitizeParameters(server, dimensionId, parameters));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Path resolveStoredParameterPath(MinecraftServer server, ResourceLocation dimensionId) {
        Path parameterPath = metadataParameterPath(server, dimensionId);
        if (Files.isRegularFile(parameterPath)) {
            return parameterPath;
        }

        // Older saves wrote raw parameter JSON beside the active dimension pack. Keep reading that
        // location so existing worlds survive cleanup and GitHub polish work.
        Path legacyPath = legacyParameterPath(server, dimensionId);
        return Files.isRegularFile(legacyPath) ? legacyPath : null;
    }

    private static JsonObject readJsonObject(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static VerseDimensionParameters sanitizeParameters(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
        List<ResourceLocation> sanitizedBiomes = sanitizeBiomeIds(server, dimensionId, parameters.biomeIds());
        if (sanitizedBiomes.equals(parameters.biomeIds())) {
            return parameters;
        }
        return parameters.withBiomeIds(sanitizedBiomes);
    }

    public static long defaultSeedOffset(ResourceLocation dimensionId) {
        long mixed = 0x9E3779B97F4A7C15L;
        mixed ^= (long) dimensionId.hashCode() * 0xC2B2AE3D27D4EB4FL;
        mixed = Long.rotateLeft(mixed, 27);
        mixed ^= 0x165667B19E3779F9L;
        return mixed;
    }

    private static List<VerseDimensionCorruption> parseCorruptions(JsonArray array) {
        java.util.ArrayList<VerseDimensionCorruption> corruptions = new java.util.ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }

            VerseDimensionCorruption.fromId(element.getAsString())
                .filter(corruption -> !corruptions.contains(corruption))
                .ifPresent(corruptions::add);
        }
        return List.copyOf(corruptions);
    }

    public static String floorBlockId(Block floorBlock) {
        return BuiltInRegistries.BLOCK.getKey(floorBlock).toString();
    }

    public static String fluidId(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    public static int normalizedSkyColor(int color) {
        return VerseDimensionVisuals.normalizedSkyColor(color);
    }

    private static List<ResourceLocation> sanitizeBiomeIds(MinecraftServer server, ResourceLocation dimensionId, List<ResourceLocation> biomeIds) {
        if (biomeIds.isEmpty()) {
            return biomeIds;
        }

        var biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        LinkedHashSet<ResourceLocation> uniqueBiomes = new LinkedHashSet<>(biomeIds);
        ArrayList<ResourceLocation> validBiomes = new ArrayList<>(uniqueBiomes.size());
        ArrayList<ResourceLocation> missingBiomes = new ArrayList<>();
        for (ResourceLocation biomeId : uniqueBiomes) {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
            if (biomeRegistry.get(biomeKey).isPresent()) {
                validBiomes.add(biomeId);
            } else {
                missingBiomes.add(biomeId);
            }
        }

        if (missingBiomes.isEmpty()) {
            return List.copyOf(validBiomes);
        }

        VerseWorks.LOGGER.warn(
            "Dropping missing biome ids {} from VerseWorks dimension {}",
            missingBiomes,
            dimensionId
        );

        if (!validBiomes.isEmpty()) {
            return List.copyOf(validBiomes);
        }

      ResourceLocation fallbackBiome = ResourceLocation.withDefaultNamespace("plains");
      VerseWorks.LOGGER.warn(
        "All configured biome ids for VerseWorks dimension {} were missing; falling back to {}",
        dimensionId,
        fallbackBiome
      );
      return List.of(fallbackBiome);
    }

    public static int normalizedCloudColor(int color) {
        return 0xFF000000 | normalizedSkyColor(color);
    }

    private static void writeUtf8(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static Path activePackRoot(MinecraftServer server) {
      return server.getWorldPath(new LevelResource("datapacks")).resolve(PACK_DIRECTORY_NAME);
    }

    private static Path metadataPackRoot(MinecraftServer server) {
      return server.getWorldPath(new LevelResource("datapacks")).resolve(METADATA_PACK_DIRECTORY_NAME);
    }

    private static Path activeDimensionTypePath(MinecraftServer server, ResourceLocation dimensionId) {
      return activePackRoot(server)
        .resolve("data")
        .resolve(dimensionId.getNamespace())
        .resolve("dimension_type")
        .resolve(dimensionId.getPath() + ".json");
    }

    private static Path activeDimensionPath(MinecraftServer server, ResourceLocation dimensionId) {
      return activePackRoot(server)
        .resolve("data")
        .resolve(dimensionId.getNamespace())
        .resolve("dimension")
        .resolve(dimensionId.getPath() + ".json");
    }

    private static Path metadataParameterPath(MinecraftServer server, ResourceLocation dimensionId) {
      return metadataPackRoot(server)
        .resolve("data")
        .resolve(dimensionId.getNamespace())
        .resolve(PARAMETER_DIRECTORY)
        .resolve(dimensionId.getPath() + ".json");
    }

    private static Path legacyParameterPath(MinecraftServer server, ResourceLocation dimensionId) {
      return activePackRoot(server)
        .resolve("data")
        .resolve(dimensionId.getNamespace())
        .resolve(PARAMETER_DIRECTORY)
        .resolve(dimensionId.getPath() + ".json");
    }

    private static void deleteIfExists(Path path) {
      try {
        Files.deleteIfExists(path);
      } catch (NoSuchFileException ignored) {
      } catch (IOException exception) {
        VerseWorks.LOGGER.warn("Failed to delete generated dimension file {}", path, exception);
      }
    }

    private static String activePackMeta() {
        return """
            {
              "pack": {
                "description": "VerseWorks active startup dimensions",
                "pack_format": %d,
                "supported_formats": [%d, %d]
              }
            }
            """.formatted(DATA_PACK_FORMAT, DATA_PACK_FORMAT, DATA_PACK_FORMAT_MAX);
    }

    private static String metadataPackMeta() {
        return """
            {
              "pack": {
                "description": "VerseWorks dimension metadata",
                "pack_format": %d,
                "supported_formats": [%d, %d]
              }
            }
            """.formatted(DATA_PACK_FORMAT, DATA_PACK_FORMAT, DATA_PACK_FORMAT_MAX);
    }

    private static String dimensionTypeJson(VerseDimensionParameters parameters) {
      boolean blackVoidSky = VerseDimensionVisuals.usesBlackVoidSky(parameters);
      boolean endLike = VerseDimensionVisuals.isEndLikeDimension(parameters);
        boolean natural = !blackVoidSky && !endLike;
        boolean hasRaids = natural;
        boolean bedWorks = !endLike;
      String effects = VerseDimensionVisuals.effectsLocation(parameters).toString();

        return """
            {
              "piglin_safe": false,
              "natural": %s,
              "has_fixed_time": false,
              "has_skylight": %s,
              "has_ceiling": false,
              "ultrawarm": false,
              "has_raids": %s,
              "bed_works": %s,
              "respawn_anchor_works": false,
              "coordinate_scale": 1.0,
              "min_y": -64,
              "height": 384,
              "logical_height": 384,
              "infiniburn": "#minecraft:infiniburn_overworld",
              "ambient_light": 0.0,
              "monster_spawn_light_level": 0,
              "monster_spawn_block_light_limit": 0,
              "effects": "%s"
            }
            """.formatted(natural, !blackVoidSky, hasRaids, bedWorks, effects);
    }

    private static String dimensionJson(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
      if (parameters.worldType() == VerseDimensionWorldType.FLAT) {
        return flatDimensionJson(server, dimensionId, parameters);
      }

        return noiseDimensionJson(server, dimensionId, parameters);
    }

    private static String flatDimensionJson(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
      JsonObject root = new JsonObject();
      root.addProperty("type", dimensionId.toString());

      JsonObject generator = new JsonObject();
      generator.addProperty("type", "minecraft:flat");
      generator.add("settings", encodeFlatGeneratorSettings(server, dimensionId, parameters));

      root.add("generator", generator);
      return root.toString();
    }

    private static String noiseDimensionJson(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
        JsonObject root = new JsonObject();
        root.addProperty("type", dimensionId.toString());

        JsonObject generator = new JsonObject();
        generator.addProperty("type", TERRAIN_GENERATOR_ID.toString());
        generator.add("biome_source", encodeBiomeSource(server, parameters));
        generator.addProperty("settings", parameters.worldType().noiseSettingsId());
        generator.add("verseworks", encodeParameters(parameters));

        root.add("generator", generator);
        return root.toString();
    }

    private static JsonElement encodeBiomeSource(MinecraftServer server, VerseDimensionParameters parameters) {
        if (parameters.biomeIds().isEmpty()) {
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                BiomeSource biomeSource = overworld.getChunkSource().getGenerator().getBiomeSource();
                RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
                Optional<JsonElement> encoded = BiomeSource.CODEC.encodeStart(ops, biomeSource).result();
                if (encoded.isPresent()) {
                    return encoded.get();
                }
            }

            return JsonParser.parseString("""
                {
                  "type": "minecraft:multi_noise",
                  "preset": "minecraft:overworld"
                }
                """);
        }

        if (parameters.biomeIds().size() == 1) {
            return JsonParser.parseString("""
                {
                  "type": "minecraft:fixed",
                  "biome": "%s"
                }
                """.formatted(parameters.primaryBiomeId()));
        }

        String entries = parameters.biomeBands().stream()
            .map(band -> """
                {
                  "parameters": {
                    "temperature": { "min": -1.0, "max": 1.0 },
                    "humidity": { "min": -1.0, "max": 1.0 },
                    "continentalness": { "min": -1.0, "max": 1.0 },
                    "erosion": { "min": -1.0, "max": 1.0 },
                    "depth": { "min": -1.0, "max": 1.0 },
                    "weirdness": { "min": %.4f, "max": %.4f },
                    "offset": 0.0
                  },
                  "biome": "%s"
                }
                """.formatted(band.minWeirdness(), band.maxWeirdness(), band.biomeId()))
            .collect(java.util.stream.Collectors.joining(",\n"));

        return JsonParser.parseString("""
            {
              "type": "minecraft:multi_noise",
              "biomes": [
            %s
              ]
            }
            """.formatted(indent(entries, 4)));
    }

    private static String parametersJson(VerseDimensionParameters parameters) {
        return encodeParameters(parameters).toString();
    }

    private static JsonElement encodeParameters(VerseDimensionParameters parameters) {
        return VerseDimensionParameters.CODEC.encodeStart(JsonOps.INSTANCE, parameters)
            .resultOrPartial(error -> VerseWorks.LOGGER.error("Failed to encode VerseWorks dimension parameters: {}", error))
            .orElseGet(JsonObject::new);
    }

    private static JsonElement encodeFlatGeneratorSettings(MinecraftServer server, ResourceLocation dimensionId, VerseDimensionParameters parameters) {
      FlatLevelGeneratorSettings settings = createFlatGeneratorSettings(server, parameters);
      RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
      return FlatLevelGeneratorSettings.CODEC.encodeStart(registryOps, settings)
        .resultOrPartial(error -> VerseWorks.LOGGER.error("Failed to encode VerseWorks flat generator settings for {}: {}", dimensionId, error))
        .orElseGet(JsonObject::new);
    }

    private static FlatLevelGeneratorSettings createFlatGeneratorSettings(MinecraftServer server, VerseDimensionParameters parameters) {
      HolderGetter<Biome> biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
      HolderGetter<PlacedFeature> placedFeatureRegistry = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
      Holder<Biome> biomeHolder = resolveFlatBiome(server, parameters, biomeRegistry);
      java.util.Optional<HolderSet<StructureSet>> structureOverrides = parameters.structures() ? java.util.Optional.empty() : java.util.Optional.of(HolderSet.direct(List.of()));
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
      return settings;
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

    private static Holder<Biome> resolveBiomeHolder(MinecraftServer server, ResourceLocation biomeId) {
      HolderGetter<Biome> biomeRegistry = server.registryAccess().lookupOrThrow(Registries.BIOME);
      ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
      return biomeRegistry.get(biomeKey)
        .orElseThrow(() -> new IllegalArgumentException("Unknown biome: " + biomeId));
    }

    private static String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return text.lines().map(line -> prefix + line).collect(java.util.stream.Collectors.joining("\n"));
    }

        private static List<ResourceLocation> parseBiomeIds(JsonArray biomes) {
          java.util.ArrayList<ResourceLocation> biomeIds = new java.util.ArrayList<>(biomes.size());
          biomes.forEach(element -> {
            ResourceLocation biomeId = ResourceLocation.tryParse(element.getAsString());
            if (biomeId != null) {
              biomeIds.add(biomeId);
            }
          });
          return List.copyOf(biomeIds);
        }

        private static Block resolveBlock(String blockId) {
          ResourceLocation identifier = ResourceLocation.tryParse(blockId);
          if (identifier == null) {
            return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace("grass_block")).orElse(Blocks.GRASS_BLOCK);
          }
          return BuiltInRegistries.BLOCK.getOptional(identifier)
            .orElseGet(() -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace("grass_block")).orElse(Blocks.GRASS_BLOCK));
        }

    private static ResourceLocation toDimensionId(Path dataRoot, Path dimensionPath) {
          Path relativePath = dataRoot.relativize(dimensionPath);
          if (relativePath.getNameCount() < 3) {
            return null;
          }

          String namespace = relativePath.getName(0).toString();
          String fileName = relativePath.getFileName().toString();
          String path = fileName.substring(0, fileName.length() - ".json".length());
          return ResourceLocation.tryParse(namespace + ":" + path);
    }

        private static List<ResourceLocation> listPackDimensionIds(MinecraftServer server, String packDirectory, String subdirectory) throws IOException {
          Path root = server.getWorldPath(new LevelResource("datapacks")).resolve(packDirectory).resolve("data");
          if (!Files.isDirectory(root)) {
            return List.of();
          }

          try (Stream<Path> stream = Files.walk(root)) {
            return stream
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .filter(path -> path.getParent() != null)
              .filter(path -> path.getParent().getFileName().toString().equals(subdirectory))
              .map(path -> toDimensionId(root, path))
              .filter(java.util.Objects::nonNull)
              .sorted(Comparator.comparing(ResourceLocation::toString))
              .toList();
          }
        }

    private static int mixColor(int first, int second, float secondWeight) {
        float firstWeight = 1.0F - secondWeight;
        int alpha = clampChannel(Math.round(alpha(first) * firstWeight + alpha(second) * secondWeight));
        int red = clampChannel(Math.round(red(first) * firstWeight + red(second) * secondWeight));
        int green = clampChannel(Math.round(green(first) * firstWeight + green(second) * secondWeight));
        int blue = clampChannel(Math.round(blue(first) * firstWeight + blue(second) * secondWeight));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int alpha(int color) {
        return color >>> 24;
    }

    private static int red(int color) {
        return color >> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public record WriteResult(Path packRoot, Path dimensionTypePath, Path dimensionPath, Path parameterPath) {
        @Override
        public String toString() {
        return String.format(Locale.ROOT, "%s -> %s (%s)", this.dimensionTypePath, this.dimensionPath, this.parameterPath);
        }
    }
}
