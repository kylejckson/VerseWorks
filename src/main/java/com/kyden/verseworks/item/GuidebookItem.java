package com.kyden.verseworks.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class GuidebookItem extends Item {
    public GuidebookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            openGuidebookClient();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.verseworks.guidebook.tooltip").withStyle(ChatFormatting.GRAY));
    }

    private static void openGuidebookClient() {
        try {
            Class<?> hooks = Class.forName("com.kyden.verseworks.client.GuidebookClientHooks");
            Method method = hooks.getDeclaredMethod("open");
            method.invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Dedicated servers do not have client classes present and never call this path.
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("VerseWorks could not access the guidebook client hook", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            throw new IllegalStateException("VerseWorks could not open the guidebook screen", cause);
        }
    }
}
