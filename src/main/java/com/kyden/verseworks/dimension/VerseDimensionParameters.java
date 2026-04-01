package com.kyden.verseworks.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record VerseDimensionParameters(
    int skyColor,
    Block floorBlock,
    Block sphereBlock,
    VerseDimensionWorldType worldType,
    List<ResourceLocation> biomeIds,
    double gravityScale,
    double dayRate,
    Integer timeOfDay,
    boolean permanentTime,
    boolean permanentRain,
    boolean permanentLightning,
    boolean permanentStorm,
    boolean meteorShowers,
    boolean spawnWarp,
    double oreMultiplier,
    boolean spheres,
    ResourceLocation fluidId,
    boolean lakes,
    Integer oceanLevel,
    boolean structures,
    List<VerseDimensionCorruption> corruptions,
    long seedOffset
) {
    private static final Set<ResourceLocation> VANILLA_END_BIOMES = Set.of(
        ResourceLocation.withDefaultNamespace("the_end"),
        ResourceLocation.withDefaultNamespace("end_highlands"),
        ResourceLocation.withDefaultNamespace("end_midlands"),
        ResourceLocation.withDefaultNamespace("small_end_islands"),
        ResourceLocation.withDefaultNamespace("end_barrens")
    );

    private static final Codec<ResourceLocation> IDENTIFIER_CODEC = Codec.STRING.comapFlatMap(
        value -> {
            ResourceLocation identifier = ResourceLocation.tryParse(value);
            return identifier == null ? DataResult.error(() -> "Invalid identifier: " + value) : DataResult.success(identifier);
        },
        ResourceLocation::toString
    );

    private static final Codec<Block> BLOCK_CODEC = Codec.STRING.comapFlatMap(
        value -> {
            ResourceLocation identifier = ResourceLocation.tryParse(value);
            if (identifier == null) {
                return DataResult.error(() -> "Invalid block id: " + value);
            }

            Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
            return block != null ? DataResult.success(block) : DataResult.error(() -> "Unknown block id: " + value);
        },
        block -> BuiltInRegistries.BLOCK.getKey(block).toString()
    );

    public static final Codec<VerseDimensionParameters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("sky_color", 0).forGetter(VerseDimensionParameters::skyColor),
        BLOCK_CODEC.optionalFieldOf("floor_block", BuiltInRegistries.BLOCK.getOptional(ResourceLocation.withDefaultNamespace("grass_block")).orElse(Blocks.GRASS_BLOCK)).forGetter(VerseDimensionParameters::floorBlock),
        BLOCK_CODEC.optionalFieldOf("sphere_block").forGetter((VerseDimensionParameters parameters) -> Optional.ofNullable(parameters.sphereBlock())),
        VerseDimensionWorldType.CODEC.optionalFieldOf("world_type", VerseDimensionWorldType.NORMAL).forGetter(VerseDimensionParameters::worldType),
        IDENTIFIER_CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(VerseDimensionParameters::biomeIds),
        Codec.DOUBLE.optionalFieldOf("gravity_scale", 1.0D).forGetter(VerseDimensionParameters::gravityScale),
        TemporalSettings.CODEC.optionalFieldOf("temporal", TemporalSettings.DEFAULT).forGetter((VerseDimensionParameters parameters) -> new TemporalSettings(
            parameters.dayRate(),
            parameters.timeOfDay(),
            parameters.permanentTime(),
            parameters.permanentRain(),
            parameters.permanentLightning(),
            parameters.permanentStorm(),
            parameters.meteorShowers(),
            parameters.spawnWarp()
        )),
        Codec.DOUBLE.optionalFieldOf("ore_multiplier", 1.0D).forGetter(VerseDimensionParameters::oreMultiplier),
        Codec.BOOL.optionalFieldOf("spheres", false).forGetter(VerseDimensionParameters::spheres),
        IDENTIFIER_CODEC.optionalFieldOf("fluid").forGetter((VerseDimensionParameters parameters) -> Optional.ofNullable(parameters.fluidId())),
        Codec.BOOL.optionalFieldOf("lakes", false).forGetter(VerseDimensionParameters::lakes),
        Codec.INT.optionalFieldOf("ocean_level").forGetter((VerseDimensionParameters parameters) -> Optional.ofNullable(parameters.oceanLevel())),
        Codec.BOOL.optionalFieldOf("structures", true).forGetter(VerseDimensionParameters::structures),
        VerseDimensionCorruption.CODEC.listOf().optionalFieldOf("corruptions", List.of()).forGetter(VerseDimensionParameters::corruptions),
        Codec.LONG.optionalFieldOf("seed_offset", 0L).forGetter(VerseDimensionParameters::seedOffset)
    ).apply(instance, (skyColor, floorBlock, sphereBlock, worldType, biomeIds, gravityScale, temporal, oreMultiplier, spheres, fluidId, lakes, oceanLevel, structures, corruptions, seedOffset) ->
        new VerseDimensionParameters(
            skyColor,
            floorBlock,
            sphereBlock.orElse(null),
            worldType,
            biomeIds,
            gravityScale,
            temporal.dayRate(),
            temporal.timeOfDay(),
            temporal.permanentTime(),
            temporal.permanentRain(),
            temporal.permanentLightning(),
            temporal.permanentStorm(),
            temporal.meteorShowers(),
            temporal.spawnWarp(),
            oreMultiplier,
            spheres,
            fluidId.orElse(null),
            lakes,
            oceanLevel.orElse(null),
            structures,
            corruptions,
            seedOffset
        )
    ));

    public VerseDimensionParameters(int skyColor, Block floorBlock, VerseDimensionWorldType worldType, List<ResourceLocation> biomeIds) {
        this(skyColor, floorBlock, null, worldType, biomeIds, 1.0D, 1.0D, null, false, false, false, false, false, false, 1.0D, false, null, false, null, true, List.of(), 0L);
    }

    public VerseDimensionParameters {
        biomeIds = List.copyOf(biomeIds);
        corruptions = List.copyOf(corruptions);
        gravityScale = clamp(gravityScale, 0.05D, 4.0D);
        dayRate = clamp(dayRate, 0.0D, 64.0D);
        permanentRain = permanentRain || permanentStorm;
        oreMultiplier = clamp(oreMultiplier, 0.0D, 8.0D);
        spheres = spheres || sphereBlock != null;
        timeOfDay = timeOfDay == null ? null : Math.floorMod(timeOfDay, 24000);
        oceanLevel = oceanLevel == null ? null : oceanLevel;
    }

    public ResourceLocation primaryBiomeId() {
        return this.biomeIds.isEmpty() ? ResourceLocation.withDefaultNamespace("plains") : this.biomeIds.get(0);
    }

    public VerseDimensionParameters withSeedOffset(long seedOffset) {
        return new VerseDimensionParameters(
            this.skyColor,
            this.floorBlock,
            this.sphereBlock,
            this.worldType,
            this.biomeIds,
            this.gravityScale,
            this.dayRate,
            this.timeOfDay,
            this.permanentTime,
            this.permanentRain,
            this.permanentLightning,
            this.permanentStorm,
            this.meteorShowers,
            this.spawnWarp,
            this.oreMultiplier,
            this.spheres,
            this.fluidId,
            this.lakes,
            this.oceanLevel,
            this.structures,
            this.corruptions,
            seedOffset
        );
    }

    public VerseDimensionParameters withBiomeIds(List<ResourceLocation> biomeIds) {
        return new VerseDimensionParameters(
            this.skyColor,
            this.floorBlock,
            this.sphereBlock,
            this.worldType,
            biomeIds,
            this.gravityScale,
            this.dayRate,
            this.timeOfDay,
            this.permanentTime,
            this.permanentRain,
            this.permanentLightning,
            this.permanentStorm,
            this.meteorShowers,
            this.spawnWarp,
            this.oreMultiplier,
            this.spheres,
            this.fluidId,
            this.lakes,
            this.oceanLevel,
            this.structures,
            this.corruptions,
            this.seedOffset
        );
    }

    public boolean hasCorruptions() {
        return !this.corruptions.isEmpty();
    }

    public boolean hasExplicitEndBiomes() {
        return this.biomeIds.stream().anyMatch(VerseDimensionParameters::isEndBiomeId);
    }

    public Block effectiveSphereBlock() {
        return this.sphereBlock != null ? this.sphereBlock : this.floorBlock;
    }

    public BlockState sphereStateForY(int y) {
        Block block = this.effectiveSphereBlock();
        if (y < 0) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null && !blockId.getPath().startsWith("deepslate_")) {
                ResourceLocation deepslateId = ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), "deepslate_" + blockId.getPath());
                Block deepslateBlock = BuiltInRegistries.BLOCK.getOptional(deepslateId).orElse(null);
                if (deepslateBlock != null) {
                    return deepslateBlock.defaultBlockState();
                }
            }
        }

        return block.defaultBlockState();
    }

    public int sampleSphereCenterY(RandomSource random, int minY, int maxYExclusive) {
        int minCenterY = minY + 16;
        int maxCenterY = maxYExclusive - 17;
        if (maxCenterY <= minCenterY) {
            return minCenterY;
        }

        if (this.worldType == VerseDimensionWorldType.SKY_ISLAND) {
            return randomBetween(random, Math.max(minCenterY, 72), Math.min(maxCenterY, 184));
        }

        if (this.worldType.isVoid()) {
            return randomBetween(random, Math.max(minCenterY, 96), Math.min(maxCenterY, 192));
        }

        if (random.nextDouble() < 0.65D) {
            int undergroundMax = Math.min(maxCenterY, 56);
            if (undergroundMax > minCenterY) {
                return randomBetween(random, minCenterY, undergroundMax);
            }
        }

        int skyMin = Math.max(minCenterY, 96);
        int skyMax = Math.min(maxCenterY, 192);
        if (skyMax > skyMin) {
            return randomBetween(random, skyMin, skyMax);
        }

        return randomBetween(random, minCenterY, maxCenterY);
    }

    public String biomeSummary() {
        if (this.biomeIds.isEmpty()) {
            return "overworld preset";
        }

        return this.biomeIds.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }

    public List<BiomeBand> biomeBands() {
        if (this.biomeIds.isEmpty()) {
            return List.of();
        }

        float rangeSize = 2.0F / this.biomeIds.size();
        java.util.ArrayList<BiomeBand> bands = new java.util.ArrayList<>(this.biomeIds.size());
        for (int index = 0; index < this.biomeIds.size(); index++) {
            float min = -1.0F + rangeSize * index;
            float max = index == this.biomeIds.size() - 1 ? 1.0F : min + rangeSize;
            bands.add(new BiomeBand(this.biomeIds.get(index), min, max));
        }
        return List.copyOf(bands);
    }

    public int resolvedTimeOfDay() {
        return this.timeOfDay == null ? 1000 : Math.floorMod(this.timeOfDay, 24000);
    }

    public ResourceLocation effectiveFluidId() {
        if (this.fluidId != null) {
            return this.fluidId;
        }

        if (this.lakes || this.oceanLevel != null) {
            return ResourceLocation.withDefaultNamespace("water");
        }

        return null;
    }

    public boolean hasFluidFeatures() {
        return this.lakes || this.oceanLevel != null;
    }

    public boolean hasGlobalChunkDecorators() {
        return this.lakes || this.oceanLevel != null || (this.oreMultiplier > 1.0D && !this.worldType.isFlat());
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }

        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    public boolean targetRain() {
        return this.permanentRain || this.permanentStorm;
    }

    public boolean targetThunder() {
        return this.permanentLightning || this.permanentStorm;
    }

    public String rulesSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(this.worldType.commandName());
        summary.append(", gravity=").append(trimmedDecimal(this.gravityScale));
        summary.append(", dayrate=").append(trimmedDecimal(this.dayRate));
        if (this.timeOfDay != null) {
            summary.append(", time=").append(this.resolvedTimeOfDay());
            if (this.permanentTime) {
                summary.append(" (locked)");
            }
        }
        if (this.permanentStorm) {
            summary.append(", endless storm");
        } else if (this.permanentLightning) {
            summary.append(", endless lightning");
        } else if (this.permanentRain) {
            summary.append(", endless rain");
        }
        if (this.meteorShowers) {
            summary.append(", meteor showers");
        }
        if (this.spawnWarp) {
            summary.append(", spawn warp");
        }
        if (this.spheres) {
            summary.append(", spheres");
            if (this.sphereBlock != null && this.sphereBlock != this.floorBlock) {
                summary.append("[").append(BuiltInRegistries.BLOCK.getKey(this.effectiveSphereBlock())).append("]");
            }
        }
        if (this.oreMultiplier != 1.0D) {
            summary.append(", ore x").append(trimmedDecimal(this.oreMultiplier));
        }
        if (this.effectiveFluidId() != null) {
            summary.append(", fluid=").append(this.effectiveFluidId());
            if (this.oceanLevel != null) {
                summary.append("@y").append(this.oceanLevel);
            }
            if (this.lakes) {
                summary.append(", lakes");
            }
        }
        if (!this.structures) {
            summary.append(", reduced structures");
        }
        if (!this.corruptions.isEmpty()) {
            String corruptionSummary = this.corruptions.stream()
                .map(VerseDimensionCorruption::id)
                .collect(Collectors.joining("|"));
            summary.append(", corruption=").append(corruptionSummary);
        }
        return summary.toString();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isEndBiomeId(ResourceLocation biomeId) {
        if (VANILLA_END_BIOMES.contains(biomeId)) {
            return true;
        }

        String path = biomeId.getPath();
        return path.equals("the_end")
            || path.startsWith("end_")
            || path.endsWith("_end")
            || path.contains("_end_");
    }

    private static String trimmedDecimal(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record BiomeBand(ResourceLocation biomeId, float minWeirdness, float maxWeirdness) {
    }

    private record TemporalSettings(double dayRate, Integer timeOfDay, boolean permanentTime, boolean permanentRain, boolean permanentLightning, boolean permanentStorm, boolean meteorShowers, boolean spawnWarp) {
        private static final TemporalSettings DEFAULT = new TemporalSettings(1.0D, null, false, false, false, false, false, false);

        private static final Codec<TemporalSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("day_rate", 1.0D).forGetter(TemporalSettings::dayRate),
            Codec.INT.optionalFieldOf("time_of_day").forGetter(settings -> Optional.ofNullable(settings.timeOfDay())),
            Codec.BOOL.optionalFieldOf("permanent_time", false).forGetter(TemporalSettings::permanentTime),
            Codec.BOOL.optionalFieldOf("permanent_rain", false).forGetter(TemporalSettings::permanentRain),
            Codec.BOOL.optionalFieldOf("permanent_lightning", false).forGetter(TemporalSettings::permanentLightning),
            Codec.BOOL.optionalFieldOf("permanent_storm", false).forGetter(TemporalSettings::permanentStorm),
            Codec.BOOL.optionalFieldOf("meteor_showers", false).forGetter(TemporalSettings::meteorShowers),
            Codec.BOOL.optionalFieldOf("spawn_warp", false).forGetter(TemporalSettings::spawnWarp)
        ).apply(instance, (dayRate, timeOfDay, permanentTime, permanentRain, permanentLightning, permanentStorm, meteorShowers, spawnWarp) -> new TemporalSettings(
            dayRate,
            timeOfDay.orElse(null),
            permanentTime,
            permanentRain,
            permanentLightning,
            permanentStorm,
            meteorShowers,
            spawnWarp
        )));
    }
}