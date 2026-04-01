package com.kyden.verseworks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WarpVineBlock extends VineBlock {
    public static final MapCodec<VineBlock> CODEC = simpleCodec(WarpVineBlock::new);
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final int SPREAD_ATTEMPT_INTERVAL = 6;
    private static final int SPREAD_RADIUS = 4;
    private static final int DREAD_RADIUS = 6;
    private static final int DREAD_VERTICAL_RADIUS = 3;
    private static final VoxelShape FLOOR_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    private static final Direction[] SPREAD_DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public WarpVineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(DOWN, false));
    }

    @Override
    public MapCodec<VineBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = super.getShape(state, level, pos, context);
        return state.getValue(DOWN) ? Shapes.or(shape, FLOOR_SHAPE) : shape;
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

        BlockState updatedState = refreshConnections(state, level, pos);
        return hasAnyAttachment(updatedState) ? super.updateShape(updatedState, direction, neighborState, level, pos, neighborPos) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof Level actualLevel && !WarpSpreadHelper.isWarpAllowed(actualLevel)) {
            return false;
        }

        return hasAnyAttachment(refreshConnections(state, level, pos));
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

        if (trySpread(level, pos, random)) {
            WarpSpreadHelper.playSpreadSound(level, pos);
        }
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 100;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 60;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placedState = super.getStateForPlacement(context);
        BlockState state = placedState != null ? placedState : this.defaultBlockState();
        state = applyAllSupportedFaces(state, context.getLevel(), context.getClickedPos());
        return hasAnyAttachment(state) ? state : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DOWN);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return super.rotate(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror);
    }

    public static boolean tryPlantNearby(ServerLevel level, BlockPos origin, RandomSource random) {
        if (!WarpSpreadHelper.isWarpAllowed(level)) {
            return false;
        }

        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos candidate = origin.offset(
                random.nextInt(-SPREAD_RADIUS, SPREAD_RADIUS + 1),
                random.nextInt(-SPREAD_RADIUS, SPREAD_RADIUS + 1),
                random.nextInt(-SPREAD_RADIUS, SPREAD_RADIUS + 1)
            );
            if (candidate.equals(origin) || candidate.distManhattan(origin) > SPREAD_RADIUS) {
                continue;
            }

            BlockState targetState = level.getBlockState(candidate);
            if (!level.getFluidState(candidate).isEmpty() || ((!targetState.isAir() && !targetState.canBeReplaced()) || targetState.is(VerseBlocks.WARP.get()))) {
                continue;
            }

            BlockState plantedState = placementStateAt(level, candidate);
            if (plantedState == null) {
                continue;
            }

            level.setBlock(candidate, plantedState, Block.UPDATE_ALL);
            WarpSpreadHelper.playSpreadSound(level, candidate);
            return true;
        }

        return false;
    }

    public static @Nullable BlockState placementStateForWorldgen(LevelReader level, BlockPos pos) {
        return placementStateAt(level, pos);
    }

    public static boolean hasNearbyWarpVine(ServerLevel level, BlockPos origin) {
        for (int dx = -DREAD_RADIUS; dx <= DREAD_RADIUS; dx++) {
            for (int dy = -DREAD_VERTICAL_RADIUS; dy <= DREAD_VERTICAL_RADIUS; dy++) {
                for (int dz = -DREAD_RADIUS; dz <= DREAD_RADIUS; dz++) {
                    if (level.getBlockState(origin.offset(dx, dy, dz)).is(VerseBlocks.WARP_VINE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void spawnDreadParticles(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();
        double x = player.getX() + (random.nextDouble() - 0.5D) * 1.4D;
        double y = player.getY() + 0.1D + random.nextDouble() * 1.2D;
        double z = player.getZ() + (random.nextDouble() - 0.5D) * 1.4D;
        level.sendParticles(ParticleTypes.ASH, x, y, z, 3, 0.18D, 0.22D, 0.18D, 0.004D);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.16D, 0.18D, 0.16D, 0.003D);
    }

    public static boolean isTouchingWarpVine(ServerPlayer player) {
        BlockPos min = BlockPos.containing(player.getBoundingBox().minX, player.getBoundingBox().minY, player.getBoundingBox().minZ);
        BlockPos max = BlockPos.containing(player.getBoundingBox().maxX, player.getBoundingBox().maxY, player.getBoundingBox().maxZ);
        ServerLevel level = (ServerLevel) player.level();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(VerseBlocks.WARP_VINE.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        List<Direction> directions = new ArrayList<>(List.of(SPREAD_DIRECTIONS));
        Collections.shuffle(directions, new java.util.Random(random.nextLong()));
        for (Direction direction : directions) {
            BlockPos candidate = pos.relative(direction);

            BlockState targetState = level.getBlockState(candidate);
            if (targetState.is(VerseBlocks.WARP.get())) {
                continue;
            }

            if (!level.getFluidState(candidate).isEmpty()) {
                continue;
            }

            if (targetState.is(this)) {
                BlockState mergedState = applyAllSupportedFaces(targetState, level, candidate);
                if (!mergedState.equals(targetState) && hasAnyAttachment(mergedState)) {
                    level.setBlock(candidate, mergedState, Block.UPDATE_ALL);
                    return true;
                }
                continue;
            }

            if (!targetState.isAir() && !targetState.canBeReplaced()) {
                continue;
            }

            BlockState plantedState = placementStateAt(level, candidate);
            if (plantedState == null) {
                continue;
            }

            level.setBlock(candidate, plantedState, Block.UPDATE_ALL);
            return true;
        }

        return false;
    }

    private static @Nullable BlockState placementStateAt(LevelReader level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) {
            return null;
        }

        BlockState state = VerseBlocks.WARP_VINE.get().defaultBlockState();
        state = applyAllSupportedFaces(state, level, pos);
        return hasAnyAttachment(state) ? state : null;
    }

    private static BlockState refreshConnections(BlockState state, LevelReader level, BlockPos pos) {
        return applyAllSupportedFaces(state, level, pos);
    }

    private static BlockState applyAllSupportedFaces(BlockState state, LevelReader level, BlockPos pos) {
        state = state.setValue(UP, hasCeilingSupport(level, pos));
        state = state.setValue(DOWN, hasGroundSupport(level, pos));
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            state = state.setValue(getPropertyForFace(direction), hasWallSupport(level, pos, direction));
        }
        return state;
    }

    private static boolean hasCeilingSupport(LevelReader level, BlockPos pos) {
        BlockPos supportPos = pos.above();
        return isAcceptableNeighbour(level, supportPos, Direction.DOWN);
    }

    private static boolean hasGroundSupport(LevelReader level, BlockPos pos) {
        BlockPos supportPos = pos.below();
        return isAcceptableNeighbour(level, supportPos, Direction.UP);
    }

    private static boolean hasWallSupport(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos supportPos = pos.relative(direction);
        if (isAcceptableNeighbour(level, supportPos, direction)) {
            return true;
        }

        BlockState aboveState = level.getBlockState(pos.above());
        return aboveState.is(VerseBlocks.WARP_VINE.get()) && aboveState.getValue(getPropertyForFace(direction));
    }

    private static boolean hasAnyAttachment(BlockState state) {
        if (state.getValue(DOWN) || state.getValue(UP)) {
            return true;
        }

        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            if (state.getValue(getPropertyForFace(direction))) {
                return true;
            }
        }

        return false;
    }
}
