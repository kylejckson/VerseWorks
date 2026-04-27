package com.kyden.verseworks.worldgen;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

public final class GiantCrystalFeature extends Feature<NoneFeatureConfiguration> {
    private static final List<Block> CRYSTAL_BLOCKS = List.of(
        Blocks.WHITE_STAINED_GLASS,
        Blocks.LIGHT_BLUE_STAINED_GLASS,
        Blocks.CYAN_STAINED_GLASS,
        Blocks.BLUE_STAINED_GLASS,
        Blocks.PURPLE_STAINED_GLASS,
        Blocks.PINK_STAINED_GLASS,
        Blocks.LIME_STAINED_GLASS,
        Blocks.YELLOW_STAINED_GLASS
    );

    public GiantCrystalFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!VerseWorks.MODID.equals(level.getLevel().dimension().location().getNamespace())) {
            return false;
        }
        if (VerseDimensionCatalog.getCached(level.getLevel().dimension().location()).map(parameters -> !parameters.crystalClusters()).orElse(true)) {
            return false;
        }

        RandomSource random = context.random();
        if (random.nextFloat() > 0.018F) {
            return false;
        }

        BlockPos origin = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, context.origin());
        if (origin.getY() <= level.getMinBuildHeight() + 8) {
            return false;
        }

        int clusterCount = 3 + random.nextInt(3);
        int placed = 0;
        for (int index = 0; index < clusterCount; index++) {
            BlockPos clusterOrigin = origin.offset(random.nextInt(28) - 14, 0, random.nextInt(28) - 14);
            BlockPos base = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, clusterOrigin);
            if (placeRod(level, base, random)) {
                placed++;
            }
        }
        return placed > 0;
    }

    private boolean placeRod(WorldGenLevel level, BlockPos base, RandomSource random) {
        BlockState crystalState = CRYSTAL_BLOCKS.get(random.nextInt(CRYSTAL_BLOCKS.size())).defaultBlockState();
        int height = 24 + random.nextInt(24);
        int width = 1 + random.nextInt(2);
        int tiltX = random.nextInt(3) - 1;
        int tiltZ = random.nextInt(3) - 1;
        if (tiltX == 0 && tiltZ == 0) {
            tiltX = 1;
        }

        boolean placedAny = false;
        for (int step = 0; step < height; step++) {
            int offsetX = (step / 5) * tiltX;
            int offsetZ = (step / 5) * tiltZ;
            BlockPos center = base.offset(offsetX, step, offsetZ);
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > width + 1) {
                        continue;
                    }
                    BlockPos placePos = center.offset(dx, 0, dz);
                    if (!level.getBlockState(placePos).canBeReplaced()) {
                        continue;
                    }
                    level.setBlock(placePos, crystalState, Block.UPDATE_CLIENTS);
                    placedAny = true;
                }
            }
        }

        if (placedAny) {
            level.setBlock(base.below(), Blocks.AMETHYST_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return placedAny;
    }
}
