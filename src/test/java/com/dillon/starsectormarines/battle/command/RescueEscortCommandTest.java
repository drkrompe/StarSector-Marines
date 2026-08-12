package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueEscortCommandTest {

    @Test
    void commandTransitionsFromShelterReliefToMovingCohort() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 41L);
        assertNotNull(payload);
        Squad squad = addMarineSquad(sim, 2, 2);
        RescueEscortCommand command =
                new RescueEscortCommand(payload.placement);

        command.tick(sim);

        assertEscortTarget(squad, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);

        long leader = sim.resolveUnit(squad.leaderId);
        sim.world().setCellPos(leader,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }

        command.tick(sim);

        assertEscortTarget(squad, 15, 8);
    }

    @Test
    void commandClearsEscortWhenNoActiveCiviliansRemain() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 42L);
        assertNotNull(payload);
        Squad squad = addMarineSquad(sim,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        RescueEscortCommand command =
                new RescueEscortCommand(payload.placement);
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        for (int i = 0; i < tracker.registeredCount(); i++) {
            tracker.markEvacuated(tracker.entityIdAt(i));
        }

        command.tick(sim);

        assertNull(squad.assignedObjective);
    }

    private static void assertEscortTarget(Squad squad, int x, int y) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        assertNotNull(assignment);
        assertEquals(AssignmentKind.ESCORT, assignment.kind());
        assertEquals(x, assignment.targetCellX());
        assertEquals(y, assignment.targetCellY());
    }

    private static Squad addMarineSquad(BattleSimulation sim, int x, int y) {
        long leader = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, x, y));
        int squadId = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 16);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(24, 16));
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                9, 5, 13, 9, 8, 7, 11, 7);
    }
}
