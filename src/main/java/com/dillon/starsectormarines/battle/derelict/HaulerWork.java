package com.dillon.starsectormarines.battle.derelict;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * A hauler's rule (`fallen-hull-nouns.md`, Hauler): the load-carrying walker that takes stripped metal from strippers
 * to the nearest store whose chain holds, along a haul road. Destroyed loaded, its load is lost ({@link #lose}). The
 * rules are here; the hull's director walks it and moves the metal.
 *
 * <p>Each pulse: full ({@link #CAPACITY}), or carrying something with no stripper left holding metal, it takes its
 * load to the nearest store and unloads within {@link #REACH_CELLS}; otherwise it goes to the stripper holding the
 * most metal (the nearest on a tie) and takes it on within reach; with nothing to carry it stands at its post.
 */
public final class HaulerWork {

    /** The metal a hauler carries at once. UNTUNED. */
    public static final float CAPACITY = 150f;
    /** How near a stripper or store, in cells (either axis, centre to centre), it takes on or unloads. */
    public static final float REACH_CELLS = 2.5f;
    /** How far short of a stripper or store, in cells (Chebyshev), it stands to reach it. */
    public static final int STAND_OFF_CELLS = 2;

    public enum Kind { STAND_BY, GO, TAKE, UNLOAD }

    /** A stripper holding metal: its lance, where it stands, what it holds. */
    public record Holder(int squadId, float x, float y, float load) { }

    /** A store the hauler may unload at: its key (the stake's point, or {@link ClaimLattice#HULL}) and place. */
    public record Store(int key, float x, float y) { }

    /** What the hauler does now: where it walks, and the stripper's lance or store's key it works with. */
    public record Goal(Kind kind, float x, float y, int with) { }

    private float load;
    private float lost;

    public float load() {
        return load;
    }

    /** Metal this hauler has lost to being destroyed loaded, over the battle. */
    public float lost() {
        return lost;
    }

    /**
     * The hauler's goal this pulse.
     *
     * @param holders strippers holding metal
     * @param stores  the stores whose chain holds
     */
    public Goal decide(float x, float y, float postX, float postY, List<Holder> holders, List<Store> stores) {
        Holder fullest = null;
        for (Holder h : holders) {
            if (h.load() <= 0f) continue;
            if (fullest == null || h.load() > fullest.load()
                    || (h.load() == fullest.load() && d2(h.x(), h.y(), x, y) < d2(fullest.x(), fullest.y(), x, y))) {
                fullest = h;
            }
        }
        boolean deliver = load >= CAPACITY || (load > 0f && fullest == null);
        if (deliver) {
            Store best = null;
            for (Store s : stores) {
                if (best == null || d2(s.x(), s.y(), x, y) < d2(best.x(), best.y(), x, y)) best = s;
            }
            if (best == null) return new Goal(Kind.STAND_BY, postX, postY, 0);
            return new Goal(near(best.x(), best.y(), x, y) ? Kind.UNLOAD : Kind.GO, best.x(), best.y(), best.key());
        }
        if (fullest == null) return new Goal(Kind.STAND_BY, postX, postY, 0);
        return new Goal(near(fullest.x(), fullest.y(), x, y) ? Kind.TAKE : Kind.GO, fullest.x(), fullest.y(),
                fullest.squadId());
    }

    /** Takes on what a stripper hands over; returns the room it had for it, which the stripper hands. */
    public float room() {
        return Math.max(0f, CAPACITY - load);
    }

    public void takeOn(float metal) {
        load += Math.max(0f, metal);
    }

    /** Unloads everything into a store. */
    public void unload(Feedstock store) {
        store.deposit(load);
        load = 0f;
    }

    /** Destroyed: what it carried is lost. Returns the metal lost. */
    public float lose() {
        float was = load;
        lost += was;
        load = 0f;
        return was;
    }

    /**
     * The cell a hauler at {@code (x, y)} walks to to reach a stripper or store at {@code (tx, ty)}: never the target's
     * own cell, which a stripper or a bay stands on and no walk ends on, but the open cell {@link #STAND_OFF_CELLS}
     * short of it on the line from the hauler, else the open cell round the target at that distance nearest the
     * hauler, else one nearer; the target's cell only when none round it is open.
     */
    public static int[] approach(float tx, float ty, float x, float y, BiPredicate<Integer, Integer> open) {
        int cx = (int) Math.floor(tx);
        int cy = (int) Math.floor(ty);
        float dx = x - tx;
        float dy = y - ty;
        float span = Math.max(Math.abs(dx), Math.abs(dy));
        for (int d = STAND_OFF_CELLS; d >= 1; d--) {
            if (span > 0f) {
                int ax = cx + Math.round(dx / span * d);
                int ay = cy + Math.round(dy / span * d);
                if (open.test(ax, ay)) return new int[] {ax, ay};
            }
            int[] best = null;
            float bestD = Float.MAX_VALUE;
            for (int oy = -d; oy <= d; oy++) {
                for (int ox = -d; ox <= d; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != d || !open.test(cx + ox, cy + oy)) continue;
                    float e = d2(cx + ox + 0.5f, cy + oy + 0.5f, x, y);
                    if (e < bestD) {
                        bestD = e;
                        best = new int[] {cx + ox, cy + oy};
                    }
                }
            }
            if (best != null) return best;
        }
        return new int[] {cx, cy};
    }

    private static boolean near(float tx, float ty, float x, float y) {
        return Math.max(Math.abs(tx - x), Math.abs(ty - y)) <= REACH_CELLS;
    }

    private static float d2(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }
}
