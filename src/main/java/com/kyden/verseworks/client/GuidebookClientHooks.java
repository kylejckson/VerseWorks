package com.kyden.verseworks.client;

import com.kyden.verseworks.client.guidebook.GuidebookScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GuidebookClientHooks {
    private GuidebookClientHooks() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GuidebookScreen());
    }
}
