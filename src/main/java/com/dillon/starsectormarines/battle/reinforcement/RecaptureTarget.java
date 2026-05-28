package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

/**
 * A defender tactical position the reinforcement layer wants re-manned. One
 * per eligible defender node ({@code defaultGuard == DEFENDER},
 * {@code garrisonSize > 0}, non-AIRBASE). Owned by
 * {@link RecaptureTargetService}; its mutable state is written only by the
 * service's per-tick pass.
 *
 * <p>Design: {@code roadmap/conquest/stories/progressive-reinforcement.md}.
 * The node's anchor is the squad-assignment coordinate (the objective in the
 * two-coordinate split — where the deboarded squad should stand), distinct
 * from the delivery hint the means picks.
 */
public final class RecaptureTarget {

    public final TacticalNode node;

    /** Biome band the node's anchor falls in. Computed once at service init; nodes don't move. */
    public final BiomeKind slice;

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
     * the node (the replacement arrived), so a subsequent wipe re-opens it.
     */
    boolean dispatched;

    RecaptureTarget(TacticalNode node, BiomeKind slice) {
        this.node = node;
        this.slice = slice;
    }

    /** Squad-assignment X (the objective — where the deboarded squad advances to). */
    public int objectiveX() { return node.anchorX; }

    /** Squad-assignment Y. */
    public int objectiveY() { return node.anchorY; }

    public boolean isOpen()       { return open; }
    public boolean isDispatched() { return dispatched; }

    @Override
    public String toString() {
        return "RecaptureTarget{" + node.kind + " @(" + node.anchorX + "," + node.anchorY
                + ") " + slice + (open ? " OPEN" : " HELD") + (dispatched ? " DISPATCHED" : "") + "}";
    }
}
