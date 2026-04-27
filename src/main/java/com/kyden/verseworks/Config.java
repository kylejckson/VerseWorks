package com.kyden.verseworks;

import com.kyden.verseworks.dimension.VerseDimensionCorruption;
import com.kyden.verseworks.dimension.VerseDimensionWorldType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Config {
    private static final List<String> DEFAULT_RANDOM_WORLD_TYPES = List.of(
        VerseDimensionWorldType.NORMAL.commandName(),
        VerseDimensionWorldType.AMPLIFIED.commandName(),
        VerseDimensionWorldType.SKY_ISLAND.commandName(),
        VerseDimensionWorldType.ISLAND.commandName()
    );

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_DIMENSIONS_PER_WORLD;
    public static final ModConfigSpec.IntValue MAX_DIMENSIONS_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue MYSTIC_CRYSTAL_OVERWORLD_PLACEMENT_CHANCE;
    public static final ModConfigSpec.DoubleValue MYSTIC_CRYSTAL_VERSE_DIMENSION_PLACEMENT_CHANCE;
    public static final ModConfigSpec.BooleanValue MYSTIC_RUIN_ENABLED;
    public static final ModConfigSpec.BooleanValue ALLOW_HYPER_BOOK_CREATION;
    public static final ModConfigSpec.BooleanValue START_WITH_GUIDEBOOK;
    public static final ModConfigSpec.IntValue STARTUP_PREPARED_DIMENSION_LIMIT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANDOM_WORLD_TYPE_POOL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANDOM_BIOME_WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RANDOM_BIOME_BLACKLIST;
    public static final Map<VerseDimensionCorruption, ModConfigSpec.BooleanValue> CORRUPTION_EFFECTS = new EnumMap<>(VerseDimensionCorruption.class);
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("limits");
        MAX_DIMENSIONS_PER_WORLD = BUILDER
            .comment("Maximum number of VerseWorks dimensions allowed in this world. Set to -1 for uncapped.")
            .defineInRange("maxDimensionsPerWorld", -1, -1, Integer.MAX_VALUE);
        MAX_DIMENSIONS_PER_PLAYER = BUILDER
            .comment("Maximum number of VerseWorks dimensions a single player may create. Set to -1 for uncapped.")
            .defineInRange("maxDimensionsPerPlayer", -1, -1, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("worldgen");
        MYSTIC_CRYSTAL_OVERWORLD_PLACEMENT_CHANCE = BUILDER
            .comment("Chance for a mystic crystal cave placement attempt in the Overworld. Default preserves current behavior.")
            .defineInRange("mysticCrystalOverworldPlacementChance", 0.12D, 0.0D, 1.0D);
        MYSTIC_CRYSTAL_VERSE_DIMENSION_PLACEMENT_CHANCE = BUILDER
            .comment("Chance for a mystic crystal cave placement attempt in VerseWorks dimensions. Default preserves current behavior.")
            .defineInRange("mysticCrystalVerseDimensionPlacementChance", 0.5D, 0.0D, 1.0D);
        MYSTIC_RUIN_ENABLED = BUILDER
            .comment("Whether the mystic ruin structure can generate.")
            .define("mysticRuinEnabled", true);
        BUILDER.pop();

        BUILDER.push("ritual");
        ALLOW_HYPER_BOOK_CREATION = BUILDER
            .comment("Whether cauldron rituals are allowed to create linked or unlinked Hyper Books.")
            .define("allowHyperBookCreation", true);
        BUILDER.pop();

        BUILDER.push("items");
        START_WITH_GUIDEBOOK = BUILDER
            .comment("Whether players should receive the VerseWorks guidebook automatically the first time they join a world.")
            .define("startWithGuidebook", true);
        BUILDER.pop();

        BUILDER.push("runtime");
        STARTUP_PREPARED_DIMENSION_LIMIT = BUILDER
            .comment("How many VerseWorks dimensions should be preloaded and fully prepared on server startup.")
            .defineInRange("startupPreparedDimensionLimit", 5, 0, Integer.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("randomization");
        RANDOM_WORLD_TYPE_POOL = BUILDER
            .comment("World types the ritual may pick when verses do not explicitly define one.")
            .defineListAllowEmpty("worldTypePool", DEFAULT_RANDOM_WORLD_TYPES, Config::isWorldTypeEntry);
        RANDOM_BIOME_WHITELIST = BUILDER
            .comment("Optional biome whitelist for ritual auto-randomization. Empty allows all biomes.")
            .defineListAllowEmpty("biomeWhitelist", List.of(), Config::isIdentifierEntry);
        RANDOM_BIOME_BLACKLIST = BUILDER
            .comment("Biome blacklist for ritual auto-randomization.")
            .defineListAllowEmpty("biomeBlacklist", List.of(), Config::isIdentifierEntry);
        BUILDER.pop();

        BUILDER.push("corruption");
        BUILDER.push("effects");
        for (VerseDimensionCorruption corruption : VerseDimensionCorruption.values()) {
            CORRUPTION_EFFECTS.put(
                corruption,
                BUILDER.comment("Whether the " + corruption.id() + " corruption may auto-apply and remain active at runtime.")
                    .define(corruption.id(), true)
            );
        }
        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private Config() {
    }

    public static boolean isUncapped(int configuredLimit) {
        return configuredLimit < 0;
    }

    public static boolean allowHyperBookCreation() {
        return ALLOW_HYPER_BOOK_CREATION.get();
    }

    public static int startupPreparedDimensionLimit() {
        return STARTUP_PREPARED_DIMENSION_LIMIT.get();
    }

    public static boolean startWithGuidebook() {
        return START_WITH_GUIDEBOOK.get();
    }

    public static boolean isCorruptionEffectEnabled(VerseDimensionCorruption corruption) {
        ModConfigSpec.BooleanValue configured = CORRUPTION_EFFECTS.get(corruption);
        return configured == null || configured.get();
    }

    public static List<VerseDimensionWorldType> allowedRandomWorldTypes() {
        List<VerseDimensionWorldType> parsed = new ArrayList<>();
        Set<VerseDimensionWorldType> unique = new LinkedHashSet<>();
        for (String entry : RANDOM_WORLD_TYPE_POOL.get()) {
            if (entry == null) {
                continue;
            }

            try {
                VerseDimensionWorldType worldType = VerseDimensionWorldType.parse(entry);
                if (unique.add(worldType)) {
                    parsed.add(worldType);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (!parsed.isEmpty()) {
            return List.copyOf(parsed);
        }

        return DEFAULT_RANDOM_WORLD_TYPES.stream()
            .map(VerseDimensionWorldType::parse)
            .toList();
    }

    public static boolean allowsRandomBiome(ResourceLocation biomeId) {
        Set<ResourceLocation> whitelist = configuredBiomeSet(RANDOM_BIOME_WHITELIST.get());
        if (!whitelist.isEmpty() && !whitelist.contains(biomeId)) {
            return false;
        }

        return !configuredBiomeSet(RANDOM_BIOME_BLACKLIST.get()).contains(biomeId);
    }

    private static boolean isWorldTypeEntry(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }

        try {
            VerseDimensionWorldType.parse(entry);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isIdentifierEntry(Object value) {
        return value instanceof String entry && ResourceLocation.tryParse(entry) != null;
    }

    private static Set<ResourceLocation> configuredBiomeSet(List<? extends String> entries) {
        LinkedHashSet<ResourceLocation> identifiers = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(entry.toLowerCase(Locale.ROOT));
            if (id != null) {
                identifiers.add(id);
            }
        }
        return Set.copyOf(identifiers);
    }
}
