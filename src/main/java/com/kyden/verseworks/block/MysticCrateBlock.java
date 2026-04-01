package com.kyden.verseworks.block;

import com.kyden.verseworks.item.VerseCatalog;
import com.kyden.verseworks.item.VerseItems;
import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class MysticCrateBlock extends Block {
    private static final float VERSE_DROP_CHANCE = 0.20F;

    public MysticCrateBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);

        RandomSource random = level.getRandom();
        if (random.nextFloat() < VERSE_DROP_CHANCE) {
            ItemStack verseDrop = random.nextBoolean()
                ? VerseCatalog.createRandomBiomeVerse(VerseItems.VERSE.get(), level.registryAccess(), random)
                : VerseCatalog.createRandomNonBiomeVerse(VerseItems.VERSE.get(), random);
            Block.popResource(level, pos, verseDrop);
            spawnSuccessParticles(level, pos);
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), 3 + random.nextInt(4));
            level.playSound(
                null,
                pos,
                random.nextBoolean() ? VerseSounds.MAGIC_1.get() : VerseSounds.MAGIC_2.get(),
                SoundSource.BLOCKS,
                0.9F,
                0.95F + random.nextFloat() * 0.15F
            );
            return;
        }

        Block.popResource(level, pos, new ItemStack(Items.STICK, 2));
        Block.popResource(level, pos, new ItemStack(Items.JUNGLE_PLANKS, 1 + random.nextInt(3)));
    }

    private static void spawnSuccessParticles(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos).add(0.0D, 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.ENCHANT, center.x(), center.y(), center.z(), 22, 0.35D, 0.25D, 0.35D, 0.18D);
        level.sendParticles(ParticleTypes.END_ROD, center.x(), center.y() + 0.15D, center.z(), 10, 0.28D, 0.22D, 0.28D, 0.04D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x(), center.y() + 0.05D, center.z(), 12, 0.3D, 0.18D, 0.3D, 0.03D);
    }
}