package com.kyden.verseworks.block;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class WarpSpreadHelper {
    private static final int WITHER_DURATION_TICKS = 60;

    private WarpSpreadHelper() {
    }

    public static boolean isWarpAllowed(Level level) {
        return VerseWorks.MODID.equals(level.dimension().location().getNamespace());
    }

    public static void applyWarpHazard(Level level, Entity entity, boolean dealDamage) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION_TICKS, 0, false, true, true));
        if (dealDamage && player.tickCount % 20 == 0) {
            player.hurt(level.damageSources().wither(), 1.0F);
        }
    }

    public static void playSpreadSound(ServerLevel level, BlockPos pos) {
        SoundEvent sound = level.getRandom().nextBoolean() ? VerseSounds.WARP_1.get() : VerseSounds.WARP_2.get();
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.9F, 1.0F);
    }

    public static boolean canReplaceWithWarp(BlockState state) {
        return !state.isAir()
            && state.getFluidState().isEmpty()
            && !state.is(VerseBlocks.WARP.get())
            && !state.is(VerseBlocks.STABILIZED_WARP.get())
            && !state.is(VerseBlocks.WARP_VINE.get())
            && !state.is(VerseBlocks.STABILIZED_WARP_VINE.get())
            && !state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
    }
}