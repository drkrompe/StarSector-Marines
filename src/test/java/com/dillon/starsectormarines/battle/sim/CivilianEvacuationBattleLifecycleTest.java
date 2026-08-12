package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationReport;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationBattleLifecycleTest {

    @Test
    void unitDeathMailboxMarksOnlyTheRegisteredCivilianLost() {
        BattleSimulation sim = simulation();
        long[] cohort = spawnAndRegisterCohort(sim);
        sim.addObjective(new FixedObjective(false));

        sim.applyDamage(cohort[0], 100f, 1f);
        sim.advance(BattleSimulation.TICK_DT * 2f);

        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        assertEquals(CivilianEvacuationTracker.State.LOST,
                tracker.state(cohort[0]));
        assertEquals(7, tracker.activeCount());
        assertFalse(tracker.isSealed());
        assertNull(tracker.report());
    }

    @Test
    void battleTerminalSealsRemainingCiviliansAsLost() {
        BattleSimulation sim = simulation();
        long[] cohort = spawnAndRegisterCohort(sim);
        sim.getCivilianEvacuationTracker().markEvacuated(cohort[0]);
        sim.addObjective(new FixedObjective(true));

        sim.advance(BattleSimulation.TICK_DT * 2f);

        assertTrue(sim.isComplete());
        assertEquals(Faction.MARINE, sim.getWinner());
        CivilianEvacuationReport report =
                sim.getCivilianEvacuationTracker().report();
        assertEquals(8, report.initial);
        assertEquals(1, report.evacuated);
        assertEquals(7, report.lost);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(12, 12));
    }

    private static long[] spawnAndRegisterCohort(BattleSimulation sim) {
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        long[] ids = new long[CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = sim.spawn(new EntitySpec("Evacuee " + (i + 1),
                    Faction.CIVILIAN, UnitType.CIVILIAN,
                    2 + i % 2, 2 + i / 2).role(UnitRole.FLEE));
            assertTrue(tracker.register(ids[i]));
        }
        return ids;
    }

    private static final class FixedObjective implements Objective {
        private final boolean complete;

        private FixedObjective(boolean complete) {
            this.complete = complete;
        }

        @Override
        public Faction owningFaction() {
            return Faction.MARINE;
        }

        @Override
        public void tick(BattleView sim) {}

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public String displayName() {
            return "Fixture";
        }
    }
}
