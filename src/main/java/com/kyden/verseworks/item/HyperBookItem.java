package com.kyden.verseworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

public final class HyperBookItem extends WrittenBookItem {
    public HyperBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return HyperBookData.from(stack)
            .map(data -> Component.literal("Hyperbook - " + data.dimensionName()).withStyle(style -> style.withColor(data.labelColorValue())))
            .orElseGet(() -> Component.literal("Hyperbook"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        HyperBookData.from(stack).ifPresent(data -> {
            tooltip.add(Component.literal("Linked Dimension: " + data.dimensionName()).withStyle(style -> style.withColor(data.labelColorValue())));
            tooltip.add(Component.literal("Target Id: " + data.dimensionId()).withStyle(ChatFormatting.DARK_GRAY));
            data.targetPosition().ifPresent(position -> {
                tooltip.add(Component.literal("Target Position: "
                    + (int) Math.floor(position.x) + ", "
                    + (int) Math.floor(position.y) + ", "
                    + (int) Math.floor(position.z)).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Target Rotation: yaw "
                    + Math.round(data.targetYRot()) + ", pitch "
                    + Math.round(data.targetXRot())).withStyle(ChatFormatting.DARK_GRAY));
            });
        });
        tooltip.add(Component.literal("Place on a lectern to travel.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        HyperBookData data = HyperBookData.from(stack).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Place this Hyperbook on a lectern to use it.").withStyle(ChatFormatting.YELLOW));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }
}
