package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.BreakContact;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * <b>Squad posture: walk the edge of a held place that has no rooms.</b> The
 * open-ground counterpart of {@link GarrisonPatrol}, for a squad holding a
 * node such as a landing zone, where there is no interior to tour. It walks a
 * ring of points just outside the node's bounding box, dwelling at each, and
 * fires at anything it sees on the way.
 *
 * <p>A landing-zone garrison used to stand perfectly still for the whole
 * battle: the hold goal only knew how to patrol rooms, so on open ground it
 * declined and the squad fell to awaiting orders. To a player that reads as a
 * broken squad.
 *
 * <p>Perpetual (always {@link ActionStatus#RUNNING}); a morale break or a
 * disengage doctrine hands the squad to survival as the room patrol does.
 */
public final class PerimeterPatrol implements Action {

    /** Cells outside the node's bounding box the ring is walked at. */
    static final int RING_MARGIN = 4;
    /** Points on the ring: the four corners and the four edge midpoints. */
    static final int RING_POINTS = 8;
    /** How far a ring point may slide to find standing ground. */
    static final int SNAP_CELLS = 4;

    private final int left, top, right, bottom;
    private final PatrolMotion.WaypointSource waypointSource = (member, squad, sim) -> nextWaypoint(squad, sim);

    public PerimeterPatrol(TacticalNode node) {
        this.left = node.left - RING_MARGIN;
        this.top = node.top - RING_MARGIN;
        this.right = node.right + RING_MARGIN;
        this.bottom = node.bottom + RING_MARGIN;
    }

    @Override public String name() { return "PerimeterPatrol"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (squad.contactPicture.doctrine() == Doctrine.DISENGAGE) {
            return BreakContact.INSTANCE.execute(member, squad, sim);
        }
        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ true);
    }

    /** The ring point after the one nearest the current waypoint, walking round in order. */
    private int[] nextWaypoint(Squad squad, BattleView sim) {
        int current = nearestRingIndex(squad.patrolWaypointX, squad.patrolWaypointY);
        for (int step = 1; step <= RING_POINTS; step++) {
            int[] point = ringPoint((current + step) % RING_POINTS);
            int[] ground = standingGround(point[0], point[1], sim);
            if (ground != null) return ground;
        }
        return null;
    }

    int nearestRingIndex(int x, int y) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < RING_POINTS; i++) {
            int[] point = ringPoint(i);
            long dx = point[0] - x, dy = point[1] - y;
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Ring point {@code i}, clockwise from the top-left corner. */
    int[] ringPoint(int i) {
        int midX = (left + right) / 2, midY = (top + bottom) / 2;
        return switch (i) {
            case 0 -> new int[] {left, top};
            case 1 -> new int[] {midX, top};
            case 2 -> new int[] {right, top};
            case 3 -> new int[] {right, midY};
            case 4 -> new int[] {right, bottom};
            case 5 -> new int[] {midX, bottom};
            case 6 -> new int[] {left, bottom};
            default -> new int[] {left, midY};
        };
    }

    private static int[] standingGround(int x, int y, BattleView sim) {
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        for (int r = 0; r <= SNAP_CELLS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (cx < 0 || cy < 0 || cx >= width || cy >= height) continue;
                    if (sim.getGrid().isWalkable(cx, cy)) return new int[] {cx, cy};
                }
            }
        }
        return null;
    }
}
