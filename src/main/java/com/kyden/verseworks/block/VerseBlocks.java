package com.kyden.verseworks.block;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VerseBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, VerseWorks.MODID);

    public static final DeferredHolder<Block, Block> MYSTIC_CRATE = BLOCKS.register(
        "mystic_crate",
        () -> new MysticCrateBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD)
        )
    );

    public static final DeferredHolder<Block, Block> MYSTIC_CRYSTAL = BLOCKS.register(
        "mystic_crystal",
        () -> new MysticCrystalBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(0.45F, 6.0F)
                .sound(SoundType.AMETHYST_CLUSTER)
                .lightLevel(state -> 4)
                .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, Block> METEOR = BLOCKS.register(
        "meteor",
        () -> new Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.0F, 12.0F)
                .sound(SoundType.DEEPSLATE)
                .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, Block> WARP = BLOCKS.register(
        "warp",
        () -> new WarpBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE)
                .lightLevel(state -> 3)
                .emissiveRendering((state, level, pos) -> true)
                .requiresCorrectToolForDrops()
                .randomTicks()
        )
    );

    public static final DeferredHolder<Block, Block> WARP_VINE = BLOCKS.register(
        "warp_vine",
        () -> new WarpVineBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(0.2F)
                .sound(SoundType.VINE)
                .noCollission()
                .noOcclusion()
                .replaceable()
                .ignitedByLava()
                .randomTicks()
        )
    );

    public static final DeferredHolder<Block, Block> STABILIZED_WARP_VINE = BLOCKS.register(
        "stabilized_warp_vine",
        () -> new StabilizedWarpVineBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(0.2F)
                .sound(SoundType.VINE)
                .noCollission()
                .noOcclusion()
                .replaceable()
                .ignitedByLava()
        )
    );

    public static final DeferredHolder<Block, Block> STABILIZED_WARP = BLOCKS.register(
        "stabilized_warp",
        () -> new Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE)
                .lightLevel(state -> 3)
                .emissiveRendering((state, level, pos) -> true)
                .requiresCorrectToolForDrops()
        )
    );

    private VerseBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}