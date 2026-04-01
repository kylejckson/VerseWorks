package com.kyden.verseworks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WarpBlock extends Block {
    public static final MapCodec<WarpBlock> CODEC = simpleCodec(WarpBlock::new);

    private static final int SPREAD_ATTEMPT_INTERVAL = 12;
    private static final int VINE_PLANT_CHANCE = 2;
    private static final Direction[] SPREAD_DIRECTIONS = Direction.values();

    public WarpBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<WarpBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!WarpSpreadHelper.isWarpAllowed(level)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        if (level instanceof Level actualLevel && !WarpSpreadHelper.isWarpAllowed(actualLevel)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof Level actualLevel && !WarpSpreadHelper.isWarpAllowed(actualLevel)) {
            return false;
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!WarpSpreadHelper.isWarpAllowed(level)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }

        if (random.nextInt(SPREAD_ATTEMPT_INTERVAL) != 0) {
            return;
        }

        boolean spreadWarpBlock = trySpread(level, pos, random);
        if (random.nextInt(VINE_PLANT_CHANCE) == 0) {
            WarpVineBlock.tryPlantNearby(level, pos, random);
        }

        if (spreadWarpBlock && random.nextBoolean()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        WarpSpreadHelper.applyWarpHazard(level, entity, false);
        super.stepOn(level, pos, state, entity);
    }

    private boolean trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        List<Direction> directions = new ArrayList<>(List.of(SPREAD_DIRECTIONS));
        Collections.shuffle(directions, new java.util.Random(random.nextLong()));
        int targetSpreadCount = random.nextInt(1, 4);
        int spreadCount = 0;
        for (Direction direction : directions) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = level.getBlockState(targetPos);
            if (!WarpSpreadHelper.canReplaceWithWarp(targetState)) {
                continue;
            }

            level.setBlock(targetPos, defaultBlockState(), Block.UPDATE_ALL);
            WarpSpreadHelper.playSpreadSound(level, targetPos);
            spreadCount++;
            if (spreadCount >= targetSpreadCount) {
                break;
            }
        }

        return spreadCount > 0;
    }
}
