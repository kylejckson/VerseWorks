package com.kyden.verseworks.dimension;

import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

final class MutableDerivedLevelData extends DerivedLevelData {
    private long dayTime;
    private int clearWeatherTime;
    private boolean thundering;
    private int thunderTime;
    private boolean raining;
    private int rainTime;

    MutableDerivedLevelData(WorldData worldData, ServerLevelData wrapped) {
        super(worldData, wrapped);
        this.dayTime = wrapped.getDayTime();
        this.clearWeatherTime = wrapped.getClearWeatherTime();
        this.thundering = wrapped.isThundering();
        this.thunderTime = wrapped.getThunderTime();
        this.raining = wrapped.isRaining();
        this.rainTime = wrapped.getRainTime();
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Override
    public void setDayTime(long dayTime) {
        this.dayTime = dayTime;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int clearWeatherTime) {
        this.clearWeatherTime = clearWeatherTime;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }
}