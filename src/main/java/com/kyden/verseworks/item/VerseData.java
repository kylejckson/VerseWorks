package com.kyden.verseworks.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record VerseData(
    String prefix,
    String label,
    String verseType,
    String parameterKey,
    String amountText,
    @Nullable String stringValue,
    @Nullable Integer intValue,
    @Nullable Double doubleValue,
    @Nullable Boolean booleanValue,
    @Nullable String secondaryParameterKey,
    @Nullable String secondaryAmountText
) {
    private static final String ROOT_TAG = "VerseWorksVerse";
    private static final String PREFIX_TAG = "Prefix";
    private static final String LABEL_TAG = "Label";
    private static final String VERSE_TYPE_TAG = "VerseType";
    private static final String PARAMETER_TAG = "Parameter";
    private static final String AMOUNT_TAG = "Amount";
    private static final String STRING_VALUE_TAG = "StringValue";
    private static final String INT_VALUE_TAG = "IntValue";
    private static final String DOUBLE_VALUE_TAG = "DoubleValue";
    private static final String BOOLEAN_VALUE_TAG = "BooleanValue";
    private static final String SECONDARY_PARAMETER_TAG = "SecondaryParameter";
    private static final String SECONDARY_AMOUNT_TAG = "SecondaryAmount";

    public static VerseData stringValue(String prefix, String label, String verseType, String parameterKey, String amountText, String value) {
        return new VerseData(prefix, label, verseType, parameterKey, amountText, value, null, null, null, null, null);
    }

    public static VerseData intValue(String prefix, String label, String verseType, String parameterKey, int value, String amountText) {
        return new VerseData(prefix, label, verseType, parameterKey, amountText, null, value, null, null, null, null);
    }

    public static VerseData doubleValue(String prefix, String label, String verseType, String parameterKey, double value, String amountText) {
        return new VerseData(prefix, label, verseType, parameterKey, amountText, null, null, value, null, null, null);
    }

    public static VerseData booleanValue(String prefix, String label, String verseType, String parameterKey, boolean value, String amountText) {
        return new VerseData(prefix, label, verseType, parameterKey, amountText, null, null, null, value, null, null);
    }

    public VerseData withSecondary(String parameterKey, String amountText) {
        return new VerseData(
            this.prefix,
            this.label,
            this.verseType,
            this.parameterKey,
            this.amountText,
            this.stringValue,
            this.intValue,
            this.doubleValue,
            this.booleanValue,
            parameterKey,
            amountText
        );
    }

    public ItemStack apply(ItemStack stack) {
        CompoundTag root = new CompoundTag();
        CompoundTag verseTag = new CompoundTag();
        verseTag.putString(PREFIX_TAG, this.prefix);
        verseTag.putString(LABEL_TAG, this.label);
        verseTag.putString(VERSE_TYPE_TAG, this.verseType);
        verseTag.putString(PARAMETER_TAG, this.parameterKey);
        verseTag.putString(AMOUNT_TAG, this.amountText);
        if (this.stringValue != null) {
            verseTag.putString(STRING_VALUE_TAG, this.stringValue);
        }
        if (this.intValue != null) {
            verseTag.putInt(INT_VALUE_TAG, this.intValue);
        }
        if (this.doubleValue != null) {
            verseTag.putDouble(DOUBLE_VALUE_TAG, this.doubleValue);
        }
        if (this.booleanValue != null) {
            verseTag.putBoolean(BOOLEAN_VALUE_TAG, this.booleanValue);
        }
        if (this.secondaryParameterKey != null && this.secondaryAmountText != null) {
            verseTag.putString(SECONDARY_PARAMETER_TAG, this.secondaryParameterKey);
            verseTag.putString(SECONDARY_AMOUNT_TAG, this.secondaryAmountText);
        }
        root.put(ROOT_TAG, verseTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    public static Optional<VerseData> from(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }

        CompoundTag root = data.copyTag();
        if (!root.contains(ROOT_TAG)) {
            return Optional.empty();
        }

        CompoundTag verseTag = root.getCompound(ROOT_TAG);
        String prefix = verseTag.contains(PREFIX_TAG) ? verseTag.getString(PREFIX_TAG) : "";
        String label = verseTag.contains(LABEL_TAG) ? verseTag.getString(LABEL_TAG) : "";
        String verseType = verseTag.contains(VERSE_TYPE_TAG) ? verseTag.getString(VERSE_TYPE_TAG) : "";
        String parameterKey = verseTag.contains(PARAMETER_TAG) ? verseTag.getString(PARAMETER_TAG) : "";
        String amountText = verseTag.contains(AMOUNT_TAG) ? verseTag.getString(AMOUNT_TAG) : "";
        String stringValue = verseTag.contains(STRING_VALUE_TAG) ? verseTag.getString(STRING_VALUE_TAG) : null;
        Integer intValue = verseTag.contains(INT_VALUE_TAG) ? verseTag.getInt(INT_VALUE_TAG) : null;
        Double doubleValue = verseTag.contains(DOUBLE_VALUE_TAG) ? verseTag.getDouble(DOUBLE_VALUE_TAG) : null;
        Boolean booleanValue = verseTag.contains(BOOLEAN_VALUE_TAG) ? verseTag.getBoolean(BOOLEAN_VALUE_TAG) : null;
        String secondaryParameterKey = verseTag.contains(SECONDARY_PARAMETER_TAG) ? verseTag.getString(SECONDARY_PARAMETER_TAG) : null;
        String secondaryAmountText = verseTag.contains(SECONDARY_AMOUNT_TAG) ? verseTag.getString(SECONDARY_AMOUNT_TAG) : null;
        return Optional.of(new VerseData(
            prefix,
            label,
            verseType,
            parameterKey,
            amountText,
            stringValue,
            intValue,
            doubleValue,
            booleanValue,
            secondaryParameterKey,
            secondaryAmountText
        ));
    }

    public Component displayName() {
        String baseName;
        if (this.prefix == null || this.prefix.isBlank()) {
            baseName = this.label;
        } else if (this.prefix.endsWith("-")) {
            baseName = this.prefix + this.label;
        } else {
            baseName = this.prefix + " " + this.label;
        }
        String displayName = baseName.trim();
        if (displayName.isEmpty()) {
            displayName = "Unknown";
        }
        return Component.literal("Verse: " + displayName);
    }
}
