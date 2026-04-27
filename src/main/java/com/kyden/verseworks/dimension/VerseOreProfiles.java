package com.kyden.verseworks.dimension;

import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VerseOreProfiles {
    private VerseOreProfiles() {
    }

    public static int clusterSize(VerseDimensionParameters.OreProfile profile, RandomSource random, double multiplier) {
        return switch (profile) {
            case COAL_SMALL -> 3 + random.nextInt(3);
            case COPPER -> 7 + random.nextInt(6);
            case IRON_LARGE -> 12 + random.nextInt(8);
            case DIAMOND_DEEPSLATE_BIAS -> 4 + random.nextInt(3);
            case DEFAULT -> 4 + random.nextInt(5) + Math.max(0, (int) Math.round((multiplier - 1.0D) * 2.5D));
        };
    }

    public static int veinsPerChunk(VerseDimensionParameters.OreProfile profile, double multiplier) {
        if (multiplier <= 1.0D && profile == VerseDimensionParameters.OreProfile.DEFAULT) {
            return 0;
        }
        return switch (profile) {
            case COAL_SMALL -> 8;
            case COPPER -> 6;
            case IRON_LARGE -> 3;
            case DIAMOND_DEEPSLATE_BIAS -> 4;
            case DEFAULT -> 8 + (int) Math.round((multiplier - 1.0D) * (multiplier - 1.0D) * 1.5D);
        };
    }

    public static int sampleY(VerseDimensionParameters.OreProfile profile, RandomSource random, int minBuildHeight) {
        return switch (profile) {
            case COAL_SMALL -> minBuildHeight + 72 + random.nextInt(56);
            case COPPER -> minBuildHeight + 48 + random.nextInt(40);
            case IRON_LARGE -> minBuildHeight + 8 + random.nextInt(48);
            case DIAMOND_DEEPSLATE_BIAS -> minBuildHeight + random.nextInt(24);
            case DEFAULT -> {
                int[] bands = {minBuildHeight + 8, minBuildHeight + 24, minBuildHeight + 40, minBuildHeight + 64, minBuildHeight + 96};
                int base = bands[random.nextInt(bands.length)];
                yield base + random.nextInt(24) - 12;
            }
        };
    }

    public static BlockState defaultReplacementState(VerseDimensionParameters.OreProfile profile, RandomSource random, int y, double oreMultiplier) {
        return switch (profile) {
            case COAL_SMALL -> Blocks.COAL_ORE.defaultBlockState();
            case COPPER -> y < 0 ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
            case IRON_LARGE -> y < 0 ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
            case DIAMOND_DEEPSLATE_BIAS -> Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
            case DEFAULT -> {
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
                    yield jackpotOres[random.nextInt(jackpotOres.length)];
                }
                if (y < -24) {
                    yield random.nextBoolean() ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState() : Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
                }
                if (y < 16) {
                    BlockState[] ores = {
                        Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState(),
                        Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()
                    };
                    yield ores[random.nextInt(ores.length)];
                }
                if (y < 64) {
                    BlockState[] ores = {
                        Blocks.IRON_ORE.defaultBlockState(),
                        Blocks.COPPER_ORE.defaultBlockState(),
                        Blocks.COAL_ORE.defaultBlockState(),
                        Blocks.GOLD_ORE.defaultBlockState()
                    };
                    yield ores[random.nextInt(ores.length)];
                }
                yield Blocks.COAL_ORE.defaultBlockState();
            }
        };
    }

    public static boolean canReplace(BlockState state) {
        return state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }
}
