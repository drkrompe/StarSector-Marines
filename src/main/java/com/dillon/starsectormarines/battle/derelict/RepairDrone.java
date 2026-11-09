package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * A zone's repair drone (`derelict-security-network.md`, slice 6): the builder drone of the tender's kind, working the
 * zone's damaged posts, cradle and walkers back up while the zone's node stands and is fed and its stock lasts, and
 * standing down under fire. The rules are here; the director flies the drone and lays what it mends.
 *
 * <p>Each pulse the drone picks the most worn of its zone's charges below {@link #MEND_BELOW}; it flies to it and,
 * within {@link #REACH_CELLS}, mends {@link #MEND_PER_SECOND} a second from its stock. Hit, or with a hostile the
 * network holds within {@link #THREAT_CELLS}, it breaks off home and stays there {@link #STAND_DOWN_TICKS}. With its
 * node down or dark, or its stock spent, it goes home and stays.
 */
public final class RepairDrone {

    /** A charge at or above this share of its whole is left alone. */
    public static final float MEND_BELOW = 0.95f;
    /** Points a second laid on a charge (plate first, then structure; plate only on a walker). UNTUNED. */
    public static final float MEND_PER_SECOND = 6f;
    /** Points the drone carries for the battle. UNTUNED. */
    public static final float STOCK = 300f;
    public static final float REACH_CELLS = 1.5f;
    public static final float THREAT_CELLS = 8f;
    public static final int STAND_DOWN_TICKS = 10 * 60;
    /** Flight speed, cells a second. */
    public static final float SPEED_CELLS = 4f;

    /** One thing the drone may mend: its entity, where it stands and how whole it is (0..1). */
    public record Charge(long entityId, float x, float y, float share) { }

    public enum Kind { HOME, GO, MEND }

    /** What the drone does now: where it flies, and whom it mends. */
    public record Goal(Kind kind, float x, float y, long entityId) { }

    private float stock = STOCK;
    private int quietUntil = Integer.MIN_VALUE;

    public float stock() {
        return stock;
    }

    /** The most worn charge below {@link #MEND_BELOW}, or null when all are whole enough. */
    public static Charge pick(List<Charge> charges) {
        Charge best = null;
        for (Charge charge : charges) {
            if (charge.share() >= MEND_BELOW) continue;
            if (best == null || charge.share() < best.share()) best = charge;
        }
        return best;
    }

    /**
     * The drone's goal this pulse.
     *
     * @param online    whether its zone's node stands and is fed
     * @param underFire whether it was hit since the last pulse or a hostile is within {@link #THREAT_CELLS}
     */
    public Goal decide(float x, float y, float homeX, float homeY, boolean online, boolean underFire,
                       List<Charge> charges, int tick) {
        Goal home = new Goal(Kind.HOME, homeX, homeY, 0L);
        if (!online || stock <= 0f) return home;
        if (underFire) {
            quietUntil = tick + STAND_DOWN_TICKS;
            return home;
        }
        if (tick < quietUntil) return home;
        Charge charge = pick(charges);
        if (charge == null) return home;
        boolean near = Math.max(Math.abs(charge.x() - x), Math.abs(charge.y() - y)) <= REACH_CELLS;
        return new Goal(near ? Kind.MEND : Kind.GO, charge.x(), charge.y(), charge.entityId());
    }

    /** Draws up to {@code seconds} of mending from the stock; returns the points to lay. */
    public float draw(float seconds) {
        float points = Math.min(stock, MEND_PER_SECOND * seconds);
        stock -= points;
        return points;
    }

    /** Puts back what could not be laid (the charge was already whole). */
    public void refund(float points) {
        stock += Math.max(0f, points);
    }
}
