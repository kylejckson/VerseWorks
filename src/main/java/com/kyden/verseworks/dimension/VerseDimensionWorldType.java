package com.kyden.verseworks.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum VerseDimensionWorldType {
    NORMAL("normal", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    AMPLIFIED("amplified", NoiseGeneratorSettings.AMPLIFIED, "minecraft:amplified"),
    SKY_ISLAND("sky_island", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    ISLAND("island", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    ALL_STONE("all_stone", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    BEDROCK_SHELL("bedrock_shell", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    OCEAN("ocean", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    CAVE_ONLY("cave_only", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    WATER_WORLD("water_world", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    CAVERN("cavern", NoiseGeneratorSettings.NETHER, "minecraft:nether"),
    INVERSE_CAVES("inverse_caves", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    FLAT("flat", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld"),
    VOID("void", NoiseGeneratorSettings.OVERWORLD, "minecraft:overworld");

    private final String commandName;
    private final ResourceKey<NoiseGeneratorSettings> noiseSettingsKey;
    private final String noiseSettingsId;

    public static final Codec<VerseDimensionWorldType> CODEC = Codec.STRING.comapFlatMap(
        value -> {
            try {
                return DataResult.success(parse(value));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        },
        VerseDimensionWorldType::commandName
    );

    VerseDimensionWorldType(String commandName, ResourceKey<NoiseGeneratorSettings> noiseSettingsKey, String noiseSettingsId) {
        this.commandName = commandName;
        this.noiseSettingsKey = noiseSettingsKey;
        this.noiseSettingsId = noiseSettingsId;
    }

    public String commandName() {
        return this.commandName;
    }

    public boolean isFlat() {
        return this == FLAT || this == VOID;
    }

    public boolean isVoid() {
        return this == VOID;
    }

    public boolean usesCustomTerrainProfile() {
        return switch (this) {
            case SKY_ISLAND, ISLAND, ALL_STONE, OCEAN, CAVE_ONLY, WATER_WORLD, CAVERN, INVERSE_CAVES, VOID -> true;
            default -> false;
        };
    }

    public boolean usesStrongSurfaceReplacement() {
        return switch (this) {
            case SKY_ISLAND, ISLAND, OCEAN, WATER_WORLD, CAVERN, INVERSE_CAVES -> true;
            default -> false;
        };
    }

    public boolean hasBedrockShell() {
        return this == BEDROCK_SHELL || this == WATER_WORLD || this == CAVERN;
    }

    public boolean hasCeiling() {
        return switch (this) {
            case ALL_STONE, BEDROCK_SHELL, CAVE_ONLY, WATER_WORLD, CAVERN -> true;
            default -> false;
        };
    }

    public boolean isFluidWorld() {
        return this == WATER_WORLD;
    }

    public ResourceKey<NoiseGeneratorSettings> noiseSettingsKey() {
        if (this.noiseSettingsKey == null) {
            throw new IllegalStateException(this.commandName + " does not use noise settings");
        }
        return this.noiseSettingsKey;
    }

    public String noiseSettingsId() {
        if (this.noiseSettingsId == null) {
            throw new IllegalStateException(this.commandName + " does not use noise settings");
        }
        return this.noiseSettingsId;
    }

    public static VerseDimensionWorldType parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
            .filter(value -> value.commandName.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown world type: " + input));
    }

    public static List<String> commandNames() {
        return Arrays.stream(values()).map(VerseDimensionWorldType::commandName).toList();
    }
}
