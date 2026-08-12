package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

import java.util.List;

/**
 * <b>Story D — Patrol intercept.</b> Per-instance action that converges all
 * squad members on a pre-computed flanking waypoint. The waypoint is placed
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
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        float dx = squad.centroidX - waypointX;
        float dy = squad.centroidY - waypointY;
        if (Math.sqrt(dx * dx + dy * dy) <= ARRIVAL_RADIUS) {
            return ActionStatus.SUCCESS;
        }

        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        if (sim.world().moveProgress(member) == 0f && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    waypointX, waypointY, sim.getOccupancyMap()));
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (pathIdx < Paths.cellCount(path)) {
            sim.advanceMovement(member);
        } else {
            sim.world().setMoveProgress(member, 0f);
            sim.world().setRenderPos(member, sim.world().cellX(member), sim.world().cellY(member));
        }
        return ActionStatus.RUNNING;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{waypointX, waypointY});
    }
}
