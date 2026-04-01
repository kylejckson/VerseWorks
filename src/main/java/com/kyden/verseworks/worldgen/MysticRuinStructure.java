package com.kyden.verseworks.worldgen;

import com.kyden.verseworks.Config;
import com.kyden.verseworks.VerseWorks;
import com.kyden.verseworks.dimension.VerseChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class MysticRuinStructure extends Structure {
    public static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(VerseWorks.MODID, "mystic_ruin");
    public static final ResourceKey<StructureSet> STRUCTURE_SET_KEY = ResourceKey.create(Registries.STRUCTURE_SET, TEMPLATE_ID);
    public static final MapCodec<MysticRuinStructure> CODEC = simpleCodec(MysticRuinStructure::new);

    private static final int BURY_OFFSET = 3;
    private static final int OVERWORLD_FREQUENCY_DIVISOR = 4;

    public MysticRuinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!Config.MYSTIC_RUIN_ENABLED.get()) {
            return Optional.empty();
        }

        if (!shouldGenerateIn(context.chunkGenerator())) {
            return Optional.empty();
        }

        if (!(context.chunkGenerator() instanceof VerseChunkGenerator) && !passesOverworldFrequencyGate(context.seed(), context.chunkPos())) {
            return Optional.empty();
        }

        Rotation rotation = Rotation.getRandom(context.random());
        StructureTemplate template = context.structureTemplateManager().getOrCreate(TEMPLATE_ID);
        Vec3i rotatedSize = template.getSize(rotation);
        int originX = context.chunkPos().getMinBlockX() + 8 - rotatedSize.getX() / 2;
        int originZ = context.chunkPos().getMinBlockZ() + 8 - rotatedSize.getZ() / 2;
        int surfaceY = getLowestY(context, originX, originZ, rotatedSize.getX(), rotatedSize.getZ());
        if (surfaceY <= context.heightAccessor().getMinBuildHeight()) {
            return Optional.empty();
        }

        BlockPos origin = new BlockPos(originX, surfaceY - BURY_OFFSET, originZ);
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new MysticRuinStructurePiece(context.structureTemplateManager(), origin, rotation))));
    }

    @Override
    public StructureType<?> type() {
        return VerseWorldGen.MYSTIC_RUIN.get();
    }

    public static BlockPos placeDebug(ServerLevel level, BlockPos center) {
        Rotation rotation = Rotation.getRandom(level.random);
        StructureTemplate template = level.getStructureManager().getOrCreate(TEMPLATE_ID);
        Vec3i rotatedSize = template.getSize(rotation);
        int originX = center.getX() - rotatedSize.getX() / 2;
        int originZ = center.getZ() - rotatedSize.getZ() / 2;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, center.getX(), center.getZ());
        BlockPos origin = new BlockPos(originX, surfaceY - BURY_OFFSET, originZ);
        template.placeInWorld(level, origin, origin, createPlaceSettings(rotation), level.random, Block.UPDATE_ALL);
        return origin;
    }

    static StructurePlaceSettings createPlaceSettings(Rotation rotation) {
        return new StructurePlaceSettings()
            .setRotation(rotation)
            .setMirror(net.minecraft.world.level.block.Mirror.NONE)
            .setIgnoreEntities(false)
            .setKnownShape(true)
            .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
    }

    private static boolean shouldGenerateIn(ChunkGenerator chunkGenerator) {
        return !(chunkGenerator instanceof VerseChunkGenerator verseChunkGenerator) || !verseChunkGenerator.parameters().worldType().isVoid();
    }

    private static boolean passesOverworldFrequencyGate(long seed, ChunkPos chunkPos) {
        long mixed = seed;
        mixed ^= (long) chunkPos.x * 341873128712L;
        mixed ^= (long) chunkPos.z * 132897987541L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return Math.floorMod(mixed, OVERWORLD_FREQUENCY_DIVISOR) == 0;
    }
}
