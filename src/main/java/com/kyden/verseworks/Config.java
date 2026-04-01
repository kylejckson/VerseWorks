package com.kyden.verseworks;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_DIMENSIONS_PER_WORLD = BUILDER
        .comment("Maximum number of VerseWorks dimensions allowed in this world. Set to -1 for uncapped.")
        .defineInRange("limits.maxDimensionsPerWorld", -1, -1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_DIMENSIONS_PER_PLAYER = BUILDER
        .comment("Maximum number of VerseWorks dimensions a single player may create. Set to -1 for uncapped.")
        .defineInRange("limits.maxDimensionsPerPlayer", -1, -1, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue MYSTIC_CRYSTAL_OVERWORLD_PLACEMENT_CHANCE = BUILDER
        .comment("Chance for a mystic crystal cave placement attempt in the Overworld. Default preserves current behavior.")
        .defineInRange("worldgen.mysticCrystalOverworldPlacementChance", 0.12D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue MYSTIC_CRYSTAL_VERSE_DIMENSION_PLACEMENT_CHANCE = BUILDER
        .comment("Chance for a mystic crystal cave placement attempt in VerseWorks dimensions. Default preserves current behavior.")
        .defineInRange("worldgen.mysticCrystalVerseDimensionPlacementChance", 0.5D, 0.0D, 1.0D);

    public static final ModConfigSpec.BooleanValue MYSTIC_RUIN_ENABLED = BUILDER
        .comment("Whether the mystic ruin structure can generate.")
        .define("worldgen.mysticRuinEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static boolean isUncapped(int configuredLimit) {
        return configuredLimit < 0;
    }
}
