package com.kyden.verseworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class VerseItem extends Item {
    public VerseItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return VerseData.from(stack)
            .map(VerseData::displayName)
            .orElseGet(() -> Component.literal("Verse"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VerseData.from(stack).ifPresent(data -> {
            tooltip.add(Component.literal("Parameter: " + data.parameterKey()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Amount: " + data.amountText()).withStyle(ChatFormatting.DARK_AQUA));
            if (data.secondaryParameterKey() != null && data.secondaryAmountText() != null) {
                tooltip.add(Component.literal("Also: " + data.secondaryParameterKey() + " = " + data.secondaryAmountText()).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.literal("Type: " + data.verseType()).withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}