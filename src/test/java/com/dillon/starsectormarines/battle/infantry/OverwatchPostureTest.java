package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A: {@link OverwatchPosture} — the hold-ground action garrison squads
 * pick when they have LOS+range but the kill-zone gate is still closed.
 * Verifies that holders stay planted without surrendering their ability to fire.
 */
public class OverwatchPostureTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long defenderAt(BattleSimulation sim, int x, int y, int squadId) {
        return sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, x, y).squad(squadId));
    }

    @Test
    public void preconditionsSelectStationaryHoldOutsideKillZone() {
        WorldState pre = OverwatchPosture.INSTANCE.preconditions();
        assertTrue(pre.isSpecified(Predicate.HAS_LOS_TO_TARGET));
        assertTrue(pre.get(Predicate.HAS_LOS_TO_TARGET));
        assertTrue(pre.isSpecified(Predicate.IN_RANGE_OF_TARGET));
        assertTrue(pre.get(Predicate.IN_RANGE_OF_TARGET));
        assertTrue(pre.isSpecified(Predicate.ENEMY_IN_KILL_ZONE));
        assertFalse(pre.get(Predicate.ENEMY_IN_KILL_ZONE),
                "the gate selects hold-ground posture; it does not authorize firing");
    }

    @Test
    public void effectsClaimEnemyDamagedSamePlannerSlotAsEngage() {
        WorldState eff = OverwatchPosture.INSTANCE.effects();
        assertTrue(eff.get(Predicate.ENEMY_DAMAGED),
                "Overwatch advertises the same effect as Engage so the planner sees both as candidates for EliminateEnemies");
    }

    @Test
    public void costIsHigherThanEngage() {
        Squad squad = new Squad(1, Faction.DEFENDER);
        float overwatchCost = OverwatchPosture.INSTANCE.cost(WorldState.EMPTY, squad, openSim());
        float engageCost = EngagePosture.INSTANCE.cost(WorldState.EMPTY, squad, openSim());
        assertTrue(overwatchCost > engageCost,
                "Overwatch must cost more than Engage so the planner prefers Engage when its preconditions are met");
    }

    @Test
    public void executeHoldsPositionWhileDispatcherAuthorsFire() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.holdsFireUntilKillZone = true;
        squad.aliveMembers = 1;

        long defender = defenderAt(sim, 5, 5, squadId);
        sim.world().setAttackRange(defender, 10f);
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 8, 5));
        com.dillon.starsectormarines.battle.squad.SquadPlan.Step step =
                new com.dillon.starsectormarines.battle.squad.SquadPlan.Step(OverwatchPosture.INSTANCE);
        step.assignments.put("any", java.util.List.of(defender));
        squad.currentPlan = new com.dillon.starsectormarines.battle.squad.SquadPlan(java.util.List.of(step));

        GoapInfantryBehavior.INSTANCE.update(defender, sim);

        assertEquals(marine, sim.combat().fireTargetId(defender),
                "closed kill-zone gate must not prevent a holder returning fire");
        assertTrue(Paths.isEmpty(sim.world().path(defender)), "Overwatch must not queue a path");
        assertEquals(5, sim.world().cellX(defender));
        assertEquals(5, sim.world().cellY(defender));
        assertEquals(0f, sim.world().moveProgress(defender), 1e-6f);
        assertEquals(sim.world().cellX(defender), sim.world().renderX(defender), 1e-6f);
        assertEquals(sim.world().cellY(defender), sim.world().renderY(defender), 1e-6f);
    }
}
