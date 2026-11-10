package com.dillon.starsectormarines.battle.derelict;

/**
 * A zone's service bay (`derelict-watch.md`, Repair): the low armoured housing beside the node where the zone's repair
 * stock is held. The zone's repair drone carries a load ({@link RepairDrone#STOCK}) at a time and flies back here for
 * another; a destroyed bay leaves the drone what it carries. A dark zone's bay issues nothing (the director asks it only
 * while the zone is online).
 */
public final class ServiceBay {

    /** The stock a bay holds for the battle: about four loads. UNTUNED. */
    public static final float STOCK = 600f;
    /** How near the bay, in cells, a drone takes on a load. */
    public static final float REACH_CELLS = 1.5f;

    private float stock;

    public ServiceBay() {
        this(STOCK);
    }

    public ServiceBay(float stock) {
        if (stock < 0f) throw new IllegalArgumentException("A bay's stock is never negative");
        this.stock = stock;
    }

    public float stock() {
        return stock;
    }

    /** Issues up to {@code wanted} points; returns what it gave. */
    public float issue(float wanted) {
        float given = Math.max(0f, Math.min(stock, wanted));
        stock -= given;
        return given;
    }
}
