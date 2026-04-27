package com.kyden.verseworks.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kyden.verseworks.worldgen.MysticRuinStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class VerseChunkGenerator extends NoiseBasedChunkGenerator {
    private static final long TERRAIN_SALT = 0x5445525241494EL;
    private static final long ORE_SALT = 0x4F5245534CL;
    private static final long LAKE_SALT = 0x4C414B454CL;
    private static final long SPHERE_SALT = 0x5350484552L;
    private static final int FLAT_SURFACE_Y = 64;
    private static volatile Method createNoiseChunkMethod;

    public static final MapCodec<VerseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(VerseChunkGenerator::getBiomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(VerseChunkGenerator::generatorSettings),
        VerseDimensionParameters.CODEC.fieldOf("verseworks").forGetter(VerseChunkGenerator::parameters)
    ).apply(instance, VerseChunkGenerator::new));

    private final VerseDimensionParameters parameters;

    public VerseChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> generatorSettings, VerseDimensionParameters parameters) {
        super(biomeSource, generatorSettings);
        this.parameters = parameters;
    }

    public VerseDimensionParameters parameters() {
        return this.parameters;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        if (this.parameters.worldType().isVoid()) {
            return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.getBiomeSource(), Stream.empty());
        }

        VerseDimensionParameters.StructureControlProfile structureControl = this.parameters.structureControl();
        if (!structureControl.allowsAll()) {
            Stream<Holder<StructureSet>> allowedStructureSets = structureSets.listElements()
                .filter(holder -> allowsStructureSet(holder, structureControl))
                .map(holder -> (Holder<StructureSet>) holder);
            return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.getBiomeSource(), allowedStructureSets);
        }

        return super.createState(structureSets, randomState, seed);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
        super.buildSurface(region, structureManager, randomState, chunk);
        applyTerrainProfile(chunk, randomState);
        if (requiresPreDecorationShellEnforcement()) {
            enforceBedrockShell(chunk);
        }
        applyBiomeSurfaceOverrides(chunk, randomState);
        applyOceanLevel(chunk);
        applyPools(chunk);
        applyShapes(chunk);
        if (this.parameters.worldType() == VerseDimensionWorldType.OCEAN) {
            floodSubmergedAirPockets(chunk);
        }
        if (shouldStabilizeFloatingTerrain()) {
            stabilizeUnsupportedFallingBlocks(chunk);
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (this.parameters.worldType() == VerseDimensionWorldType.OCEAN) {
            floodSubmergedAirPockets(chunk);
        }
        applyPostDecorationSurfaceOverrides(level, chunk);
        if (shouldStabilizeFloatingTerrain()) {
            stabilizeUnsupportedFallingBlocks(chunk);
        }
        if (this.parameters.worldType().hasBedrockShell()) {
            enforceBedrockShell(chunk);
        }
        applyOreMorphRules(chunk);
    }

    @Override
    public int getSeaLevel() {
        return this.parameters.effectiveOceanLevel() != null ? this.parameters.effectiveOceanLevel() : super.getSeaLevel();
    }

    @Override
    public void applyCarvers(
        WorldGenRegion level,
        long seed,
        RandomState random,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.Carving step
    ) {
        if (!requiresCarverFiltering()) {
            super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
            return;
        }
        if (!(chunk instanceof ProtoChunk protoChunk)) {
            super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
            return;
        }

        BiomeManager biomeSource = biomeManager.withDifferentSource(
            (quartX, quartY, quartZ) -> this.getBiomeSource().getNoiseBiome(quartX, quartY, quartZ, random.sampler())
        );
        WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos chunkPos = chunk.getPos();
        NoiseChunk noiseChunk = resolveNoiseChunk(level, structureManager, chunk, random);
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext carvingContext = new CarvingContext(
            this,
            level.registryAccess(),
            chunk.getHeightAccessorForGeneration(),
            noiseChunk,
            random,
            this.generatorSettings().value().surfaceRule()
        );
        CarvingMask carvingMask = protoChunk.getOrCreateCarvingMask(step);

        for (int offsetX = -8; offsetX <= 8; offsetX++) {
            for (int offsetZ = -8; offsetZ <= 8; offsetZ++) {
                ChunkPos sourceChunkPos = new ChunkPos(chunkPos.x + offsetX, chunkPos.z + offsetZ);
                ChunkAccess sourceChunk = level.getChunk(sourceChunkPos.x, sourceChunkPos.z);
                BiomeGenerationSettings generationSettings = sourceChunk.carverBiome(
                    () -> this.getBiomeGenerationSettings(
                        this.getBiomeSource().getNoiseBiome(
                            QuartPos.fromBlock(sourceChunkPos.getMinBlockX()),
                            0,
                            QuartPos.fromBlock(sourceChunkPos.getMinBlockZ()),
                            random.sampler()
                        )
                    )
                );
                int index = 0;
                for (Holder<ConfiguredWorldCarver<?>> holder : generationSettings.getCarvers(step)) {
                    ResourceLocation carverId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
                    if (shouldSkipCarver(carverId)) {
                        index++;
                        continue;
                    }

                    ConfiguredWorldCarver<?> configuredWorldCarver = holder.value();
                    worldgenRandom.setLargeFeatureSeed(seed + index, sourceChunkPos.x, sourceChunkPos.z);
                    if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
                        configuredWorldCarver.carve(carvingContext, chunk, biomeSource::getBiome, worldgenRandom, aquifer, sourceChunkPos, carvingMask);
                    }
                    index++;
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        super.spawnOriginalMobs(level);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos position) {
        super.addDebugScreenInfo(lines, randomState, position);
        lines.add("VerseWorks: " + this.parameters.rulesSummary());
    }

    private void applyOceanLevel(ChunkAccess chunk) {
        Integer configuredOceanLevel = this.parameters.effectiveOceanLevel();
        if (configuredOceanLevel == null) {
            return;
        }

        Fluid fluid = resolveFluid();
        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int oceanLevel = Math.min(configuredOceanLevel, minY + chunk.getHeight() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
                if (surfaceY >= oceanLevel) {
                    continue;
                }

                for (int y = Math.max(minY + 1, surfaceY + 1); y <= oceanLevel; y++) {
                    cursor.set(chunkPos.getMinBlockX() + localX, y, chunkPos.getMinBlockZ() + localZ);
                    if (chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, fluidState, false);
                    }
                }
            }
        }
    }

    private void applyTerrainProfile(ChunkAccess chunk, RandomState randomState) {
        switch (this.parameters.worldType()) {
            case SKY_ISLAND -> applySkyIslandTerrain(chunk, randomState);
            case ISLAND -> applyIslandTerrain(chunk, randomState);
            case ALL_STONE -> applyAllStoneTerrain(chunk, randomState);
            case BEDROCK_SHELL -> applyBedrockShell(chunk, randomState);
            case OCEAN -> applyOceanTerrain(chunk, randomState);
            case CAVE_ONLY -> applyCaveOnlyTerrain(chunk, randomState);
            case WATER_WORLD -> applyWaterWorldTerrain(chunk);
            case CAVERN -> applyCavernTerrain(chunk, randomState);
            case INVERSE_CAVES -> applyInverseCavesTerrain(chunk, randomState);
            case FLAT -> applyFlatTerrain(chunk, randomState);
            case VOID -> clearChunk(chunk);
            default -> {
            }
        }
    }

    private void applySkyIslandTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                double largeShape = sampleTerrainNoise(worldX, worldZ, 112.0D, 3, 0x13579BDFL);
                double islandMask = sampleTerrainNoise(worldX, worldZ, 52.0D, 3, 0x2468ACE0L);
                double detail = sampleTerrainNoise(worldX, worldZ, 20.0D, 2, 0x51F15EEDL);
                double signal = largeShape * 0.85D + islandMask * 0.45D + detail * 0.18D - 0.32D;
                if (signal <= 0.0D) {
                    clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                    continue;
                }

                int topY = 140
                    + Mth.floor(largeShape * 18.0D)
                    + Mth.floor(islandMask * 12.0D)
                    + Mth.floor(detail * 5.0D);
                int thickness = 7 + Mth.floor(Math.max(0.0D, signal) * 18.0D);
                int undersideDrop = signal > 0.42D ? 2 + Mth.floor((signal - 0.42D) * 18.0D) : 0;
                int bottomY = topY - thickness - undersideDrop;
                if (topY < minY || bottomY > maxY) {
                    continue;
                }

                BlockPalette palette = terrainPaletteForColumn(chunk, cursor, worldX, worldZ, minY, maxY, surfacePalette(worldX, topY, worldZ, randomState));
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);

                for (int y = Math.max(bottomY, minY); y <= Math.min(topY, maxY); y++) {
                    cursor.set(worldX, y, worldZ);
                    int depth = topY - y;
                    if (depth == 0) {
                        chunk.setBlockState(cursor, palette.topState(), false);
                    } else if (depth < 4) {
                        chunk.setBlockState(cursor, palette.fillerState(), false);
                    } else {
                        chunk.setBlockState(cursor, palette.coreState(), false);
                    }
                }
            }
        }
    }

    private void applyIslandTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = this.parameters.effectiveOceanLevel() != null ? this.parameters.effectiveOceanLevel() : super.getSeaLevel();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                double continent = sampleTerrainNoise(worldX, worldZ, 120.0D, 3, 0x1A2B3C4DL);
                double detail = sampleTerrainNoise(worldX, worldZ, 38.0D, 2, 0x4D3C2B1AL);
                double islandSignal = continent * 0.85D + detail * 0.25D - 0.18D;
                if (islandSignal <= 0.0D) {
                    clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                    continue;
                }

                int topY = seaLevel + 1 + Mth.floor(islandSignal * 22.0D) + Mth.floor(detail * 4.0D);
                int thickness = 10 + Mth.floor(Math.max(0.0D, islandSignal) * 16.0D);
                int bottomY = Math.max(minY + 24, topY - thickness);
                BlockPalette palette = terrainPaletteForColumn(chunk, cursor, worldX, worldZ, minY, maxY, surfacePalette(worldX, topY, worldZ, randomState));
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                fillTerrainColumn(chunk, cursor, worldX, worldZ, minY, maxY, bottomY, topY, palette);
            }
        }
    }

    private void applyAllStoneTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                fillSolidCoreColumn(chunk, cursor, worldX, worldZ, minY + 1, maxY - 1, surfacePalette(worldX, maxY - 1, worldZ, randomState));
                applyBedrockColumn(chunk, cursor, worldX, worldZ, minY, maxY);
            }
        }
    }

    private void applyOceanTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = this.parameters.effectiveOceanLevel() != null ? this.parameters.effectiveOceanLevel() : super.getSeaLevel();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int surfaceY = sampleOceanFloorHeight(worldX, worldZ, minY, maxY, seaLevel);
                BlockPalette palette = submergedOceanPalette(
                    terrainPaletteForColumn(chunk, cursor, worldX, worldZ, minY, maxY, surfacePalette(worldX, surfaceY, worldZ, randomState))
                );
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                fillTerrainColumn(chunk, cursor, worldX, worldZ, minY, maxY, minY, surfaceY, palette);
            }
        }
    }

    private void applyCaveOnlyTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int topCeilingY = Math.max(minY + 5, maxY - 5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                BlockPalette palette = surfacePalette(worldX, topCeilingY - 1, worldZ, randomState);
                fillSolidCoreColumn(chunk, cursor, worldX, worldZ, minY + 1, maxY - 1, palette);
                applyBedrockColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                carveCaverns(chunk, cursor, worldX, worldZ, minY + 7, topCeilingY - 6, 0.16D, 34.0D, 12.0D, 0x43415645L);
            }
        }
    }

    private void applyWaterWorldTerrain(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int topCeilingY = Math.max(minY + 5, maxY - 5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Fluid fluid = resolveFluid();
        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            fluidState = net.minecraft.world.level.material.Fluids.WATER.defaultFluidState().createLegacyBlock();
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                applyBedrockColumn(chunk, cursor, worldX, worldZ, minY, maxY);

                for (int y = minY + 5; y <= topCeilingY - 1; y++) {
                    cursor.set(worldX, y, worldZ);
                    chunk.setBlockState(cursor, fluidState, false);
                }
            }
        }
    }

    private void applyCavernTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int topCeilingY = Math.max(minY + 5, maxY - 5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                applyBedrockColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                fillTerrainColumn(chunk, cursor, worldX, worldZ, minY, maxY, minY + 5, topCeilingY, surfacePalette(worldX, topCeilingY, worldZ, randomState));
                carveCaverns(chunk, cursor, worldX, worldZ, minY + 7, topCeilingY - 4, 0.08D, 44.0D, 18.0D, 0x4E455448L);
            }
        }
    }

    private void applyInverseCavesTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int highestSolidY = minY;
                BlockPalette palette = terrainPaletteForColumn(chunk, cursor, worldX, worldZ, minY, maxY, surfacePalette(worldX, maxY - 12, worldZ, randomState));
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);
                for (int y = minY + 1; y <= maxY - 1; y++) {
                    double primary = sampleVolumeNoise(worldX, y, worldZ, 42.0D, 3, 0x49564E56L);
                    double detail = sampleVolumeNoise(worldX, y, worldZ, 16.0D, 2, 0x54454E44L);
                    double tendrilSignal = primary - Math.abs(detail) * 0.35D;
                    if (tendrilSignal <= 0.36D) {
                        continue;
                    }

                    cursor.set(worldX, y, worldZ);
                    chunk.setBlockState(cursor, terrainCoreState(palette.coreState(), y), false);
                    highestSolidY = y;
                }

                if (highestSolidY > minY + 1) {
                    replaceSurfaceColumn(chunk, cursor, worldX, worldZ, highestSolidY, minY, palette);
                }
            }
        }
    }

    private void applyBedrockShell(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                BlockPalette palette = terrainPaletteForColumn(chunk, cursor, worldX, worldZ, minY, maxY, surfacePalette(worldX, maxY - 12, worldZ, randomState));
                int currentSurfaceY = findHighestTerrainY(chunk, cursor, worldX, worldZ, minY, maxY);
                int targetSurfaceY = desiredBedrockShellSurfaceY(worldX, worldZ, minY, maxY);
                if (currentSurfaceY >= targetSurfaceY) {
                    continue;
                }

                fillTerrainColumn(chunk, cursor, worldX, worldZ, minY, maxY, currentSurfaceY + 1, targetSurfaceY, palette);
            }
        }
    }

    private int findHighestTerrainY(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int minY, int maxY) {
        for (int y = maxY - 6; y >= minY + 1; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(cursor);
            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                return y;
            }
        }

        return minY;
    }

    private int desiredBedrockShellSurfaceY(int worldX, int worldZ, int minY, int maxY) {
        double broadRelief = sampleTerrainNoise(worldX, worldZ, 132.0D, 3, 0x5348454CL);
        double detail = sampleTerrainNoise(worldX, worldZ, 44.0D, 2, 0x48594C4CL);
        int roofClearance = 26
            + Mth.floor((broadRelief + 1.0D) * 8.0D)
            + Mth.floor(detail * 6.0D);
        return Mth.clamp(maxY - 8 - roofClearance, minY + 28, maxY - 40);
    }

    private void applyBedrockColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int minY, int maxY) {
        RandomSource random = chunkRandom(new ChunkPos(worldX >> 4, worldZ >> 4), 0x42454452L ^ worldX ^ ((long) worldZ << 32));
        int floorThickness = 3 + random.nextInt(3);
        int ceilingThickness = 3 + random.nextInt(3);
        for (int y = minY; y < minY + floorThickness; y++) {
            cursor.set(worldX, y, worldZ);
            chunk.setBlockState(cursor, Blocks.BEDROCK.defaultBlockState(), false);
        }
        for (int y = maxY; y > maxY - ceilingThickness; y--) {
            cursor.set(worldX, y, worldZ);
            chunk.setBlockState(cursor, Blocks.BEDROCK.defaultBlockState(), false);
        }
    }

    private void enforceBedrockShell(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                applyBedrockColumn(chunk, cursor, worldX, worldZ, minY, maxY);
            }
        }
    }

    private boolean requiresPreDecorationShellEnforcement() {
        return this.parameters.worldType() == VerseDimensionWorldType.WATER_WORLD
            || this.parameters.worldType() == VerseDimensionWorldType.CAVERN;
    }

    private void fillTerrainColumn(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int worldX,
        int worldZ,
        int minY,
        int maxY,
        int bottomY,
        int topY,
        BlockPalette palette
    ) {
        if (topY < minY) {
            return;
        }

        int startY = Math.max(minY + 1, bottomY);
        int endY = Math.min(topY, maxY);
        if (bottomY <= minY + 1) {
            cursor.set(worldX, minY, worldZ);
            chunk.setBlockState(cursor, Blocks.BEDROCK.defaultBlockState(), false);
        }
        for (int y = startY; y <= endY; y++) {
            cursor.set(worldX, y, worldZ);
            int depth = endY - y;
            if (depth == 0) {
                chunk.setBlockState(cursor, palette.topState(), false);
            } else if (depth < 4) {
                chunk.setBlockState(cursor, palette.fillerState(), false);
            } else {
                chunk.setBlockState(cursor, terrainCoreState(palette.coreState(), y), false);
            }
        }
    }

    private void fillSolidCoreColumn(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int worldX,
        int worldZ,
        int startY,
        int endY,
        BlockPalette palette
    ) {
        for (int y = startY; y <= endY; y++) {
            cursor.set(worldX, y, worldZ);
            chunk.setBlockState(cursor, terrainCoreState(palette.coreState(), y), false);
        }
    }

    private void carveCaverns(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int worldX,
        int worldZ,
        int minY,
        int maxY,
        double threshold,
        double primaryScale,
        double detailScale,
        long salt
    ) {
        if (maxY <= minY) {
            return;
        }

        for (int y = minY; y <= maxY; y++) {
            double cavern = sampleVolumeNoise(worldX, y, worldZ, primaryScale, 3, salt);
            double detail = sampleVolumeNoise(worldX, y, worldZ, detailScale, 2, salt ^ 0x5DEECE66DL);
            if (cavern + detail * 0.35D <= threshold) {
                continue;
            }

            cursor.set(worldX, y, worldZ);
            chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
        }
    }

    private void applyFlatTerrain(ChunkAccess chunk, RandomState randomState) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int topY = Mth.clamp(FLAT_SURFACE_Y, minY + 3, maxY);
        int stoneY = topY - 1;
        int lowerStoneY = topY - 2;
        int bedrockY = topY - 3;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                BlockPalette palette = surfacePalette(worldX, topY, worldZ, randomState);
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);

                cursor.set(worldX, bedrockY, worldZ);
                chunk.setBlockState(cursor, Blocks.BEDROCK.defaultBlockState(), false);
                cursor.set(worldX, lowerStoneY, worldZ);
                chunk.setBlockState(cursor, palette.coreState(), false);
                cursor.set(worldX, stoneY, worldZ);
                chunk.setBlockState(cursor, palette.fillerState(), false);
                cursor.set(worldX, topY, worldZ);
                chunk.setBlockState(cursor, palette.topState(), false);
            }
        }
    }

    private void clearChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                clearColumn(chunk, cursor, chunkPos.getMinBlockX() + localX, chunkPos.getMinBlockZ() + localZ, minY, maxY);
            }
        }
    }

    private void clearColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            cursor.set(worldX, y, worldZ);
            if (!chunk.getBlockState(cursor).isAir()) {
                chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
            }
        }
    }

    private void applyBiomeSurfaceOverrides(ChunkAccess chunk, RandomState randomState) {
        if (this.parameters.worldType().isFlat() || this.parameters.worldType().isVoid()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int surfaceY = findExposedSurfaceY(chunk, cursor, worldX, worldZ, minY, maxY);
                if (surfaceY < minY || surfaceY > maxY) {
                    continue;
                }

                BlockPalette palette = surfaceOverridePalette(surfacePalette(worldX, surfaceY, worldZ, randomState), surfaceY);
                if (palette == null) {
                    continue;
                }
                replaceSurfaceColumn(chunk, cursor, worldX, worldZ, surfaceY, minY, palette);
            }
        }
    }

    private void applyPostDecorationSurfaceOverrides(WorldGenLevel level, ChunkAccess chunk) {
        if (this.parameters.worldType().isFlat() || this.parameters.worldType().isVoid()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int surfaceY = findExposedSurfaceY(chunk, cursor, worldX, worldZ, minY, maxY);
                if (surfaceY < minY || surfaceY > maxY) {
                    continue;
                }

                BlockPalette palette = surfaceOverridePalette(surfacePalette(level, worldX, surfaceY, worldZ), surfaceY);
                if (palette == null) {
                    continue;
                }
                replaceSurfaceColumn(chunk, cursor, worldX, worldZ, surfaceY, minY, palette);
            }
        }
    }

    private void replaceSurfaceColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int surfaceY, int minY, BlockPalette palette) {
        cursor.set(worldX, surfaceY, worldZ);
        if (chunk.getBlockState(cursor).is(Blocks.BEDROCK)) {
            return;
        }

        int targetDepth = palette.isSpecial() ? 8 : 4;
        int replacementDepth = 0;
        for (int y = surfaceY; y >= minY && replacementDepth < targetDepth; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState currentState = chunk.getBlockState(cursor);
            if (currentState.isAir()) {
                continue;
            }
            if (currentState.is(Blocks.BEDROCK)) {
                continue;
            }

            BlockState replacementState = replacementDepth == 0
                ? palette.topState()
                : replacementDepth < 3 ? palette.fillerState() : palette.coreState();
            if (!currentState.equals(replacementState)) {
                chunk.setBlockState(cursor, replacementState, false);
            }
            replacementDepth++;
        }
    }

    private int findExposedSurfaceY(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(cursor);
            if (!isTerrainSurfaceCandidate(chunk, cursor, state)) {
                continue;
            }
            return y;
        }

        return minY - 1;
    }

    private boolean isTerrainSurfaceCandidate(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(Blocks.BEDROCK)) {
            return false;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        if (state.is(Blocks.CRIMSON_STEM)
            || state.is(Blocks.WARPED_STEM)
            || state.is(Blocks.STRIPPED_CRIMSON_STEM)
            || state.is(Blocks.STRIPPED_WARPED_STEM)
            || state.is(Blocks.NETHER_WART_BLOCK)
            || state.is(Blocks.WARPED_WART_BLOCK)
            || state.is(Blocks.SHROOMLIGHT)
            || state.is(Blocks.WEEPING_VINES)
            || state.is(Blocks.WEEPING_VINES_PLANT)
            || state.is(Blocks.TWISTING_VINES)
            || state.is(Blocks.TWISTING_VINES_PLANT)) {
            return false;
        }
        if (!state.getCollisionShape(chunk, cursor).isEmpty() && state.isFaceSturdy(chunk, cursor, Direction.UP)) {
            return true;
        }

        return false;
    }

    private BlockPalette terrainPaletteForColumn(
        ChunkAccess chunk,
        BlockPos.MutableBlockPos cursor,
        int worldX,
        int worldZ,
        int minY,
        int maxY,
        BlockPalette fallback
    ) {
        if (fallback.isSpecial()) {
            return fallback;
        }

        int surfaceY = findExposedSurfaceY(chunk, cursor, worldX, worldZ, minY, maxY);
        if (surfaceY < minY || surfaceY > maxY) {
            return fallback;
        }

        cursor.set(worldX, surfaceY, worldZ);
        BlockState topState = chunk.getBlockState(cursor);
        BlockState fillerState = topState;
        BlockState coreState = fallback.coreState();
        int solidDepth = 0;
        for (int y = surfaceY - 1; y >= minY; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(cursor);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }

            solidDepth++;
            if (solidDepth == 1) {
                fillerState = state;
            }
            if (solidDepth >= 3) {
                coreState = state;
                break;
            }
        }

        if (fillerState.isAir() || !fillerState.getFluidState().isEmpty()) {
            fillerState = topState;
        }
        if (coreState.isAir() || !coreState.getFluidState().isEmpty()) {
            coreState = fillerState;
        }

        return new BlockPalette(topState, fillerState, coreState, false);
    }

    private BlockPalette surfacePalette(int worldX, int surfaceY, int worldZ, RandomState randomState) {
        BiomeSurfaceFamily family = biomeSurfaceFamily(worldX, surfaceY, worldZ, randomState);
        return surfacePalette(family);
    }

    private BlockPalette surfacePalette(WorldGenLevel level, int worldX, int surfaceY, int worldZ) {
        BlockPos pos = new BlockPos(worldX, surfaceY, worldZ);
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(this.parameters.primaryBiomeId());
        return surfacePalette(BiomeSurfaceFamily.from(biomeHolder, biomeId));
    }

    private BlockPalette surfacePalette(BiomeSurfaceFamily family) {
        return switch (family) {
            case END -> new BlockPalette(Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(), true);
            case CRIMSON_FOREST -> new BlockPalette(Blocks.CRIMSON_NYLIUM.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            case WARPED_FOREST -> new BlockPalette(Blocks.WARPED_NYLIUM.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            case SOUL_SAND_VALLEY -> new BlockPalette(Blocks.SOUL_SAND.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            case BASALT_DELTAS -> new BlockPalette(Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), true);
            case NETHER -> new BlockPalette(Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            case OVERWORLD -> {
                VerseDimensionParameters.SurfaceProfile surfaceProfile = this.parameters.surfaceProfile();
                yield new BlockPalette(
                    surfaceProfile.topBlock().defaultBlockState(),
                    surfaceProfile.fillerBlock().defaultBlockState(),
                    surfaceProfile.supportBlock().defaultBlockState(),
                    false
                );
            }
        };
    }

    private BiomeSurfaceFamily biomeSurfaceFamily(int worldX, int surfaceY, int worldZ, RandomState randomState) {
        Climate.Sampler sampler = randomState.sampler();
        Holder<Biome> biomeHolder = this.getBiomeSource().getNoiseBiome(
            QuartPos.fromBlock(worldX),
            QuartPos.fromBlock(surfaceY),
            QuartPos.fromBlock(worldZ),
            sampler
        );
        ResourceLocation biomeId = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(this.parameters.primaryBiomeId());
        return BiomeSurfaceFamily.from(biomeHolder, biomeId);
    }

    private double sampleTerrainNoise(int worldX, int worldZ, double scale, int octaves, long salt) {
        double amplitude = 1.0D;
        double frequency = 1.0D / scale;
        double total = 0.0D;
        double maxAmplitude = 0.0D;
        for (int octave = 0; octave < octaves; octave++) {
            total += sampleValueNoise(worldX * frequency, worldZ * frequency, salt + octave * 0x9E3779B97F4A7C15L) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= 0.5D;
            frequency *= 2.0D;
        }
        return maxAmplitude == 0.0D ? 0.0D : total / maxAmplitude;
    }

    private double sampleValueNoise(double x, double z, long salt) {
        int minCellX = Mth.floor(x);
        int minCellZ = Mth.floor(z);
        double localX = x - minCellX;
        double localZ = z - minCellZ;
        double smoothX = localX * localX * (3.0D - 2.0D * localX);
        double smoothZ = localZ * localZ * (3.0D - 2.0D * localZ);

        double v00 = hashToUnit(minCellX, minCellZ, salt);
        double v10 = hashToUnit(minCellX + 1, minCellZ, salt);
        double v01 = hashToUnit(minCellX, minCellZ + 1, salt);
        double v11 = hashToUnit(minCellX + 1, minCellZ + 1, salt);
        double x0 = Mth.lerp(smoothX, v00, v10);
        double x1 = Mth.lerp(smoothX, v01, v11);
        return Mth.lerp(smoothZ, x0, x1);
    }

    private int sampleSurfaceHeight(int worldX, int worldZ, int minY, int maxY) {
        int seaLevel = this.parameters.effectiveOceanLevel() != null ? this.parameters.effectiveOceanLevel() : super.getSeaLevel();
        double continental = sampleTerrainNoise(worldX, worldZ, 164.0D, 4, 0x53465246L);
        double ridges = sampleTerrainNoise(worldX, worldZ, 72.0D, 3, 0x52494447L);
        double detail = sampleTerrainNoise(worldX, worldZ, 26.0D, 2, 0x44455441L);
        int height = seaLevel
            + Mth.floor(continental * 24.0D)
            + Mth.floor(ridges * 10.0D)
            + Mth.floor(detail * 5.0D);
        return Mth.clamp(height, minY + 8, maxY - 8);
    }

    private int sampleOceanFloorHeight(int worldX, int worldZ, int minY, int maxY, int seaLevel) {
        double continental = sampleTerrainNoise(worldX, worldZ, 196.0D, 4, 0x4F434541L);
        double ridges = sampleTerrainNoise(worldX, worldZ, 88.0D, 3, 0x44455054L);
        double detail = sampleTerrainNoise(worldX, worldZ, 30.0D, 2, 0x5348454CL);
        int height = seaLevel - 18
            + Mth.floor(continental * 18.0D)
            + Mth.floor(ridges * 7.0D)
            + Mth.floor(detail * 3.0D);
        return Mth.clamp(height, minY + 10, seaLevel - 6);
    }

    private BlockState terrainCoreState(BlockState coreState, int y) {
        if (y >= 0 || !coreState.is(Blocks.STONE)) {
            return coreState;
        }

        return Blocks.DEEPSLATE.defaultBlockState();
    }

    private double sampleVolumeNoise(int worldX, int worldY, int worldZ, double scale, int octaves, long salt) {
        double amplitude = 1.0D;
        double frequency = 1.0D / scale;
        double total = 0.0D;
        double maxAmplitude = 0.0D;
        for (int octave = 0; octave < octaves; octave++) {
            total += sampleValueNoise3D(
                worldX * frequency,
                worldY * frequency,
                worldZ * frequency,
                salt + octave * 0x9E3779B97F4A7C15L
            ) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= 0.5D;
            frequency *= 2.0D;
        }
        return maxAmplitude == 0.0D ? 0.0D : total / maxAmplitude;
    }

    private double sampleValueNoise3D(double x, double y, double z, long salt) {
        int minCellX = Mth.floor(x);
        int minCellY = Mth.floor(y);
        int minCellZ = Mth.floor(z);
        double localX = x - minCellX;
        double localY = y - minCellY;
        double localZ = z - minCellZ;
        double smoothX = localX * localX * (3.0D - 2.0D * localX);
        double smoothY = localY * localY * (3.0D - 2.0D * localY);
        double smoothZ = localZ * localZ * (3.0D - 2.0D * localZ);

        double c000 = hashToUnit3D(minCellX, minCellY, minCellZ, salt);
        double c100 = hashToUnit3D(minCellX + 1, minCellY, minCellZ, salt);
        double c010 = hashToUnit3D(minCellX, minCellY + 1, minCellZ, salt);
        double c110 = hashToUnit3D(minCellX + 1, minCellY + 1, minCellZ, salt);
        double c001 = hashToUnit3D(minCellX, minCellY, minCellZ + 1, salt);
        double c101 = hashToUnit3D(minCellX + 1, minCellY, minCellZ + 1, salt);
        double c011 = hashToUnit3D(minCellX, minCellY + 1, minCellZ + 1, salt);
        double c111 = hashToUnit3D(minCellX + 1, minCellY + 1, minCellZ + 1, salt);

        double x00 = Mth.lerp(smoothX, c000, c100);
        double x10 = Mth.lerp(smoothX, c010, c110);
        double x01 = Mth.lerp(smoothX, c001, c101);
        double x11 = Mth.lerp(smoothX, c011, c111);
        double y0 = Mth.lerp(smoothY, x00, x10);
        double y1 = Mth.lerp(smoothY, x01, x11);
        return Mth.lerp(smoothZ, y0, y1);
    }

    private double hashToUnit(int cellX, int cellZ, long salt) {
        long mixed = this.parameters.seedOffset() ^ TERRAIN_SALT ^ salt;
        mixed ^= (long) cellX * 341873128712L;
        mixed ^= (long) cellZ * 132897987541L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        long mantissa = mixed & ((1L << 53) - 1L);
        return mantissa / (double) (1L << 53) * 2.0D - 1.0D;
    }

    private double hashToUnit3D(int cellX, int cellY, int cellZ, long salt) {
        long mixed = this.parameters.seedOffset() ^ TERRAIN_SALT ^ salt;
        mixed ^= (long) cellX * 341873128712L;
        mixed ^= (long) cellY * 42317861L;
        mixed ^= (long) cellZ * 132897987541L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        long mantissa = mixed & ((1L << 53) - 1L);
        return mantissa / (double) (1L << 53) * 2.0D - 1.0D;
    }

    private long chunkPosSeed(ChunkPos chunkPos) {
        return ((long) chunkPos.x * 341873128712L) ^ ((long) chunkPos.z * 132897987541L);
    }

    private boolean shouldSkipCarver(ResourceLocation carverId) {
        if (carverId == null || !"minecraft".equals(carverId.getNamespace())) {
            return false;
        }

        String path = carverId.getPath();
        if ((this.parameters.worldType() == VerseDimensionWorldType.WATER_WORLD || this.parameters.worldType() == VerseDimensionWorldType.OCEAN)
            && ("cave".equals(path) || "cave_extra_underground".equals(path) || "nether_cave".equals(path) || "canyon".equals(path))) {
            return true;
        }
        if (!this.parameters.cavesEnabled() && ("cave".equals(path) || "cave_extra_underground".equals(path) || "nether_cave".equals(path))) {
            return true;
        }

        return !this.parameters.chasmsEnabled() && "canyon".equals(path);
    }

    private boolean requiresCarverFiltering() {
        return this.parameters.worldType() == VerseDimensionWorldType.WATER_WORLD
            || this.parameters.worldType() == VerseDimensionWorldType.OCEAN
            || !this.parameters.cavesEnabled()
            || !this.parameters.chasmsEnabled();
    }

    private BlockPalette surfaceOverridePalette(BlockPalette palette, int surfaceY) {
        if (this.parameters.worldType() == VerseDimensionWorldType.OCEAN && surfaceY < getSeaLevel()) {
            return submergedOceanPalette(palette);
        }

        if (!this.parameters.surfaceProfile().equals(VerseDimensionParameters.SurfaceProfile.DEFAULT)) {
            return palette;
        }

        return palette.isSpecial() ? palette : null;
    }

    private BlockPalette submergedOceanPalette(BlockPalette palette) {
        if (palette.isSpecial()) {
            return palette;
        }

        BlockState topState = palette.fillerState();
        if (topState.equals(palette.topState())) {
            topState = palette.coreState();
        }

        if (topState.isAir() || !topState.getFluidState().isEmpty()) {
            topState = palette.coreState();
        }

        BlockState fillerState = palette.coreState();
        if (fillerState.isAir() || !fillerState.getFluidState().isEmpty()) {
            fillerState = topState;
        }

        return new BlockPalette(topState, fillerState, palette.coreState(), false);
    }

    private void floodSubmergedAirPockets(ChunkAccess chunk) {
        int oceanLevel = getSeaLevel();
        int minY = chunk.getMinBuildHeight();
        int maxY = Math.min(oceanLevel, minY + chunk.getHeight() - 1);
        if (maxY <= minY) {
            return;
        }

        BlockState fluidState = resolveFluid().defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            fluidState = net.minecraft.world.level.material.Fluids.WATER.defaultFluidState().createLegacyBlock();
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                for (int y = minY + 1; y <= maxY; y++) {
                    cursor.set(worldX, y, worldZ);
                    if (chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, fluidState, false);
                    }
                }
            }
        }
    }

    private boolean shouldStabilizeFloatingTerrain() {
        return this.parameters.worldType() == VerseDimensionWorldType.ISLAND || this.parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND;
    }

    private void stabilizeUnsupportedFallingBlocks(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowCursor = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                for (int y = minY + 1; y <= maxY; y++) {
                    cursor.set(worldX, y, worldZ);
                    BlockState fallingState = chunk.getBlockState(cursor);
                    if (!(fallingState.getBlock() instanceof FallingBlock)) {
                        continue;
                    }

                    belowCursor.set(worldX, y - 1, worldZ);
                    BlockState belowState = chunk.getBlockState(belowCursor);
                    if (!needsFallingBlockSupport(chunk, belowCursor, belowState)) {
                        continue;
                    }

                    chunk.setBlockState(belowCursor, stableSupportStateFor(fallingState), false);
                }
            }
        }
    }

    private boolean needsFallingBlockSupport(ChunkAccess chunk, BlockPos.MutableBlockPos belowCursor, BlockState belowState) {
        if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
            return true;
        }

        return !belowState.isFaceSturdy(chunk, belowCursor, Direction.UP);
    }

    private BlockState stableSupportStateFor(BlockState fallingState) {
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(fallingState.getBlock());
        String path = blockId.getPath();
        if (path.contains("red_sand")) {
            return Blocks.RED_SANDSTONE.defaultBlockState();
        }
        if (path.contains("sand")) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (path.contains("gravel")) {
            return Blocks.STONE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private NoiseChunk resolveNoiseChunk(WorldGenRegion level, StructureManager structureManager, ChunkAccess chunk, RandomState randomState) {
        Blender blender = Blender.of(level);
        return chunk.getOrCreateNoiseChunk(candidate -> createNoiseChunkReflectively(candidate, structureManager, blender, randomState));
    }

    // 1.21.1 keeps the generator's noise-chunk factory private, so filtered carver runs
    // need a narrow reflective bridge instead of reimplementing the full pipeline.
    private NoiseChunk createNoiseChunkReflectively(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState randomState) {
        try {
            return (NoiseChunk) resolveCreateNoiseChunkMethod().invoke(this, chunk, structureManager, blender, randomState);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create a noise chunk for VerseWorks carver filtering", exception);
        }
    }

    private static Method resolveCreateNoiseChunkMethod() {
        Method method = createNoiseChunkMethod;
        if (method != null) {
            return method;
        }

        try {
            method = NoiseBasedChunkGenerator.class.getDeclaredMethod("createNoiseChunk", ChunkAccess.class, StructureManager.class, Blender.class, RandomState.class);
            method.setAccessible(true);
            createNoiseChunkMethod = method;
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access NoiseBasedChunkGenerator#createNoiseChunk", exception);
        }
    }

    private void applyPools(ChunkAccess chunk) {
        if (this.parameters.poolFeatures().isEmpty()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (VerseDimensionParameters.PoolFeatureSpec poolSpec : this.parameters.poolFeatures()) {
            RandomSource random = chunkRandom(chunk, LAKE_SALT ^ poolSpec.fluidId().hashCode());
            int lakeCount = random.nextDouble() < poolSpec.chancePerChunk() ? 1 : 0;
            if (lakeCount == 0) {
                continue;
            }

            Fluid fluid = resolveFluid(poolSpec.fluidId());
            BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
            if (fluidState.isAir()) {
                continue;
            }

            for (int lakeIndex = 0; lakeIndex < lakeCount; lakeIndex++) {
                int centerX = 3 + random.nextInt(10);
                int centerZ = 3 + random.nextInt(10);
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1;
                int basinY = Math.max(chunk.getMinBuildHeight() + 4, surfaceY);
                if (!isTerrainPuddleSite(chunk, centerX, basinY, centerZ)) {
                    continue;
                }

                int radius = poolSpec.radius();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double distance = Math.sqrt(dx * dx + dz * dz);
                        if (distance > radius) {
                            continue;
                        }

                        int depth = Math.max(1, poolSpec.depth() - (int) Math.floor(distance / 2.5D));
                        int absoluteX = chunkPos.getMinBlockX() + centerX + dx;
                        int absoluteZ = chunkPos.getMinBlockZ() + centerZ + dz;
                        for (int yOffset = 0; yOffset < depth; yOffset++) {
                            cursor.set(absoluteX, basinY - yOffset, absoluteZ);
                            chunk.setBlockState(cursor, fluidState, false);
                        }
                        cursor.set(absoluteX, basinY + 1, absoluteZ);
                        chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    private void applyShapes(ChunkAccess chunk) {
        if (this.parameters.shapeFeatures().isEmpty()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minChunkX = chunkPos.getMinBlockX();
        int maxChunkX = chunkPos.getMaxBlockX();
        int minChunkZ = chunkPos.getMinBlockZ();
        int maxChunkZ = chunkPos.getMaxBlockZ();

        for (int candidateChunkX = chunkPos.x - 1; candidateChunkX <= chunkPos.x + 1; candidateChunkX++) {
            for (int candidateChunkZ = chunkPos.z - 1; candidateChunkZ <= chunkPos.z + 1; candidateChunkZ++) {
                ChunkPos candidateChunk = new ChunkPos(candidateChunkX, candidateChunkZ);
                for (VerseDimensionParameters.ShapeFeatureSpec shapeSpec : this.parameters.shapeFeatures()) {
                    RandomSource random = chunkRandom(candidateChunk, SPHERE_SALT ^ BuiltInRegistries.BLOCK.getKey(shapeSpec.block()).hashCode() ^ shapeSpec.shape().serializedName().hashCode());
                    int shapeCount = random.nextDouble() < shapeSpec.chancePerChunk() ? 1 : 0;
                    if (this.parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND && random.nextDouble() < 0.20D) {
                        shapeCount++;
                    }

                    for (int shapeIndex = 0; shapeIndex < shapeCount; shapeIndex++) {
                        int centerX = candidateChunk.getMinBlockX() + random.nextInt(16);
                        int centerZ = candidateChunk.getMinBlockZ() + random.nextInt(16);
                        int centerY = this.parameters.sampleHeight(random, shapeSpec.heightDistribution(), chunk.getMinBuildHeight(), chunk.getMinBuildHeight() + chunk.getHeight());
                        if ("surface".equals(shapeSpec.heightDistribution().profile())) {
                            int localX = Mth.clamp(centerX - candidateChunk.getMinBlockX(), 0, 15);
                            int localZ = Mth.clamp(centerZ - candidateChunk.getMinBlockZ(), 0, 15);
                            int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
                            centerY = Math.max(chunk.getMinBuildHeight() + 8, surfaceY + Math.max(2, shapeSpec.maxRadius() / 2));
                            if (shapeSpec.shape() == VerseDimensionParameters.ShapeKind.CUBE && random.nextDouble() < 0.28D) {
                                centerY = Math.min(chunk.getMinBuildHeight() + chunk.getHeight() - shapeSpec.maxRadius() - 4, centerY + 14 + random.nextInt(28));
                            }
                        }
                        int radius = shapeSpec.minRadius() + random.nextInt(Math.max(1, shapeSpec.maxRadius() - shapeSpec.minRadius() + 1));
                        int radiusSquared = radius * radius;
                        int innerRadiusSquared = Math.max(0, (radius - 1) * (radius - 1));

                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dy = -radius; dy <= radius; dy++) {
                                for (int dz = -radius; dz <= radius; dz++) {
                                    int x = centerX + dx;
                                    int y = centerY + dy;
                                    int z = centerZ + dz;
                                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ || y < chunk.getMinBuildHeight() || y >= chunk.getMinBuildHeight() + chunk.getHeight()) {
                                        continue;
                                    }

                                    boolean inside = shapeSpec.shape() == VerseDimensionParameters.ShapeKind.CUBE
                                        ? Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius
                                        : dx * dx + dy * dy + dz * dz <= radiusSquared;
                                    if (!inside) {
                                        continue;
                                    }

                                    boolean shell = shapeSpec.hollow() && shapeSpec.shape() == VerseDimensionParameters.ShapeKind.SPHERE
                                        ? dx * dx + dy * dy + dz * dz >= innerRadiusSquared
                                        : shapeSpec.hollow() && shapeSpec.shape() == VerseDimensionParameters.ShapeKind.CUBE
                                        && (Math.abs(dx) == radius || Math.abs(dy) == radius || Math.abs(dz) == radius);
                                    if (shapeSpec.hollow() && !shell) {
                                        continue;
                                    }

                                    cursor.set(x, y, z);
                                    chunk.setBlockState(cursor, resolveShapeState(shapeSpec, y), false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyOreMorphRules(ChunkAccess chunk) {
        if (this.parameters.oreMorphFeatures().isEmpty() || this.parameters.worldType().isFlat()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        for (VerseDimensionParameters.OreMorphSpec oreSpec : this.parameters.oreMorphFeatures()) {
            RandomSource random = chunkRandom(chunk, ORE_SALT ^ oreSpec.profile().serializedName().hashCode() ^ BuiltInRegistries.BLOCK.getKey(oreSpec.replacementBlock()).hashCode());
            int extraVeins = VerseOreProfiles.veinsPerChunk(oreSpec.profile(), Math.max(this.parameters.oreMultiplier(), oreSpec.multiplier()));
            for (int vein = 0; vein < extraVeins; vein++) {
                int x = chunkPos.getMinBlockX() + random.nextInt(16);
                int y = VerseOreProfiles.sampleY(oreSpec.profile(), random, chunk.getMinBuildHeight());
                int z = chunkPos.getMinBlockZ() + random.nextInt(16);
                BlockPos anchor = findOreAnchor(chunk, x, y, z);
                if (anchor == null) {
                    continue;
                }
                placeOreCluster(chunk, anchor, oreSpec.replacementBlock().defaultBlockState(), VerseOreProfiles.clusterSize(oreSpec.profile(), random, Math.max(this.parameters.oreMultiplier(), oreSpec.multiplier())), random);
            }
        }
    }

    private boolean isTerrainPuddleSite(ChunkAccess chunk, int localX, int basinY, int localZ) {
        if (localX <= 1 || localX >= 14 || localZ <= 1 || localZ >= 14) {
            return false;
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        cursor.set(chunkPos.getMinBlockX() + localX, basinY - 1, chunkPos.getMinBlockZ() + localZ);
        if (!chunk.getBlockState(cursor).isSolid()) {
            return false;
        }

        int exposedSides = 0;
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            cursor.set(chunkPos.getMinBlockX() + localX + direction.getStepX(), basinY, chunkPos.getMinBlockZ() + localZ + direction.getStepZ());
            if (chunk.getBlockState(cursor).isAir()) {
                exposedSides++;
            }
        }
        return exposedSides <= 1;
    }

    private Fluid resolveFluid() {
        return resolveFluid(this.parameters.effectiveFluidId());
    }

    private Fluid resolveFluid(ResourceLocation fluidId) {
        if (fluidId == null) {
            return net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(ResourceLocation.withDefaultNamespace("water")).orElse(net.minecraft.world.level.material.Fluids.WATER);
        }

        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(fluidId)
            .orElseGet(() -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getOptional(ResourceLocation.withDefaultNamespace("water")).orElse(net.minecraft.world.level.material.Fluids.WATER));
    }

    private RandomSource chunkRandom(ChunkAccess chunk, long salt) {
        return chunkRandom(chunk.getPos(), salt);
    }

    private RandomSource chunkRandom(ChunkPos chunkPos, long salt) {
        long mixedSeed = this.parameters.seedOffset() ^ salt ^ (chunkPos.x * 341873128712L) ^ (chunkPos.z * 132897987541L);
        return RandomSource.create(mixedSeed);
    }

    private static void placeOreCluster(ChunkAccess chunk, BlockPos origin, BlockState oreState, int size, RandomSource random) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        for (int index = 0; index < size; index++) {
            if (cursor.getY() >= minY && cursor.getY() <= maxY) {
                BlockState existingState = chunk.getBlockState(cursor);
                if (VerseOreProfiles.canReplace(existingState)) {
                    chunk.setBlockState(cursor, oreState, false);
                }
            }

            cursor.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        }
    }

    private enum BiomeSurfaceFamily {
        OVERWORLD,
        NETHER,
        CRIMSON_FOREST,
        WARPED_FOREST,
        SOUL_SAND_VALLEY,
        BASALT_DELTAS,
        END;

        private static BiomeSurfaceFamily from(Holder<Biome> biomeHolder, ResourceLocation biomeId) {
            if (biomeHolder.is(BiomeTags.IS_END)) {
                return END;
            }
            if (biomeHolder.is(BiomeTags.IS_NETHER)) {
                return inferNetherSurfaceFamily(biomeId);
            }
            return inferSurfaceFamily(biomeId);
        }

        private static BiomeSurfaceFamily inferSurfaceFamily(ResourceLocation biomeId) {
            String path = biomeId.getPath().toLowerCase(Locale.ROOT);
            if (path.equals("the_end") || path.startsWith("end_") || path.endsWith("_end") || path.contains("_end_")) {
                return END;
            }
            if (path.equals("nether_wastes") || path.startsWith("nether_") || path.endsWith("_nether") || path.contains("_nether_")) {
                return inferNetherSurfaceFamily(biomeId);
            }
            return OVERWORLD;
        }

        private static BiomeSurfaceFamily inferNetherSurfaceFamily(ResourceLocation biomeId) {
            String path = biomeId.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("crimson") && path.contains("forest")) {
                return CRIMSON_FOREST;
            }
            if (path.contains("warped") && path.contains("forest")) {
                return WARPED_FOREST;
            }
            if (path.contains("soul") && (path.contains("valley") || path.contains("sand"))) {
                return SOUL_SAND_VALLEY;
            }
            if (path.contains("basalt") && path.contains("delta")) {
                return BASALT_DELTAS;
            }
            return NETHER;
        }
    }

    private static BlockPos findOreAnchor(ChunkAccess chunk, int x, int sampledY, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int localX = x & 15;
        int localZ = z & 15;
        int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
        int startY = Mth.clamp(sampledY, minY + 4, Math.min(surfaceY, maxY - 4));
        for (int delta = 0; delta <= 32; delta++) {
            int downY = startY - delta;
            if (downY >= minY) {
                cursor.set(x, downY, z);
                if (VerseOreProfiles.canReplace(chunk.getBlockState(cursor))) {
                    return cursor.immutable();
                }
            }
            int upY = startY + delta;
            if (delta > 0 && upY <= maxY) {
                cursor.set(x, upY, z);
                if (VerseOreProfiles.canReplace(chunk.getBlockState(cursor))) {
                    return cursor.immutable();
                }
            }
        }
        return null;
    }

    private record BlockPalette(BlockState topState, BlockState fillerState, BlockState coreState, boolean isSpecial) {
    }

    private boolean allowsStructureSet(Holder<StructureSet> holder, VerseDimensionParameters.StructureControlProfile structureControl) {
        ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) {
            return structureControl.mode() != VerseDimensionParameters.StructureControlMode.NONE;
        }
        if (holder.is(MysticRuinStructure.STRUCTURE_SET_KEY)) {
            return true;
        }
        return VerseStructureGroups.matches(id, structureControl);
    }

    private BlockState resolveShapeState(VerseDimensionParameters.ShapeFeatureSpec shapeSpec, int y) {
        Block block = shapeSpec.block();
        if (y < 0) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId != null && !blockId.getPath().startsWith("deepslate_")) {
                Block deepslateBlock = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), "deepslate_" + blockId.getPath())).orElse(null);
                if (deepslateBlock != null) {
                    return deepslateBlock.defaultBlockState();
                }
            }
        }
        return block.defaultBlockState();
    }
}
