package com.kyden.verseworks.attachment;

import com.kyden.verseworks.item.HyperBookData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Optional;

public final class HyperBookLecternLink implements INBTSerializable<CompoundTag> {
    private HyperBookData data;

    public Optional<HyperBookData> data() {
        return Optional.ofNullable(this.data);
    }

    public void set(HyperBookData data) {
        this.data = data;
    }

    public void clear() {
        this.data = null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return this.data != null ? this.data.toTag() : new CompoundTag();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.data = HyperBookData.fromTag(tag).orElse(null);
    }
}