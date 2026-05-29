package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * Story J — Sabotage cordon. Fires once a marine squad has entered the same
 * zone as its planter's {@link ChargeSiteObjective}: the combatant members
 * deploy to the room's doorways while the planter is assigned the
 * {@link HoldPortalCordon#PLANTER_SLOT} slot in the same squad-plan step
 * and paths to the charge cell to channel. The legacy
 * {@code PlanterBehavior} per-unit dispatch is gone — the planter now waits
 * for its squad to reach the target zone instead of charging straight in.
 *
 * <p>{@link Priority#MISSION} bucket — sits alongside
 * {@link SecureObjectiveZone}, but the two are mutually exclusive by
 * relevance: SecureObjectiveZone requires the squad be in a <em>different</em>
 * zone than the objective, CordonForPlant requires the squad be in the
 * <em>same</em> zone. The room-clear sweep ends naturally as the squad enters
 * the target zone, and the cordon takes over on the next replan.
 *
 * <p>Custom-plan goal: synthesizes one {@link HoldPortalCordon} step carrying
 * the zone's portal list (with a guard cell adjacent to each doorway on the
 * inside of the squad's zone). The backward chainer is bypassed.
 */
public final class CordonForPlant implements Goal {

    public static final CordonForPlant INSTANCE = new CordonForPlant();

    private CordonForPlant() {}

    @Override public String name() { return "CordonForPlant"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        int planterZone = findPlanterZone(squad, sim);
        if (planterZone < 0) return 0f;
        int squadZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (squadZone != planterZone) return 0f;
        NavigationZone zone = sim.getZoneGraph().zoneById(planterZone);
        if (zone == null || zone.getPortalIds().isEmpty()) return 0f;
        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ChargeSiteObjective charge = findActiveChargeObjective(squad, sim);
        if (charge == null) return null;
        int planterZone = sim.getZoneGraph().zoneIdAt(charge.cellX(), charge.cellY());
        NavigationZone zone = sim.getZoneGraph().zoneById(planterZone);
        if (zone == null) return null;

        List<HoldPortalCordon.GuardPost> posts = buildGuardPosts(zone, planterZone, sim);
        if (posts.isEmpty()) return null;

        List<SquadPlan.Step> steps = new ArrayList<>(1);
        steps.add(new SquadPlan.Step(
                new HoldPortalCordon(charge.cellX(), charge.cellY(), posts)));
        return new SquadPlan(steps);
    }

    /**
     * Zone id of an alive squadmate's charge-site objective, or {@code -1}
     * when the squad has no live planter with an in-progress charge. Mirrors
     * the lookup {@link SecureObjectiveZone#findObjectiveZone} does — kept
     * separate to avoid one goal's edge-case logic leaking into the other.
     */
    private static int findPlanterZone(Squad squad, BattleView sim) {
        ChargeSiteObjective cs = findActiveChargeObjective(squad, sim);
        return cs == null ? -1 : sim.getZoneGraph().zoneIdAt(cs.cellX(), cs.cellY());
    }

    /** Returns the in-progress charge an alive squadmate is assigned to, or null. Two callers want it (relevance via zone lookup; customPlan needs the cells). */
    private static ChargeSiteObjective findActiveChargeObjective(Squad squad, BattleView sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.role != UnitRole.PLANTER) continue;
            if (u.assignedObjective instanceof ChargeSiteObjective cs) {
                if (cs.isComplete()) continue;
                return cs;
            }
        }
        return null;
    }

    /**
     * For each portal touching {@code zone}, finds the cell <em>inside</em>
     * {@code zone} that's a cardinal neighbor of the doorway cell — that's
     * the cordon guard position. Falls back to the doorway cell itself when
     * no cardinal neighbor sits in this zone (degenerate map geometry); the
     * holder still has LOS through the portal in that case.
     */
    private static List<HoldPortalCordon.GuardPost> buildGuardPosts(
            NavigationZone zone, int zoneId, BattleView sim) {
        ZoneGraph graph = sim.getZoneGraph();
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        List<HoldPortalCordon.GuardPost> posts = new ArrayList<>(zone.getPortalIds().size());
        for (int portalId : zone.getPortalIds()) {
            Portal p = graph.portalById(portalId);
            if (p == null) continue;
            int dwIdx = p.getDoorwayCellIdx();
            int dwX = dwIdx % w;
            int dwY = dwIdx / w;
            int gx = dwX;
            int gy = dwY;
            for (int[] d : dirs) {
                int nx = dwX + d[0];
                int ny = dwY + d[1];
                if (graph.zoneIdAt(nx, ny) == zoneId) {
                    gx = nx;
                    gy = ny;
                    break;
                }
            }
            posts.add(new HoldPortalCordon.GuardPost(portalId, gx, gy));
        }
        return posts;
    }
}
