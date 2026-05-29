package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

import java.util.List;

/**
 * <b>Squad posture: patrol route walking.</b> Custom-plan action emitted by
 * {@link com.dillon.starsectormarines.battle.infantry.RoutinePatrol} for
 * non-garrison defender squads when not actively engaged.
 *
 * <ul>
 *   <li><b>SUSPICIOUS with last-seen cell</b> — converge on the last known
 *       enemy cell. Unlike garrisons, patrols aren't post-anchored, so the
 *       move target isn't clamped to a hold radius — a sniped patrol commits
 *       to investigating.</li>
 *   <li><b>UNAWARE</b> — walk a squad-scoped waypoint route through nearby
 *       tactical nodes in the same district. The waypoint lives on the squad
 *       so members converge instead of each picking their own; one tick per
 *       squad picks the next waypoint when the current is reached, others
 *       follow.</li>
 * </ul>
 *
 * <p>When the squad goes ENGAGED, {@code RoutinePatrol} drops its relevance
 * to zero and the engagement-tier goal (typically {@code EliminateEnemiesGoal})
 * takes over with the standard {@code Approach → Engage} plan.
 */
public final class PatrolRoute implements Action {

    public static final PatrolRoute INSTANCE = new PatrolRoute();

    /** Default cell-radius around {@link Squad#assignedNode} a patrol squad samples waypoints from. Tight enough that a patrol stays in its district; loose enough that 5–7 candidate nodes are available on a typical conquest map. Used as the {@link Squad#patrolRadius} default at squad mint; per-squad overrides remain possible. */
    public static final int DEFAULT_DISTRICT_RADIUS = 20;
    /** Sim-seconds the squad rests at a waypoint before picking a new one. Long enough that the foot-traffic reads as patrol-pausing-to-look-around, not march-step. */
    public static final float PATROL_DWELL_SECONDS = 4.0f;
    /** Cell-radius around the waypoint inside which the squad's centroid counts as "arrived" and the dwell starts. Bigger than 1 so the squad doesn't stutter at the exact cell while members straggle in. */
    public static final int PATROL_ARRIVAL_RADIUS = 3;
    /** Max attempts to roll a random walkable fallback cell when no other tactical nodes are in range. */
    private static final int FALLBACK_SAMPLE_ATTEMPTS = 16;

    private PatrolRoute() {}

    @Override public String name() { return "PatrolRoute"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            moveToward(member, sim, squad.lastSeenEnemyX, squad.lastSeenEnemyY);
            return ActionStatus.RUNNING;
        }

        // UNAWARE — squad walks its route. Waypoint state is squad-scoped so
        // members converge on the same target rather than each picking their
        // own. Members may execute concurrently (per-unit parallel dispatch):
        // the dwell-timer decrement is leader-gated so it counts once per tick
        // (every-member RMW races would drop decrements); waypoint selection
        // is squad-lock protected so X/Y writes are atomic (no torn
        // member-A's X paired with member-B's Y).
        if (squad.patrolDwellTimer > 0f) {
            if (member == squad.leader) {
                squad.patrolDwellTimer -= BattleSimulation.TICK_DT;
            }
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (!hasValidWaypoint(squad) || squadHasArrived(squad)) {
            synchronized (squad.lock) {
                // Re-check inside the lock — a sibling worker may have already
                // picked a fresh waypoint and started a new dwell while we were
                // waiting. Don't clobber their pick with another roll.
                if (squad.patrolDwellTimer > 0f || (hasValidWaypoint(squad) && !squadHasArrived(squad))) {
                    hold(member, sim);
                    return ActionStatus.RUNNING;
                }
                int[] waypoint = pickWaypoint(member, squad, sim);
                if (waypoint == null) {
                    squad.patrolDwellTimer = PATROL_DWELL_SECONDS;
                    hold(member, sim);
                    return ActionStatus.RUNNING;
                }
                squad.patrolWaypointX = waypoint[0];
                squad.patrolWaypointY = waypoint[1];
                squad.patrolDwellTimer = PATROL_DWELL_SECONDS;
            }
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        moveToward(member, sim, squad.patrolWaypointX, squad.patrolWaypointY);
        return ActionStatus.RUNNING;
    }

    private static boolean hasValidWaypoint(Squad squad) {
        return squad.patrolWaypointX >= 0 && squad.patrolWaypointY >= 0;
    }

    private static boolean squadHasArrived(Squad squad) {
        if (squad.aliveMembers == 0) return true;
        float dx = squad.centroidX - squad.patrolWaypointX;
        float dy = squad.centroidY - squad.patrolWaypointY;
        return Math.sqrt(dx * dx + dy * dy) <= PATROL_ARRIVAL_RADIUS;
    }

    /**
     * Picks a new patrol destination. Prefers a same-faction tactical node
     * within the squad's patrol radius of the assigned anchor that isn't the
     * current waypoint; falls back to a random walkable cell in that radius.
     * Returns null if neither produces a valid cell.
     */
    private static int[] pickWaypoint(Unit member, Squad squad, BattleView sim) {
        TacticalNode anchor = squad.assignedNode;
        TacticalMap map = sim.getTacticalMap();
        int radius = squad.patrolRadius;
        if (anchor != null && map != null) {
            List<TacticalNode> nearby = map.within(anchor.anchorX, anchor.anchorY, radius);
            int[] best = null;
            int bestRoll = -1;
            for (TacticalNode n : nearby) {
                if (n.defaultGuard != squad.faction) continue;
                if (n.anchorX == squad.patrolWaypointX && n.anchorY == squad.patrolWaypointY) continue;
                if (!sim.getGrid().inBounds(n.anchorX, n.anchorY)) continue;
                if (!sim.getGrid().isWalkable(n.anchorX, n.anchorY)) continue;
                int roll = member.rng.nextInt(1000);
                if (roll > bestRoll) {
                    bestRoll = roll;
                    best = new int[]{n.anchorX, n.anchorY};
                }
            }
            if (best != null) return best;
        }

        NavigationGrid grid = sim.getGrid();
        int seedX = anchor != null ? anchor.anchorX : Math.round(squad.centroidX);
        int seedY = anchor != null ? anchor.anchorY : Math.round(squad.centroidY);
        int r = radius;
        for (int i = 0; i < FALLBACK_SAMPLE_ATTEMPTS; i++) {
            int dx = member.rng.nextInt(r * 2 + 1) - r;
            int dy = member.rng.nextInt(r * 2 + 1) - r;
            int cx = seedX + dx;
            int cy = seedY + dy;
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
            if (cx == squad.patrolWaypointX && cy == squad.patrolWaypointY) continue;
            return new int[]{cx, cy};
        }
        return null;
    }

    private static void moveToward(Unit member, BattleControl sim, int tx, int ty) {
        if (member.getMoveProgress() == 0f && member.pathIdx >= member.pathCellCount()) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), tx, ty, sim.getOccupancyMap()));
        }
        if (member.pathIdx < member.pathCellCount()) {
            sim.advanceMovement(member);
        } else {
            member.setMoveProgress(0f);
            member.setRenderPos(member.getCellX(), member.getCellY());
        }
    }

    private static void hold(Unit member, BattleControl sim) {
        sim.clearPath(member);
        member.setMoveProgress(0f);
        member.setRenderPos(member.getCellX(), member.getCellY());
    }
}
