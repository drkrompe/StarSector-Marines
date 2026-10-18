package com.dillon.starsectormarines.battle.logistics;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A finite stock of resupply on the field, handed out one munition at a time by {@link ResupplySystem}.
 *
 * <p>Two kinds share the one rule set. A <em>ground</em> cache is set down by an air payload and stays on its cell.
 * A <em>carried</em> cache rides a supply vehicle: its owner moves it with the vehicle every tick, opens it only
 * while the vehicle is halted, and empties it when the vehicle is wrecked. Who may draw from it, how fast, and what
 * contests it are the same either way.
 */
public final class ResupplyCache {

    public static final int DEFAULT_STOCK = 12;
    public static final float SERVICE_RADIUS_CELLS = 4f;
    public static final float CONTEST_RADIUS_CELLS = 2.5f;

    /** The carrying vehicle's entity id, or 0 for a ground cache. */
    public final long carrierId;
    public final Faction faction;
    public final int maximumStock;

    /** Where service is measured from, in cells: a ground cache's cell centre, or its carrier's position. */
    private float centerX;
    private float centerY;
    /** Whether the cache is handing anything out this tick; a ground cache always is, a carried one while parked. */
    private boolean open = true;

    public int stock;
    public float transferCooldown;
    public boolean contested;

    public ResupplyCache(int cellX, int cellY, Faction faction) {
        this(cellX, cellY, faction, DEFAULT_STOCK);
    }

    public ResupplyCache(int cellX, int cellY, Faction faction, int stock) {
        this(0L, cellX + 0.5f, cellY + 0.5f, faction, stock);
    }

    private ResupplyCache(long carrierId, float centerX, float centerY, Faction faction, int stock) {
        this.carrierId = carrierId;
        this.centerX = centerX;
        this.centerY = centerY;
        this.faction = faction;
        this.stock = Math.max(0, stock);
        this.maximumStock = Math.max(1, stock);
    }

    /** A cache riding {@code carrierId}, closed until its owner first reports the vehicle halted. */
    public static ResupplyCache carried(long carrierId, float x, float y, Faction faction, int stock) {
        ResupplyCache cache = new ResupplyCache(carrierId, x, y, faction, stock);
        cache.open = false;
        return cache;
    }

    public boolean carried() { return carrierId != 0L; }

    public float centerX() { return centerX; }

    public float centerY() { return centerY; }

    public boolean open() { return open; }

    /** Moves a carried cache with its vehicle and opens or closes it. A ground cache never moves. */
    public void follow(float x, float y, boolean open) {
        if (!carried()) throw new IllegalStateException("a ground cache does not move");
        this.centerX = x;
        this.centerY = y;
        this.open = open;
    }

    /** The carrier is gone: what it was carrying goes with it. */
    public void lose() {
        stock = 0;
        open = false;
    }

    public boolean depleted() { return stock <= 0; }

    /** Whether service runs this tick: something left, and open. */
    public boolean serving() { return open && !depleted(); }

    public float stockFraction() {
        return Math.max(0f, Math.min(1f, stock / (float) maximumStock));
    }
}
