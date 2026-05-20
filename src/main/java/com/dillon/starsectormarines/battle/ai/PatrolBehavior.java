package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

import java.util.List;

/**
 * Squad walks a route between random tactical nodes in their district. Idle
 * routine is squad-scoped: one waypoint per squad, members converge on it,
 * brief dwell on arrival, then a new waypoint nearby. This makes a patrol
 * read as a coordinated fireteam moving together instead of three independent
 * wanderers. Combat path (when squad goes ENGAGED) is identical to
 * {@link CombatantBehavior}.
 *
 * <p>Waypoint selection prefers other {@link TacticalNode}s in the same
 * faction within {@link #PATROL_DISTRICT_RADIUS} cells of the squad's
 * {@link Squad#assignedNode}. If there aren't enough nearby nodes (small
 * map, isolated post), the squad falls back to a random walkable cell in
 * the same radius — still bounded, still local.
 *
 * <p>SUSPICIOUS state converges on {@link Squad#lastSeenEnemyX} /
 * {@link Squad#lastSeenEnemyY} so a patrol that gets sniped from cover
 * commits to investigating rather than continuing its route. The squad
 * alert decay in {@link BattleSimulation#updateSquadAlertLevels} eventually
 * drops it back to UNAWARE and the route resumes.
 */
public final class PatrolBehavior implements UnitBehavior {

    public static final PatrolBehavior INSTANCE = new PatrolBehavior();

    /** Manhattan radius around {@link Squad#assignedNode} we sample waypoints from. Tight enough that a patrol stays in its district; loose enough that 5-7 candidate nodes are available on a typical conquest map. */
    public static final int PATROL_DISTRICT_RADIUS = 20;
    /** Sim-seconds the squad rests at a waypoint before picking a new one. Long enough that the foot-traffic reads as patrol-pausing-to-look-around, not march-step. */
    public static final float PATROL_DWELL_SECONDS = 4.0f;
    /** Cell-radius around the waypoint inside which the squad's centroid counts as "arrived" and the dwell starts. Bigger than 1 so the squad doesn't stutter at the exact cell while members straggle in. */
    public static final int PATROL_ARRIVAL_RADIUS = 3;
    /** Max attempts to roll a random walkable fallback cell when no other tactical nodes are in range. */
    private static final int FALLBACK_SAMPLE_ATTEMPTS = 16;

    private PatrolBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        Squad squad = sim.getSquad(u.squadId);
        if (squad == null) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        // Tier-override preempt — same gate the garrison runs. Without it
        // a morale-broken patrol that decayed off ENGAGED would fall
        // through to UNAWARE wander while the squad-level planner expects
        // BreakContact to be running.
        if (TierOverride.check(u, squad, sim) == TierOverride.Result.DELEGATE_TO_GOAP) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        if (squad.alertLevel == SquadAlertLevel.ENGAGED) {
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            moveToward(u, sim, squad.lastSeenEnemyX, squad.lastSeenEnemyY);
            return;
        }

        // UNAWARE — squad walks its route. Waypoint state lives on the squad
        // so members converge on the same target rather than each picking
        // their own. The first squadmate to update this tick picks the
        // waypoint (or starts a fresh dwell on arrival); subsequent members
        // read what was written and path to it.
        if (squad.patrolDwellTimer > 0f) {
            squad.patrolDwellTimer -= BattleSimulation.TICK_DT;
            holdPosition(u, sim);
            return;
        }
        if (!hasValidWaypoint(squad) || squadHasArrived(squad, sim)) {
            int[] waypoint = pickWaypoint(squad, sim);
            if (waypoint == null) {
                // No reachable destination — sit tight and try again after a dwell.
                squad.patrolDwellTimer = PATROL_DWELL_SECONDS;
                holdPosition(u, sim);
                return;
            }
            squad.patrolWaypointX = waypoint[0];
            squad.patrolWaypointY = waypoint[1];
            squad.patrolDwellTimer = PATROL_DWELL_SECONDS;
            holdPosition(u, sim);
            return;
        }
        moveToward(u, sim, squad.patrolWaypointX, squad.patrolWaypointY);
    }

    private static boolean hasValidWaypoint(Squad squad) {
        return squad.patrolWaypointX >= 0 && squad.patrolWaypointY >= 0;
    }

    /** True when the squad's centroid (avg of alive members) is within {@link #PATROL_ARRIVAL_RADIUS} of the current waypoint. Centroid is cached per-tick on the squad. */
    private static boolean squadHasArrived(Squad squad, BattleSimulation sim) {
        if (squad.aliveMembers == 0) return true;
        float dx = squad.centroidX - squad.patrolWaypointX;
        float dy = squad.centroidY - squad.patrolWaypointY;
        return Math.sqrt(dx * dx + dy * dy) <= PATROL_ARRIVAL_RADIUS;
    }

    /**
     * Picks a new patrol destination. Prefers a same-faction tactical node
     * within {@link #PATROL_DISTRICT_RADIUS} of the squad's assigned node
     * that isn't the current waypoint; falls back to a random walkable cell
     * in that radius. Returns null if neither produces a valid cell.
     */
    private static int[] pickWaypoint(Squad squad, BattleSimulation sim) {
        TacticalNode anchor = squad.assignedNode;
        TacticalMap map = sim.getTacticalMap();
        int radius = squad.patrolRadius;
        if (anchor != null && map != null) {
            List<TacticalNode> nearby = map.within(anchor.anchorX, anchor.anchorY, radius);
            // Filter to same-faction, exclude current waypoint exactly.
            int[] best = null;
            int bestRoll = -1;
            for (TacticalNode n : nearby) {
                if (n.defaultGuard != squad.faction) continue;
                if (n.anchorX == squad.patrolWaypointX && n.anchorY == squad.patrolWaypointY) continue;
                if (!sim.getGrid().inBounds(n.anchorX, n.anchorY)) continue;
                if (!sim.getGrid().isWalkable(n.anchorX, n.anchorY)) continue;
                int roll = sim.getRng().nextInt(1000);
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
            int dx = sim.getRng().nextInt(r * 2 + 1) - r;
            int dy = sim.getRng().nextInt(r * 2 + 1) - r;
            int cx = seedX + dx;
            int cy = seedY + dy;
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
            if (cx == squad.patrolWaypointX && cy == squad.patrolWaypointY) continue;
            return new int[]{cx, cy};
        }
        return null;
    }

    private static void moveToward(Unit u, BattleSimulation sim, int tx, int ty) {
        if (u.moveProgress == 0f && u.pathIdx >= u.pathCellCount()) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                    u.cellX, u.cellY, tx, ty, sim.getOccupancyMap()));
        }
        if (u.pathIdx < u.pathCellCount()) {
            sim.advanceMovement(u);
        } else {
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
        }
    }

    private static void holdPosition(Unit u, BattleSimulation sim) {
        sim.clearPath(u);
        u.moveProgress = 0f;
        u.renderX = u.cellX;
        u.renderY = u.cellY;
    }
}
