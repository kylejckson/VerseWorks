package com.kyden.verseworks.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record AddFeaturesAllBiomesModifier(HolderSet<PlacedFeature> features, Decoration step) implements BiomeModifier {
    public static final MapCodec<AddFeaturesAllBiomesModifier> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
        PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(AddFeaturesAllBiomesModifier::features),
        Decoration.CODEC.fieldOf("step").forGetter(AddFeaturesAllBiomesModifier::step)
    ).apply(builder, AddFeaturesAllBiomesModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) {
            return;
        }

        var generationSettings = builder.getGenerationSettings();
        this.features.forEach(feature -> generationSettings.addFeature(this.step, feature));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}