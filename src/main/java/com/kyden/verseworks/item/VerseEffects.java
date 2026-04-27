package com.kyden.verseworks.item;

import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.dimension.VerseStructureGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VerseEffects {
    public static final String STRUCTURE_GROUP = "structureGroup";
    public static final String SHAPE_FEATURE = "shapeFeature";
    public static final String POOL_FEATURE = "poolFeature";
    public static final String SURFACE_PROFILE = "surfaceProfile";
    public static final String CRYSTAL_CLUSTERS = "crystalClusters";

    private VerseEffects() {
    }

    public static List<VerseData> catalogEntries() {
        List<VerseData> verses = new ArrayList<>();
        verses.addAll(structureVerses());
        verses.addAll(shapeVerses());
        verses.addAll(poolVerses());
        verses.addAll(surfaceVerses());
        verses.addAll(crystalVerses());
        return List.copyOf(verses);
    }

    public static List<VerseData> structureVerses() {
        List<VerseData> verses = new ArrayList<>();
        verses.add(VerseData.stringValue("", "No Structures", "structure", STRUCTURE_GROUP, "none", "none"));
        for (String group : VerseStructureGroups.knownGroups()) {
            verses.add(VerseData.stringValue("", humanize(group) + " Structures", "structure", STRUCTURE_GROUP, group, group));
        }
        return verses;
    }

    public static List<VerseData> shapeVerses() {
        List<VerseData> verses = new ArrayList<>();
        addShapeVerse(verses, "Coal Sphere", "sphere", Blocks.COAL_ORE);
        addShapeVerse(verses, "Iron Sphere", "sphere", Blocks.IRON_ORE);
        addShapeVerse(verses, "Coal Cube", "cube", Blocks.COAL_ORE);
        addShapeVerse(verses, "Iron Cube", "cube", Blocks.IRON_ORE);
        addShapeVerse(verses, "Copper Cube", "cube", Blocks.COPPER_ORE);
        addShapeVerse(verses, "Diamond Cube", "cube", Blocks.DIAMOND_ORE);
        addShapeVerse(verses, "Obsidian Cube", "cube", Blocks.OBSIDIAN);
        addShapeVerse(verses, "Oak Log Cube", "cube", Blocks.OAK_LOG);
        return verses;
    }

    public static List<VerseData> poolVerses() {
        List<VerseData> verses = new ArrayList<>();
        Set<ResourceLocation> fluidIds = new LinkedHashSet<>();
        fluidIds.add(ResourceLocation.withDefaultNamespace("water"));
        fluidIds.add(ResourceLocation.withDefaultNamespace("lava"));
        BuiltInRegistries.FLUID.stream()
            .map(BuiltInRegistries.FLUID::getKey)
            .filter(id -> id != null && !id.getPath().startsWith("flowing_"))
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(fluidIds::add);
        for (ResourceLocation fluidId : fluidIds) {
            addPoolVerseIfPresent(verses, humanizeFluid(fluidId) + " Pools", fluidId);
        }
        return verses;
    }

    public static List<VerseData> surfaceVerses() {
        return List.of(
            VerseData.stringValue("", "Mycelium Surface", "surface", SURFACE_PROFILE, "minecraft:mycelium", "minecraft:mycelium"),
            VerseData.stringValue("", "Packed Ice Surface", "surface", SURFACE_PROFILE, "minecraft:packed_ice", "minecraft:packed_ice"),
            VerseData.stringValue("", "Sand Surface", "surface", SURFACE_PROFILE, "minecraft:sand", "minecraft:sand"),
            VerseData.stringValue("", "Dirt Surface", "surface", SURFACE_PROFILE, "minecraft:dirt", "minecraft:dirt")
        );
    }

    public static List<VerseData> crystalVerses() {
        return List.of(
            VerseData.booleanValue("Prisma", "Crystal Clusters", "structure", CRYSTAL_CLUSTERS, true, "true"),
            VerseData.booleanValue("Anti-", "Crystal Clusters", "structure", CRYSTAL_CLUSTERS, false, "false")
        );
    }

    private static void addShapeVerse(List<VerseData> verses, String label, String kind, Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id != null) {
            verses.add(VerseData.stringValue("", label, "shape", SHAPE_FEATURE, kind + "|" + id, kind + "|" + id));
        }
    }

    private static void addPoolVerseIfPresent(List<VerseData> verses, String label, ResourceLocation fluidId) {
        if (fluidId == null || fluidId.getPath().isBlank() || fluidId.getPath().contains("null")) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
        if (label == null || label.isBlank() || label.toLowerCase(Locale.ROOT).contains("null")) {
            return;
        }
        if (fluid == null || !fluid.defaultFluidState().isSource() || fluid.defaultFluidState().createLegacyBlock().isAir()) {
            return;
        }
        verses.add(VerseData.stringValue("", label, "pool", POOL_FEATURE, fluidId.toString(), fluidId.toString()));
    }

    private static String humanize(String value) {
        return value.replace('_', ' ').substring(0, 1).toUpperCase(Locale.ROOT) + value.replace('_', ' ').substring(1);
    }

    private static String humanizeFluid(ResourceLocation fluidId) {
        return humanize(fluidId.getPath().replace("still_", "").replace("source_", ""));
    }
}
