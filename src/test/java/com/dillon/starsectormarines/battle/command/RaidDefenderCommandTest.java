package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidDefenderCommandTest {

    @Test
    void holdsReserveUntilIdentityFreeTargetAlarmMobilizesResponders() {
        try (BattleSimulation sim = simulation()) {
            RaidObjective objective = new RaidObjective("RAID-01", "comms",
                    15, 10, sim.getZoneGraph().zoneIdAt(15, 10), 2, 2);
            sim.addObjective(objective);
            Set<Integer> mobile = new TreeSet<>();
            for (int i = 0; i < 4; i++) mobile.add(squad(sim, 20, 3 + i * 2));
            RaidDefenderCommand command = new RaidDefenderCommand(mobile);

            CommanderService.runSingle(command,
                    RaidDefenderCommandDisclosure.INSTANCE, sim);
            assertEquals(1, assigned(sim, mobile));
            assertEquals(3, command.raidSnapshot().squadIntents().stream()
                    .filter(intent -> "RESERVE".equals(intent.role())).count());
            assertFalse(command.raidSnapshot().alarmActive());
            assertEquals(-1, command.raidSnapshot().egressCellX());

            sim.spawn(new EntitySpec("raider", Faction.MARINE,
                    UnitType.MARINE, 15, 10));
            objective.tick(sim);
            assertTrue(objective.defenderAlarm().active());
            CommanderService.runSingle(command,
                    RaidDefenderCommandDisclosure.INSTANCE, sim);

            assertEquals(RaidDefenderCommand.ALARM_RESPONSE_LIMIT,
                    assigned(sim, mobile));
            assertEquals("ALARM_RESPONSE", command.raidSnapshot().phase());
        }
    }

    private static long assigned(BattleSimulation sim, Set<Integer> squads) {
        return squads.stream().filter(id ->
                sim.getSquad(id).assignedObjective != null).count();
    }

    private static int squad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec("d" + squadId,
                Faction.DEFENDER, UnitType.MILITIA, x, y).squad(squadId));
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
