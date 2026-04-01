package com.kyden.verseworks.item;

import com.kyden.verseworks.dimension.VerseDimensionWorldType;
import com.kyden.verseworks.util.VerseText;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class VerseCatalog {
    private static final float CRYSTAL_BIOME_DROP_CHANCE = 0.60F;
    private static final List<SkyColorEntry> SKY_COLOR_VERSES = List.of(
        new SkyColorEntry("White", 0x00F9FFFE),
        new SkyColorEntry("Light Gray", 0x009D9D97),
        new SkyColorEntry("Gray", 0x00474F52),
        new SkyColorEntry("Black", 0x001D1D21),
        new SkyColorEntry("Brown", 0x00835432),
        new SkyColorEntry("Red", 0x00B02E26),
        new SkyColorEntry("Orange", 0x00F9801D),
        new SkyColorEntry("Yellow", 0x00FED83D),
        new SkyColorEntry("Lime", 0x0080C71F),
        new SkyColorEntry("Green", 0x005E7C16),
        new SkyColorEntry("Cyan", 0x00169C9C),
        new SkyColorEntry("Light Blue", 0x003AB3DA),
        new SkyColorEntry("Blue", 0x003C44AA),
        new SkyColorEntry("Purple", 0x008932B8),
        new SkyColorEntry("Magenta", 0x00C74EBD),
        new SkyColorEntry("Pink", 0x00F38BAA)
    );

    private VerseCatalog() {
    }

    public static int randomSkyColor(RandomSource random) {
        return SKY_COLOR_VERSES.get(random.nextInt(SKY_COLOR_VERSES.size())).skyColor();
    }

    public static ItemStack createIconStack(Item item) {
        return createStack(item, skyColorVerse(SKY_COLOR_VERSES.getFirst()));
    }

    public static void populateBiomeVerses(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output, Item item) {
        lookupBiomes(parameters.holders()).stream()
            .sorted(Comparator.comparing(identifier -> identifier.location().toString()))
            .forEach(biomeKey -> output.accept(createStack(item, biomeVerse(biomeKey.location()))));
    }

    public static void populateOtherVerses(CreativeModeTab.Output output, Item item) {
        for (VerseData verse : nonBiomeVerses()) {
            output.accept(createStack(item, verse));
        }
    }

    public static ItemStack createRandomDropVerse(Item item, HolderLookup.Provider provider, RandomSource random) {
        boolean useBiomeVerse = random.nextFloat() < CRYSTAL_BIOME_DROP_CHANCE;
        if (useBiomeVerse) {
            return createRandomBiomeVerse(item, provider, random);
        }

        return createRandomNonBiomeVerse(item, random);
    }

    public static ItemStack createRandomBiomeVerse(Item item, HolderLookup.Provider provider, RandomSource random) {
        List<ResourceKey<Biome>> biomes = lookupBiomes(provider);
        if (biomes.isEmpty()) {
            return createRandomNonBiomeVerse(item, random);
        }

        ResourceKey<Biome> biomeKey = biomes.get(random.nextInt(biomes.size()));
        return createStack(item, biomeVerse(biomeKey.location()));
    }

    public static ItemStack createRandomNonBiomeVerse(Item item, RandomSource random) {
        List<VerseData> nonBiomeVerses = nonBiomeVerses();
        VerseData selected = nonBiomeVerses.get(random.nextInt(nonBiomeVerses.size()));
        return createStack(item, selected);
    }

    public static int randomCrystalVerseDropCount(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.72F) {
            return 1;
        }
        if (roll < 0.94F) {
            return 2;
        }
        return 3;
    }

    // Creative tab population and random drops both draw from the same source so new verses
    // only need to be added in one place.
    private static List<VerseData> nonBiomeVerses() {
        List<VerseData> verses = new ArrayList<>();
        for (SkyColorEntry entry : SKY_COLOR_VERSES) {
            verses.add(skyColorVerse(entry));
        }
        for (VerseDimensionWorldType worldType : VerseDimensionWorldType.values()) {
            verses.add(worldTypeVerse(worldType));
        }
        verses.addAll(sphereOreVerses());
        verses.addAll(environmentVerses());
        return verses;
    }

    private static VerseData biomeVerse(ResourceLocation biomeId) {
        return VerseData.stringValue("Bioma", VerseText.displayBiomeName(biomeId), "biome", "biomeId", biomeId.toString(), biomeId.toString());
    }

    private static VerseData skyColorVerse(SkyColorEntry entry) {
        return VerseData.intValue("Caelum", entry.label(), "sky_color", "skyColor", entry.skyColor(), VerseText.colorHex(entry.skyColor()));
    }

    private static VerseData worldTypeVerse(VerseDimensionWorldType worldType) {
        return VerseData.stringValue("", VerseText.humanize(worldType.commandName()), "world_type", "worldType", worldType.commandName(), worldType.commandName());
    }

    private static List<VerseData> environmentVerses() {
        return List.of(
            VerseData.booleanValue("Anti-", "Spheres", "sphere", "spheres", false, "false"),
            VerseData.booleanValue("", "Stabilized Realm", "stability", "stabilizedRealm", true, "true"),
            VerseData.doubleValue("Gravitas", "Low", "gravity", "gravityScale", 0.25D, trimmedDouble(0.25D)),
            VerseData.doubleValue("Gravitas", "Normal", "gravity", "gravityScale", 1.0D, trimmedDouble(1.0D)),
            VerseData.doubleValue("Gravitas", "High", "gravity", "gravityScale", 4.0D, trimmedDouble(4.0D)),
            VerseData.doubleValue("Anti-", "Gravity", "gravity", "gravityScale", 1.0D, trimmedDouble(1.0D)),
            VerseData.intValue("Tempus", "Day", "fixed_time", "timeOfDay", 1000, "1000").withSecondary("permanentTime", "true"),
            VerseData.intValue("Tempus", "Midnight", "fixed_time", "timeOfDay", 18000, "18000").withSecondary("permanentTime", "true"),
            VerseData.booleanValue("Anti-", "Fixed Time", "fixed_time", "permanentTime", false, "false"),
            VerseData.booleanValue("Tempestas", "Endless Rain", "weather", "permanentRain", true, "true"),
            VerseData.booleanValue("Tempestas", "Endless Lightning", "weather", "permanentLightning", true, "true"),
            VerseData.booleanValue("Tempestas", "Endless Storm", "weather", "permanentStorm", true, "true").withSecondary("permanentRain", "true"),
            VerseData.booleanValue("Tempestas", "Meteor Showers", "weather", "meteorShowers", true, "true"),
            VerseData.booleanValue("Anti-", "Endless Rain", "weather", "permanentRain", false, "false"),
            VerseData.booleanValue("Anti-", "Endless Lightning", "weather", "permanentLightning", false, "false"),
            VerseData.booleanValue("Anti-", "Endless Storm", "weather", "permanentStorm", false, "false"),
            VerseData.booleanValue("Anti-", "Meteors", "weather", "meteorShowers", false, "false"),
            VerseData.booleanValue("Anti-", "Warp", "weather", "spawnWarp", false, "false"),
            VerseData.doubleValue("Metallum", "None", "ore", "oreMultiplier", 0.0D, trimmedDouble(0.0D)),
            VerseData.doubleValue("Metallum", "I", "ore", "oreMultiplier", 2.0D, trimmedDouble(2.0D)),
            VerseData.doubleValue("Metallum", "II", "ore", "oreMultiplier", 4.0D, trimmedDouble(4.0D)),
            VerseData.doubleValue("Metallum", "III", "ore", "oreMultiplier", 8.0D, trimmedDouble(8.0D))
        );
    }

    private static List<VerseData> sphereOreVerses() {
        List<VerseData> verses = new ArrayList<>();
        addSphereOreVerseIfPresent(verses, "Coal Ore Sphere", "minecraft:coal_ore");
        addSphereOreVerseIfPresent(verses, "Iron Ore Sphere", "minecraft:iron_ore");
        addSphereOreVerseIfPresent(verses, "Copper Ore Sphere", "minecraft:copper_ore");
        addSphereOreVerseIfPresent(verses, "Gold Ore Sphere", "minecraft:gold_ore");
        addSphereOreVerseIfPresent(verses, "Diamond Ore Sphere", "minecraft:diamond_ore");
        addSphereOreVerseIfPresent(verses, "Lapis Lazuli Ore Sphere", "minecraft:lapis_ore");
        addSphereOreVerseIfPresent(verses, "Uranium Ore Sphere", "c:uranium_ores", "forge:uranium_ore", "forge:uranium");
        addSphereOreVerseIfPresent(verses, "Tin Ore Sphere", "c:tin_ores", "forge:tin_ore", "forge:tin");
        return List.copyOf(verses);
    }

    private static void addSphereOreVerseIfPresent(List<VerseData> verses, String label, String... candidates) {
        for (String candidate : candidates) {
            ResourceLocation resolvedBlockId = resolveSphereBlockCandidate(candidate);
            if (resolvedBlockId == null) {
                continue;
            }

            verses.add(VerseData.stringValue("", label, "sphere", "sphereBlock", resolvedBlockId.toString(), resolvedBlockId.toString()));
            return;
        }
    }

    private static ResourceLocation resolveSphereBlockCandidate(String candidate) {
        ResourceLocation id = ResourceLocation.tryParse(candidate);
        if (id == null) {
            return null;
        }

        if (BuiltInRegistries.BLOCK.getOptional(id).isPresent()) {
            return id;
        }

        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, id);
        return BuiltInRegistries.BLOCK.getTag(tagKey)
            .flatMap(tag -> tag.stream()
                .map(Holder::value)
                .map(BuiltInRegistries.BLOCK::getKey)
                .filter(blockId -> blockId != null && !BuiltInRegistries.BLOCK.get(blockId).defaultBlockState().isAir())
                .findFirst())
            .orElse(null);
    }

    private static List<ResourceKey<Biome>> lookupBiomes(HolderLookup.Provider provider) {
        return provider.lookupOrThrow(Registries.BIOME)
            .listElements()
            .map(Holder.Reference::key)
            .toList();
    }

    private static ItemStack createStack(Item item, VerseData data) {
        return data.apply(new ItemStack(item));
    }

    private static String trimmedDouble(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record SkyColorEntry(String label, int skyColor) {
    }
}
