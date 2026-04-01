package com.kyden.verseworks.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

final class TintedMysticCrystalModel implements IDynamicBakedModel {
    private final BakedModel delegate;

    TintedMysticCrystalModel(BakedModel delegate) {
        this.delegate = delegate;
    }

    static ModelResourceLocation modelLocation(BlockState state) {
        return BlockModelShaper.stateToModelLocation(state);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        return delegate.getQuads(state, side, rand, extraData, renderType).stream()
            .map(TintedMysticCrystalModel::withTintIndexZero)
            .toList();
    }

    private static BakedQuad withTintIndexZero(BakedQuad quad) {
        if (quad.getTintIndex() == 0) {
            return quad;
        }

        return new BakedQuad(
            Arrays.copyOf(quad.getVertices(), quad.getVertices().length),
            0,
            quad.getDirection(),
            quad.getSprite(),
            quad.isShade(),
            quad.hasAmbientOcclusion()
        );
    }

    @Override
    public boolean useAmbientOcclusion() {
        return delegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return delegate.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return delegate.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return delegate.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return delegate.getOverrides();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return delegate.getRenderTypes(state, rand, data);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        return delegate.getRenderPasses(itemStack, fabulous);
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        return List.of(RenderType.itemEntityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
    }
}