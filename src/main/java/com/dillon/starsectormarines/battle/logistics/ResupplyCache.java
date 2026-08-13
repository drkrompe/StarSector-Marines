package com.dillon.starsectormarines.battle.logistics;

import com.dillon.starsectormarines.battle.unit.Faction;

/** Persistent battlefield supply cache delivered by an air payload. */
public final class ResupplyCache {

    public static final int DEFAULT_STOCK = 12;
    public static final float SERVICE_RADIUS_CELLS = 4f;
    public static final float CONTEST_RADIUS_CELLS = 2.5f;

    public final int cellX;
    public final int cellY;
    public final Faction faction;
    public final int maximumStock;

    public int stock;
    public float transferCooldown;
    public boolean contested;

    public ResupplyCache(int cellX, int cellY, Faction faction) {
        this(cellX, cellY, faction, DEFAULT_STOCK);
    }

    public ResupplyCache(int cellX, int cellY, Faction faction, int stock) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.faction = faction;
        this.stock = Math.max(0, stock);
        this.maximumStock = Math.max(1, stock);
    }

    public boolean depleted() { return stock <= 0; }

    public float stockFraction() {
        return Math.max(0f, Math.min(1f, stock / (float) maximumStock));
    }
}
