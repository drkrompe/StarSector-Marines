package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story A: {@link OverwatchPosture} — the silent-hold action garrison squads
 * pick when they have LOS+range but the kill-zone gate is still closed.
 * Verifies the no-fire / no-move discipline.
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

    private static Unit defenderAt(int x, int y, int squadId) {
        Unit u = new Unit("d", Faction.DEFENDER, UnitType.MARINE, x, y);
        u.squadId = squadId;
        return u;
    }

    @Test
    public void preconditionsRequireLosRangeAndClosedKillZone() {
        WorldState pre = OverwatchPosture.INSTANCE.preconditions();
        assertTrue(pre.isSpecified(Predicate.HAS_LOS_TO_TARGET));
        assertTrue(pre.get(Predicate.HAS_LOS_TO_TARGET));
        assertTrue(pre.isSpecified(Predicate.IN_RANGE_OF_TARGET));
        assertTrue(pre.get(Predicate.IN_RANGE_OF_TARGET));
        assertTrue(pre.isSpecified(Predicate.ENEMY_IN_KILL_ZONE));
        assertFalse(pre.get(Predicate.ENEMY_IN_KILL_ZONE),
                "Overwatch only applies while the kill-zone gate is closed");
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
    public void executeHoldsPositionAndDoesNotFire() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.DEFENDER, null);
        Squad squad = sim.getSquad(squadId);
        squad.holdsFireUntilKillZone = true;
        squad.aliveMembers = 1;

        Unit defender = defenderAt(5, 5, squadId);
        // Pre-cooldown set: if any action accidentally fires, cooldownTimer
        // would jump to attackCooldown. We assert it doesn't change.
        defender.cooldownTimer = 0.1f;
        float startCooldown = defender.cooldownTimer;
        sim.addUnit(defender);

        // Marine in LOS + range, but the squad's holdsFireUntilKillZone gate
        // is closed (killZoneLosTicks defaults to 0). Overwatch must hold.
        Unit marine = new Unit("m", Faction.MARINE, UnitType.MARINE, 8, 5);
        sim.addUnit(marine);

        ActionStatus status = OverwatchPosture.INSTANCE.execute(defender, squad, sim);
        assertEquals(ActionStatus.RUNNING, status);
        assertEquals(startCooldown, defender.cooldownTimer, 1e-6f,
                "Overwatch must not fire — cooldownTimer should be unchanged from its starting value");
        assertTrue(defender.pathEmpty(), "Overwatch must not queue a path");
        assertEquals(5, defender.getCellX());
        assertEquals(5, defender.getCellY());
        assertEquals(0f, defender.moveProgress, 1e-6f);
        assertEquals(defender.getCellX(), defender.renderX, 1e-6f);
        assertEquals(defender.getCellY(), defender.renderY, 1e-6f);
    }
}
