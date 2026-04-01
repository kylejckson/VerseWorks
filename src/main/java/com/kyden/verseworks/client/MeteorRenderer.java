package com.kyden.verseworks.client;

import com.kyden.verseworks.block.VerseBlocks;
import com.kyden.verseworks.entity.MeteorEntity;
import com.kyden.verseworks.entity.VerseEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private final BlockRenderDispatcher dispatcher;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.75F;
        this.shadowStrength = 0.8F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VerseEntities.METEOR.get(), MeteorRenderer::new);
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(MeteorEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        var blockState = VerseBlocks.METEOR.get().defaultBlockState();
        if (blockState.getRenderShape() == RenderShape.MODEL) {
            Level level = entity.level();
            BlockPos renderPos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            poseStack.pushPose();
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick)));
            poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderRoll(partialTick)));
            poseStack.scale(MeteorEntity.RENDER_SCALE, MeteorEntity.RENDER_SCALE, MeteorEntity.RENDER_SCALE);
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            var model = this.dispatcher.getBlockModel(blockState);
            RandomSource random = RandomSource.create(blockState.getSeed(entity.blockPosition()));
            for (var renderType : model.getRenderTypes(blockState, random, ModelData.EMPTY)) {
                this.dispatcher
                    .getModelRenderer()
                    .tesselateBlock(
                        level,
                        model,
                        blockState,
                        renderPos,
                        poseStack,
                        buffer.getBuffer(RenderTypeHelper.getMovingBlockRenderType(renderType)),
                        false,
                        RandomSource.create(),
                        blockState.getSeed(entity.blockPosition()),
                        OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY,
                        renderType
                    );
            }
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}