package com.kyden.verseworks.worldgen;

import com.mojang.serialization.Codec;
import com.kyden.verseworks.Config;
import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.block.MysticCrystalBlock;
import com.kyden.verseworks.block.VerseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class MysticCrystalCaveFeature extends Feature<NoneFeatureConfiguration> {
    private static final int RANDOM_SEARCH_ATTEMPTS = 48;
    private static final int FALLBACK_SCAN_RADIUS_XZ = 6;
    private static final int FALLBACK_SCAN_RADIUS_Y = 5;
    private static final int CLUSTER_NEIGHBOR_ATTEMPTS = 10;
    private static final float OTHER_DIMENSION_PLACEMENT_CHANCE = 0.18F;

    public MysticCrystalCaveFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        if (!shouldAttemptPlacement(level, random)) {
            return false;
        }

        Placement placement = findPlacement(level, context.origin(), random);
        if (placement == null) {
            return false;
        }

        int targetCount = randomClusterSize(level, random);
        int placedCount = 0;
        if (placeCrystal(level, placement.pos(), placement.facing(), random)) {
            placedCount++;
        }

        for (int index = placedCount; index < targetCount; index++) {
            boolean placedExtra = false;
            for (int attempt = 0; attempt < CLUSTER_NEIGHBOR_ATTEMPTS; attempt++) {
                BlockPos nearbyPos = placement.pos().offset(
                    random.nextInt(5) - 2,
                    random.nextInt(5) - 2,
                    random.nextInt(5) - 2
                );
                Placement nearbyPlacement = choosePlacement(level, nearbyPos, random);
                if (nearbyPlacement != null && placeCrystal(level, nearbyPlacement.pos(), nearbyPlacement.facing(), random)) {
                    placedCount++;
                    placedExtra = true;
                    break;
                }
            }

            if (!placedExtra) {
                break;
            }
        }

        return placedCount > 0;
    }

    private static boolean shouldAttemptPlacement(WorldGenLevel level, RandomSource random) {
        if (level.getLevel().dimension() == Level.OVERWORLD) {
            return random.nextFloat() < Config.MYSTIC_CRYSTAL_OVERWORLD_PLACEMENT_CHANCE.get().floatValue();
        }
        if (isVerseWorksLevel(level)) {
            return random.nextFloat() < Config.MYSTIC_CRYSTAL_VERSE_DIMENSION_PLACEMENT_CHANCE.get().floatValue();
        }
        return random.nextFloat() < OTHER_DIMENSION_PLACEMENT_CHANCE;
    }

    private static Placement findPlacement(WorldGenLevel level, BlockPos origin, RandomSource random) {
        Placement directPlacement = choosePlacement(level, origin, random);
        if (directPlacement != null) {
            return directPlacement;
        }

        for (int attempt = 0; attempt < RANDOM_SEARCH_ATTEMPTS; attempt++) {
            BlockPos candidatePos = origin.offset(
                random.nextInt(FALLBACK_SCAN_RADIUS_XZ * 2 + 1) - FALLBACK_SCAN_RADIUS_XZ,
                random.nextInt(FALLBACK_SCAN_RADIUS_Y * 2 + 1) - FALLBACK_SCAN_RADIUS_Y,
                random.nextInt(FALLBACK_SCAN_RADIUS_XZ * 2 + 1) - FALLBACK_SCAN_RADIUS_XZ
            );
            Placement candidatePlacement = choosePlacement(level, candidatePos, random);
            if (candidatePlacement != null) {
                return candidatePlacement;
            }
        }

        for (int yOffset = -FALLBACK_SCAN_RADIUS_Y; yOffset <= FALLBACK_SCAN_RADIUS_Y; yOffset++) {
            for (int xOffset = -FALLBACK_SCAN_RADIUS_XZ; xOffset <= FALLBACK_SCAN_RADIUS_XZ; xOffset++) {
                for (int zOffset = -FALLBACK_SCAN_RADIUS_XZ; zOffset <= FALLBACK_SCAN_RADIUS_XZ; zOffset++) {
                    Placement candidatePlacement = choosePlacement(level, origin.offset(xOffset, yOffset, zOffset), random);
                    if (candidatePlacement != null) {
                        return candidatePlacement;
                    }
                }
            }
        }

        return null;
    }

    private static Placement choosePlacement(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).isAir()) {
            return null;
        }

        for (Direction direction : shuffledDirections(random)) {
            BlockPos supportPos = pos.relative(direction.getOpposite());
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, direction)) {
                continue;
            }

            BlockState state = crystalState(direction, random);
            if (state.canSurvive(level, pos)) {
                return new Placement(pos.immutable(), direction);
            }
        }

        return null;
    }

    private static boolean placeCrystal(WorldGenLevel level, BlockPos pos, Direction facing, RandomSource random) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }

        BlockState state = crystalState(facing, random);
        if (!state.canSurvive(level, pos)) {
            return false;
        }

        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        level.getChunk(pos).markPosForPostprocessing(pos);
        return true;
    }

    private static BlockState crystalState(Direction facing, RandomSource random) {
        return VerseBlocks.MYSTIC_CRYSTAL.get().defaultBlockState()
            .setValue(DirectionalBlock.FACING, facing)
            .setValue(MysticCrystalBlock.VARIANT, random.nextInt(3));
    }

    private static int randomClusterSize(WorldGenLevel level, RandomSource random) {
        if (isVerseWorksLevel(level)) {
            float roll = random.nextFloat();
            if (roll < 0.22F) {
                return 4;
            }
            if (roll < 0.68F) {
                return 3;
            }
            if (roll < 0.95F) {
                return 2;
            }
            return 1;
        }

        float roll = random.nextFloat();
        if (roll < 0.52F) {
            return 2;
        }
        if (roll < 0.87F) {
            return 3;
        }
        return 1;
    }

    private static boolean isVerseWorksLevel(WorldGenLevel level) {
        return VerseWorks.MODID.equals(level.getLevel().dimension().location().getNamespace());
    }

    private static Direction[] shuffledDirections(RandomSource random) {
        Direction[] directions = Direction.values();
        for (int index = directions.length - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            Direction swap = directions[index];
            directions[index] = directions[swapIndex];
            directions[swapIndex] = swap;
        }
        return directions;
    }

    private record Placement(BlockPos pos, Direction facing) {
    }
}
