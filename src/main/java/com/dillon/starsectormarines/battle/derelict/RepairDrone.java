package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

/**
 * A zone's repair drone (`derelict-security-network.md`, slice 6; `derelict-watch.md`, Repair): the builder drone of the
 * tender's kind, working the zone's damaged posts, cradle and walkers back up, and raising its fallen ones from their
 * wrecks, while the zone's node stands and is fed and it has stock; standing down under fire. The rules are here; the
 * director flies the drone and lays what it mends.
 *
 * <p>It carries a load of {@link #STOCK} points. Where its zone has a {@link ServiceBay} it flies back for another load
 * when it runs dry; with the bay gone it has only what it carries.
 *
 * <p>Each pulse it picks, in order: a raise it has under way; the most worn charge below {@link #MEND_URGENT}; a wreck
 * it may raise, nearest first; the most worn charge below {@link #MEND_BELOW}. It flies to it and, within
 * {@link #REACH_CELLS}, mends or raises at its rate from its load. Hit, or with a hostile the network holds within
 * {@link #THREAT_CELLS}, it breaks off home and stays there {@link #STAND_DOWN_TICKS}. With its node down or dark, or
 * dry with no bay to reload at, it goes home and stays.
 */
public final class RepairDrone {

    /** A charge at or above this share of its whole is left alone. */
    public static final float MEND_BELOW = 0.95f;
    /** A charge below this share comes before any wreck. */
    public static final float MEND_URGENT = 0.5f;
    /** Points a second laid on a charge (plate first, then structure; plate only on a walker). UNTUNED. */
    public static final float MEND_PER_SECOND = 6f;
    /** Points the drone carries at once: its first load, and each load from its bay. UNTUNED. */
    public static final float STOCK = 300f;
    public static final float REACH_CELLS = 1.5f;
    public static final float THREAT_CELLS = 8f;
    public static final int STAND_DOWN_TICKS = 10 * 60;
    /** Flight speed, cells a second. */
    public static final float SPEED_CELLS = 4f;

    /** One thing the drone may mend: its entity, where it stands and how whole it is (0..1). */
    public record Charge(long entityId, float x, float y, float share) { }

    /** One wreck the drone may raise: its id and where it lies. */
    public record Fallen(long wreckId, float x, float y) { }

    /** Where the zone's service bay stands and what it still holds. */
    public record Bay(float x, float y, float stock) { }

    public enum Kind { HOME, GO, MEND, RAISE, RELOAD }

    /** What the drone does now: where it flies, and whom it mends or raises (a wreck id for a raise). */
    public record Goal(Kind kind, float x, float y, long entityId) { }

    private float stock = STOCK;
    private int quietUntil = Integer.MIN_VALUE;
    private long raising;

    public float stock() {
        return stock;
    }

    /** The most worn charge below {@link #MEND_BELOW}, or null when all are whole enough. */
    public static Charge pick(List<Charge> charges) {
        return pick(charges, MEND_BELOW);
    }

    private static Charge pick(List<Charge> charges, float below) {
        Charge best = null;
        for (Charge charge : charges) {
            if (charge.share() >= below) continue;
            if (best == null || charge.share() < best.share()) best = charge;
        }
        return best;
    }

    /**
     * The drone's goal this pulse, with no bay and no wrecks: it mends from what it carries.
     *
     * @param online    whether its zone's node stands and is fed
     * @param underFire whether it was hit since the last pulse or a hostile is within {@link #THREAT_CELLS}
     */
    public Goal decide(float x, float y, float homeX, float homeY, boolean online, boolean underFire,
                       List<Charge> charges, int tick) {
        return decide(x, y, homeX, homeY, online, underFire, charges, List.of(), null, tick);
    }

    /**
     * The drone's goal this pulse.
     *
     * @param online    whether its zone's node stands and is fed
     * @param underFire whether it was hit since the last pulse or a hostile is within {@link #THREAT_CELLS}
     * @param wrecks    its zone's wrecks it may still raise
     * @param bay       its zone's service bay, or null with none standing
     */
    public Goal decide(float x, float y, float homeX, float homeY, boolean online, boolean underFire,
                       List<Charge> charges, List<Fallen> wrecks, Bay bay, int tick) {
        Goal home = new Goal(Kind.HOME, homeX, homeY, 0L);
        if (!online) return home;
        if (underFire) {
            quietUntil = tick + STAND_DOWN_TICKS;
            return home;
        }
        if (tick < quietUntil) return home;
        if (stock <= 0f) {
            if (bay == null || bay.stock() <= 0f) return home;
            boolean near = Math.max(Math.abs(bay.x() - x), Math.abs(bay.y() - y)) <= ServiceBay.REACH_CELLS;
            return new Goal(near ? Kind.RELOAD : Kind.GO, bay.x(), bay.y(), 0L);
        }
        Fallen raise = null;
        for (Fallen wreck : wrecks) if (wreck.wreckId() == raising) raise = wreck;
        if (raise == null) {
            raising = 0L;
            Charge urgent = pick(charges, MEND_URGENT);
            if (urgent != null) return toward(Kind.MEND, urgent.x(), urgent.y(), urgent.entityId(), x, y);
            raise = nearest(wrecks, x, y);
        }
        if (raise != null) {
            raising = raise.wreckId();
            return toward(Kind.RAISE, raise.x(), raise.y(), raise.wreckId(), x, y);
        }
        Charge charge = pick(charges);
        if (charge == null) return home;
        return toward(Kind.MEND, charge.x(), charge.y(), charge.entityId(), x, y);
    }

    private static Goal toward(Kind work, float tx, float ty, long id, float x, float y) {
        boolean near = Math.max(Math.abs(tx - x), Math.abs(ty - y)) <= REACH_CELLS;
        return new Goal(near ? work : Kind.GO, tx, ty, id);
    }

    private static Fallen nearest(List<Fallen> wrecks, float x, float y) {
        Fallen best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Fallen wreck : wrecks) {
            float dx = wreck.x() - x;
            float dy = wreck.y() - y;
            float distance = dx * dx + dy * dy;
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && wreck.wreckId() < best.wreckId())) {
                best = wreck;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Draws up to {@code seconds} of mending from the load; returns the points to lay. */
    public float draw(float seconds) {
        return take(MEND_PER_SECOND * seconds);
    }

    /** Draws up to {@code seconds} of raising from the load; returns the points to lay on the wreck. */
    public float drawRaise(float seconds) {
        return take(Wreck.RAISE_PER_SECOND * seconds);
    }

    private float take(float wanted) {
        float points = Math.min(stock, wanted);
        stock -= points;
        return points;
    }

    /** Puts back what could not be laid (the charge was already whole, or the wreck raised). */
    public void refund(float points) {
        stock += Math.max(0f, points);
    }

    /** Takes on a load at the bay: as much as the bay gives toward a full {@link #STOCK}. */
    public void reload(ServiceBay bay) {
        stock += bay.issue(STOCK - stock);
    }
}
