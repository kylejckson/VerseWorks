package com.kyden.verseworks.dimension;

import com.kyden.verseworks.VerseWorks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.SpawnClusterSizeEvent;

import java.util.Optional;

public final class VerseDimensionMobSpawnHooks {
    private VerseDimensionMobSpawnHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(VerseDimensionMobSpawnHooks::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(VerseDimensionMobSpawnHooks::onSpawnClusterSize);
    }

    private static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        Optional<VerseDimensionParameters> parameters = resolveParameters(event.getLevel());
        if (parameters.isEmpty()) {
            return;
        }

        double multiplier = effectiveConfiguredSpawnMultiplier(parameters.get());
        if (multiplier <= 0.0D) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            return;
        }

        if (multiplier < 1.0D && event.getRandom().nextDouble() > multiplier) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    private static void onSpawnClusterSize(SpawnClusterSizeEvent event) {
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        Optional<VerseDimensionParameters> parameters = resolveParameters(event.getEntity().level());
        if (parameters.isEmpty()) {
            return;
        }

        double pressureMultiplier = effectiveSpawnPressure(effectiveConfiguredSpawnMultiplier(parameters.get()));
        if (pressureMultiplier <= 1.0D) {
            return;
        }

        int boostedClusterSize = Math.max(event.getSize(), (int) Math.ceil(event.getSize() * pressureMultiplier));
        event.setSize(boostedClusterSize);
    }

    private static Optional<VerseDimensionParameters> resolveParameters(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return Optional.empty();
        }

        if (!VerseWorks.MODID.equals(serverLevel.dimension().location().getNamespace())) {
            return Optional.empty();
        }

        return VerseDimensionCatalog.get(serverLevel.getServer(), serverLevel.dimension().location());
    }

    private static double effectiveSpawnPressure(double multiplier) {
        if (multiplier < 1.0D) {
            return multiplier;
        }

        return 1.0D + multiplier * multiplier;
    }

    private static double effectiveConfiguredSpawnMultiplier(VerseDimensionParameters parameters) {
        if (parameters.corruptions().contains(VerseDimensionCorruption.HOSTILE_HORDES)
            && !com.kyden.verseworks.Config.isCorruptionEffectEnabled(VerseDimensionCorruption.HOSTILE_HORDES)) {
            return 1.0D;
        }
        return parameters.mobSpawnMultiplier();
    }
}
