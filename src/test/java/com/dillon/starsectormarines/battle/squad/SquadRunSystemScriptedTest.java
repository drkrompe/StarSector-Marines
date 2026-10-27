package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mission script runs a squad of any side, member by member, until it says a member has arrived. */
class SquadRunSystemScriptedTest {

    @Test
    void aScriptedRaiderRunsUntilTheScriptSaysItHasArrived() {
        BattleSimulation sim = openSim();
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long raider = sim.spawn(new EntitySpec("raider", Faction.DEFENDER, UnitType.MILITIA, 2, 2).squad(squad));
        boolean[] arrived = {false};
        sim.squadRun().runScripted(squad, id -> !arrived[0]);

        sim.squadRun().beforeMovement(sim);
        assertTrue(sim.squadRun().boosted(raider), "a raider on a scripted run runs, whatever its side");

        arrived[0] = true;
        sim.squadRun().beforeMovement(sim);
        assertFalse(sim.squadRun().boosted(raider), "once arrived it walks");

        arrived[0] = false;
        sim.squadRun().stopScripted(squad);
        sim.squadRun().beforeMovement(sim);
        assertFalse(sim.squadRun().boosted(raider), "a stopped run boosts nobody");
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(10, 10));
    }
}
