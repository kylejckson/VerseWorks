package com.kyden.verseworks.sound;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VerseSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, VerseWorks.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ENTER_DIMENSION = register("enter_dimension");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTERED_DIMENSION = register("entered_dimension");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLING_CAULDRON = register("bubbling_cauldron", 32.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> DIMENSION_PREPARED = register("dimension_prepared", 24.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> DIMENSIONAL_ANALYZER = register("dimensional_analyzer");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_1 = register("magic_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_2 = register("magic_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_1 = register("warp_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_2 = register("warp_2");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_ENTER = register("meteor_enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_IMPACT = register("meteor_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> RITUAL_COMPLETE = register("ritual_complete");

    private VerseSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, path);
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path, float fixedRange) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, path);
        return SOUND_EVENTS.register(path, () -> SoundEvent.createFixedRangeEvent(id, fixedRange));
    }
}