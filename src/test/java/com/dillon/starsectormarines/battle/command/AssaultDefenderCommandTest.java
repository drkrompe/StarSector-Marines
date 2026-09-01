package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultDefenderCommandTest {

    @Test
    void routineSecuritySpreadsAndBoundedReserveKeepsReadinessOrders() {
        BattleSimulation sim = openSim(false);
        Set<Integer> mobile = addMobileForce(sim, 6);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        tick(command, sim);

        AssaultDefenseSnapshot snapshot = command.defenseSnapshot();
        assertEquals(2, snapshot.reserveCount());
        assertEquals(2, snapshot.directives().stream()
                .filter(row -> row.role() == AssaultDefenseSnapshot.Role.RESERVE)
                .count());
        assertTrue(snapshot.directives().stream()
                .filter(row -> row.role() == AssaultDefenseSnapshot.Role.RESERVE)
                .allMatch(row -> row.assignmentKind()
                        == AssignmentKind.DEFEND_AREA));
        assertEquals(4, snapshot.directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.ROUTINE_SECURITY)
                .map(AssaultDefenseSnapshot.SquadDirective::areaIndex)
                .distinct().count());
        assertTrue(GoapInfantryBehavior.INFANTRY_GOALS.contains(
                DefendAssignedAreaGoal.INSTANCE));
    }

    @Test
    void loneMobileSquadGuardsAnAreaInsteadOfWaitingInReserve() {
        BattleSimulation sim = openSim(false);
        Set<Integer> mobile = addMobileForce(sim, 1);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        tick(command, sim);

        AssaultDefenseSnapshot snapshot = command.defenseSnapshot();
        assertEquals(0, snapshot.reserveCount());
        assertEquals(AssaultDefenseSnapshot.Role.ROUTINE_SECURITY,
                snapshot.directives().get(0).role());
        assertEquals(AssignmentKind.DEFEND_AREA,
                snapshot.directives().get(0).assignmentKind());
    }

    @Test
    void reserveTargetFollowsAPoolThatOnlyFillsUpAfterTheFirstPulse() {
        BattleSimulation sim = openSim(false);
        Set<Integer> mobile = addMobileForce(sim, 4);
        AssignmentArbiter ownership = new AssignmentArbiter();
        Set<Integer> held = new LinkedHashSet<>(mobile);
        held.remove(mobile.iterator().next());
        for (int squadId : held) {
            ownership.claimExternal(sim.getSquad(squadId),
                    CommandAuthority.REINFORCEMENT, "reinforcement-delivery",
                    "awaiting handoff", sim.getSimTickIndex());
        }
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        AssaultDefenseSnapshot whileHeld = plan(command, sim, ownership);

        assertEquals(1, whileHeld.mobilePool(),
                "three of the four squads are owned elsewhere");
        assertEquals(0, whileHeld.reserveCount(),
                "a pool of one is routine coverage, never a reserve");
        for (int squadId : held) {
            ownership.releaseExternal(sim.getSquad(squadId),
                    "reinforcement-delivery", "delivered",
                    sim.getSimTickIndex());
        }

        AssaultDefenseSnapshot afterRelease = plan(command, sim, ownership);

        assertEquals(4, afterRelease.mobilePool());
        assertTrue(afterRelease.reserveCount() > 0,
                "the reserve target re-derives from the pool the commander "
                        + "has now, not the one it had at the first pulse");
        assertTrue(afterRelease.directives().stream()
                        .anyMatch(row -> row.role()
                                == AssaultDefenseSnapshot.Role.RESERVE),
                afterRelease.toString());
    }

    @Test
    void casualtyRepairsRoutineCoverageAndReplenishesReserve() {
        BattleSimulation sim = openSim(false);
        Set<Integer> mobile = addMobileForce(sim, 7);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);
        tick(command, sim);
        AssaultDefenseSnapshot.AreaState unique = command.defenseSnapshot()
                .areas().stream().filter(area -> area.routineSquads() == 1)
                .findFirst().orElseThrow();
        int casualtyId = command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.ROUTINE_SECURITY)
                .filter(row -> row.areaIndex() == unique.index())
                .findFirst().orElseThrow().squadId();
        Squad casualty = sim.getSquad(casualtyId);
        sim.applyDamage(casualty.leaderId, 1_000_000f, 1f, 0f);

        tick(command, sim);

        assertEquals(4, command.defenseSnapshot().areas().stream()
                .filter(area -> area.routineSquads() > 0).count());
        assertEquals(2, command.defenseSnapshot().directives().stream()
                .filter(row -> row.role() == AssaultDefenseSnapshot.Role.RESERVE)
                .count());
    }

    @Test
    void hiddenLiveMarineDoesNotMobilizeDefenderReserve() {
        BattleSimulation sim = openSim(true);
        Set<Integer> mobile = addMobileForce(sim, 6);
        sim.spawn(new EntitySpec("hidden", Faction.MARINE,
                UnitType.MARINE, 50, 5).health(10_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        tick(command, sim);

        assertTrue(command.defenseSnapshot().areas().stream()
                .allMatch(area -> area.reportState()
                        == AssaultDefenseSnapshot.ReportState.QUIET));
        assertEquals(0, command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER).count());
    }

    @Test
    void audioRelocationCannotTransferFreshDirectAuthorityAcrossAreas() {
        BattleSimulation sim = openSim(true);
        Set<Integer> mobile = new LinkedHashSet<>();
        for (int i = 0; i < 6; i++) {
            mobile.add(addDefender(sim, 25 + i % 3, 4 + i,
                    UnitRole.PATROL).id);
        }
        long marine = sim.spawn(new EntitySpec("relocating", Faction.MARINE,
                UnitType.MARINE, 27, 5).health(100_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);
        tick(command, sim);
        AssaultDefenseSnapshot.AreaState first = command.defenseSnapshot()
                .areas().stream().filter(area -> area.reportState()
                        == AssaultDefenseSnapshot.ReportState.ACTIVE)
                .findFirst().orElseThrow();
        assertEquals(0, first.index());

        sim.world().setCellPos(marine, 35, 5);
        sim.postShot(new ShotEvent(marine, 35.5f, 5.5f,
                36.5f, 5.5f, false, Faction.MARINE, 0.1f));
        for (int i = 0; i < 14; i++) sim.advance(BattleSimulation.TICK_DT);
        tick(command, sim);

        assertEquals(AssaultDefenseSnapshot.ReportState.ACTIVE,
                command.defenseSnapshot().area(0).reportState());
        assertEquals(AssaultDefenseSnapshot.ReportState.QUIET,
                command.defenseSnapshot().area(1).reportState());
    }

    @Test
    void directReportMobilizesOneReserveWithoutBriefingExactEnemyCell() {
        BattleSimulation sim = responseSim();
        Set<Integer> mobile = addSeparatedMobileForce(sim);
        sim.spawn(new EntitySpec("reported", Faction.MARINE,
                UnitType.MARINE, 5, 8).health(10_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        tick(command, sim);

        AssaultDefenseSnapshot snapshot = command.defenseSnapshot();
        AssaultDefenseSnapshot.AreaState active = snapshot.areas().stream()
                .filter(area -> area.reportState()
                        == AssaultDefenseSnapshot.ReportState.ACTIVE)
                .findFirst().orElseThrow();
        AssaultDefenseSnapshot.SquadDirective response = snapshot.directives()
                .stream().filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER)
                .findFirst().orElseThrow();
        assertEquals(active.index(), response.areaIndex());
        assertEquals(1, active.respondingSquads());
        assertEquals(1, snapshot.reserveCount());
        assertTrue(active.reportExpiresTick() > snapshot.tick());
        assertNotEquals("5,8", response.markerCellX() + ","
                + response.markerCellY(),
                "the order must use defender-owned area geometry");
    }

    @Test
    void reportedLocalOvermatchConcentratesTheSecondReserve() {
        BattleSimulation sim = responseSim();
        Set<Integer> mobile = addSeparatedMobileForce(sim);
        sim.spawn(new EntitySpec("reported-mech", Faction.MARINE,
                UnitType.HEAVY_MECH, 5, 8).health(100_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);

        tick(command, sim);

        AssaultDefenseSnapshot.AreaState responseArea = command
                .defenseSnapshot().areas().stream()
                .filter(area -> area.respondingSquads() > 0)
                .findFirst().orElseThrow();
        assertEquals(2, responseArea.respondingSquads());
        assertEquals(2, command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER).count());
    }

    @Test
    void firstReportImmediatelySupersedesStableReserveReadiness() {
        BattleSimulation sim = responseSim();
        Set<Integer> mobile = addSeparatedMobileForce(sim);
        Squad reporter = sim.getSquads().stream()
                .min(java.util.Comparator.comparingInt(squad -> squad.id))
                .orElseThrow();
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);
        tick(command, sim);
        Set<Integer> held = command.defenseSnapshot().directives().stream()
                .filter(row -> row.role() == AssaultDefenseSnapshot.Role.RESERVE)
                .map(AssaultDefenseSnapshot.SquadDirective::squadId)
                .collect(java.util.stream.Collectors.toSet());
        sim.spawn(new EntitySpec("first-contact", Faction.MARINE,
                UnitType.MARINE, 5, 8).health(10_000f).moveSpeed(0f));
        advanceInfluenceWindow(sim);

        tick(command, sim);

        AssaultDefenseSnapshot.SquadDirective response = command
                .defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER)
                .findFirst().orElseThrow(() -> new AssertionError(
                        command.defenseSnapshot().toString()));
        assertTrue(held.contains(response.squadId()));
        assertNotEquals(reporter.id, response.squadId());
        assertEquals(response.markerCellX(),
                sim.getSquad(response.squadId()).assignedObjective.targetCellX());
        assertEquals(response.markerCellY(),
                sim.getSquad(response.squadId()).assignedObjective.targetCellY());
    }

    @Test
    void locallyEngagedReserveDoesNotConsumeDispatchedResponseSlot() {
        BattleSimulation sim = responseSim();
        addDefender(sim, 20, 12, UnitRole.PATROL);
        addDefender(sim, 22, 14, UnitRole.PATROL);
        addDefender(sim, 24, 16, UnitRole.PATROL);
        addDefender(sim, 26, 18, UnitRole.PATROL);
        Squad freeReserve = addDefender(sim, 12, 5, UnitRole.PATROL);
        Squad engagedReserve = addDefender(sim, 5, 5, UnitRole.PATROL);
        Set<Integer> mobile = sim.getSquads().stream()
                .map(squad -> squad.id).collect(java.util.stream.Collectors.toSet());
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);
        tick(command, sim);
        assertEquals(AssaultDefenseSnapshot.Role.RESERVE,
                command.defenseSnapshot().directiveFor(engagedReserve.id).role());
        sim.spawn(new EntitySpec("reserve-contact", Faction.MARINE,
                UnitType.MARINE, 5, 8).health(10_000f).moveSpeed(0f));
        advanceInfluenceWindow(sim);

        tick(command, sim);

        assertTrue(engagedReserve.hasBelievedContacts());
        assertTrue(!freeReserve.hasBelievedContacts(),
                command.defenseSnapshot().toString());
        assertNotEquals(AssaultDefenseSnapshot.Role.RESPONDER,
                command.defenseSnapshot().directiveFor(engagedReserve.id).role());
        AssaultDefenseSnapshot.SquadDirective response = command
                .defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER)
                .findFirst().orElseThrow(() -> new AssertionError(
                        command.defenseSnapshot().toString()));
        assertNotEquals(engagedReserve.id, response.squadId());
        assertEquals(freeReserve.id, response.squadId());
    }

    @Test
    void authoredGarrisonRemainsUnderGarrisonOwnership() {
        BattleSimulation sim = openSim(false);
        Squad mobile = addDefender(sim, 5, 5, UnitRole.PATROL);
        Squad garrison = addDefender(sim, 8, 5, UnitRole.GARRISON);
        sim.claimSquadCommand(garrison.id, CommandAuthority.GARRISON,
                "setup", "authored post");
        AssaultDefenderCommand command = new AssaultDefenderCommand(
                Set.of(mobile.id));

        tick(command, sim);

        assertEquals(AssaultDefenseSnapshot.Role.AUTHORED_POST,
                command.defenseSnapshot().directiveFor(garrison.id).role());
        assertNull(garrison.assignedObjective);
        assertEquals(CommandAuthority.GARRISON,
                sim.getSquadCommandDirective(garrison.id).authority());
    }

    @Test
    void responderReturnsToReadinessOnlyAfterLegalReportExpiry() {
        BattleSimulation sim = responseSim();
        Set<Integer> mobile = addSeparatedMobileForce(sim);
        long marine = sim.spawn(new EntitySpec("fleeting", Faction.MARINE,
                UnitType.MARINE, 5, 8).health(100_000f).moveSpeed(0f));
        sim.advance(BattleSimulation.TICK_DT);
        AssaultDefenderCommand command = new AssaultDefenderCommand(mobile);
        tick(command, sim);
        AssaultDefenseSnapshot.AreaState report = command.defenseSnapshot()
                .areas().stream().filter(area -> area.reportState()
                        != AssaultDefenseSnapshot.ReportState.QUIET)
                .findFirst().orElseThrow();
        int responder = command.defenseSnapshot().directives().stream()
                .filter(row -> row.role()
                        == AssaultDefenseSnapshot.Role.RESPONDER)
                .findFirst().orElseThrow().squadId();

        sim.applyDamage(marine, 1_000_000f, 1f, 0f);
        assertEquals(AssaultDefenseSnapshot.Role.RESPONDER,
                command.defenseSnapshot().directiveFor(responder).role(),
                "loss of live visibility is not proof that the report ended");
        sim.simTickIndex = report.reportExpiresTick() + 1;
        tick(command, sim);

        AssaultDefenseSnapshot.SquadDirective readiness =
                command.defenseSnapshot().directiveFor(responder);
        assertEquals(AssaultDefenseSnapshot.Role.RESERVE, readiness.role());
        assertEquals(AssignmentKind.DEFEND_AREA, readiness.assignmentKind());
    }

    @Test
    void blockedIncumbentAreaOrderMayBeReplacedDuringStabilityWindow() {
        BattleSimulation sim = openSim(false);
        Squad squad = addDefender(sim, 5, 5, UnitRole.PATROL);
        AssaultDefenderCommand command = new AssaultDefenderCommand(
                Set.of(squad.id));
        tick(command, sim);
        ObjectiveAssignment blocked = squad.assignedObjective;
        sim.getGrid().setWalkable(
                blocked.targetCellX(), blocked.targetCellY(), false);

        tick(command, sim);

        assertNotEquals(blocked, squad.assignedObjective);
        assertTrue(sim.getGrid().isWalkable(
                squad.assignedObjective.targetCellX(),
                squad.assignedObjective.targetCellY()));
    }

    private static BattleSimulation openSim(boolean dividingWall) {
        int width = 60;
        int height = 30;
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        if (dividingWall) {
            for (int y = 0; y < height; y++) grid.setWalkable(30, y, false);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static BattleSimulation responseSim() {
        BattleSimulation sim = openSim(false);
        for (int y = 0; y < 10; y++) {
            sim.getGrid().setWalkable(7, y, false);
        }
        sim.getGrid().setWalkableFloor(7, 9);
        sim.getGrid().setDoorway(7, 9, true);
        return sim;
    }

    private static Set<Integer> addSeparatedMobileForce(BattleSimulation sim) {
        Set<Integer> ids = new LinkedHashSet<>();
        int[][] cells = {{5, 5}, {5, 12}, {20, 16}, {22, 18},
                {12, 5}, {14, 5}};
        for (int[] cell : cells) {
            ids.add(addDefender(sim, cell[0], cell[1], UnitRole.PATROL).id);
        }
        return ids;
    }

    private static void advanceInfluenceWindow(BattleSimulation sim) {
        for (int i = 0; i < 15; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
    }

    private static Set<Integer> addMobileForce(BattleSimulation sim,
                                                int count) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(addDefender(sim, 4 + i, 5 + i % 2,
                    UnitRole.PATROL).id);
        }
        return ids;
    }

    private static Squad addDefender(BattleSimulation sim, int x, int y,
                                     UnitRole role) {
        long leader = sim.spawn(new EntitySpec("defender-" + x + "-" + y,
                Faction.DEFENDER, UnitType.MILITIA, x, y).role(role)
                .health(10_000f).moveSpeed(0f));
        int squadId = sim.mintSquad(Faction.DEFENDER, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private static void tick(AssaultDefenderCommand command,
                             BattleSimulation sim) {
        CommanderService.runSingle(command,
                AssaultDefenderCommandDisclosure.INSTANCE, sim);
    }

    /**
     * Plans one pulse against an ownership ledger the test controls, so a
     * squad can be owned elsewhere on one pulse and free on the next without
     * a delivery system to release it.
     */
    private static AssaultDefenseSnapshot plan(AssaultDefenderCommand command,
                                               BattleSimulation sim,
                                               AssignmentArbiter ownership) {
        CommandTopology topology = CommandTopology.freeze(sim);
        return command.plan(AssaultDefenderCommandDisclosure.INSTANCE.freeze(
                sim, Faction.DEFENDER, topology, ownership.snapshot()))
                .detail();
    }
}
