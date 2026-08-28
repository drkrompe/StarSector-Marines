package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.SweepSector;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.infantry.SweepAssignedSectorGoal;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentColonyCommandTest {

    @Test
    void routeAndStrengthSeedStableArchiveAndSurvivorBranches() {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        Squad survivors = squad(fixture.sim, 7, 7, 4);
        CommanderService service = service(fixture);
        claim(service, archive);
        claim(service, survivors);

        pulse(service, fixture.sim);

        SilentColonyCommandSnapshot first = picture(service);
        assertEquals(SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY,
                first.intentFor(archive.id).role());
        assertEquals(SilentColonyCommandSnapshot.Role.SURVIVOR_ESCORT,
                first.intentFor(survivors.id).role());
        assertEquals(AssignmentKind.SWEEP_SECTOR,
                archive.assignedObjective.kind());
        assertEquals(AssignmentKind.ESCORT,
                survivors.assignedObjective.kind());
        Map<Integer, SilentColonyCommandSnapshot.Role> roles = roles(first);

        pulse(service, fixture.sim);

        assertEquals(roles, roles(picture(service)));
        assertEquals(1, picture(service).archiveBranchSquads());
        assertEquals(1, picture(service).survivorBranchSquads());
    }

    @Test
    void clearArchiveOrderBuildsAnExecutableApproachPath() {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        CommanderService service = service(fixture);
        claim(service, archive);

        pulse(service, fixture.sim);

        assertTrue(SweepAssignedSectorGoal.INSTANCE.relevance(
                WorldState.EMPTY, archive, fixture.sim) > 0f,
                "a clear archive must still produce a mission movement goal");
        SquadPlan plan = SweepAssignedSectorGoal.INSTANCE.customPlan(
                archive, fixture.sim);
        SweepSector action = (SweepSector) plan.currentStep().action;
        assertEquals(ActionStatus.RUNNING, action.execute(
                archive.leaderId, archive, fixture.sim));
        assertEquals(fixture.archive.cellX(),
                Paths.destX(fixture.sim.world().path(archive.leaderId)));
        assertEquals(fixture.archive.cellY(),
                Paths.destY(fixture.sim.world().path(archive.leaderId)));
    }

    @Test
    void equalRouteSendsTheStrongerSquadToArchiveRecovery() {
        Fixture fixture = fixture();
        Squad weak = squad(fixture.sim, 20, 14, 2);
        Squad strong = squad(fixture.sim, 20, 14, 6);
        CommanderService service = service(fixture);
        claim(service, weak);
        claim(service, strong);

        pulse(service, fixture.sim);

        assertEquals(SilentColonyCommandSnapshot.Role.SURVIVOR_ESCORT,
                picture(service).intentFor(weak.id).role());
        assertEquals(SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY,
                picture(service).intentFor(strong.id).role());
    }

    @Test
    void lostArchiveElementIsReplacedWithoutMovingTheOtherSurvivor() {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        Squad firstSurvivor = squad(fixture.sim, 7, 7, 4);
        Squad secondSurvivor = squad(fixture.sim, 9, 7, 3);
        CommanderService service = service(fixture);
        claim(service, archive);
        claim(service, firstSurvivor);
        claim(service, secondSurvivor);
        pulse(service, fixture.sim);
        archive.aliveMembers = 0;

        pulse(service, fixture.sim);

        SilentColonyCommandSnapshot picture = picture(service);
        long replacements = picture.squadIntents().stream()
                .filter(intent -> intent.role()
                        == SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY)
                .filter(intent -> intent.membershipReason()
                        == SilentColonyCommandSnapshot.Reason
                        .ARCHIVE_BRANCH_LOSS_REBALANCE)
                .count();
        assertEquals(1, replacements);
        assertEquals(1, picture.archiveBranchSquads());
        assertEquals(1, picture.survivorBranchSquads());
    }

    @Test
    void unreachableArchiveMemberDoesNotOccupyBranchCoverage() {
        Fixture fixture = fixture();
        Squad strandedArchive = squad(fixture.sim, 31, 20, 100);
        Squad reachableReplacement = squad(fixture.sim, 33, 20, 6);
        Squad survivor = squad(fixture.sim, 7, 7, 4);
        CommanderService service = service(fixture);
        claim(service, strandedArchive);
        claim(service, reachableReplacement);
        claim(service, survivor);
        pulse(service, fixture.sim);
        assertEquals(SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY,
                picture(service).intentFor(strandedArchive.id).role());

        blockVerticalBoundary(fixture.sim.getGrid(), 31);
        pulse(service, fixture.sim);

        SilentColonyCommandSnapshot picture = picture(service);
        assertEquals(SilentColonyCommandSnapshot.Role.SURVIVOR_ESCORT,
                picture.intentFor(strandedArchive.id).role());
        assertEquals(SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY,
                picture.intentFor(reachableReplacement.id).role());
        assertEquals(SilentColonyCommandSnapshot.Reason
                        .ARCHIVE_BRANCH_LOSS_REBALANCE,
                picture.intentFor(reachableReplacement.id).membershipReason());
        assertEquals(SilentColonyCommandSnapshot.Role.SURVIVOR_ESCORT,
                picture.intentFor(survivor.id).role());
        assertEquals(1, picture.archiveBranchSquads());
        assertEquals(2, picture.survivorBranchSquads());
    }

    @Test
    void recoveredArchiveBranchRejoinsTheSurvivors() {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        Squad survivors = squad(fixture.sim, 7, 7, 4);
        CommanderService service = service(fixture);
        claim(service, archive);
        claim(service, survivors);
        pulse(service, fixture.sim);
        completeArchive(fixture);

        pulse(service, fixture.sim);

        SilentColonyCommandSnapshot picture = picture(service);
        assertEquals(SilentColonyCommandSnapshot.Phase.SURVIVOR_ESCORT_ONLY,
                picture.phase());
        assertEquals(SilentColonyCommandSnapshot.Role.SURVIVOR_ESCORT,
                picture.intentFor(archive.id).role());
        assertEquals(SilentColonyCommandSnapshot.Reason.ARCHIVE_COMPLETE_REJOIN,
                picture.intentFor(archive.id).membershipReason());
        assertEquals(2, picture.survivorBranchSquads());
    }

    @Test
    void exhaustedSurvivorBranchReinforcesTheArchive() {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        Squad survivors = squad(fixture.sim, 7, 7, 4);
        CommanderService service = service(fixture);
        claim(service, archive);
        claim(service, survivors);
        pulse(service, fixture.sim);
        completeSurvivors(fixture);

        pulse(service, fixture.sim);

        SilentColonyCommandSnapshot picture = picture(service);
        assertEquals(SilentColonyCommandSnapshot.Phase.ARCHIVE_RECOVERY_ONLY,
                picture.phase());
        assertEquals(SilentColonyCommandSnapshot.Role.ARCHIVE_RECOVERY,
                picture.intentFor(survivors.id).role());
        assertEquals(
                SilentColonyCommandSnapshot.Reason.SURVIVORS_GONE_REINFORCE_ARCHIVE,
                picture.intentFor(survivors.id).membershipReason());
        assertEquals(2, picture.archiveBranchSquads());
    }

    @Test
    void terminalExpeditionReleasesOnlyItsOwnedSquads() {
        Fixture fixture = fixture();
        Squad owned = squad(fixture.sim, 31, 20, 6);
        Squad external = squad(fixture.sim, 7, 7, 4);
        CommanderService service = service(fixture);
        claim(service, owned);
        ObjectiveAssignment externalOrder = ObjectiveAssignment.escort(
                external.id, 5, 5);
        service.assignments().assignExternal(external, externalOrder,
                CommandAuthority.PAYLOAD, "survivor-payload", "external", 0);
        pulse(service, fixture.sim);
        completeArchive(fixture);
        completeSurvivors(fixture);

        pulse(service, fixture.sim);

        assertNull(owned.assignedObjective);
        assertEquals(externalOrder, external.assignedObjective);
        assertEquals(SilentColonyCommandSnapshot.Role.RELEASED,
                picture(service).intentFor(owned.id).role());
        assertEquals(SilentColonyCommandSnapshot.Role.EXTERNAL,
                picture(service).intentFor(external.id).role());
    }

    @Test
    void activePlayerLeaseIsRetainedAndExpiryRestoresBranchCommand() {
        Fixture fixture = fixture();
        Squad marine = squad(fixture.sim, 31, 20, 6);
        CommanderService service = service(fixture);
        ObjectiveAssignment intervention = ObjectiveAssignment.defendTrack(
                marine.id, 4, 4);
        service.assignments().assignExternal(marine, intervention,
                CommandAuthority.PLAYER_INTERVENTION, "player-intervention",
                "hold", 0, 1);

        pulse(service, fixture.sim);

        assertEquals(intervention, marine.assignedObjective);
        assertEquals(SilentColonyCommandSnapshot.Role.EXTERNAL,
                picture(service).intentFor(marine.id).role());

        fixture.sim.simTickIndex = 2;
        pulse(service, fixture.sim);

        assertNotNull(marine.assignedObjective);
        assertEquals(SilentColonyCommand.ISSUER,
                service.activeDirective(marine.id).issuer());
    }

    @Test
    void terminalExpiredPlayerLeaseIsReportedAsRetainedExternalCommand() {
        Fixture fixture = fixture();
        Squad marine = squad(fixture.sim, 31, 20, 6);
        CommanderService service = service(fixture);
        claim(service, marine);
        pulse(service, fixture.sim);
        ObjectiveAssignment intervention = ObjectiveAssignment.defendTrack(
                marine.id, 4, 4);
        service.assignments().assignExternal(marine, intervention,
                CommandAuthority.PLAYER_INTERVENTION, "player-intervention",
                "hold", 0, 1);
        completeArchive(fixture);
        completeSurvivors(fixture);
        fixture.sim.simTickIndex = 2;

        pulse(service, fixture.sim);

        assertEquals(intervention, marine.assignedObjective);
        assertEquals("player-intervention",
                service.activeDirective(marine.id).issuer());
        SilentColonyCommandSnapshot.SquadIntent intent =
                picture(service).intentFor(marine.id);
        assertEquals(SilentColonyCommandSnapshot.Role.EXTERNAL,
                intent.role());
        assertEquals(AssignmentKind.DEFEND_TRACK, intent.assignmentKind());
        assertEquals(4, intent.targetCellX());
        assertEquals(4, intent.targetCellY());
    }

    @Test
    void unseenDefenderLocationDoesNotChangeTheExpeditionPlan() {
        assertEquals(planSignature(-1), planSignature(36));
    }

    private static Map<Integer, String> planSignature(int defenderX) {
        Fixture fixture = fixture();
        Squad archive = squad(fixture.sim, 31, 20, 6);
        Squad survivors = squad(fixture.sim, 7, 7, 4);
        if (defenderX >= 0) {
            fixture.sim.spawn(new EntitySpec("hidden-defense",
                    Faction.DEFENDER, UnitType.TURRET, defenderX, 28));
        }
        CommanderService service = service(fixture);
        claim(service, archive);
        claim(service, survivors);
        pulse(service, fixture.sim);
        Map<Integer, String> signature = new LinkedHashMap<>();
        for (SilentColonyCommandSnapshot.SquadIntent intent
                : picture(service).squadIntents()) {
            signature.put(intent.squadId(), intent.role() + ":"
                    + intent.assignmentKind() + ":" + intent.targetCellX()
                    + ":" + intent.targetCellY());
        }
        return signature;
    }

    private static CommanderService service(Fixture fixture) {
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE,
                new SilentColonyCommand(),
                new SilentColonyCommandDisclosure(fixture.payload.placement));
        return service;
    }

    private static void pulse(CommanderService service,
                              BattleSimulation sim) {
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
    }

    private static void claim(CommanderService service, Squad squad) {
        service.assignments().claimExternal(squad,
                CommandAuthority.MISSION_COMMAND, SilentColonyCommand.ISSUER,
                "Silent Colony expedition force", 0);
    }

    private static SilentColonyCommandSnapshot picture(
            CommanderService service) {
        CommanderSnapshot<?> snapshot = service.snapshot(Faction.MARINE);
        assertNotNull(snapshot);
        return (SilentColonyCommandSnapshot) snapshot.detail();
    }

    private static Map<Integer, SilentColonyCommandSnapshot.Role> roles(
            SilentColonyCommandSnapshot picture) {
        Map<Integer, SilentColonyCommandSnapshot.Role> roles =
                new LinkedHashMap<>();
        for (SilentColonyCommandSnapshot.SquadIntent intent
                : picture.squadIntents()) {
            roles.put(intent.squadId(), intent.role());
        }
        return roles;
    }

    private static void completeArchive(Fixture fixture) {
        fixture.sim.spawn(new EntitySpec("archive-recovery-test",
                Faction.MARINE, UnitType.MARINE, 32, 20));
        for (int i = 0; i < 160; i++) fixture.archive.tick(fixture.sim);
    }

    private static void completeSurvivors(Fixture fixture) {
        for (int i = 0; i < fixture.payload.size(); i++) {
            fixture.sim.getCivilianEvacuationTracker().markEvacuated(
                    fixture.payload.entityId(i));
        }
        fixture.payload.objective.tick(fixture.sim);
    }

    private static void blockVerticalBoundary(NavigationGrid grid,
                                              int leftX) {
        for (int y = 0; y < grid.getHeight(); y++) {
            grid.setEdgePassable(leftX, y, Direction.E, false);
            grid.setEdgePassable(leftX + 1, y, Direction.W, false);
        }
    }

    private static Fixture fixture() {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(40, 30));
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 41L);
        assertNotNull(payload);
        int archiveZone = sim.getZoneGraph().zoneIdAt(32, 20);
        ColonyArchiveObjective archive = new ColonyArchiveObjective(
                32, 20, archiveZone);
        sim.addObjective(archive);
        return new Fixture(sim, payload, archive);
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                9, 5, 13, 9, 8, 7, 11, 7);
    }

    private static Squad squad(BattleSimulation sim, int x, int y,
                               int strength) {
        long leader = sim.spawn(new EntitySpec("marine-" + x,
                Faction.MARINE, UnitType.MARINE, x, y));
        int squadId = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = strength;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }

    private record Fixture(
            BattleSimulation sim,
            CivilianEvacuationPayload payload,
            ColonyArchiveObjective archive) { }
}
