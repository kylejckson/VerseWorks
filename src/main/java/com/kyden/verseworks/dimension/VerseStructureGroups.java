package com.kyden.verseworks.dimension;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class VerseStructureGroups {
    private static final Map<String, Set<String>> GROUP_PATTERNS = Map.of(
        "villages", Set.of("village"),
        "ruins", Set.of("ruin", "trail_ruins"),
        "dungeons", Set.of("dungeon", "monster_room"),
        "strongholds", Set.of("stronghold"),
        "temples", Set.of("temple", "pyramid", "igloo", "witch_hut", "jungle_temple", "desert_pyramid"),
        "ancient_city", Set.of("ancient_city"),
        "mineshafts", Set.of("mineshaft"),
        "trial_chambers", Set.of("trial_chambers", "trial"),
        "verseworks", Set.of("verseworks")
    );

    private VerseStructureGroups() {
    }

    public static Set<String> knownGroups() {
        return GROUP_PATTERNS.keySet();
    }

    public static boolean matches(ResourceLocation id, VerseDimensionParameters.StructureControlProfile profile) {
        if (profile.mode() == VerseDimensionParameters.StructureControlMode.NONE) {
            return id.getNamespace().equals("verseworks");
        }

        String normalizedPath = id.getPath().toLowerCase(Locale.ROOT);
        String normalizedId = id.toString().toLowerCase(Locale.ROOT);
        if (profile.exactBlock().contains(id)) {
            return false;
        }
        if (profile.exactAllow().contains(id)) {
            return true;
        }

        Set<String> matchedGroups = groupsFor(id);
        if (!profile.blockedGroups().isEmpty() && matchedGroups.stream().anyMatch(profile.blockedGroups()::contains)) {
            return false;
        }

        return switch (profile.mode()) {
            case ALL, DENYLIST -> true;
            case NONE -> id.getNamespace().equals("verseworks");
            case ALLOWLIST -> !profile.allowedGroups().isEmpty() && matchedGroups.stream().anyMatch(profile.allowedGroups()::contains);
        };
    }

    public static Set<String> groupsFor(ResourceLocation id) {
        String normalizedPath = id.getPath().toLowerCase(Locale.ROOT);
        String normalizedId = id.toString().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, Boolean> matches = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : GROUP_PATTERNS.entrySet()) {
            boolean matched = entry.getValue().stream().anyMatch(pattern -> normalizedPath.contains(pattern) || normalizedId.contains(pattern));
            if (matched) {
                matches.put(entry.getKey(), true);
            }
        }
        return matches.keySet();
    }
}
