package com.kyden.verseworks.client.guidebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kyden.verseworks.VerseWorks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GuidebookContentManager extends SimplePreparableReloadListener<GuidebookContentManager.GuidebookBookDefinition> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation BOOK_DEFINITION = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "guidebook/book.json");
    private static final String CHAPTER_PREFIX = "guidebook/chapters";
    private static final GuidebookBookDefinition FALLBACK = new GuidebookBookDefinition(
        "guidebook.verseworks.title",
        "guidebook.verseworks.subtitle",
        "v1",
        List.of("welcome"),
        List.of(new GuidebookChapterDefinition(
            "welcome",
            "guidebook.chapter.welcome.title",
            "guidebook.chapter.welcome.summary",
            0,
            IconDefinition.item(Items.BOOK),
            List.of(new GuidebookPageDefinition(
                GuidebookPageType.TEXT,
                "guidebook.entry.fallback.title",
                List.of("guidebook.entry.fallback.body"),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
            ))
        ))
    );

    private static GuidebookBookDefinition activeBook = FALLBACK;

    public static GuidebookBookDefinition activeBook() {
        return activeBook;
    }

    @Override
    protected GuidebookBookDefinition prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            GuidebookBookDefinition book = loadBook(resourceManager);
            return book.chapters().isEmpty() ? FALLBACK : book;
        } catch (Exception exception) {
            VerseWorks.LOGGER.error("Failed to load VerseWorks guidebook content", exception);
            return FALLBACK;
        }
    }

    @Override
    protected void apply(GuidebookBookDefinition object, ResourceManager resourceManager, ProfilerFiller profiler) {
        activeBook = object;
    }

    private static GuidebookBookDefinition loadBook(ResourceManager resourceManager) throws IOException {
        BookIndexDefinition index;
        try (Reader reader = resourceManager.getResource(BOOK_DEFINITION).orElseThrow(() ->
            new IOException("Missing guidebook definition " + BOOK_DEFINITION)).openAsReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            index = BookIndexDefinition.CODEC.parse(JsonOps.INSTANCE, root)
                .resultOrPartial(error -> VerseWorks.LOGGER.error("Invalid guidebook index: {}", error))
                .orElseThrow(() -> new IOException("Invalid guidebook index"));
        }

        Map<String, GuidebookChapterDefinition> chaptersById = new LinkedHashMap<>();
        resourceManager.listResources(CHAPTER_PREFIX, location -> location.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonElement root = JsonParser.parseReader(reader);
                GuidebookChapterDefinition chapter = GuidebookChapterDefinition.CODEC.parse(JsonOps.INSTANCE, root)
                    .resultOrPartial(error -> VerseWorks.LOGGER.error("Invalid guidebook chapter {}: {}", location, error))
                    .orElse(null);
                if (chapter != null) {
                    chaptersById.put(chapter.id(), chapter);
                }
            } catch (Exception exception) {
                VerseWorks.LOGGER.error("Failed to load guidebook chapter {}", location, exception);
            }
        });

        List<GuidebookChapterDefinition> chapters = new ArrayList<>();
        for (String chapterId : index.chapterOrder()) {
            GuidebookChapterDefinition chapter = chaptersById.get(chapterId);
            if (chapter != null) {
                chapters.add(chapter);
            } else {
                VerseWorks.LOGGER.warn("Guidebook chapter {} was referenced but not found", chapterId);
            }
        }
        chaptersById.values().stream()
            .filter(chapter -> !index.chapterOrder().contains(chapter.id()))
            .sorted(Comparator.comparingInt(GuidebookChapterDefinition::sortOrder).thenComparing(GuidebookChapterDefinition::id))
            .forEach(chapters::add);

        return new GuidebookBookDefinition(index.titleKey(), index.subtitleKey(), index.version(), index.landingChapterIds(), chapters);
    }

    public record GuidebookBookDefinition(
        String titleKey,
        String subtitleKey,
        String version,
        List<String> landingChapterIds,
        List<GuidebookChapterDefinition> chapters
    ) {
        public Optional<GuidebookChapterDefinition> chapter(String id) {
            return this.chapters.stream().filter(chapter -> chapter.id().equals(id)).findFirst();
        }
    }

    private record BookIndexDefinition(String titleKey, String subtitleKey, String version, List<String> landingChapterIds, List<String> chapterOrder) {
        private static final Codec<BookIndexDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("title_key").forGetter(BookIndexDefinition::titleKey),
            Codec.STRING.fieldOf("subtitle_key").forGetter(BookIndexDefinition::subtitleKey),
            Codec.STRING.optionalFieldOf("version", "v1").forGetter(BookIndexDefinition::version),
            Codec.STRING.listOf().optionalFieldOf("landing_chapter_ids", List.of()).forGetter(BookIndexDefinition::landingChapterIds),
            Codec.STRING.listOf().fieldOf("chapter_order").forGetter(BookIndexDefinition::chapterOrder)
        ).apply(instance, BookIndexDefinition::new));
    }

    public record GuidebookChapterDefinition(
        String id,
        String titleKey,
        String summaryKey,
        int sortOrder,
        IconDefinition icon,
        List<GuidebookPageDefinition> pages
    ) {
        public static final Codec<GuidebookChapterDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GuidebookChapterDefinition::id),
            Codec.STRING.fieldOf("title_key").forGetter(GuidebookChapterDefinition::titleKey),
            Codec.STRING.fieldOf("summary_key").forGetter(GuidebookChapterDefinition::summaryKey),
            Codec.INT.optionalFieldOf("sort_order", 0).forGetter(GuidebookChapterDefinition::sortOrder),
            IconDefinition.CODEC.fieldOf("icon").forGetter(GuidebookChapterDefinition::icon),
            GuidebookPageDefinition.CODEC.listOf().fieldOf("pages").forGetter(GuidebookChapterDefinition::pages)
        ).apply(instance, GuidebookChapterDefinition::new));
    }

    public record GuidebookPageDefinition(
        GuidebookPageType type,
        String titleKey,
        List<String> bodyKeys,
        String image,
        String iconLabelKey,
        IconDefinition icon,
        List<IconDefinition> ingredients,
        List<String> linkChapterIds,
        List<String> notes
    ) {
        public static final Codec<GuidebookPageDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GuidebookPageType.CODEC.fieldOf("type").forGetter(GuidebookPageDefinition::type),
            Codec.STRING.fieldOf("title_key").forGetter(GuidebookPageDefinition::titleKey),
            Codec.STRING.listOf().optionalFieldOf("body_keys", List.of()).forGetter(GuidebookPageDefinition::bodyKeys),
            Codec.STRING.optionalFieldOf("image").forGetter(page -> Optional.ofNullable(page.image())),
            Codec.STRING.optionalFieldOf("icon_label_key").forGetter(page -> Optional.ofNullable(page.iconLabelKey())),
            IconDefinition.CODEC.optionalFieldOf("icon").forGetter(page -> Optional.ofNullable(page.icon())),
            IconDefinition.CODEC.listOf().optionalFieldOf("ingredients", List.of()).forGetter(GuidebookPageDefinition::ingredients),
            Codec.STRING.listOf().optionalFieldOf("link_chapter_ids", List.of()).forGetter(GuidebookPageDefinition::linkChapterIds),
            Codec.STRING.listOf().optionalFieldOf("notes", List.of()).forGetter(GuidebookPageDefinition::notes)
        ).apply(instance, (type, titleKey, bodyKeys, image, iconLabelKey, icon, ingredients, linkChapterIds, notes) ->
            new GuidebookPageDefinition(type, titleKey, bodyKeys, image.orElse(null), iconLabelKey.orElse(null), icon.orElse(null), ingredients, linkChapterIds, notes)
        ));
    }

    public enum GuidebookPageType {
        TEXT,
        IMAGE,
        ITEM_SPOTLIGHT,
        RITUAL_LAYOUT,
        FEATURE_CALLOUT,
        LINK_LIST;

        public static final Codec<GuidebookPageType> CODEC = Codec.STRING.xmap(GuidebookPageType::parse, GuidebookPageType::serializedName);

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static GuidebookPageType parse(String value) {
            return GuidebookPageType.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public record IconDefinition(String type, String value) {
        public static final Codec<IconDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(IconDefinition::type),
            Codec.STRING.fieldOf("value").forGetter(IconDefinition::value)
        ).apply(instance, IconDefinition::new));

        public static IconDefinition item(Item item) {
            return new IconDefinition("item", BuiltInRegistries.ITEM.getKey(item).toString());
        }

        public static IconDefinition block(Block block) {
            return new IconDefinition("block", BuiltInRegistries.BLOCK.getKey(block).toString());
        }

        public static IconDefinition texture(ResourceLocation texture) {
            return new IconDefinition("texture", texture.toString());
        }

        public Item resolveItem() {
            if ("block".equals(this.type)) {
                Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(this.value)).orElse(Blocks.AIR);
                return block.asItem();
            }
            if ("item".equals(this.type)) {
                return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(this.value)).orElse(Items.BARRIER);
            }
            return Items.BARRIER;
        }

        public ResourceLocation resolveTexture() {
            return ResourceLocation.parse(this.value);
        }
    }
}
