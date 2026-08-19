package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

        int[] route = GridPathfinder.findPath(sim.getGrid(), 15, 8,
                payload.placement.liftX, payload.placement.liftY);
        int targetCell = Math.min(RescueEscortCommand.ADVANCE_SCREEN_CELLS,
                Paths.cellCount(route) - 1);
        assertEscortTarget(squad, Paths.cellX(route, targetCell),
                Paths.cellY(route, targetCell));
    }

    @Test
    void mobileSquadsReceiveSeparatedSlotsBeforeShelterRelief() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 46L);
        assertNotNull(payload);
        List<Squad> squads = addMarineSquads(sim, 5);

        new RescueEscortCommand(payload.placement).tick(sim);

        assertEscortTarget(squads.get(0),
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        assertSeparatedEscortTargets(squads);
    }

    @Test
    void mobileSquadsKeepSeparatedSlotsAroundTheMovingEscortScreen() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 47L);
        assertNotNull(payload);
        List<Squad> squads = addMarineSquads(sim, 5);
        long lead = sim.resolveUnit(squads.get(0).leaderId);
        sim.world().setCellPos(lead,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }

        new RescueEscortCommand(payload.placement).tick(sim);

        assertSeparatedEscortTargets(squads);
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

    @Test
    void engagedEscortAdvancesInTimedNonRegressingBounds() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 43L);
        assertNotNull(payload);
        Squad squad = addMarineSquad(sim,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }
        squad.alertLevel = SquadAlertLevel.ENGAGED;
        sim.spawn(new EntitySpec("nearby-runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER,
                payload.placement.shelterApproachX + 1,
                payload.placement.shelterApproachY));
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);
        int[] route = GridPathfinder.findPath(sim.getGrid(), 15, 8,
                payload.placement.liftX, payload.placement.liftY);

        command.tick(sim);
        assertEscortTarget(squad,
                Paths.cellX(route, RescueEscortCommand.ENGAGED_BOUND_CELLS),
                Paths.cellY(route, RescueEscortCommand.ENGAGED_BOUND_CELLS));

        sim.simTickIndex += RescueEscortCommand.ENGAGED_BOUND_TICKS - 1;
        command.tick(sim);
        assertEscortTarget(squad,
                Paths.cellX(route, RescueEscortCommand.ENGAGED_BOUND_CELLS),
                Paths.cellY(route, RescueEscortCommand.ENGAGED_BOUND_CELLS));

        sim.simTickIndex++;
        command.tick(sim);
        int secondBound = RescueEscortCommand.ENGAGED_BOUND_CELLS * 2;
        assertEscortTarget(squad, Paths.cellX(route, secondBound),
                Paths.cellY(route, secondBound));
    }

    @Test
    void engagedAlertWithOnlyADistantAttackerDoesNotThrottleProgress() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 48L);
        assertNotNull(payload);
        Squad squad = addMarineSquad(sim,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }
        squad.alertLevel = SquadAlertLevel.ENGAGED;
        sim.spawn(new EntitySpec("distant-runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 35, 25));
        RescueEscortCommand command = new RescueEscortCommand(
                payload.placement);
        int[] route = GridPathfinder.findPath(sim.getGrid(), 15, 8,
                payload.placement.liftX, payload.placement.liftY);

        command.tick(sim);

        assertEscortTarget(squad,
                Paths.cellX(route, RescueEscortCommand.ADVANCE_SCREEN_CELLS),
                Paths.cellY(route, RescueEscortCommand.ADVANCE_SCREEN_CELLS));
    }

    @Test
    void nearbyAttackerThrottlesOnlyTheRelatedSquad() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 49L);
        assertNotNull(payload);
        Squad clearSquad = addMarineSquad(sim, 35, 25);
        Squad pressuredSquad = addMarineSquad(sim,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        clearSquad.alertLevel = SquadAlertLevel.ENGAGED;
        pressuredSquad.alertLevel = SquadAlertLevel.ENGAGED;
        sim.spawn(new EntitySpec("local-runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER,
                payload.placement.shelterApproachX + 1,
                payload.placement.shelterApproachY));
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.isCivilianEvacuationTriggered());
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }
        RescueEscortCommand command = new RescueEscortCommand(
                payload.placement);
        int[] route = GridPathfinder.findPath(sim.getGrid(), 15, 8,
                payload.placement.liftX, payload.placement.liftY);

        command.tick(sim);

        assertEscortTarget(clearSquad,
                Paths.cellX(route, RescueEscortCommand.ADVANCE_SCREEN_CELLS),
                Paths.cellY(route, RescueEscortCommand.ADVANCE_SCREEN_CELLS));
        ObjectiveAssignment pressured = pressuredSquad.assignedObjective;
        assertNotNull(pressured);
        assertTrue(pressured.targetCellX()
                        != clearSquad.assignedObjective.targetCellX()
                        || pressured.targetCellY()
                        != clearSquad.assignedObjective.targetCellY(),
                "the locally pressured squad keeps its own bounded formation slot");
    }

    @Test
    void pickupGuardsKeepTheirAuthoredPerimeterPosts() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 44L);
        assertNotNull(payload);
        Squad guard = addMarineSquad(sim, 2, 2);
        guard.rescuePickupGuard = true;
        int postX = payload.placement.formationX(0);
        int postY = payload.placement.formationY(0);
        guard.assignedObjective = ObjectiveAssignment.escort(
                guard.id, postX, postY);

        new RescueEscortCommand(payload.placement).tick(sim);

        assertEscortTarget(guard, postX, postY);
    }

    @Test
    void pickupGuardWithoutAnAuthoredPostFallsBackToTheLift() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 45L);
        assertNotNull(payload);
        Squad guard = addMarineSquad(sim, 2, 2);
        guard.rescuePickupGuard = true;

        new RescueEscortCommand(payload.placement).tick(sim);

        assertEscortTarget(guard, payload.placement.liftX,
                payload.placement.liftY);
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

    private static List<Squad> addMarineSquads(BattleSimulation sim,
                                                int count) {
        List<Squad> squads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            squads.add(addMarineSquad(sim, 2, 2 + i));
        }
        return squads;
    }

    private static void assertSeparatedEscortTargets(List<Squad> squads) {
        for (int i = 0; i < squads.size(); i++) {
            ObjectiveAssignment first = squads.get(i).assignedObjective;
            assertNotNull(first);
            for (int j = i + 1; j < squads.size(); j++) {
                ObjectiveAssignment second = squads.get(j).assignedObjective;
                assertNotNull(second);
                int dx = first.targetCellX() - second.targetCellX();
                int dy = first.targetCellY() - second.targetCellY();
                assertTrue(dx * dx + dy * dy
                                >= RescueEscortCommand.MIN_SLOT_SEPARATION
                                * RescueEscortCommand.MIN_SLOT_SEPARATION,
                        "mobile squad rally anchors must not clump together");
            }
        }
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(40, 30));
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                9, 5, 13, 9, 8, 7, 11, 7);
    }
}
