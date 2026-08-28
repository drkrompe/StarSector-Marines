package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidObjectiveTest {

    @Test
    void requiresUncontestedServiceThenOneSurvivorAtEgress() {
        try (BattleSimulation sim = simulation()) {
            RaidObjective objective = new RaidObjective("RAID-01", "depot",
                    10, 10, sim.getZoneGraph().zoneIdAt(10, 10),
                    2, 2, BattleSimulation.TICK_DT * 3f);
            long marine = sim.spawn(new EntitySpec("raider", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            long guard = sim.spawn(new EntitySpec("guard", Faction.DEFENDER,
                    UnitType.MILITIA, 10, 11));

            objective.tick(sim);
            assertEquals(0f, objective.serviceProgress());
            assertFalse(objective.defenderAlarm().active());

            sim.world().setCellPos(guard, 18, 18);
            for (int i = 0; i < 3; i++) objective.tick(sim);

            assertTrue(objective.targetSecured());
            assertTrue(objective.defenderAlarm().active());
            assertFalse(objective.isComplete());
            sim.world().setCellPos(marine, 2, 2);
            objective.tick(sim);
            assertTrue(objective.isComplete());
            assertEquals(RaidObjective.Phase.COMPLETE, objective.phase());
        }
    }

    @Test
    void interruptedServiceRestartsInsteadOfBankingProgress() {
        try (BattleSimulation sim = simulation()) {
            RaidObjective objective = new RaidObjective("RAID-01", "comms",
                    10, 10, sim.getZoneGraph().zoneIdAt(10, 10),
                    2, 2, BattleSimulation.TICK_DT * 3f);
            sim.spawn(new EntitySpec("raider", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            objective.tick(sim);
            assertTrue(objective.serviceProgress() > 0f);
            sim.spawn(new EntitySpec("responder", Faction.DEFENDER,
                    UnitType.MILITIA, 11, 10));
            objective.tick(sim);
            assertEquals(0f, objective.serviceProgress());
            assertEquals(RaidObjective.Phase.APPROACH, objective.phase());
        }
    }

    @Test
    void nearbyMarineCannotServiceTargetThroughDisconnectedStructureWall() {
        NavigationGrid grid = new NavigationGrid(14, 12);
        grid.setWalkableFloor(10, 6);
        grid.setWalkableFloor(8, 6);
        grid.setWalkableFloor(2, 2);
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(14, 12))) {
            RaidObjective objective = new RaidObjective("RAID-01", "archive",
                    10, 6, sim.getZoneGraph().zoneIdAt(10, 6),
                    2, 2, BattleSimulation.TICK_DT);
            sim.spawn(new EntitySpec("outside raider", Faction.MARINE,
                    UnitType.MARINE, 8, 6));

            objective.tick(sim);

            assertEquals(0f, objective.serviceProgress());
            assertFalse(objective.defenderAlarm().active());
        }
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }
}
