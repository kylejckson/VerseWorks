package com.kyden.verseworks.item;

import com.kyden.verseworks.util.VerseText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class UnlinkedHyperBookItem extends Item {
    public UnlinkedHyperBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to bind this book to your current position.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide());
        }

        bindCurrentPosition(player, serverLevel, usedHand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        bindCurrentPosition(player, level, context.getHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Unlinked Hyperbook").withStyle(ChatFormatting.GRAY);
    }

    private static void bindCurrentPosition(Player player, ServerLevel level, InteractionHand hand) {
        Vec3 targetPosition = player.position();
        ResourceLocation dimensionId = level.dimension().location();
        String dimensionName = VerseText.displayDimensionName(dimensionId);

        ItemStack linkedBook = new ItemStack(VerseItems.HYPER_BOOK.get());
        linkedBook.set(DataComponents.WRITTEN_BOOK_CONTENT, createWrittenBookContent(dimensionName, targetPosition));
        linkedBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Hyperbook - " + dimensionName).withStyle(ChatFormatting.LIGHT_PURPLE));
        new HyperBookData(dimensionId, dimensionName, null, targetPosition, player.getYRot(), player.getXRot()).apply(linkedBook);
        player.setItemInHand(hand, linkedBook);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.05F);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("Bound Hyperbook to " + dimensionName + " at "
                + (int) Math.floor(targetPosition.x) + ", "
                + (int) Math.floor(targetPosition.y) + ", "
                + (int) Math.floor(targetPosition.z)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static WrittenBookContent createWrittenBookContent(String dimensionName, Vec3 targetPosition) {
        String title = dimensionName.length() > 32 ? dimensionName.substring(0, 32) : dimensionName;
        String pageText = "Bound to " + dimensionName + " at "
            + (int) Math.floor(targetPosition.x) + ", "
            + (int) Math.floor(targetPosition.y) + ", "
            + (int) Math.floor(targetPosition.z);
        return new WrittenBookContent(
            Filterable.passThrough(title),
            "VerseWorks",
            0,
            List.of(Filterable.passThrough(Component.literal(pageText))),
            true
        );
    }

}
