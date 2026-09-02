package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueEscortCommandTest {

    @Test
    void commandTransitionsFromShelterReliefToPublishedCohortCorridor() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        Squad escort = addMarineSquad(sim, 2, 2);
        Squad screen = addMarineSquad(sim, 2, 4);
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);

        command.tick(sim);

        assertEscortTarget(escort, payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        assertEquals("AT_SOURCE", command.rescueSnapshot().phase());
        assertEquals(RescueCommandSnapshot.Role.COHORT_ESCORT,
                command.rescueSnapshot().intentFor(escort.id).role());

        long leader = sim.resolveUnit(escort.leaderId);
        sim.world().setCellPos(leader,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        sim.advance(BattleSimulation.TICK_DT);
        for (int i = 0; i < payload.size(); i++) {
            sim.world().setCellPos(payload.entityId(i), 15, 8);
        }
        payload.objective.tick(sim);
        command.tick(sim);

        RescueCommandSnapshot moving = command.rescueSnapshot();
        assertEquals("IN_TRANSIT", moving.phase());
        assertEquals(15, moving.cohortCellX());
        assertEquals(8, moving.cohortCellY());
        assertEquals(RescueCommandSnapshot.Role.LEAD_SCREEN,
                moving.intentFor(screen.id).role());
        assertTrue(moving.corridorGuideCellX() != moving.cohortCellX()
                        || moving.corridorGuideCellY() != moving.cohortCellY(),
                "moving picture exposes a route-relative screen guide");
    }

    @Test
    void mobileSquadsReceiveStableSeparatedScreenRoles() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        List<Squad> squads = addMarineSquads(sim, 5);
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);

        command.tick(sim);
        Set<RescueCommandSnapshot.Role> firstRoles = roles(command);
        assertSeparatedEscortTargets(squads);
        command.tick(sim);

        assertEquals(firstRoles, roles(command));
        assertSeparatedEscortTargets(squads);
    }

    @Test
    void authoredGuardsRemainExternalWithTheirOwnPosts() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        Squad shelter = addMarineSquad(sim, 10, 7);
        sim.registerShelterGuardSquad(shelter.id);
        Squad pickup = addMarineSquad(sim, 30, 20);
        pickup.rescuePickupGuard = true;
        sim.assignSquadCommand(ObjectiveAssignment.escort(shelter.id, 10, 7),
                CommandAuthority.GARRISON, "test-shelter", "hold");
        sim.assignSquadCommand(ObjectiveAssignment.escort(pickup.id, 30, 20),
                CommandAuthority.PAYLOAD, "test-pickup", "hold");
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);

        command.tick(sim);

        assertEscortTarget(shelter, 10, 7);
        assertEscortTarget(pickup, 30, 20);
        assertEquals(RescueCommandSnapshot.Role.SHELTER_GUARD,
                command.rescueSnapshot().intentFor(shelter.id).role());
        assertEquals(RescueCommandSnapshot.Role.PICKUP_GUARD,
                command.rescueSnapshot().intentFor(pickup.id).role());
        assertEquals(0, command.rescueSnapshot().squadIntents().stream()
                .filter(intent -> intent.squadId() == shelter.id
                        && intent.reason().startsWith("SHELTER_RELIEF"))
                .count());
    }

    @Test
    void missingAuthoredGuardPostIsNotInventedByMissionCommand() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        Squad pickup = addMarineSquad(sim, 2, 2);
        pickup.rescuePickupGuard = true;
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);

        command.tick(sim);

        assertNull(pickup.assignedObjective);
        RescueCommandSnapshot.SquadIntent intent =
                command.rescueSnapshot().intentFor(pickup.id);
        assertEquals(RescueCommandSnapshot.Role.PICKUP_GUARD, intent.role());
        assertEquals("AUTHORED_PICKUP_GUARD", intent.reason());
    }

    @Test
    void terminalCohortReleasesMissionOwnedEscort() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        Squad escort = addMarineSquad(sim,
                payload.placement.shelterApproachX,
                payload.placement.shelterApproachY);
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);
        command.tick(sim);
        for (int i = 0; i < payload.size(); i++) {
            sim.getCivilianEvacuationTracker().markEvacuated(payload.entityId(i));
        }
        payload.objective.tick(sim);

        command.tick(sim);

        assertNull(escort.assignedObjective);
        assertTrue(command.rescueSnapshot().complete());
        assertEquals(RescueCommandSnapshot.Role.RELEASED,
                command.rescueSnapshot().intentFor(escort.id).role());
    }

    @Test
    void snapshotReportsOnlyFactionKnownPressure() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = install(sim);
        Squad escort = addMarineSquad(sim, 2, 2);
        sim.spawn(new EntitySpec("unseen-runner", Faction.DEFENDER,
                UnitType.SWARM_RUNNER, 39, 29));
        RescueEscortCommand command = new RescueEscortCommand(payload.placement);

        command.tick(sim);

        assertEquals(0, command.rescueSnapshot().knownPressureContacts());
        assertFalse(command.rescueSnapshot().intentFor(escort.id).localContact());
    }

    private static Set<RescueCommandSnapshot.Role> roles(
            RescueEscortCommand command) {
        Set<RescueCommandSnapshot.Role> result = new HashSet<>();
        for (RescueCommandSnapshot.SquadIntent intent
                : command.rescueSnapshot().squadIntents()) {
            result.add(intent.role());
        }
        return result;
    }

    private static CivilianEvacuationPayload install(BattleSimulation sim) {
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 41L);
        assertNotNull(payload);
        return payload;
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
            squads.add(addMarineSquad(sim, 2, 2 + i * 2));
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
