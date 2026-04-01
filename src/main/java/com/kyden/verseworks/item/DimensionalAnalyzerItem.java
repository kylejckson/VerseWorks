package com.kyden.verseworks.item;

import com.kyden.verseworks.dimension.VerseDimensionCatalog;
import com.kyden.verseworks.dimension.VerseDimensionCorruption;
import com.kyden.verseworks.dimension.VerseDimensionParameters;
import com.kyden.verseworks.sound.VerseSounds;
import com.kyden.verseworks.util.VerseText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DimensionalAnalyzerItem extends Item {
    private static final long COOLDOWN_TICKS = 100L;
    private static final java.util.Map<UUID, Long> NEXT_SCAN_TICKS = new ConcurrentHashMap<>();

    public DimensionalAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to scan the current dimension.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Uses remaining: " + (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage()).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide());
        }

        scanDimension(serverLevel, player, usedHand);
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

        scanDimension(level, player, context.getHand());
        return InteractionResult.SUCCESS;
    }

    private static void scanDimension(ServerLevel level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextScanAt = NEXT_SCAN_TICKS.getOrDefault(serverPlayer.getUUID(), 0L);
        if (gameTime < nextScanAt) {
            long remainingTicks = nextScanAt - gameTime;
            double remainingSeconds = remainingTicks / 20.0D;
            serverPlayer.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Dimensional Analyzer recharging: %.1fs", remainingSeconds)).withStyle(ChatFormatting.YELLOW));
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation dimensionId = level.dimension().location();
        Optional<VerseDimensionParameters> parameters = VerseDimensionCatalog.get(level.getServer(), dimensionId);
        ResourceLocation biomeId = level.getBiome(player.blockPosition())
            .unwrapKey()
            .map(ResourceKey::location)
            .orElse(ResourceLocation.withDefaultNamespace("unknown"));

        Component header = Component.literal("Dimension Scanned").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        Component summary = Component.literal(VerseText.displayDimensionName(dimensionId) + " - " + VerseText.displayBiomeName(biomeId) + " - " + VerseText.formatTimeOfDay(level.getDayTime()))
            .withStyle(ChatFormatting.GRAY);
        Component corruptionStatus = statusLine(
            "Dimensional Corruption",
            parameters.map(VerseDimensionParameters::hasCorruptions).orElse(false),
            "Identified",
            "Not Identified",
            ChatFormatting.DARK_PURPLE,
            ChatFormatting.GREEN
        );

        serverPlayer.sendSystemMessage(header);
        serverPlayer.sendSystemMessage(summary);
        serverPlayer.sendSystemMessage(corruptionStatus);
        for (VerseDimensionCorruption corruption : VerseDimensionCorruption.values()) {
            boolean present = parameters
                .map(found -> found.corruptions().contains(corruption))
                .orElse(false);
            if (!present) {
                continue;
            }
            serverPlayer.sendSystemMessage(statusLine(corruptionLabel(corruption), true, "Present", "Not Present", ChatFormatting.RED, ChatFormatting.GREEN));
        }

        NEXT_SCAN_TICKS.put(serverPlayer.getUUID(), gameTime + COOLDOWN_TICKS);
        serverPlayer.getCooldowns().addCooldown(stack.getItem(), (int) COOLDOWN_TICKS);
        level.playSound(null, player.blockPosition(), VerseSounds.DIMENSIONAL_ANALYZER.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    private static Component statusLine(String label, boolean active, String activeText, String inactiveText, ChatFormatting activeColor, ChatFormatting inactiveColor) {
        return Component.literal(label + ": ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(active ? activeText : inactiveText).withStyle(active ? activeColor : inactiveColor));
    }

    private static String corruptionLabel(VerseDimensionCorruption corruption) {
        return switch (corruption) {
            case ENDLESS_RAIN -> "Endless Rain";
            case ENDLESS_STORM -> "Endless Storm";
            case ENDLESS_LIGHTNING -> "Endless Lightning";
            case METEORS -> "Meteors";
            case WARP -> "Warp";
            case FIXED_TIME -> "Endless Time";
            case GRAVITY -> "Gravity";
            case SPHERES -> "Spheres";
        };
    }

}
