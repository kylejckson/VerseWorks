package com.kyden.verseworks.attachment;

import com.kyden.verseworks.VerseWorks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class VerseAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VerseWorks.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HyperBookLecternLink>> HYPER_BOOK_LECTERN_LINK = ATTACHMENT_TYPES.register(
        "hyper_book_lectern_link",
        () -> AttachmentType.serializable(HyperBookLecternLink::new).build()
    );

    private VerseAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}