package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.resources.ResourceLocation;

public final class VerseDimensionVisuals {
    public static final ResourceLocation COLORED_SKY_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "colored_sky");
    public static final ResourceLocation ENDLIKE_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "endlike_sky");
    public static final ResourceLocation BLACK_VOID_EFFECTS = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "black_void");
    public static final int DEFAULT_COLORED_SKY = 0x0073C2FB;

    private VerseDimensionVisuals() {
    }

    public static ResourceLocation effectsLocation(VerseDimensionParameters parameters) {
        if (usesBlackVoidSky(parameters)) {
            return BLACK_VOID_EFFECTS;
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

    public static boolean usesVanillaSkyGeometry(VerseDimensionParameters parameters) {
        return !usesBlackVoidSky(parameters) && !isEndLikeDimension(parameters);
    }

    public static int normalizedSkyColor(int color) {
        return color & 0x00FFFFFF;
    }

    public static int resolvedColoredSkyColor(VerseDimensionParameters parameters) {
        int color = normalizedSkyColor(parameters.skyColor());
        return color != 0 ? color : DEFAULT_COLORED_SKY;
    }
}