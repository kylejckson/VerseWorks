package com.kyden.verseworks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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

public final class StabilizedWarpVineBlock extends VineBlock {
    public static final MapCodec<VineBlock> CODEC = simpleCodec(StabilizedWarpVineBlock::new);
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    private static final VoxelShape FLOOR_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public StabilizedWarpVineBlock(BlockBehaviour.Properties properties) {
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
    protected BlockState updateShape(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        BlockState updatedState = refreshConnections(state, level, pos);
        return hasAnyAttachment(updatedState) ? updatedState : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasAnyAttachment(refreshConnections(state, level, pos));
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
        return isAcceptableNeighbour(level, pos.above(), Direction.DOWN);
    }

    private static boolean hasGroundSupport(LevelReader level, BlockPos pos) {
        return isAcceptableNeighbour(level, pos.below(), Direction.UP);
    }

    private static boolean hasWallSupport(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos supportPos = pos.relative(direction);
        if (isAcceptableNeighbour(level, supportPos, direction)) {
            return true;
        }

        BlockState aboveState = level.getBlockState(pos.above());
        return aboveState.is(VerseBlocks.STABILIZED_WARP_VINE.get()) && aboveState.getValue(getPropertyForFace(direction));
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