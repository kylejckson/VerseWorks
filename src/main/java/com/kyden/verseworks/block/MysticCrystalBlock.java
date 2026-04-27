package com.kyden.verseworks.block;

import com.mojang.serialization.MapCodec;
import com.kyden.verseworks.item.VerseCatalog;
import com.kyden.verseworks.item.VerseItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class MysticCrystalBlock extends DirectionalBlock {
    public static final MapCodec<MysticCrystalBlock> CODEC = simpleCodec(MysticCrystalBlock::new);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

    public MysticCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(VARIANT, 0));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
            .setValue(FACING, context.getClickedFace())
            .setValue(VARIANT, context.getLevel().getRandom().nextInt(3));

        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, VARIANT);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
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
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() > 0.18F) {
            return;
        }

        Direction facing = state.getValue(FACING);
        double baseX = pos.getX() + 0.5D + facing.getStepX() * 0.14D;
        double baseY = pos.getY() + 0.35D + facing.getStepY() * 0.18D;
        double baseZ = pos.getZ() + 0.5D + facing.getStepZ() * 0.14D;
        double offsetX = (random.nextDouble() - 0.5D) * 0.28D;
        double offsetY = random.nextDouble() * 0.42D;
        double offsetZ = (random.nextDouble() - 0.5D) * 0.28D;
        double velocityX = (random.nextDouble() - 0.5D) * 0.015D;
        double velocityY = 0.02D + random.nextDouble() * 0.02D;
        double velocityZ = (random.nextDouble() - 0.5D) * 0.015D;

        level.addParticle(
            ParticleTypes.ENCHANT,
            baseX + offsetX,
            baseY + offsetY,
            baseZ + offsetZ,
            velocityX,
            velocityY,
            velocityZ
        );
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);

        RandomSource random = level.getRandom();
        int hyperDustCount = random.nextInt(3);
        if (hyperDustCount > 0) {
            Block.popResource(level, pos, new ItemStack(VerseItems.HYPER_DUST.get(), hyperDustCount));
        }

        int verseCount = VerseCatalog.randomCrystalVerseDropCount(random);
        for (int index = 0; index < verseCount; index++) {
            Block.popResource(level, pos, VerseCatalog.createRandomDropReward(VerseItems.VERSE.get(), VerseItems.BLANK_VERSE.get(), level.registryAccess(), random));
        }
    }
}
