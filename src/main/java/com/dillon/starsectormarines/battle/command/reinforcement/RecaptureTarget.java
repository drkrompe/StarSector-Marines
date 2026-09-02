package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * A defender tactical position the reinforcement layer wants re-manned. One
 * per eligible defender node ({@code defaultGuard == DEFENDER},
 * {@code garrisonSize > 0}, non-AIRBASE). Held by
 * {@link RecaptureTargetService}; its mutable {@code open}/{@code dispatched}
 * flags are written by the per-tick recompute in {@link RecaptureTargetSystem}
 * (and {@code dispatched} also by {@link RecaptureTargetService#markDispatched}).
 *
 * <p>Design: {@code reinforcement-nouns.md}.
 * The node's anchor is the squad-assignment coordinate (the objective in the
 * two-coordinate split — where the deboarded squad should stand), distinct
 * from the delivery hint the means picks.
 */
public final class RecaptureTarget {

    public final TacticalNode node;

    /**
     * Front band the node's anchor falls in — {@code 0} at the objective,
     * rising toward the attacker. Computed once at service init; nodes don't
     * move.
     */
    public final int band;

    /**
     * True when the node currently has zero alive defenders assigned — its
     * garrison (original or a prior reinforcement) has been wiped. Recomputed
     * each service tick.
     */
    boolean open;

    /**
     * True once a reinforcement has been dispatched to this target and not yet
     * confirmed arrived — prevents duplicate dispatch while a squad is en
     * route. Cleared automatically when an alive defender is again assigned to
     * the node (the replacement arrived), so a subsequent wipe re-opens it; or
     * immediately when every means rejects, or by the
     * {@link RecaptureTargetSystem#DISPATCH_TIMEOUT_TICKS} safety net when a
     * committed in-flight delivery never arrives.
     */
    boolean dispatched;

    /**
     * True once the recompute has observed an alive squad assigned to this
     * node — the position was actually manned at some point. Targets that were
     * never manned ({@code BattleSetup} ran out of defenders during
     * allocation) are not recapture targets: the design semantic is "had a
     * garrison, then lost it", not "should have had one". Without this latch
     * the trigger would drain reinforcement tickets from tick&nbsp;1 re-manning
     * positions that never had troops.
     */
    boolean manned;

    /**
     * The lane this position stands on, or {@code -1} for one on no lane —
     * a settlement's guard post, or any map that laid no lanes. Computed once
     * at service init from the place's claimed ground; positions don't move.
     */
    int lane = -1;

    /**
     * Which rung of {@link #lane} this position belongs to, or {@code -1}
     * alongside a lane of {@code -1}. This is the finer band: a ring around
     * the objective says how deep a position is, and a rung says which place
     * it is part of.
     */
    int link = -1;

    /**
     * Consecutive recompute ticks this target has sat {@code open && dispatched}
     * — a reinforcement was posted but no alive squad has been assigned yet
     * (en route, or lost in the delivery pipeline). At
     * {@link RecaptureTargetSystem#DISPATCH_TIMEOUT_TICKS} the dispatch flag is
     * presumed lost and cleared. Reset by
     * {@link RecaptureTargetService#markDispatched} and whenever the node is
     * observed held.
     */
    int dispatchAgeTicks;

    /** Monotonic owner token preventing an expired request from releasing a newer reservation. */
    long dispatchReservationGeneration;

    RecaptureTarget(TacticalNode node, int band) {
        this.node = node;
        this.band = band;
    }

    /** Squad-assignment X (the objective — where the deboarded squad advances to). */
    public int objectiveX() { return node.anchorX; }

    /** Squad-assignment Y. */
    public int objectiveY() { return node.anchorY; }

    /** The lane this position stands on, or {@code -1} for one on no lane. */
    public int lane() { return lane; }

    /** Which rung of its lane, or {@code -1} for a position on no lane. */
    public int link() { return link; }

    public boolean isOpen()       { return open; }
    public boolean isDispatched() { return dispatched; }

    @Override
    public String toString() {
        return "RecaptureTarget{" + node.kind + " @(" + node.anchorX + "," + node.anchorY
                + ") band " + band + (open ? " OPEN" : " HELD") + (dispatched ? " DISPATCHED" : "") + "}";
    }
}
