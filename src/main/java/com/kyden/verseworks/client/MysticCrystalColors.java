package com.kyden.verseworks.client;

import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.item.VerseItems;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.awt.Color;

public final class MysticCrystalColors {
    private MysticCrystalColors() {
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, getter, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFF;
            }
            return rainbowCrystalColor(getter, pos);
        }, VerseBlocks.MYSTIC_CRYSTAL.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? HyperDustTintSource.colorForCurrentTime() : 0xFFFFFF, VerseItems.HYPER_DUST.get());
    }

    private static int rainbowCrystalColor(BlockAndTintGetter getter, BlockPos pos) {
        if (getter == null || pos == null) {
            return 0x8CF7FF;
        }

        int biomeColor = BiomeColors.getAverageGrassColor(getter, pos);
        float[] biomeHsb = Color.RGBtoHSB(
            (biomeColor >> 16) & 0xFF,
            (biomeColor >> 8) & 0xFF,
            biomeColor & 0xFF,
            null
        );

        float gradientHue = Mth.positiveModulo(
            biomeHsb[0] * 0.35F
                + pos.getX() * 0.0145F
                + pos.getZ() * 0.0095F
                + pos.getY() * 0.0035F,
            1.0F
        );
        int rgb = Color.HSBtoRGB(gradientHue, 0.72F, 1.0F);
        return rgb & 0xFFFFFF;
    }
}