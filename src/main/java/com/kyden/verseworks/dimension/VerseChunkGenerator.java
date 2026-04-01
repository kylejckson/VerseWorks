package com.kyden.verseworks.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.kyden.verseworks.worldgen.MysticRuinStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class VerseChunkGenerator extends NoiseBasedChunkGenerator {
    private static final long TERRAIN_SALT = 0x5445525241494EL;
    private static final long ORE_SALT = 0x4F5245534CL;
    private static final long LAKE_SALT = 0x4C414B454CL;
    private static final long SPHERE_SALT = 0x5350484552L;
    private static final int FLAT_SURFACE_Y = 64;

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

        if (!this.parameters.structures()) {
            Stream<Holder<StructureSet>> allowedStructureSets = structureSets.listElements()
                .filter(holder -> holder.is(MysticRuinStructure.STRUCTURE_SET_KEY))
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
        applyBiomeSurfaceOverrides(chunk, randomState);
        applyOceanLevel(chunk);
        applyLakes(chunk);
        applySpheres(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        applyOreRules(chunk);
    }

    @Override
    public int getSeaLevel() {
        return this.parameters.oceanLevel() != null ? this.parameters.oceanLevel() : super.getSeaLevel();
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos position) {
        super.addDebugScreenInfo(lines, randomState, position);
        lines.add("VerseWorks: " + this.parameters.rulesSummary());
    }

    private void applyOceanLevel(ChunkAccess chunk) {
        if (this.parameters.oceanLevel() == null) {
            return;
        }

        Fluid fluid = resolveFluid();
        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int oceanLevel = Math.min(this.parameters.oceanLevel(), minY + chunk.getHeight() - 1);
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
                clearColumn(chunk, cursor, worldX, worldZ, minY, maxY);

                double largeShape = sampleTerrainNoise(worldX, worldZ, 112.0D, 3, 0x13579BDFL);
                double islandMask = sampleTerrainNoise(worldX, worldZ, 52.0D, 3, 0x2468ACE0L);
                double detail = sampleTerrainNoise(worldX, worldZ, 20.0D, 2, 0x51F15EEDL);
                double signal = largeShape * 0.85D + islandMask * 0.45D + detail * 0.18D - 0.32D;
                if (signal <= 0.0D) {
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

                BlockPalette palette = surfacePalette(worldX, topY, worldZ, randomState);

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
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) - 1;
                if (surfaceY < minY || surfaceY > maxY) {
                    continue;
                }

                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                BlockPalette palette = surfacePalette(worldX, surfaceY, worldZ, randomState);
                if (!palette.isSpecial()) {
                    continue;
                }
                replaceSurfaceColumn(chunk, cursor, worldX, worldZ, surfaceY, minY, palette);
            }
        }
    }

    private void replaceSurfaceColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int surfaceY, int minY, BlockPalette palette) {
        int replacementDepth = 0;
        for (int y = surfaceY; y >= minY && replacementDepth < 4; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState currentState = chunk.getBlockState(cursor);
            if (currentState.isAir()) {
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

    private BlockPalette surfacePalette(int worldX, int surfaceY, int worldZ, RandomState randomState) {
        BiomeSurfaceFamily family = biomeSurfaceFamily(worldX, surfaceY, worldZ, randomState);
        return switch (family) {
            case END -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.END_STONE.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(), true);
            }
            case CRIMSON_FOREST -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.CRIMSON_NYLIUM.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            }
            case WARPED_FOREST -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.WARPED_NYLIUM.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            }
            case SOUL_SAND_VALLEY -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.SOUL_SAND.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.SOUL_SOIL.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            }
            case BASALT_DELTAS -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.BASALT.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), true);
            }
            case NETHER -> {
                BlockState topState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.NETHERRACK.defaultBlockState() : this.parameters.floorBlock().defaultBlockState();
                yield new BlockPalette(topState, Blocks.NETHERRACK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState(), true);
            }
            case OVERWORLD -> {
                BlockState topState = this.parameters.floorBlock().defaultBlockState();
                BlockState fillerState = this.parameters.floorBlock() == Blocks.GRASS_BLOCK ? Blocks.DIRT.defaultBlockState() : topState;
                yield new BlockPalette(topState, fillerState, Blocks.STONE.defaultBlockState(), false);
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
        ResourceLocation biomeId = biomeHolder.unwrapKey()
            .map(ResourceKey::location)
            .orElse(this.parameters.primaryBiomeId());
        return BiomeSurfaceFamily.from(biomeId);
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

    private void applyLakes(ChunkAccess chunk) {
        if (!this.parameters.lakes()) {
            return;
        }

        RandomSource random = chunkRandom(chunk, LAKE_SALT);
        int lakeCount = random.nextDouble() < 0.28D ? 1 : 0;
        if (lakeCount == 0) {
            return;
        }

        Fluid fluid = resolveFluid();
        BlockState fluidState = fluid.defaultFluidState().createLegacyBlock();
        if (fluidState.isAir()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int lakeIndex = 0; lakeIndex < lakeCount; lakeIndex++) {
            int centerX = 3 + random.nextInt(10);
            int centerZ = 3 + random.nextInt(10);
            int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) - 1;
            int basinY = Math.max(chunk.getMinBuildHeight() + 4, surfaceY - 2 - random.nextInt(2));
            if (!isTerrainPuddleSite(chunk, centerX, basinY, centerZ)) {
                continue;
            }

            int radius = 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > radius) {
                        continue;
                    }

                    int depth = Math.max(1, 2 - (int) Math.floor(distance / 2.0D));
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

    private void applySpheres(ChunkAccess chunk) {
        if (!this.parameters.spheres()) {
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
                RandomSource random = chunkRandom(candidateChunk, SPHERE_SALT);
                int sphereCount = random.nextDouble() < 0.12D ? 1 : 0;
                if (this.parameters.worldType() == VerseDimensionWorldType.SKY_ISLAND && random.nextDouble() < 0.20D) {
                    sphereCount++;
                }

                for (int sphereIndex = 0; sphereIndex < sphereCount; sphereIndex++) {
                    int centerX = candidateChunk.getMinBlockX() + random.nextInt(16);
                    int centerZ = candidateChunk.getMinBlockZ() + random.nextInt(16);
                    int centerY = this.parameters.sampleSphereCenterY(random, chunk.getMinBuildHeight(), chunk.getMinBuildHeight() + chunk.getHeight());
                    int radius = 3 + random.nextInt(5);
                    int radiusSquared = radius * radius;

                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -radius; dy <= radius; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                int x = centerX + dx;
                                int y = centerY + dy;
                                int z = centerZ + dz;
                                if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ || y < chunk.getMinBuildHeight() || y >= chunk.getMinBuildHeight() + chunk.getHeight()) {
                                    continue;
                                }

                                int distanceSquared = dx * dx + dy * dy + dz * dz;
                                if (distanceSquared > radiusSquared) {
                                    continue;
                                }

                                cursor.set(x, y, z);
                                chunk.setBlockState(cursor, this.parameters.sphereStateForY(y), false);
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyOreRules(ChunkAccess chunk) {
        if (this.parameters.oreMultiplier() <= 1.0D || this.parameters.worldType().isFlat()) {
            return;
        }

        RandomSource random = chunkRandom(chunk, ORE_SALT);
        int extraVeins = oreVeinsPerChunk(this.parameters.oreMultiplier());
        ChunkPos chunkPos = chunk.getPos();
        for (int vein = 0; vein < extraVeins; vein++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int y = sampleOreY(random, chunk.getMinBuildHeight());
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            placeOreCluster(chunk, new BlockPos(x, y, z), pickOre(random, y, this.parameters.oreMultiplier()), oreClusterSize(random, this.parameters.oreMultiplier()), random);
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
        ResourceLocation fluidId = this.parameters.effectiveFluidId();
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

    private static int sampleOreY(RandomSource random, int minBuildHeight) {
        int[] bands = {minBuildHeight + 8, minBuildHeight + 24, minBuildHeight + 40, minBuildHeight + 64, minBuildHeight + 96};
        int base = bands[random.nextInt(bands.length)];
        return base + random.nextInt(24) - 12;
    }

    private static BlockState pickOre(RandomSource random, int y, double oreMultiplier) {
        if (oreMultiplier >= 7.5D) {
            BlockState[] jackpotOres = {
                Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
                Blocks.DIAMOND_ORE.defaultBlockState(),
                Blocks.EMERALD_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState(),
                Blocks.REDSTONE_ORE.defaultBlockState(),
                Blocks.LAPIS_ORE.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(),
                Blocks.COPPER_ORE.defaultBlockState()
            };
            return jackpotOres[random.nextInt(jackpotOres.length)];
        }

        if (y < -24) {
            return random.nextBoolean() ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        }
        if (y < 16) {
            BlockState[] ores = {
                Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()
            };
            return ores[random.nextInt(ores.length)];
        }
        if (y < 64) {
            BlockState[] ores = {
                Blocks.IRON_ORE.defaultBlockState(),
                Blocks.COPPER_ORE.defaultBlockState(),
                Blocks.COAL_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState()
            };
            return ores[random.nextInt(ores.length)];
        }
        return Blocks.COAL_ORE.defaultBlockState();
    }

    private static int oreClusterSize(RandomSource random, double oreMultiplier) {
        if (oreMultiplier >= 7.5D) {
            return 10 + random.nextInt(7);
        }

        int baseSize = 4 + random.nextInt(5);
        int bonus = Math.max(0, (int) Math.round((oreMultiplier - 1.0D) * 2.5D));
        return baseSize + bonus;
    }

    private static int oreVeinsPerChunk(double oreMultiplier) {
        if (oreMultiplier <= 1.0D) {
            return 0;
        }

        if (oreMultiplier >= 7.5D) {
            return 72;
        }

        double normalized = oreMultiplier - 1.0D;
        return 8 + (int) Math.round(normalized * normalized * 1.5D);
    }

    private static void placeOreCluster(ChunkAccess chunk, BlockPos origin, BlockState oreState, int size, RandomSource random) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        for (int index = 0; index < size; index++) {
            if (cursor.getY() >= minY && cursor.getY() <= maxY) {
                BlockState existingState = chunk.getBlockState(cursor);
                if (existingState.is(BlockTags.STONE_ORE_REPLACEABLES) || existingState.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
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

        private static BiomeSurfaceFamily from(ResourceLocation biomeId) {
            String path = biomeId.getPath().toLowerCase(Locale.ROOT);
            if (path.equals("the_end") || path.startsWith("end_") || path.endsWith("_end") || path.contains("_end_")) {
                return END;
            }
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
            if (path.equals("nether_wastes") || path.startsWith("nether_") || path.endsWith("_nether") || path.contains("_nether_")) {
                return NETHER;
            }
            return OVERWORLD;
        }
    }

    private record BlockPalette(BlockState topState, BlockState fillerState, BlockState coreState, boolean isSpecial) {
    }
}