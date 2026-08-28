package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidCommandTest {

    @Test
    void assignsOneServiceElementAndSecurityThenTransitionsToWithdrawal() {
        try (BattleSimulation sim = simulation()) {
            RaidObjective objective = objective(sim);
            int first = squad(sim, 2, 2);
            int second = squad(sim, 3, 4);
            sim.spawn(new EntitySpec("distant guard", Faction.DEFENDER,
                    UnitType.MILITIA, 22, 14));
            sim.addObjective(new EliminateFactionObjective(
                    Faction.DEFENDER, Faction.MARINE));
            RaidCommand command = new RaidCommand();
            CommanderService.runSingle(command, RaidCommandDisclosure.INSTANCE,
                    sim);

            assertNotNull(sim.getSquad(first).assignedObjective,
                    "first Raid squad should receive a target order");
            assertNotNull(sim.getSquad(second).assignedObjective,
                    "second Raid squad should receive a target order");
            List<AssignmentKind> initial = List.of(
                    sim.getSquad(first).assignedObjective.kind(),
                    sim.getSquad(second).assignedObjective.kind());
            assertTrue(initial.contains(AssignmentKind.RUSH_OBJECTIVE));
            assertTrue(initial.contains(AssignmentKind.DEFEND_SITE));
            RaidCommandSnapshot snapshot = command.raidSnapshot();
            assertEquals("APPROACH", snapshot.phase());
            assertNotNull(snapshot.squadIntents());

            sim.spawn(new EntitySpec("objective operator", Faction.MARINE,
                    UnitType.MARINE, objective.targetCellX(),
                    objective.targetCellY()));
            for (int i = 0; i < Math.ceil(objective.serviceDuration()
                    / BattleSimulation.TICK_DT) + 2; i++) objective.tick(sim);
            assertTrue(objective.targetSecured());

            CommanderService.runSingle(command, RaidCommandDisclosure.INSTANCE,
                    sim);
            assertEquals(AssignmentKind.WITHDRAW,
                    sim.getSquad(first).assignedObjective.kind());
            assertEquals(AssignmentKind.WITHDRAW,
                    sim.getSquad(second).assignedObjective.kind());
        }
    }

    private static RaidObjective objective(BattleSimulation sim) {
        int zone = sim.getZoneGraph().zoneIdAt(15, 10);
        RaidObjective objective = new RaidObjective("RAID-01", "depot",
                15, 10, zone, 2, 2);
        sim.addObjective(objective);
        return objective;
    }

    private static int squad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("m" + squadId, Faction.MARINE,
                UnitType.MARINE, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squadId;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 16));
    }
}
