package com.kyden.verseworks.client;

import java.awt.Color;

public final class HyperDustTintSource {
    private HyperDustTintSource() {
    }

    public static int colorForCurrentTime() {
        long timeMs = System.currentTimeMillis();
        float hue = (timeMs % 6000L) / 6000.0F;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0x00FFFFFF);
    }
}