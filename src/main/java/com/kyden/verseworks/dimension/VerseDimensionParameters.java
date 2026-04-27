package com.kyden.verseworks.dimension;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
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
    double mobSpawnMultiplier,
    boolean cavesEnabled,
    boolean chasmsEnabled,
    StructureControlProfile structureControl,
    List<ShapeFeatureSpec> shapeFeatures,
    List<PoolFeatureSpec> poolFeatures,
    List<OreMorphSpec> oreMorphFeatures,
    SurfaceProfile surfaceProfile,
    SkyProfile skyProfile,
    boolean crystalClusters,
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

    private static final MapCodec<LegacyCoreCodecState> LEGACY_CORE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.INT.optionalFieldOf("sky_color", 0).forGetter(LegacyCoreCodecState::skyColor),
        BLOCK_CODEC.optionalFieldOf("floor_block", Blocks.GRASS_BLOCK).forGetter(LegacyCoreCodecState::floorBlock),
        BLOCK_CODEC.optionalFieldOf("sphere_block").forGetter(state -> Optional.ofNullable(state.sphereBlock())),
        VerseDimensionWorldType.CODEC.optionalFieldOf("world_type", VerseDimensionWorldType.NORMAL).forGetter(LegacyCoreCodecState::worldType),
        IDENTIFIER_CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(LegacyCoreCodecState::biomeIds),
        Codec.DOUBLE.optionalFieldOf("gravity_scale", 1.0D).forGetter(LegacyCoreCodecState::gravityScale),
        TemporalSettings.CODEC.optionalFieldOf("temporal", TemporalSettings.DEFAULT).forGetter(LegacyCoreCodecState::temporal),
        Codec.DOUBLE.optionalFieldOf("ore_multiplier", 1.0D).forGetter(LegacyCoreCodecState::oreMultiplier),
        Codec.BOOL.optionalFieldOf("spheres", false).forGetter(LegacyCoreCodecState::spheres),
        IDENTIFIER_CODEC.optionalFieldOf("fluid").forGetter(state -> Optional.ofNullable(state.fluidId())),
        Codec.BOOL.optionalFieldOf("lakes", false).forGetter(LegacyCoreCodecState::lakes),
        Codec.INT.optionalFieldOf("ocean_level").forGetter(state -> Optional.ofNullable(state.oceanLevel())),
        Codec.BOOL.optionalFieldOf("structures", true).forGetter(LegacyCoreCodecState::structures)
    ).apply(instance, (skyColor, floorBlock, sphereBlock, worldType, biomeIds, gravityScale, temporal, oreMultiplier, spheres, fluidId, lakes, oceanLevel, structures) ->
        new LegacyCoreCodecState(
            skyColor,
            floorBlock,
            sphereBlock.orElse(null),
            worldType,
            biomeIds,
            gravityScale,
            temporal,
            oreMultiplier,
            spheres,
            fluidId.orElse(null),
            lakes,
            oceanLevel.orElse(null),
            structures,
            StructureControlProfile.DEFAULT,
            List.of(),
            List.of(),
            List.of(),
            SurfaceProfile.DEFAULT,
            SkyProfile.DEFAULT
        )
    ));

    private static final MapCodec<ExpansionCodecState> EXPANSION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        StructureControlProfile.CODEC.optionalFieldOf("structure_control", StructureControlProfile.DEFAULT).forGetter(ExpansionCodecState::structureControl),
        ShapeFeatureSpec.CODEC.listOf().optionalFieldOf("shape_features", List.of()).forGetter(ExpansionCodecState::shapeFeatures),
        PoolFeatureSpec.CODEC.listOf().optionalFieldOf("pool_features", List.of()).forGetter(ExpansionCodecState::poolFeatures),
        OreMorphSpec.CODEC.listOf().optionalFieldOf("ore_morph_features", List.of()).forGetter(ExpansionCodecState::oreMorphFeatures),
        SurfaceProfile.CODEC.optionalFieldOf("surface_profile", SurfaceProfile.DEFAULT).forGetter(ExpansionCodecState::surfaceProfile),
        SkyProfile.CODEC.optionalFieldOf("sky_profile", SkyProfile.DEFAULT).forGetter(ExpansionCodecState::skyProfile),
        Codec.BOOL.optionalFieldOf("crystal_clusters", false).forGetter(ExpansionCodecState::crystalClusters)
    ).apply(instance, ExpansionCodecState::new));

    private static final MapCodec<ExtraCodecState> EXTRA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.DOUBLE.optionalFieldOf("mob_spawn_multiplier", 1.0D).forGetter(ExtraCodecState::mobSpawnMultiplier),
        Codec.BOOL.optionalFieldOf("caves_enabled", true).forGetter(ExtraCodecState::cavesEnabled),
        Codec.BOOL.optionalFieldOf("chasms_enabled", true).forGetter(ExtraCodecState::chasmsEnabled),
        VerseDimensionCorruption.CODEC.listOf().optionalFieldOf("corruptions", List.of()).forGetter(ExtraCodecState::corruptions),
        Codec.LONG.optionalFieldOf("seed_offset", 0L).forGetter(ExtraCodecState::seedOffset)
    ).apply(instance, ExtraCodecState::new));

    public static final Codec<VerseDimensionParameters> CODEC = Codec.mapPair(Codec.mapPair(LEGACY_CORE_CODEC, EXPANSION_CODEC), EXTRA_CODEC).xmap(pair ->
        new VerseDimensionParameters(
            pair.getFirst().getFirst().skyColor(),
            pair.getFirst().getFirst().floorBlock(),
            pair.getFirst().getFirst().sphereBlock(),
            pair.getFirst().getFirst().worldType(),
            pair.getFirst().getFirst().biomeIds(),
            pair.getFirst().getFirst().gravityScale(),
            pair.getFirst().getFirst().temporal().dayRate(),
            pair.getFirst().getFirst().temporal().timeOfDay(),
            pair.getFirst().getFirst().temporal().permanentTime(),
            pair.getFirst().getFirst().temporal().permanentRain(),
            pair.getFirst().getFirst().temporal().permanentLightning(),
            pair.getFirst().getFirst().temporal().permanentStorm(),
            pair.getFirst().getFirst().temporal().meteorShowers(),
            pair.getFirst().getFirst().temporal().spawnWarp(),
            pair.getFirst().getFirst().oreMultiplier(),
            pair.getFirst().getFirst().spheres(),
            pair.getFirst().getFirst().fluidId(),
            pair.getFirst().getFirst().lakes(),
            pair.getFirst().getFirst().oceanLevel(),
            pair.getFirst().getFirst().structures(),
            pair.getSecond().mobSpawnMultiplier(),
            pair.getSecond().cavesEnabled(),
            pair.getSecond().chasmsEnabled(),
            pair.getFirst().getSecond().structureControl(),
            pair.getFirst().getSecond().shapeFeatures(),
            pair.getFirst().getSecond().poolFeatures(),
            pair.getFirst().getSecond().oreMorphFeatures(),
            pair.getFirst().getSecond().surfaceProfile(),
            pair.getFirst().getSecond().skyProfile(),
            pair.getFirst().getSecond().crystalClusters(),
            pair.getSecond().corruptions(),
            pair.getSecond().seedOffset()
        )
    , parameters -> Pair.of(
        Pair.of(
        new LegacyCoreCodecState(
            parameters.skyColor(),
            parameters.floorBlock(),
            parameters.sphereBlock(),
            parameters.worldType(),
            parameters.biomeIds(),
            parameters.gravityScale(),
            new TemporalSettings(
                parameters.dayRate(),
                parameters.timeOfDay(),
                parameters.permanentTime(),
                parameters.permanentRain(),
                parameters.permanentLightning(),
                parameters.permanentStorm(),
                parameters.meteorShowers(),
                parameters.spawnWarp()
            ),
            parameters.oreMultiplier(),
            parameters.spheres(),
            parameters.fluidId(),
            parameters.lakes(),
            parameters.oceanLevel(),
            parameters.structures(),
            parameters.structureControl(),
            parameters.shapeFeatures(),
            parameters.poolFeatures(),
            parameters.oreMorphFeatures(),
            parameters.surfaceProfile(),
            parameters.skyProfile()
        ),
        new ExpansionCodecState(
            parameters.structureControl(),
            parameters.shapeFeatures(),
            parameters.poolFeatures(),
            parameters.oreMorphFeatures(),
            parameters.surfaceProfile(),
            parameters.skyProfile(),
            parameters.crystalClusters()
        )),
        new ExtraCodecState(
            parameters.mobSpawnMultiplier(),
            parameters.cavesEnabled(),
            parameters.chasmsEnabled(),
            parameters.corruptions(),
            parameters.seedOffset()
        )
    )).codec();

    public VerseDimensionParameters(int skyColor, Block floorBlock, VerseDimensionWorldType worldType, List<ResourceLocation> biomeIds) {
        this(skyColor, floorBlock, null, worldType, biomeIds, 1.0D, 1.0D, null, false, false, false, false, false, false, 1.0D, false, null, false, null, true, 1.0D, true, true, StructureControlProfile.DEFAULT, List.of(), List.of(), List.of(), SurfaceProfile.DEFAULT, SkyProfile.DEFAULT, false, List.of(), 0L);
    }

    public VerseDimensionParameters(
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
        double mobSpawnMultiplier,
        boolean cavesEnabled,
        boolean chasmsEnabled,
        List<VerseDimensionCorruption> corruptions,
        long seedOffset
    ) {
        this(skyColor, floorBlock, sphereBlock, worldType, biomeIds, gravityScale, dayRate, timeOfDay, permanentTime, permanentRain, permanentLightning, permanentStorm, meteorShowers, spawnWarp, oreMultiplier, spheres, fluidId, lakes, oceanLevel, structures, mobSpawnMultiplier, cavesEnabled, chasmsEnabled, StructureControlProfile.DEFAULT, List.of(), List.of(), List.of(), SurfaceProfile.DEFAULT, SkyProfile.DEFAULT, false, corruptions, seedOffset);
    }

    public VerseDimensionParameters {
        biomeIds = List.copyOf(biomeIds);
        corruptions = List.copyOf(corruptions);
        gravityScale = clamp(gravityScale, 0.05D, 4.0D);
        dayRate = clamp(dayRate, 0.0D, 64.0D);
        permanentRain = permanentRain || permanentStorm;
        oreMultiplier = clamp(oreMultiplier, 0.0D, 8.0D);
        mobSpawnMultiplier = clamp(mobSpawnMultiplier, 0.0D, 3.0D);
        spheres = spheres || sphereBlock != null;
        timeOfDay = timeOfDay == null ? null : Math.floorMod(timeOfDay, 24000);
        structureControl = structureControl == null ? StructureControlProfile.DEFAULT : structureControl;
        shapeFeatures = deriveShapeFeatures(shapeFeatures, spheres, sphereBlock, floorBlock);
        poolFeatures = derivePoolFeatures(poolFeatures, fluidId, lakes, worldType);
        oreMorphFeatures = deriveOreMorphFeatures(oreMorphFeatures, oreMultiplier);
        surfaceProfile = (surfaceProfile == null ? SurfaceProfile.DEFAULT : surfaceProfile).resolve(floorBlock);
        skyProfile = (skyProfile == null ? SkyProfile.DEFAULT : skyProfile).resolve();
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
            this.mobSpawnMultiplier,
            this.cavesEnabled,
            this.chasmsEnabled,
            this.structureControl,
            this.shapeFeatures,
            this.poolFeatures,
            this.oreMorphFeatures,
            this.surfaceProfile,
            this.skyProfile,
            this.crystalClusters,
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
            this.mobSpawnMultiplier,
            this.cavesEnabled,
            this.chasmsEnabled,
            this.structureControl,
            this.shapeFeatures,
            this.poolFeatures,
            this.oreMorphFeatures,
            this.surfaceProfile,
            this.skyProfile,
            this.crystalClusters,
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
        return this.sampleHeight(random, HeightDistribution.DEFAULT, minY, maxYExclusive);
    }

    public int sampleHeight(RandomSource random, HeightDistribution distribution, int minY, int maxYExclusive) {
        int minCenterY = minY + 16;
        int maxCenterY = maxYExclusive - 17;
        if (maxCenterY <= minCenterY) {
            return minCenterY;
        }

        if ("surface".equals(distribution.profile())) {
            return randomBetween(random, Math.max(minCenterY, 56), Math.min(maxCenterY, 116));
        }
        if ("deep".equals(distribution.profile())) {
            return randomBetween(random, minCenterY, Math.min(maxCenterY, 32));
        }
        if ("sky".equals(distribution.profile())) {
            return randomBetween(random, Math.max(minCenterY, 96), Math.min(maxCenterY, 192));
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
        ArrayList<BiomeBand> bands = new ArrayList<>(this.biomeIds.size());
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

    public boolean forcesPermanentNight() {
        return this.worldType == VerseDimensionWorldType.BEDROCK_SHELL;
    }

    public boolean effectivePermanentTime() {
        return this.forcesPermanentNight() || this.permanentTime;
    }

    public int effectiveResolvedTimeOfDay() {
        return this.forcesPermanentNight() ? 18000 : this.resolvedTimeOfDay();
    }

    public ResourceLocation effectiveFluidId() {
        if (!this.poolFeatures.isEmpty()) {
            return this.poolFeatures.getFirst().fluidId();
        }

        if (this.fluidId != null) {
            return this.fluidId;
        }

        if (this.lakes || this.oceanLevel != null || this.worldType == VerseDimensionWorldType.WATER_WORLD) {
            return ResourceLocation.withDefaultNamespace("water");
        }

        return null;
    }

    public Integer effectiveOceanLevel() {
        if (this.oceanLevel != null) {
            return this.oceanLevel;
        }

        if (this.worldType == VerseDimensionWorldType.OCEAN) {
            return 63;
        }

        return null;
    }

    public boolean hasFluidFeatures() {
        return !this.poolFeatures.isEmpty() || this.effectiveOceanLevel() != null || this.worldType.isFluidWorld();
    }

    public boolean hasGlobalChunkDecorators() {
        return !this.poolFeatures.isEmpty()
            || this.effectiveOceanLevel() != null
            || !this.shapeFeatures.isEmpty()
            || !this.oreMorphFeatures.isEmpty();
    }

    public boolean allowsNaturalSpawnSearch() {
        return !this.worldType.isVoid()
            && !this.worldType.isFluidWorld()
            && !this.worldType.hasCeiling()
            && this.effectiveOceanLevel() == null;
    }

    public boolean requiresSafetyPlatformSpawn() {
        return this.worldType.isVoid()
            || this.worldType.isFluidWorld()
            || this.worldType.hasCeiling()
            || this.effectiveOceanLevel() != null;
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
        if (this.timeOfDay != null || this.forcesPermanentNight()) {
            summary.append(", time=").append(this.effectiveResolvedTimeOfDay());
            if (this.effectivePermanentTime()) {
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
        if (!this.shapeFeatures.isEmpty()) {
            summary.append(", shapes=").append(this.shapeFeatures.stream().map(spec -> spec.shape().serializedName()).collect(Collectors.joining("|")));
        }
        if (!this.oreMorphFeatures.isEmpty()) {
            summary.append(", ore-profiles=").append(this.oreMorphFeatures.stream().map(spec -> spec.profile().serializedName()).collect(Collectors.joining("|")));
        } else if (this.oreMultiplier != 1.0D) {
            summary.append(", ore x").append(trimmedDecimal(this.oreMultiplier));
        }
        if (Math.abs(this.mobSpawnMultiplier - 1.0D) > 0.00001D) {
            summary.append(", mob spawns x").append(trimmedDecimal(this.mobSpawnMultiplier));
        }
        if (!this.cavesEnabled) {
            summary.append(", caves off");
        }
        if (!this.chasmsEnabled) {
            summary.append(", chasms off");
        }
        if (!this.poolFeatures.isEmpty()) {
            summary.append(", pools=").append(this.poolFeatures.stream().map(spec -> spec.fluidId().toString()).collect(Collectors.joining("|")));
        }
        if (!this.structureControl.allowsAll()) {
            summary.append(", structures=").append(this.structureControl.mode().serializedName());
        }
        if (!this.skyProfile.sunVisible()) {
            summary.append(", no sun");
        }
        if (!this.skyProfile.moonVisible()) {
            summary.append(", no moon");
        }
        if (this.skyProfile.fogDensity() != 1.0D) {
            summary.append(", fog=").append(trimmedDecimal(this.skyProfile.fogDensity()));
        }
        if (this.crystalClusters) {
            summary.append(", crystals");
        }
        if (!this.corruptions.isEmpty()) {
            summary.append(", corruption=").append(this.corruptions.stream().map(VerseDimensionCorruption::id).collect(Collectors.joining("|")));
        }
        return summary.toString();
    }

    private static List<ShapeFeatureSpec> deriveShapeFeatures(List<ShapeFeatureSpec> configured, boolean legacySpheres, Block sphereBlock, Block floorBlock) {
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        if (!legacySpheres) {
            return List.of();
        }
        return List.of(ShapeFeatureSpec.legacySphere(sphereBlock != null ? sphereBlock : floorBlock));
    }

    private static List<PoolFeatureSpec> derivePoolFeatures(List<PoolFeatureSpec> configured, ResourceLocation fluidId, boolean lakes, VerseDimensionWorldType worldType) {
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        if (lakes || worldType == VerseDimensionWorldType.WATER_WORLD) {
            return List.of(PoolFeatureSpec.legacy(fluidId != null ? fluidId : ResourceLocation.withDefaultNamespace("water")));
        }
        return List.of();
    }

    private static List<OreMorphSpec> deriveOreMorphFeatures(List<OreMorphSpec> configured, double oreMultiplier) {
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        if (oreMultiplier <= 1.0D) {
            return List.of();
        }
        return List.of();
    }

    private static int randomBetween(RandomSource random, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
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

    private record LegacyCoreCodecState(
        int skyColor,
        Block floorBlock,
        Block sphereBlock,
        VerseDimensionWorldType worldType,
        List<ResourceLocation> biomeIds,
        double gravityScale,
        TemporalSettings temporal,
        double oreMultiplier,
        boolean spheres,
        ResourceLocation fluidId,
        boolean lakes,
        Integer oceanLevel,
        boolean structures,
        StructureControlProfile structureControl,
        List<ShapeFeatureSpec> shapeFeatures,
        List<PoolFeatureSpec> poolFeatures,
        List<OreMorphSpec> oreMorphFeatures,
        SurfaceProfile surfaceProfile,
        SkyProfile skyProfile
    ) {
    }

    private record ExpansionCodecState(
        StructureControlProfile structureControl,
        List<ShapeFeatureSpec> shapeFeatures,
        List<PoolFeatureSpec> poolFeatures,
        List<OreMorphSpec> oreMorphFeatures,
        SurfaceProfile surfaceProfile,
        SkyProfile skyProfile,
        boolean crystalClusters
    ) {
    }

    private record ExtraCodecState(
        double mobSpawnMultiplier,
        boolean cavesEnabled,
        boolean chasmsEnabled,
        List<VerseDimensionCorruption> corruptions,
        long seedOffset
    ) {
    }

    public record StructureControlProfile(StructureControlMode mode, List<String> allowedGroups, List<String> blockedGroups, List<ResourceLocation> exactAllow, List<ResourceLocation> exactBlock) {
        public static final StructureControlProfile DEFAULT = new StructureControlProfile(StructureControlMode.ALL, List.of(), List.of(), List.of(), List.of());
        public static final Codec<StructureControlProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StructureControlMode.CODEC.optionalFieldOf("mode", StructureControlMode.ALL).forGetter(StructureControlProfile::mode),
            Codec.STRING.listOf().optionalFieldOf("allowed_groups", List.of()).forGetter(StructureControlProfile::allowedGroups),
            Codec.STRING.listOf().optionalFieldOf("blocked_groups", List.of()).forGetter(StructureControlProfile::blockedGroups),
            IDENTIFIER_CODEC.listOf().optionalFieldOf("exact_allow", List.of()).forGetter(StructureControlProfile::exactAllow),
            IDENTIFIER_CODEC.listOf().optionalFieldOf("exact_block", List.of()).forGetter(StructureControlProfile::exactBlock)
        ).apply(instance, StructureControlProfile::new));

        public StructureControlProfile {
            mode = mode == null ? StructureControlMode.ALL : mode;
            allowedGroups = List.copyOf(allowedGroups);
            blockedGroups = List.copyOf(blockedGroups);
            exactAllow = List.copyOf(exactAllow);
            exactBlock = List.copyOf(exactBlock);
        }

        public boolean allowsAll() {
            return this.mode == StructureControlMode.ALL && this.allowedGroups.isEmpty() && this.blockedGroups.isEmpty() && this.exactAllow.isEmpty() && this.exactBlock.isEmpty();
        }
    }

    public enum StructureControlMode {
        ALL("all"),
        ALLOWLIST("allowlist"),
        DENYLIST("denylist"),
        NONE("none");

        public static final Codec<StructureControlMode> CODEC = Codec.STRING.xmap(StructureControlMode::parse, StructureControlMode::serializedName);
        private final String serializedName;

        StructureControlMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static StructureControlMode parse(String value) {
            for (StructureControlMode mode : values()) {
                if (mode.serializedName.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return ALL;
        }
    }

    public record ShapeFeatureSpec(ShapeKind shape, Block block, int minRadius, int maxRadius, double chancePerChunk, HeightDistribution heightDistribution, boolean hollow) {
        public static final Codec<ShapeFeatureSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ShapeKind.CODEC.optionalFieldOf("shape", ShapeKind.SPHERE).forGetter(ShapeFeatureSpec::shape),
            BLOCK_CODEC.fieldOf("block").forGetter(ShapeFeatureSpec::block),
            Codec.INT.optionalFieldOf("min_radius", 3).forGetter(ShapeFeatureSpec::minRadius),
            Codec.INT.optionalFieldOf("max_radius", 7).forGetter(ShapeFeatureSpec::maxRadius),
            Codec.DOUBLE.optionalFieldOf("chance_per_chunk", 0.12D).forGetter(ShapeFeatureSpec::chancePerChunk),
            HeightDistribution.CODEC.optionalFieldOf("height_distribution", HeightDistribution.DEFAULT).forGetter(ShapeFeatureSpec::heightDistribution),
            Codec.BOOL.optionalFieldOf("hollow", false).forGetter(ShapeFeatureSpec::hollow)
        ).apply(instance, ShapeFeatureSpec::new));

        public static ShapeFeatureSpec legacySphere(Block block) {
            return new ShapeFeatureSpec(ShapeKind.SPHERE, block, 3, 7, 0.12D, HeightDistribution.DEFAULT, false);
        }
    }

    public enum ShapeKind {
        SPHERE("sphere"),
        CUBE("cube");

        public static final Codec<ShapeKind> CODEC = Codec.STRING.xmap(ShapeKind::parse, ShapeKind::serializedName);
        private final String serializedName;

        ShapeKind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static ShapeKind parse(String value) {
            for (ShapeKind kind : values()) {
                if (kind.serializedName.equalsIgnoreCase(value)) {
                    return kind;
                }
            }
            return SPHERE;
        }
    }

    public record HeightDistribution(String profile) {
        public static final HeightDistribution DEFAULT = new HeightDistribution("default");
        public static final Codec<HeightDistribution> CODEC = Codec.STRING.xmap(HeightDistribution::new, HeightDistribution::profile);
    }

    public record PoolFeatureSpec(ResourceLocation fluidId, int radius, int depth, double chancePerChunk) {
        public static final Codec<PoolFeatureSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IDENTIFIER_CODEC.fieldOf("fluid").forGetter(PoolFeatureSpec::fluidId),
            Codec.INT.optionalFieldOf("radius", 4).forGetter(PoolFeatureSpec::radius),
            Codec.INT.optionalFieldOf("depth", 2).forGetter(PoolFeatureSpec::depth),
            Codec.DOUBLE.optionalFieldOf("chance_per_chunk", 0.28D).forGetter(PoolFeatureSpec::chancePerChunk)
        ).apply(instance, PoolFeatureSpec::new));

        public static PoolFeatureSpec legacy(ResourceLocation fluidId) {
            return new PoolFeatureSpec(fluidId, 4, 2, 0.28D);
        }
    }

    public record OreMorphSpec(OreProfile profile, Block replacementBlock, double multiplier) {
        public static final Codec<OreMorphSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OreProfile.CODEC.optionalFieldOf("profile", OreProfile.DEFAULT).forGetter(OreMorphSpec::profile),
            BLOCK_CODEC.fieldOf("replacement_block").forGetter(OreMorphSpec::replacementBlock),
            Codec.DOUBLE.optionalFieldOf("multiplier", 1.0D).forGetter(OreMorphSpec::multiplier)
        ).apply(instance, OreMorphSpec::new));
    }

    public enum OreProfile {
        DEFAULT("default"),
        COAL_SMALL("coal_small"),
        COPPER("copper"),
        IRON_LARGE("iron_large"),
        DIAMOND_DEEPSLATE_BIAS("diamond_deepslate_bias");

        public static final Codec<OreProfile> CODEC = Codec.STRING.xmap(OreProfile::parse, OreProfile::serializedName);
        private final String serializedName;

        OreProfile(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static OreProfile parse(String value) {
            for (OreProfile profile : values()) {
                if (profile.serializedName.equalsIgnoreCase(value)) {
                    return profile;
                }
            }
            return DEFAULT;
        }
    }

    public record SurfaceProfile(Block topBlock, Block fillerBlock, Block supportBlock, boolean preserveNaturalDecoration) {
        public static final SurfaceProfile DEFAULT = new SurfaceProfile(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, true);
        public static final Codec<SurfaceProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BLOCK_CODEC.optionalFieldOf("top_block", Blocks.GRASS_BLOCK).forGetter(SurfaceProfile::topBlock),
            BLOCK_CODEC.optionalFieldOf("filler_block", Blocks.DIRT).forGetter(SurfaceProfile::fillerBlock),
            BLOCK_CODEC.optionalFieldOf("support_block", Blocks.DIRT).forGetter(SurfaceProfile::supportBlock),
            Codec.BOOL.optionalFieldOf("preserve_natural_decoration", true).forGetter(SurfaceProfile::preserveNaturalDecoration)
        ).apply(instance, SurfaceProfile::new));

        public SurfaceProfile resolve(Block floorBlock) {
            if (this != DEFAULT || floorBlock == null || floorBlock == Blocks.GRASS_BLOCK) {
                return this;
            }
            return fromBlock(floorBlock);
        }

        public static SurfaceProfile fromBlock(Block block) {
            if (block == Blocks.MYCELIUM) {
                return new SurfaceProfile(Blocks.MYCELIUM, Blocks.DIRT, Blocks.DIRT, true);
            }
            if (block == Blocks.SAND) {
                return new SurfaceProfile(Blocks.SAND, Blocks.SANDSTONE, Blocks.SANDSTONE, false);
            }
            if (block == Blocks.PACKED_ICE) {
                return new SurfaceProfile(Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.DIRT, true);
            }
            return new SurfaceProfile(block, block, Blocks.DIRT, true);
        }
    }

    public record SkyProfile(boolean sunVisible, boolean moonVisible, double fogDensity) {
        public static final SkyProfile DEFAULT = new SkyProfile(true, true, 1.0D);
        public static final Codec<SkyProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("sun_visible", true).forGetter(SkyProfile::sunVisible),
            Codec.BOOL.optionalFieldOf("moon_visible", true).forGetter(SkyProfile::moonVisible),
            Codec.DOUBLE.optionalFieldOf("fog_density", 1.0D).forGetter(SkyProfile::fogDensity)
        ).apply(instance, SkyProfile::new));

        public SkyProfile resolve() {
            return new SkyProfile(this.sunVisible, this.moonVisible, clamp(this.fogDensity, 0.2D, 4.0D));
        }
    }
}
