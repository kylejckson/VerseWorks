package com.kyden.verseworks;

import com.mojang.logging.LogUtils;
import com.kyden.verseworks.attachment.VerseAttachments;
import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.command.VerseWorksCommands;
import com.kyden.verseworks.dimension.VerseChunkGenerator;
import com.kyden.verseworks.dimension.HyperBookCollapseHooks;
import com.kyden.verseworks.dimension.VerseDimensionMobSpawnHooks;
import com.kyden.verseworks.dimension.VerseDimensionParameterSync;
import com.kyden.verseworks.dimension.VerseDimensionRuntimeHooks;
import com.kyden.verseworks.entity.VerseEntities;
import com.kyden.verseworks.item.VerseItems;
import com.kyden.verseworks.ritual.HyperBookRitualHooks;
import com.kyden.verseworks.sound.VerseSounds;
import com.kyden.verseworks.worldgen.VerseWorldGen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(VerseWorks.MODID)
public final class VerseWorks {
    public static final String MODID = "verseworks";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    public VerseWorks(IEventBus modEventBus, ModContainer modContainer) {
        CHUNK_GENERATORS.register("terrain", () -> VerseChunkGenerator.CODEC);
        CHUNK_GENERATORS.register(modEventBus);
        VerseAttachments.register(modEventBus);
        VerseBlocks.register(modEventBus);
        VerseEntities.register(modEventBus);
        VerseItems.register(modEventBus);
        VerseSounds.register(modEventBus);
        VerseWorldGen.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(VerseDimensionParameterSync::registerPayloadHandlers);

        // Runtime systems live on the gameplay bus because they coordinate dynamic worlds after
        // registry setup has already completed.
        NeoForge.EVENT_BUS.addListener(VerseWorksCommands::register);
        VerseDimensionRuntimeHooks.register();
        VerseDimensionMobSpawnHooks.register();
        HyperBookCollapseHooks.register();
        HyperBookRitualHooks.register();

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("VerseWorks common setup complete");
    }
}
