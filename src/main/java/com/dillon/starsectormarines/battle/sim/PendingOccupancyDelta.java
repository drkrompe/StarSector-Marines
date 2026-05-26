package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * Deferred {@code occupancyMap} + {@code destIndex} mutation produced by
 * {@code BattleSimulation.setPath} during UPDATE_UNITS and drained serially
 * by {@code flushPendingOccupancyDeltas} in the APPLY_OCCUPANCY phase that
 * runs immediately after. Sibling of the SoA damage queue on
 * {@code DamageService} — same rule (no shared sim mutation in the per-unit
 * dispatch hot path), different mutation surface (spatial bookkeeping vs
 * combat outcomes).
 *
 * <p>{@code oldDestX/Y == Integer.MIN_VALUE} means "no decrement / removal"
 * (the unit had no prior destination, or its destination was its current
 * cell which doesn't claim occupancy). {@code newDestX/Y == Integer.MIN_VALUE}
 * means "no increment / addition". A delta with both sides skipped is never
 * enqueued — see {@code setPath}.
 *
 * <p>Within-tick consequence: a unit pathfinding later in the same tick sees
 * occupancy / destIndex as of the most recent REBUILD_OCCUPANCY (tick start),
 * not the freshest peer updates. Drift is one tick of slightly-bunchier
 * convergence; the next tick's rebuild resets the picture.
 */
public final class PendingOccupancyDelta {
    public Unit u;
    public int oldDestX;
    public int oldDestY;
    public int newDestX;
    public int newDestY;
}
