package com.kyden.verseworks.worldgen;

import com.mojang.serialization.MapCodec;
import com.kyden.verseworks.VerseWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class VerseWorldGen {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, VerseWorks.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, VerseWorks.MODID);
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, VerseWorks.MODID);
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, VerseWorks.MODID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MYSTIC_CRYSTAL_CAVE = FEATURES.register(
        "mystic_crystal_cave",
        () -> new MysticCrystalCaveFeature(NoneFeatureConfiguration.CODEC)
    );

    public static final DeferredHolder<StructureType<?>, StructureType<MysticRuinStructure>> MYSTIC_RUIN = STRUCTURE_TYPES.register(
        "mystic_ruin",
        () -> () -> MysticRuinStructure.CODEC
    );

    public static final DeferredHolder<StructurePieceType, StructurePieceType> MYSTIC_RUIN_PIECE = STRUCTURE_PIECES.register(
        "mystic_ruin",
        () -> (StructurePieceType.StructureTemplateType) MysticRuinStructurePiece::new
    );

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<? extends BiomeModifier>> ADD_FEATURES_ALL_BIOMES = BIOME_MODIFIER_SERIALIZERS.register(
        "add_features_all_biomes",
        () -> AddFeaturesAllBiomesModifier.CODEC
    );

    private VerseWorldGen() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
    }
}