package com.kyden.verseworks.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;

public enum VerseDimensionCorruption {
    ENDLESS_RAIN("endless_rain", "rain refuses to leave"),
    ENDLESS_STORM("endless_storm", "storms linger"),
    ENDLESS_LIGHTNING("endless_lightning", "lightning hunts the horizon"),
    METEORS("meteors", "look to the skies"),
    WARP("warp", "warp has found its way in"),
    FIXED_TIME("fixed_time", "time doesn't pass"),
    GRAVITY("gravity", "gravity feels wrong"),
    SPHERES("spheres", "strange spheres drift through the world"),
    HOSTILE_HORDES("hostile_hordes", "monsters gather in unusual numbers");

    public static final Codec<VerseDimensionCorruption> CODEC = Codec.STRING.comapFlatMap(
        value -> fromId(value)
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown corruption: " + value)),
        VerseDimensionCorruption::id
    );

    private final String id;
    private final String message;

    VerseDimensionCorruption(String id, String message) {
        this.id = id;
        this.message = message;
    }

    public String id() {
        return this.id;
    }

    public String message() {
        return this.message;
    }

    public static java.util.Optional<VerseDimensionCorruption> fromId(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(corruption -> corruption.id.equals(normalized))
            .findFirst();
    }
}
