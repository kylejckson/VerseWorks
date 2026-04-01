package com.kyden.verseworks.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record HyperBookData(
    ResourceLocation dimensionId,
    String dimensionName,
    @Nullable Integer labelColor,
    @Nullable Double targetX,
    @Nullable Double targetY,
    @Nullable Double targetZ,
    @Nullable Float targetYRot,
    @Nullable Float targetXRot
) {
    private static final String ROOT_TAG = "VerseWorksHyperBook";
    private static final String DIMENSION_ID_TAG = "DimensionId";
    private static final String DIMENSION_NAME_TAG = "DimensionName";
    private static final String LABEL_COLOR_TAG = "LabelColor";
    private static final String TARGET_X_TAG = "TargetX";
    private static final String TARGET_Y_TAG = "TargetY";
    private static final String TARGET_Z_TAG = "TargetZ";
    private static final String TARGET_Y_ROT_TAG = "TargetYRot";
    private static final String TARGET_X_ROT_TAG = "TargetXRot";
    private static final int DEFAULT_LABEL_COLOR = 0xFF55FF;

    public HyperBookData(ResourceLocation dimensionId, String dimensionName) {
        this(dimensionId, dimensionName, null, null, null, null, null, null);
    }

    public HyperBookData(ResourceLocation dimensionId, String dimensionName, @Nullable Integer labelColor) {
        this(dimensionId, dimensionName, labelColor, null, null, null, null, null);
    }

    public HyperBookData(ResourceLocation dimensionId, String dimensionName, Vec3 targetPosition, float targetYRot, float targetXRot) {
        this(dimensionId, dimensionName, null, targetPosition.x, targetPosition.y, targetPosition.z, targetYRot, targetXRot);
    }

    public HyperBookData(ResourceLocation dimensionId, String dimensionName, @Nullable Integer labelColor, Vec3 targetPosition, float targetYRot, float targetXRot) {
        this(dimensionId, dimensionName, labelColor, targetPosition.x, targetPosition.y, targetPosition.z, targetYRot, targetXRot);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION_ID_TAG, this.dimensionId.toString());
        tag.putString(DIMENSION_NAME_TAG, this.dimensionName);
        if (this.labelColor != null) {
            tag.putInt(LABEL_COLOR_TAG, this.labelColor);
        }
        if (hasSavedTarget()) {
            tag.putDouble(TARGET_X_TAG, this.targetX);
            tag.putDouble(TARGET_Y_TAG, this.targetY);
            tag.putDouble(TARGET_Z_TAG, this.targetZ);
            tag.putFloat(TARGET_Y_ROT_TAG, this.targetYRot);
            tag.putFloat(TARGET_X_ROT_TAG, this.targetXRot);
        }
        return tag;
    }

    public ItemStack apply(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.put(ROOT_TAG, this.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    public boolean hasSavedTarget() {
        return this.targetX != null
            && this.targetY != null
            && this.targetZ != null
            && this.targetYRot != null
            && this.targetXRot != null;
    }

    public Optional<Vec3> targetPosition() {
        if (!hasSavedTarget()) {
            return Optional.empty();
        }
        return Optional.of(new Vec3(this.targetX, this.targetY, this.targetZ));
    }
    public HyperBookData withResolvedTarget(Vec3 targetPosition, float resolvedTargetYRot, float resolvedTargetXRot) {
        return new HyperBookData(
            this.dimensionId,
            this.dimensionName,
            this.labelColor,
            targetPosition.x,
            targetPosition.y,
            targetPosition.z,
            resolvedTargetYRot,
            resolvedTargetXRot
        );
    }

    public int labelColorValue() {
        return this.labelColor != null ? this.labelColor : DEFAULT_LABEL_COLOR;
    }

    public static Optional<HyperBookData> from(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }

        CompoundTag root = data.copyTag();
        if (!root.contains(ROOT_TAG)) {
            return Optional.empty();
        }

        Optional<HyperBookData> resolved = fromTag(root.getCompound(ROOT_TAG));
        if (resolved.isEmpty() || resolved.get().labelColor() != null) {
            return resolved;
        }

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        Integer resolvedLabelColor = customName != null && customName.getStyle().getColor() != null
            ? customName.getStyle().getColor().getValue()
            : null;
        if (resolvedLabelColor == null) {
            return resolved;
        }

        HyperBookData existing = resolved.get();
        return Optional.of(new HyperBookData(
            existing.dimensionId(),
            existing.dimensionName(),
            resolvedLabelColor,
            existing.targetX(),
            existing.targetY(),
            existing.targetZ(),
            existing.targetYRot(),
            existing.targetXRot()
        ));
    }

    public static Optional<HyperBookData> fromTag(CompoundTag tag) {
        if (tag.isEmpty()) {
            return Optional.empty();
        }

        @Nullable ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION_ID_TAG));
        if (dimensionId == null) {
            return Optional.empty();
        }

        String dimensionName = tag.contains(DIMENSION_NAME_TAG) ? tag.getString(DIMENSION_NAME_TAG) : dimensionId.getPath();
        Integer labelColor = tag.contains(LABEL_COLOR_TAG) ? tag.getInt(LABEL_COLOR_TAG) : null;
        Double targetX = tag.contains(TARGET_X_TAG) ? tag.getDouble(TARGET_X_TAG) : null;
        Double targetY = tag.contains(TARGET_Y_TAG) ? tag.getDouble(TARGET_Y_TAG) : null;
        Double targetZ = tag.contains(TARGET_Z_TAG) ? tag.getDouble(TARGET_Z_TAG) : null;
        Float targetYRot = tag.contains(TARGET_Y_ROT_TAG) ? tag.getFloat(TARGET_Y_ROT_TAG) : null;
        Float targetXRot = tag.contains(TARGET_X_ROT_TAG) ? tag.getFloat(TARGET_X_ROT_TAG) : null;
        return Optional.of(new HyperBookData(dimensionId, dimensionName, labelColor, targetX, targetY, targetZ, targetYRot, targetXRot));
    }
}