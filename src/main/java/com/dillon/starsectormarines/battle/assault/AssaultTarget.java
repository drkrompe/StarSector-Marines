package com.dillon.starsectormarines.battle.assault;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.squad.HoldPlaceDecision;
import com.dillon.starsectormarines.battle.squad.OccupyPositionDecision;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

/**
 * A garrisonable a machine can be ordered to assault: a fighting-position run or a held place
 * ({@code garrisonables.md}). Found by the same lookups the infantry occupy order asks, in the
 * same order — the run under the click first, then the smallest held place around it — so the
 * ground a machine assaults is the ground an infantry squad would have been sent to hold.
 *
 * <p>The footprint is a bounding box in cells. A run's membership is its own cells (a contact
 * within a cell of one counts, since a man in a trench may stand on its lip); a place's is the
 * box. Only a place has walls of its own: a run is assaulted by direct fire alone.
 */
public final class AssaultTarget {

    /** Which kind of garrisonable this is. */
    public enum Kind { FIGHTING_POSITION, HELD_PLACE }

    private final Kind kind;
    private final int runId;
    private final TacticalNode place;
    private final FightingPositionIndex positions;
    public final int left, top, right, bottom;

    private AssaultTarget(Kind kind, int runId, TacticalNode place, FightingPositionIndex positions,
                          int left, int top, int right, int bottom) {
        this.kind = kind;
        this.runId = runId;
        this.place = place;
        this.positions = positions;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /** The held place {@code node}, assaulted at its footprint. */
    public static AssaultTarget place(TacticalNode node) {
        return new AssaultTarget(Kind.HELD_PLACE, OccupyPositionDecision.NONE, node, null,
                node.left, node.top, node.right, node.bottom);
    }

    /** Fighting-position run {@code runId} of {@code positions}. */
    public static AssaultTarget run(FightingPositionIndex positions, int runId) {
        return stretch(positions, runId, 0, 0, Integer.MAX_VALUE);
    }

    /**
     * The stretch of fighting-position run {@code runId} within {@code reach} cells (Chebyshev) of ({@code cx},
     * {@code cy}): the part of a long run a garrison standing there holds. A fort's belt run can wrap a corner of the
     * works; a machine guarding the garrison by the gate stands by the gate, not by the run's far end. The whole run
     * when no cell of it is that near.
     */
    public static AssaultTarget stretch(FightingPositionIndex positions, int runId, int cx, int cy, int reach) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (int pass = 0; pass < 2 && left == Integer.MAX_VALUE; pass++) {
            for (int i = 0, n = positions.runSize(runId); i < n; i++) {
                int x = positions.runCellX(runId, i), y = positions.runCellY(runId, i);
                if (pass == 0 && reach != Integer.MAX_VALUE
                        && Math.max(Math.abs(x - cx), Math.abs(y - cy)) > reach) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return new AssaultTarget(Kind.FIGHTING_POSITION, runId, null, positions, left, top, right, bottom);
    }

    /**
     * The garrisonable a click at ({@code cellX}, {@code cellY}) points at, or null on plain
     * ground: the run under it, else the smallest held place around it.
     *
     * @param positionsEnabled whether fighting positions are in effect ({@code DigInSystem.isEnabled()})
     */
    public static AssaultTarget underClick(FightingPositionIndex positions, boolean positionsEnabled,
                                           TacticalMap map, int cellX, int cellY) {
        int run = OccupyPositionDecision.runUnderClick(positions, positionsEnabled, cellX, cellY);
        if (run >= 0) return run(positions, run);
        TacticalNode node = HoldPlaceDecision.placeUnderClick(map, cellX, cellY);
        return node == null ? null : place(node);
    }

    public Kind kind() { return kind; }

    /** The run id, or {@link OccupyPositionDecision#NONE} for a place. */
    public int runId() { return runId; }

    /** The held place, or null for a run. */
    public TacticalNode heldPlace() { return place; }

    /** Whether a unit placed at ({@code x}, {@code y}) is in this garrisonable (a run's cells within its box). */
    public boolean contains(int x, int y) {
        if (kind == Kind.HELD_PLACE) return x >= left && x <= right && y >= top && y <= bottom;
        if (gap(x, y) > 1) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (positions.runIdAt(x + dx, y + dy) == runId) return true;
            }
        }
        return false;
    }

    /** Chebyshev cells from ({@code x}, {@code y}) to the footprint's box; zero inside it. */
    public int gap(int x, int y) {
        int dx = Math.max(0, Math.max(left - x, x - right));
        int dy = Math.max(0, Math.max(top - y, y - bottom));
        return Math.max(dx, dy);
    }

    /** Whether the wall cell at ({@code x}, {@code y}) is part of this garrisonable's shell. A run has none. */
    public boolean ownsWall(int x, int y) {
        return kind == Kind.HELD_PLACE && gap(x, y) <= 1;
    }

    /** The footprint's centre cell. */
    public int centreX() { return (left + right) / 2; }

    public int centreY() { return (top + bottom) / 2; }

    /** Whether this is the same garrisonable as {@code other}. */
    public boolean sameAs(AssaultTarget other) {
        if (other == null || other.kind != kind) return false;
        return kind == Kind.HELD_PLACE ? other.place == place : other.runId == runId;
    }
}
