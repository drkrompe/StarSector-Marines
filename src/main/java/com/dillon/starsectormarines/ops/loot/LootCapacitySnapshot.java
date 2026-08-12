package com.dillon.starsectormarines.ops.loot;

/** Free capacity in each vanilla cargo bucket at confirmation time. */
public final class LootCapacitySnapshot {

    public final float cargo;
    public final float fuel;
    public final float personnel;

    public LootCapacitySnapshot(float cargo, float fuel, float personnel) {
        this.cargo = Math.max(0f, cargo);
        this.fuel = Math.max(0f, fuel);
        this.personnel = Math.max(0f, personnel);
    }
}
