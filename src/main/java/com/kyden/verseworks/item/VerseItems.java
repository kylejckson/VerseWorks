package com.kyden.verseworks.item;

import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VerseItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, VerseWorks.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VerseWorks.MODID);

    public static final DeferredHolder<Item, VerseItem> VERSE = ITEMS.register("verse", () -> new VerseItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredHolder<Item, Item> HYPER_DUST = ITEMS.register(
        "hyper_dust",
        () -> new Item(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredHolder<Item, DimensionalAnalyzerItem> DIMENSIONAL_ANALYZER = ITEMS.register(
        "dimensional_analyzer",
        () -> new DimensionalAnalyzerItem(new Item.Properties().durability(50).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredHolder<Item, HyperBookItem> HYPER_BOOK = ITEMS.register(
        "hyper_book",
        () -> new HyperBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final DeferredHolder<Item, UnlinkedHyperBookItem> UNLINKED_HYPER_BOOK = ITEMS.register(
        "unlinked_hyper_book",
        () -> new UnlinkedHyperBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredHolder<Item, BlockItem> MYSTIC_CRATE = ITEMS.register(
        "mystic_crate",
        () -> new BlockItem(VerseBlocks.MYSTIC_CRATE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, BlockItem> MYSTIC_CRYSTAL = ITEMS.register(
        "mystic_crystal",
        () -> new BlockItem(VerseBlocks.MYSTIC_CRYSTAL.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, BlockItem> WARP = ITEMS.register(
        "warp",
        () -> new BlockItem(VerseBlocks.WARP.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, BlockItem> WARP_VINE = ITEMS.register(
        "warp_vine",
        () -> new BlockItem(VerseBlocks.WARP_VINE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, BlockItem> STABILIZED_WARP_VINE = ITEMS.register(
        "stabilized_warp_vine",
        () -> new BlockItem(VerseBlocks.STABILIZED_WARP_VINE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, BlockItem> STABILIZED_WARP = ITEMS.register(
        "stabilized_warp",
        () -> new BlockItem(VerseBlocks.STABILIZED_WARP.get(), new Item.Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VERSEWORKS_TAB = TABS.register(
        "verseworks",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.verseworks.general"))
            .icon(() -> new ItemStack(MYSTIC_CRYSTAL.get()))
            .displayItems((parameters, output) -> {
                output.accept(MYSTIC_CRYSTAL.get());
                output.accept(HYPER_DUST.get());
                output.accept(DIMENSIONAL_ANALYZER.get());
                output.accept(STABILIZED_WARP.get());
                output.accept(MYSTIC_CRATE.get());
                output.accept(UNLINKED_HYPER_BOOK.get());
                output.accept(WARP.get());
                output.accept(WARP_VINE.get());
                output.accept(STABILIZED_WARP_VINE.get());
            })
            .build()
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BIOME_VERSES_TAB = TABS.register(
        "biome_verses",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.verseworks.biome_verses"))
            .icon(VerseItems::iconStack)
            .displayItems((parameters, output) -> VerseCatalog.populateBiomeVerses(parameters, output, VERSE.get()))
            .build()
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> VERSES_TAB = TABS.register(
        "verses",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.verseworks.verses"))
            .icon(VerseItems::iconStack)
            .displayItems((parameters, output) -> VerseCatalog.populateOtherVerses(output, VERSE.get()))
            .build()
    );

    private VerseItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }

    private static ItemStack iconStack() {
        return VerseCatalog.createIconStack(VERSE.get());
    }
}
