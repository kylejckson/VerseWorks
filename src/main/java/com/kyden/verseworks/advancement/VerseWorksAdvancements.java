package com.kyden.verseworks.advancement;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class VerseWorksAdvancements {
    public static final ResourceLocation MYSTICAL_TRAVEL = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "mystical_travel");
    public static final ResourceLocation NEW_WORLDS_AWAIT = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "new_worlds_await");
    public static final ResourceLocation ENTER_A_NEW_WORLD = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "enter_a_new_world");

    private VerseWorksAdvancements() {
    }

    public static void award(ServerPlayer player, ResourceLocation advancementId, String criterion) {
        var server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            return;
        }

        var advancement = server.getAdvancements().get(advancementId);
        if (advancement == null) {
            VerseWorks.LOGGER.warn("Missing VerseWorks advancement {} while awarding {}", advancementId, player.getScoreboardName());
            return;
        }

        PlayerAdvancements advancements = player.getAdvancements();
        advancements.award(advancement, criterion);
    }
}