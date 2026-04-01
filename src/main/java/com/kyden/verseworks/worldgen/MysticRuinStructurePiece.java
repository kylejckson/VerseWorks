package com.kyden.verseworks.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class MysticRuinStructurePiece extends TemplateStructurePiece {
    private static final String ROTATION_TAG = "Rotation";
    private final Rotation rotation;

    public MysticRuinStructurePiece(StructureTemplateManager structureTemplateManager, BlockPos templatePosition, Rotation rotation) {
        super(
            VerseWorldGen.MYSTIC_RUIN_PIECE.get(),
            0,
            structureTemplateManager,
            MysticRuinStructure.TEMPLATE_ID,
            MysticRuinStructure.TEMPLATE_ID.getPath(),
            MysticRuinStructure.createPlaceSettings(rotation),
            templatePosition
        );
        this.rotation = rotation;
    }

    public MysticRuinStructurePiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
        super(
            VerseWorldGen.MYSTIC_RUIN_PIECE.get(),
            tag,
            structureTemplateManager,
            ignored -> MysticRuinStructure.createPlaceSettings(readRotation(tag))
        );
        this.rotation = readRotation(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString(ROTATION_TAG, this.rotation.name());
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
    }

    private static Rotation readRotation(CompoundTag tag) {
        String name = tag.contains(ROTATION_TAG) ? tag.getString(ROTATION_TAG) : Rotation.NONE.name();
        try {
            return Rotation.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Rotation.NONE;
        }
    }
}