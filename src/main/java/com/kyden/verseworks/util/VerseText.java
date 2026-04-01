package com.kyden.verseworks.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class VerseText {
    private VerseText() {
    }

    public static String humanize(String raw) {
        String normalized = raw.replace('/', ' ').replace('_', ' ').replace('-', ' ');
        String[] parts = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            result.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    public static String displayDimensionName(ResourceLocation dimensionId) {
        if (dimensionId.equals(Level.OVERWORLD.location())) {
            return "Overworld";
        }
        if (dimensionId.equals(Level.NETHER.location())) {
            return "The Nether";
        }
        if (dimensionId.equals(Level.END.location())) {
            return "The End";
        }
        return humanize(dimensionId.getPath());
    }

    public static String displayBiomeName(ResourceLocation biomeId) {
        String label = humanize(biomeId.getPath());
        if (ResourceLocation.DEFAULT_NAMESPACE.equals(biomeId.getNamespace())) {
            return label;
        }
        return humanize(biomeId.getNamespace()) + " " + label;
    }

    public static String formatTimeOfDay(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        long hours = (timeOfDay / 1000L + 6L) % 24L;
        long minutes = (timeOfDay % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    public static String colorHex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0x00FFFFFF);
    }
}
