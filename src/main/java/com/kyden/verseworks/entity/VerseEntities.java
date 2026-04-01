package com.kyden.verseworks.entity;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VerseEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, VerseWorks.MODID);
    private static final ResourceKey<EntityType<?>> METEOR_ID = ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "meteor"));

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR = ENTITY_TYPES.register(
        "meteor",
        () -> EntityType.Builder.of(MeteorEntity::new, MobCategory.MISC)
            .sized(MeteorEntity.HITBOX_SIZE, MeteorEntity.HITBOX_SIZE)
            .fireImmune()
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(METEOR_ID.location().toString())
    );

    private VerseEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}