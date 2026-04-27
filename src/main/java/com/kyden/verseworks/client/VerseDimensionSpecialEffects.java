package com.kyden.verseworks.client;

import com.kyden.verseworks.VerseWorks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.dimension.VerseDimensionVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = VerseWorks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VerseDimensionSpecialEffects {
    private VerseDimensionSpecialEffects() {
    }

    private static java.util.Optional<VerseDimensionParameters> currentParameters() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return java.util.Optional.empty();
        }
        return VerseDimensionCatalog.getCached(minecraft.level.dimension().location());
    }

    private static Vector3f skyTint(VerseDimensionParameters parameters) {
        int color = VerseDimensionVisuals.resolvedColoredSkyColor(parameters);
        return new Vector3f(
            ((color >> 16) & 0xFF) / 255.0F,
            ((color >> 8) & 0xFF) / 255.0F,
            (color & 0xFF) / 255.0F
        );
    }

    private static boolean usesVanillaSkyGeometry(VerseDimensionParameters parameters) {
        return VerseDimensionVisuals.usesVanillaSkyGeometry(parameters) && !VerseDimensionVisuals.hidesCelestialBodies(parameters);
    }

    @SubscribeEvent
    public static void onRegisterDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(VerseDimensionVisuals.COLORED_SKY_EFFECTS, new ColoredSkyEffects());
        event.register(VerseDimensionVisuals.ENDLIKE_EFFECTS, new EndlikeSkyEffects());
        event.register(VerseDimensionVisuals.BLACK_VOID_EFFECTS, new BlackVoidEffects());
        event.register(VerseDimensionVisuals.SEALED_SKY_EFFECTS, new SealedSkyEffects());
    }

    private static final class ColoredSkyEffects extends DimensionSpecialEffects {
        private ColoredSkyEffects() {
            super(192.0F, true, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return currentParameters()
                .map(parameters -> {
                    Vector3f tint = skyTint(parameters);
                    float multiplier = brightness * 0.15F + 0.85F;
                    return new Vec3(tint.x() * multiplier, tint.y() * multiplier, tint.z() * multiplier);
                })
                .orElse(fogColor);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, org.joml.Matrix4f modelViewMatrix, Camera camera, org.joml.Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            java.util.Optional<VerseDimensionParameters> parameters = currentParameters();
            if (parameters.isEmpty()) {
                return false;
            }

            return !usesVanillaSkyGeometry(parameters.get());
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
            currentParameters().ifPresent(parameters -> {
                Vector3f tint = skyTint(parameters);
                colors.lerp(tint, 0.18F);
                colors.mul(1.02F, 1.01F, 1.02F);
            });
        }
    }

    private static final class EndlikeSkyEffects extends DimensionSpecialEffects {
        private EndlikeSkyEffects() {
            super(Float.NaN, false, SkyType.END, true, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.scale(0.15F);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTicks) {
            return null;
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
            currentParameters().ifPresent(parameters -> {
                Vector3f tint = skyTint(parameters);
                colors.lerp(tint, 0.10F);
                colors.mul(0.86F, 0.88F, 0.93F);
            });
        }
    }

    private static final class BlackVoidEffects extends DimensionSpecialEffects {
        private BlackVoidEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.scale(0.05F);
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, org.joml.Matrix4f modelViewMatrix, Camera camera, org.joml.Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, double camX, double camY, double camZ, org.joml.Matrix4f modelViewMatrix, org.joml.Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
            return true;
        }

        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            return true;
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
            colors.mul(0.92F, 0.92F, 0.96F);
        }
    }

    private static final class SealedSkyEffects extends DimensionSpecialEffects {
        private SealedSkyEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return currentParameters()
                .map(parameters -> {
                    Vector3f tint = skyTint(parameters);
                    float multiplier = brightness * 0.10F + 0.70F;
                    return new Vec3(tint.x() * multiplier, tint.y() * multiplier, tint.z() * multiplier);
                })
                .orElse(fogColor.scale(0.8F));
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderSky(ClientLevel level, int ticks, float partialTick, org.joml.Matrix4f modelViewMatrix, Camera camera, org.joml.Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            return true;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, double camX, double camY, double camZ, org.joml.Matrix4f modelViewMatrix, org.joml.Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight, int pixelX, int pixelY, Vector3f colors) {
            currentParameters().ifPresent(parameters -> {
                Vector3f tint = skyTint(parameters);
                colors.lerp(tint, 0.08F);
                colors.mul(0.96F, 0.96F, 0.98F);
            });
        }
    }
}
