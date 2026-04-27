package com.kyden.verseworks.client;

import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.client.guidebook.GuidebookContentManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = VerseWorks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MysticCrystalClient {
    private MysticCrystalClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        VerseWorks.LOGGER.info("VerseWorks client setup for {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        MysticCrystalColors.registerBlockColors(event);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        MysticCrystalColors.registerItemColors(event);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        VerseBlocks.MYSTIC_CRYSTAL.get().getStateDefinition().getPossibleStates().forEach(state ->
            event.getModels().computeIfPresent(
                TintedMysticCrystalModel.modelLocation(state),
                (location, model) -> new TintedMysticCrystalModel(model)
            )
        );
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        MeteorRenderer.register(event);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new GuidebookContentManager());
    }
}
