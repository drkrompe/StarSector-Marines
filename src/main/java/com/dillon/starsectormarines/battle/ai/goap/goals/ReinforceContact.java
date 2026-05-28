package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.FlankApproach;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Collection;
import java.util.List;

/**
 * <b>Story D — Patrol intercept.</b> A defender patrol squad that hears
 * gunfire converges on the firefight at a flanking angle relative to any
 * friendly squad already engaged, rather than walking straight toward the
 * contact. Produces a visible two-angle crossfire that forces the player
 * to reposition.
 *
 * <p>{@link Priority#MISSION} — same tier as {@link RoutinePatrol}. The
 * handoff is clean: {@code RoutinePatrol} yields (returns 0) when
 * SUSPICIOUS + valid lastSeenEnemy, and this goal takes over. On arrival
 * the squad goes ENGAGED and {@link EliminateEnemiesGoal} picks up the
 * actual engagement from the flanking position.
 *
 * <p>Custom-plan: computes a flanking waypoint ~90° off the garrison's
 * engagement axis and emits a single {@link FlankApproach} step. Members
 * converge silently (no firing during approach) so the flank is a
 * surprise.
 *
 * <p>Yields to {@link SurviveContact} via the MORALE_BROKEN gate — a
 * mauled patrol retreats instead of pressing the flank.
 */
public final class ReinforceContact implements Goal {

    public static final ReinforceContact INSTANCE = new ReinforceContact();

    /** Cells from contact to place the flanking waypoint along the perpendicular axis. */
    static final float FLANK_RADIUS = 10f;
    /** Cells from contact within which the squad is "already at the fight" and should just engage. */
    static final float ALREADY_AT_CONTACT_RADIUS = 13f;
    /** Search radius for an engaged friendly squad near the contact point. */
    static final float FRIENDLY_SEARCH_RADIUS = 25f;
    /** Spiral search radius when snapping a waypoint to a walkable cell. */
    static final int WALKABLE_SNAP_RADIUS = 5;

    private ReinforceContact() {}

    @Override public String name() { return "ReinforceContact"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        if (squad.faction != Faction.DEFENDER) return 0f;
        if (squad.holdsFireUntilKillZone) return 0f;
        if (squad.alertLevel == SquadAlertLevel.UNAWARE) return 0f;
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;

        float dx = squad.centroidX - squad.lastSeenEnemyX;
        float dy = squad.centroidY - squad.lastSeenEnemyY;
        if (Math.sqrt(dx * dx + dy * dy) <= ALREADY_AT_CONTACT_RADIUS) return 0f;

        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        int[] wp = computeFlankWaypoint(squad, sim);
        return new SquadPlan(List.of(new SquadPlan.Step(new FlankApproach(wp[0], wp[1]))));
    }

    // ---- Flanking waypoint algorithm ----

    static int[] computeFlankWaypoint(Squad squad, BattleSimulation sim) {
        int contactX = squad.lastSeenEnemyX;
        int contactY = squad.lastSeenEnemyY;

        Squad garrison = findEngagedFriendlyNearContact(squad, sim);

        float axisX, axisY;
        if (garrison != null) {
            axisX = contactX - garrison.centroidX;
            axisY = contactY - garrison.centroidY;
        } else {
            axisX = contactX - squad.centroidX;
            axisY = contactY - squad.centroidY;
        }

        float len = (float) Math.sqrt(axisX * axisX + axisY * axisY);
        if (len < 0.01f) return new int[]{contactX, contactY};
        axisX /= len;
        axisY /= len;

        float perpX = -axisY;
        float perpY = axisX;

        float toPatrolX = squad.centroidX - contactX;
        float toPatrolY = squad.centroidY - contactY;
        float dot = toPatrolX * perpX + toPatrolY * perpY;
        if (dot < 0) {
            perpX = -perpX;
            perpY = -perpY;
        }

        int rawX = Math.round(contactX + perpX * FLANK_RADIUS);
        int rawY = Math.round(contactY + perpY * FLANK_RADIUS);

        return snapToWalkable(rawX, rawY, sim.getGrid(), contactX, contactY);
    }

    private static Squad findEngagedFriendlyNearContact(Squad self, BattleSimulation sim) {
        Collection<Squad> squads = sim.getSquads();
        Squad best = null;
        float bestDist = Float.MAX_VALUE;
        for (Squad s : squads) {
            if (s.id == self.id) continue;
            if (s.faction != self.faction) continue;
            if (s.alertLevel != SquadAlertLevel.ENGAGED) continue;
            if (s.aliveMembers <= 0) continue;
            float dx = s.centroidX - self.lastSeenEnemyX;
            float dy = s.centroidY - self.lastSeenEnemyY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > FRIENDLY_SEARCH_RADIUS) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    static int[] snapToWalkable(int x, int y, NavigationGrid grid, int fallbackX, int fallbackY) {
        if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        for (int r = 1; r <= WALKABLE_SNAP_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int cx = x + dx;
                    int cy = y + dy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        return new int[]{fallbackX, fallbackY};
    }
}
