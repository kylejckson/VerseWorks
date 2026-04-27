package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.resources.ResourceLocation;

public final class VerseDimensionVisuals {
    public static final ResourceLocation COLORED_SKY_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "colored_sky");
    public static final ResourceLocation ENDLIKE_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "endlike_sky");
    public static final ResourceLocation BLACK_VOID_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "black_void");
    public static final ResourceLocation SEALED_SKY_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "sealed_sky");
    public static final int DEFAULT_COLORED_SKY = 0x0073C2FB;

    private VerseDimensionVisuals() {
    }

    public static ResourceLocation effectsLocation(VerseDimensionParameters parameters) {
        if (usesBlackVoidSky(parameters)) {
            return BLACK_VOID_EFFECTS;
        }

        if (hidesCelestialBodies(parameters)) {
            return SEALED_SKY_EFFECTS;
        }

        if (usesSealedSky(parameters)) {
            return SEALED_SKY_EFFECTS;
        }

        if (isEndLikeDimension(parameters)) {
            return ENDLIKE_EFFECTS;
        }

        return COLORED_SKY_EFFECTS;
    }

    public static boolean usesBlackVoidSky(VerseDimensionParameters parameters) {
        return parameters.worldType().isVoid() && normalizedSkyColor(parameters.skyColor()) == 0;
    }

    public static boolean isEndLikeDimension(VerseDimensionParameters parameters) {
        return parameters.worldType() == VerseDimensionWorldType.ISLAND || parameters.hasExplicitEndBiomes();
    }

    public static boolean usesSealedSky(VerseDimensionParameters parameters) {
        return parameters.worldType().hasCeiling() && !usesBlackVoidSky(parameters);
    }

    public static boolean usesAmbientCeilingFog(VerseDimensionParameters parameters) {
        VerseDimensionWorldType worldType = parameters.worldType();
        if (worldType != VerseDimensionWorldType.CAVE_ONLY
            && worldType != VerseDimensionWorldType.CAVERN
            && worldType != VerseDimensionWorldType.BEDROCK_SHELL) {
            return false;
        }

        long hash = parameters.seedOffset();
        hash ^= (long) normalizedSkyColor(parameters.skyColor()) << 32;
        hash ^= (long) parameters.biomeIds().hashCode() * 0x9E3779B97F4A7C15L;
        hash ^= (long) worldType.ordinal() * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdl;
        hash ^= hash >>> 33;
        return (hash & 1L) == 0L;
    }

    public static boolean usesVanillaSkyGeometry(VerseDimensionParameters parameters) {
        return !usesBlackVoidSky(parameters) && !usesSealedSky(parameters) && !isEndLikeDimension(parameters);
    }

    public static boolean hidesCelestialBodies(VerseDimensionParameters parameters) {
        return !parameters.skyProfile().sunVisible() || !parameters.skyProfile().moonVisible();
    }

    public static float fogDensityScale(VerseDimensionParameters parameters) {
        return (float) parameters.skyProfile().fogDensity();
    }

    public static int normalizedSkyColor(int color) {
        return color & 0x00FFFFFF;
    }

    public static int resolvedColoredSkyColor(VerseDimensionParameters parameters) {
        int color = normalizedSkyColor(parameters.skyColor());
        return color != 0 ? color : DEFAULT_COLORED_SKY;
    }
}
