package com.dillon.starsectormarines.battle.vehicle;

/**
 * How many grid searches one caller is willing to spend answering one routing
 * question. Handed to {@link VehicleRoutePlanner} as a parameter — the planner
 * stays a function of its inputs, and how long it may look is one of them.
 *
 * <p>It exists because "up to eight tries" was a bound on the wrong thing. The
 * planner's kinematic retry loop is eight searches <em>per endpoint pair</em>,
 * and the convoy route proof enumerates pairs: entries against junctions
 * against exits. A per-pair bound therefore says nothing at all about what one
 * dispatch costs, and on a production Conquest map one dispatch reached a
 * thousand full-grid floods and stalled the game thread for five seconds. A
 * budget spanning the whole enumeration is a bound on the answer somebody is
 * waiting for.
 *
 * <p>Exhaustion is not an error. A caller that has looked this hard and found
 * nothing reports no route, which is the same answer it would have reached more
 * slowly; the request falls through to another means or is dropped as a map
 * diagnostic.
 */
public final class RouteSearchBudget {

    private final int total;
    private int spent;

    /** A budget of {@code searches} grid searches; a negative count is treated as none. */
    public RouteSearchBudget(int searches) {
        this.total = Math.max(0, searches);
    }

    /**
     * Claims one search, returning {@code false} when nothing is left — in
     * which case the caller must stop rather than search anyway.
     */
    public boolean claim() {
        if (spent >= total) return false;
        spent++;
        return true;
    }

    /** True once the budget is gone. Cheap enough to test at the top of every enumeration loop. */
    public boolean isExhausted() { return spent >= total; }

    /** Searches claimed so far — the number worth putting in a diagnostic. */
    public int spent() { return spent; }

    /** Searches this budget was created with. */
    public int total() { return total; }
}
