package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <b>Fire-team flank.</b> Per-instance action that sends one intact fire team
 * to a pre-computed flanking waypoint while its siblings hold the contact
 * axis. The waypoint is placed
 * ~90° off the garrison's engagement axis by
 * {@link com.dillon.starsectormarines.battle.infantry.ReinforceContact#customPlan}
 * so the patrol arrives at a crossfire angle rather than stacking behind the
 * garrison.
 *
 * <p>Members keep moving toward the flank waypoint while the infantry
 * dispatcher fills any otherwise-empty primary fire intent with a legal shot
 * of opportunity. The passing shot does not replace the action's waypoint or
 * pursuit target, and normal moving-fire accuracy still applies. This keeps a
 * mission maneuver from making the squad ignore enemies already shooting at
 * it. {@link com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior#prepareForAction}
 * also handles turret-of-opportunity rockets above this action.
 *
 * <p>Returns {@link ActionStatus#SUCCESS} when the squad centroid reaches
 * {@link #ARRIVAL_RADIUS} of the waypoint. On SUCCESS the replan fires and
 * {@link com.dillon.starsectormarines.battle.infantry.EliminateEnemiesGoal}
 * takes over — members engage from their flanking positions.
 */
public final class FlankApproach implements Action {

    public static final float ARRIVAL_RADIUS = 3.0f;
    static final String FIX = "fix:";
    static final String FLANK = "flank:";

    private final int waypointX;
    private final int waypointY;

    public FlankApproach(int waypointX, int waypointY) {
        this.waypointX = waypointX;
        this.waypointY = waypointY;
    }

    public int waypointX() { return waypointX; }
    public int waypointY() { return waypointY; }

    @Override public String name() { return "FlankApproach"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim,
                                                List<Long> candidates) {
        List<FireTeamGroups.Team> teams = FireTeamGroups.organize(candidates, sim.squad());
        if (teams.size() < 2) {
            return FireTeamGroups.assignments(FLANK, candidates, sim.squad());
        }
        Map<String, List<Long>> result = new LinkedHashMap<>();
        for (int i = 0; i < teams.size(); i++) {
            FireTeamGroups.Team team = teams.get(i);
            String role = i == teams.size() - 1 ? FLANK : FIX;
            result.put(role + team.index(), team.members());
        }
        return result;
    }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        SquadPlan.Step step = squad.currentPlan != null
                ? squad.currentPlan.currentStep() : null;
        String role = step != null ? step.slotOf(member) : null;
        if (role != null && role.startsWith(FIX)) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            return ActionStatus.RUNNING;
        }

        if (maneuverDistance(step, squad, sim) <= ARRIVAL_RADIUS) {
            return ActionStatus.SUCCESS;
        }

        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        if (sim.movement().mayRepath(member) && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    waypointX, waypointY, sim.getOccupancyMap()));
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (pathIdx < Paths.cellCount(path)) {
            sim.advanceMovement(member);
        }
        return ActionStatus.RUNNING;
    }

    private float maneuverDistance(SquadPlan.Step step, Squad squad,
                                   BattleView sim) {
        if (step == null) {
            return distanceToWaypoint(squad.centroidX, squad.centroidY);
        }
        float x = 0f;
        float y = 0f;
        int count = 0;
        for (Map.Entry<String, List<Long>> entry : step.assignments.entrySet()) {
            if (!entry.getKey().startsWith(FLANK)) continue;
            for (long member : entry.getValue()) {
                if (sim.resolveUnit(member) == 0L) continue;
                x += sim.world().x(member);
                y += sim.world().y(member);
                count++;
            }
        }
        return count > 0 ? distanceToWaypoint(x / count, y / count)
                : distanceToWaypoint(squad.centroidX, squad.centroidY);
    }

    private float distanceToWaypoint(float x, float y) {
        float dx = x - (waypointX + 0.5f);
        float dy = y - (waypointY + 0.5f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{waypointX, waypointY});
    }
}
