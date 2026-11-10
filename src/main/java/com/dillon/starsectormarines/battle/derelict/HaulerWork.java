package com.dillon.starsectormarines.battle.derelict;

import java.util.List;

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

    private static boolean near(float tx, float ty, float x, float y) {
        return Math.max(Math.abs(tx - x), Math.abs(ty - y)) <= REACH_CELLS;
    }

    private static float d2(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }
}
