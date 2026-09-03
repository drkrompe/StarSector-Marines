package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BattleSimulation#frameBudget} owes a rendered frame at most
 * {@link BattleSimulation#MAX_CATCH_UP_TICKS} ticks. A frame that arrives
 * late because the last tick was slow must not be answered with every tick
 * the last frame could not afford — that is the spiral that turned one
 * 460 ms tick into a 14-tick frame and then a 190-tick one. The cap is the
 * frame's, not the simulation's: a harness advancing whole seconds in one
 * call still gets every tick it asked for.
 */
class BattleSimulationCatchUpTest {

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(12, 12));
        // Both sides present, so the elimination backstop does not end the
        // battle on its first tick and stop the clock.
        sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE_BLUE, 1, 1));
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE_BLUE, 10, 10));
        return sim;
    }

    @Test
    void aFrameOwingSecondsOfSimTimeIsBudgetedToTheCatchUpLimit() {
        BattleSimulation sim = simulation();

        sim.advance(BattleSimulation.frameBudget(5f));

        int ticks = sim.getSimTickIndex();
        assertTrue(ticks <= BattleSimulation.MAX_CATCH_UP_TICKS,
                "ran " + ticks + " ticks for one frame");
        assertTrue(ticks >= BattleSimulation.MAX_CATCH_UP_TICKS - 1,
                "the budget itself is honoured, not merely a cap: " + ticks);
    }

    @Test
    void anOrdinaryFrameIsHandedThroughUntouched() {
        assertEquals(BattleSimulation.TICK_DT, BattleSimulation.frameBudget(BattleSimulation.TICK_DT));
        assertEquals(4 * BattleSimulation.TICK_DT,
                BattleSimulation.frameBudget(4 * BattleSimulation.TICK_DT), 1e-6f);
    }

    @Test
    void aHarnessAdvancingWholeSecondsStillGetsEveryTick() {
        BattleSimulation sim = simulation();

        sim.advance(2f);

        assertTrue(sim.getSimTickIndex() >= 59, "ticks: " + sim.getSimTickIndex());
    }
}
