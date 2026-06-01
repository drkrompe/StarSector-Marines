package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
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
 *       tactical nodes in the same district via {@link PatrolMotion}; this
 *       action only supplies the node-route {@link PatrolMotion.WaypointSource}.
 *       The waypoint lives on the squad so members converge instead of each
 *       picking their own.</li>
 * </ul>
 *
 * <p>Patrols don't fire opportunistically while walking (the route is the
 * not-yet-engaged posture) — once a squadmate gains LOS the squad goes ENGAGED,
 * {@code RoutinePatrol} drops its relevance to zero, and the engagement-tier
 * goal ({@code EliminateEnemiesGoal}) takes over with {@code Approach → Engage}.
 */
public final class PatrolRoute implements Action {

    public static final PatrolRoute INSTANCE = new PatrolRoute();

    /** Default cell-radius (≈ metres) around {@link Squad#assignedNode} a patrol squad samples waypoints from — ~1.8× infantry weapon range (~24), so a district patrol covers a real chunk of a 144–240-wide map rather than a third of its own gun range. Used as the {@link Squad#patrolRadius} default at squad mint; per-squad overrides remain possible. */
    public static final int DEFAULT_DISTRICT_RADIUS = 44;
    /** Max attempts to roll a random walkable fallback cell when no other tactical nodes are in range. */
    private static final int FALLBACK_SAMPLE_ATTEMPTS = 16;

    /** Node-route waypoint strategy (default staleness: no-waypoint or arrived). Singleton, so resolve once. */
    private final PatrolMotion.WaypointSource waypointSource = this::pickWaypoint;

    private PatrolRoute() {}

    @Override public String name() { return "PatrolRoute"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleControl sim) {
        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            PatrolMotion.moveToward(member, sim, squad.lastSeenEnemyX, squad.lastSeenEnemyY);
            return ActionStatus.RUNNING;
        }
        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ false);
    }

    /**
     * Picks a new patrol destination. Prefers a same-faction tactical node
     * within the squad's patrol radius of the assigned anchor that isn't the
     * current waypoint; falls back to a random walkable cell in that radius.
     * Returns null if neither produces a valid cell.
     */
    private int[] pickWaypoint(Unit member, Squad squad, BattleView sim) {
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
}
