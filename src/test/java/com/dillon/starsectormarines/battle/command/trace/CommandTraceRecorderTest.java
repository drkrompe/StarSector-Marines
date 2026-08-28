package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultSearchSnapshot;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPayload;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTraceRecorderTest {

    @Test
    void perspectiveRowsAreCanonicalSortedAndDeduplicated() {
        CommandDirective later = directive(9, "later");
        CommandDirective earlier = directive(2, "quote \" and slash \\");
        CommanderSnapshot<Void> snapshot = new CommanderSnapshot<>(
                Faction.MARINE, "conquest", "LANE_ADVANCE", 75, 60,
                2, 0, List.of("remaining compounds=3"),
                List.of(later, earlier), null);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "CONQUEST", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);
        recorder.recordPerspective(snapshot);

        List<String> lines = recorder.canonicalJsonLines().lines().toList();
        assertEquals(2, lines.size());
        assertEquals("{\"stream\":\"run\",\"tick\":0,\"schemaVersion\":4,"
                + "\"fixtureKind\":\"CONQUEST\","
                + "\"schedulerMode\":\"SERIAL_DETERMINISTIC\"}", lines.get(0));
        String line = lines.get(1);
        assertEquals("{\"stream\":\"perspective\",\"tick\":75,"
                        + "\"observedTick\":75,"
                        + "\"perspective\":\"MARINE\",\"strategy\":\"conquest\","
                        + "\"phase\":\"LANE_ADVANCE\",\"influenceTick\":60,"
                        + "\"commandPoolSize\":2,\"reserveCount\":0,"
                        + "\"objectives\":[\"remaining compounds=3\"],"
                        + "\"directives\":["
                        + directiveJson(2, "quote \\\" and slash \\\\" ) + ","
                        + directiveJson(9, "later") + "]}", line);
        assertTrue(recorder.canonicalJsonLines().endsWith(line + "\n"));
    }

    @Test
    void eachPerspectiveHasItsOwnDeduplicationCursor() {
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                null, "TEST", 12);
        recorder.recordPerspective(snapshot(Faction.DEFENDER, 75));
        recorder.recordPerspective(snapshot(Faction.MARINE, 75));
        recorder.recordPerspective(snapshot(Faction.DEFENDER, 75));

        List<String> lines = recorder.canonicalJsonLines().lines().toList();
        assertEquals(3, lines.size());
        assertTrue(lines.get(1)
                .contains("\"perspective\":\"DEFENDER\""));
        assertTrue(lines.get(2)
                .contains("\"perspective\":\"MARINE\""));
    }

    @Test
    void assaultDetailSerializesSectorsAndActionsInCanonicalOrder() {
        AssaultSearchSnapshot.SectorState later =
                new AssaultSearchSnapshot.SectorState(2, 20, 10, 10, 8,
                        AssaultSearchSnapshot.SectorStatus.ACTIVE,
                        3, 9, 2, 70, 1, 24, 13);
        AssaultSearchSnapshot.SectorState earlier =
                new AssaultSearchSnapshot.SectorState(0, 0, 0, 10, 8,
                        AssaultSearchSnapshot.SectorStatus.SEARCHING,
                        1, 9, 0, -1, 1, 4, 3);
        AssaultSearchSnapshot.SquadDirective action =
                new AssaultSearchSnapshot.SquadDirective(7, 2,
                        AssaultSearchSnapshot.AssignmentReason
                                .ACTIVE_CONTACT_REINFORCEMENT,
                        AssignmentKind.SWEEP_SECTOR, 24, 13);
        AssaultSearchSnapshot detail = new AssaultSearchSnapshot(75, 70,
                Faction.MARINE, AssaultSearchSnapshot.Phase.CONVERGE, 1,
                List.of(later, earlier), List.of(), List.of(action));
        CommanderSnapshot<AssaultSearchSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.MARINE, "assault-attacker",
                        "CONVERGE", 75, 70, 1, 0, List.of(), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "ASSAULT", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);
        String row = recorder.canonicalJsonLines().lines().toList().get(1);

        assertTrue(row.contains("\"assault\":{\"phase\":\"CONVERGE\""));
        assertTrue(row.indexOf("\"index\":0") < row.indexOf("\"index\":2"));
        assertTrue(row.contains("\"reason\":\"ACTIVE_CONTACT_REINFORCEMENT\""));
        assertTrue(row.contains("\"believedContacts\":2"));
    }

    @Test
    void assaultDefenderTracePublishesReportsStrongpointsAndActions() {
        AssaultDefenseSnapshot.AreaState area =
                new AssaultDefenseSnapshot.AreaState(1, 15, 0, 15, 15,
                        80, 1, 1, 1, 1,
                        AssaultDefenseSnapshot.ReportState.SUSPECTED,
                        2, 40, 490, 4f, 3f, 20, 7);
        AssaultDefenseSnapshot.StrongpointState point =
                new AssaultDefenseSnapshot.StrongpointState(0, "GATE", 1,
                        21, 7, 20, 7, 3, 80);
        AssaultDefenseSnapshot.SquadDirective action =
                new AssaultDefenseSnapshot.SquadDirective(9, 1,
                        AssaultDefenseSnapshot.Role.RESPONDER,
                        AssaultDefenseSnapshot.Reason
                                .SUSPECTED_CONTACT_RESPONSE,
                        AssignmentKind.DEFEND_AREA, 20, 7);
        AssaultDefenseSnapshot detail = new AssaultDefenseSnapshot(75, 60,
                Faction.DEFENDER,
                AssaultDefenseSnapshot.Phase.REPORTED_CONTACT_RESPONSE,
                4, 1, List.of(area), List.of(point), List.of(),
                List.of(action));
        CommanderSnapshot<AssaultDefenseSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.DEFENDER,
                        "assault-defender", "REPORTED_CONTACT_RESPONSE",
                        75, 60, 4, 1, List.of(), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "ASSAULT", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"assaultDefense\":{"));
        assertTrue(line.contains("\"reportState\":\"SUSPECTED\""));
        assertTrue(line.contains("\"reportExpiresTick\":490"));
        assertTrue(line.contains("\"kind\":\"GATE\""));
        assertTrue(line.contains("\"role\":\"RESPONDER\""));
    }

    @Test
    void conquestTracePublishesDistantCaptureDeferralSeparatelyFromOrderReason() {
        ConquestFrontSnapshot.SquadDirective action =
                new ConquestFrontSnapshot.SquadDirective(9, 2, 1,
                        ConquestFrontSnapshot.AssignmentReason.TRACK_ADVANCE,
                        AssignmentKind.CLEAR_ZONE, 17)
                        .withDistantCaptureDeferred();
        ConquestFrontSnapshot detail = new ConquestFrontSnapshot(75, 60,
                TraversalAxis.SOUTH_TO_NORTH,
                ConquestFrontSnapshot.Phase.LANE_ADVANCE, 2, 25,
                CompoundService.CompoundState.DEFENDER_HELD,
                List.of(), List.of(action));
        CommanderSnapshot<ConquestFrontSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.MARINE, "conquest",
                        "LANE_ADVANCE", 75, 60, 2, 0,
                        List.of("remaining compounds=2"), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "CONQUEST", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"reason\":\"TRACK_ADVANCE\""));
        assertTrue(line.contains("\"distantCaptureDeferred\":true"));
    }

    @Test
    void sabotageTracePublishesStableSiteIdentityAndGroupReason() {
        SabotageSiteSnapshot.SiteState site =
                new SabotageSiteSnapshot.SiteState(0, "SAB-01", "reactor",
                        12, 7, 3, 2f, 8f, true, false,
                        0, 0, SabotageSiteSnapshot.GroupReason.PLANTER_ACTIVE,
                        1, 0, 2, 12, 4f, 3f);
        SabotageSiteSnapshot.SquadDirective action =
                new SabotageSiteSnapshot.SquadDirective(9, 0,
                        SabotageSiteSnapshot.GroupRole.SECURITY,
                        SabotageSiteSnapshot.AssignmentReason.SITE_SECURITY_PRESERVED,
                        AssignmentKind.CLEAR_ZONE, 3, 12, 7);
        SabotageSiteSnapshot detail = new SabotageSiteSnapshot(75, 60,
                Faction.MARINE, SabotageSiteSnapshot.Phase.PLANTING,
                List.of(site), List.of(), List.of(action));
        CommanderSnapshot<SabotageSiteSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.MARINE, "sabotage-attacker",
                        "PLANTING", 75, 60, 1, 0, List.of(), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "SABOTAGE", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"sabotage\":{"));
        assertTrue(line.contains("\"id\":\"SAB-01\""));
        assertTrue(line.contains("\"groupReason\":\"PLANTER_ACTIVE\""));
        assertTrue(line.contains("\"groupRole\":\"SECURITY\""));
        assertTrue(line.contains("\"reason\":\"SITE_SECURITY_PRESERVED\""));
    }

    @Test
    void authoritativeChargeProgressIsLabelledAsRefereeEvidence() {
        BattleSimulation sim = new BattleSimulation(new NavigationGrid(8, 8),
                new CellTopology(8, 8));
        sim.addObjective(new ChargeSiteObjective(
                4, 4, 8f, "SAB-01", "reactor"));
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "SABOTAGE", "SERIAL_DETERMINISTIC", 0);

        recorder.sample(sim);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"stream\":\"referee\""));
        assertTrue(line.contains("\"event\":\"charge-site-state\""));
        assertTrue(line.contains("\"siteId\":\"SAB-01\""));
    }

    @Test
    void defenderSabotageTracePublishesAlarmWithoutAttackerTaskFacts() {
        SabotageDefenseSnapshot.SiteState site =
                new SabotageDefenseSnapshot.SiteState(0, "SAB-01", "reactor",
                        12, 7, 3, false, true, 70, 520,
                        1, 1, 10, 4f, 3f);
        SabotageDefenseSnapshot.SquadDirective action =
                new SabotageDefenseSnapshot.SquadDirective(9, 0,
                        SabotageDefenseSnapshot.Role.ALARM_RESPONDER,
                        SabotageDefenseSnapshot.Reason.SITE_ALARM_RESPONSE,
                        AssignmentKind.DEFEND_SITE, 12, 7);
        SabotageDefenseSnapshot detail = new SabotageDefenseSnapshot(75, 60,
                Faction.DEFENDER,
                SabotageDefenseSnapshot.Phase.ALARM_RESPONSE,
                4, 0, List.of(site), List.of(action));
        CommanderSnapshot<SabotageDefenseSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.DEFENDER,
                        "sabotage-defender", "ALARM_RESPONSE", 75, 60,
                        4, 0, List.of("reactor=alarm"), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "SABOTAGE", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"sabotageDefense\":{"));
        assertTrue(line.contains("\"alarmActive\":true"));
        assertTrue(line.contains("\"role\":\"ALARM_RESPONDER\""));
        assertTrue(!line.contains("\"progress\""));
        assertTrue(!line.contains("\"planterOnSite\""));
        assertTrue(!line.contains("\"activeKitDrops\""));
    }

    @Test
    void raidTracePublishesMissionPhaseAndCanonicalSquadIntents() {
        RaidCommandSnapshot detail = new RaidCommandSnapshot(75,
                Faction.MARINE, "EGRESS", "RAID-01", "depot",
                30, 20, 4, 5, 6, 6f, 6f, true,
                false, -1, List.of(
                new RaidCommandSnapshot.SquadIntent(9, "WITHDRAWAL",
                        "TARGET_SECURED_WITHDRAW", AssignmentKind.WITHDRAW,
                        6, 6),
                new RaidCommandSnapshot.SquadIntent(2, "WITHDRAWAL",
                        "TARGET_SECURED_WITHDRAW", AssignmentKind.WITHDRAW,
                        4, 6)));
        CommanderSnapshot<RaidCommandSnapshot> snapshot =
                new CommanderSnapshot<>(Faction.MARINE, "raid-attacker",
                        "EGRESS", 75, 60, 2, 0, List.of(), List.of(), detail);
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "RAID", "SERIAL_DETERMINISTIC", 0);

        recorder.recordPerspective(snapshot);

        String line = recorder.canonicalJsonLines().lines().toList().get(1);
        assertTrue(line.contains("\"raid\":{\"phase\":\"EGRESS\""));
        assertTrue(line.contains("\"targetId\":\"RAID-01\""));
        assertTrue(line.contains("\"assignmentKind\":\"WITHDRAW\""));
        assertTrue(line.indexOf("\"squadId\":2")
                < line.indexOf("\"squadId\":9"));
    }

    @Test
    void controlCasualtyAndTimeoutRowsStayNeutralAndCanonical() {
        CommandTraceRecorder recorder = new CommandTraceRecorder(
                "CONQUEST", "SERIAL_DETERMINISTIC", 0);

        recorder.recordCapturePaused(10);
        recorder.recordCaptureResumed(20);
        recorder.recordCasualty(21, 9_001L, Faction.MARINE,
                UnitType.MARINE, 7, 8);
        recorder.recordTimeout(30, 30);
        recorder.recordTimeout(31, 30);
        recorder.recordCapturePaused(32);
        recorder.recordPerspective(snapshot(Faction.MARINE, 75));

        List<String> lines = recorder.canonicalJsonLines().lines().toList();
        assertEquals(5, lines.size());
        assertEquals("{\"stream\":\"control\",\"tick\":10,"
                + "\"event\":\"capture-paused\"}", lines.get(1));
        assertEquals("{\"stream\":\"control\",\"tick\":20,"
                + "\"event\":\"capture-resumed\"}", lines.get(2));
        assertEquals("{\"stream\":\"referee\",\"tick\":21,"
                + "\"event\":\"casualty\",\"unitId\":9001,"
                + "\"faction\":\"MARINE\",\"unitType\":\"MARINE\","
                + "\"combatant\":true,\"cellX\":7,\"cellY\":8}",
                lines.get(3));
        assertEquals("{\"stream\":\"referee\",\"tick\":30,"
                + "\"event\":\"timeout\",\"maxTicks\":30}", lines.get(4));
        assertTrue(recorder.isSealed());
    }

    @Test
    void timedOutSimulationCannotReenableItsSealedTrace() {
        try (BattleSimulation sim = new BattleSimulation(
                new NavigationGrid(4, 4), new CellTopology(4, 4))) {
            sim.setCommandTraceEnabled(true, "CONQUEST");
            sim.recordCommandTraceTimeout(1);
            String sealed = sim.getCommandTraceJsonLines();

            sim.setCommandTraceEnabled(true, "CONQUEST");

            assertEquals(sealed, sim.getCommandTraceJsonLines());
            assertTrue(!sim.isCommandTraceEnabled());
        }
    }

    @Test
    void rescueTraceSeparatesMarineCommandFromSwarmDirector() {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(40, 30))) {
            CivilianEvacuationPayload payload =
                    CivilianEvacuationPayload.install(sim,
                            List.of(new PointOfInterest(
                                    PointOfInterest.Kind.RESIDENTIAL,
                                    9, 5, 13, 9, 8, 7, 11, 7)), 91L);
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 2, 2).squad(squadId));
            sim.getSquad(squadId).leaderId = marine;
            sim.spawn(new EntitySpec("runner", Faction.DEFENDER,
                    UnitType.SWARM_RUNNER, 39, 29)
                    .role(UnitRole.SWARM_PRESSURE));
            assertTrue(sim.configureSwarmReinforcements(
                    payload.placement, 1, 91L));
            sim.setCommandTraceEnabled(true, "CIVILIAN_RESCUE");

            sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                    + BattleSimulation.TICK_DT);

            String trace = sim.getCommandTraceJsonLines();
            assertTrue(trace.contains("\"strategy\":\"rescue-corridor\""));
            assertTrue(trace.contains("\"rescue\":{"));
            assertTrue(trace.contains("\"stream\":\"director\""));
            assertTrue(trace.contains("\"perspective\":\"SWARM\""));
            assertTrue(trace.contains(
                    "\"director\":\"rescue-swarm-pressure\""));
            for (String line : trace.lines()
                    .filter(row -> row.contains(
                            "\"director\":\"rescue-swarm-pressure\""))
                    .toList()) {
                assertTrue(!line.contains("controllingSquadId")
                                && !line.contains("cohortCellX")
                                && !line.contains("marineCellX"),
                        "swarm picture may not disclose hidden corridor truth");
            }
        }
    }

    private static CommanderSnapshot<Void> snapshot(Faction side, int tick) {
        return new CommanderSnapshot<>(side, "conquest", "phase", tick, tick,
                0, 0, List.of(), List.of(), null);
    }

    private static CommandDirective directive(int squadId, String reason) {
        return new CommandDirective(squadId, Faction.MARINE, "conquest",
                CommandAuthority.MISSION_COMMAND, reason,
                ObjectiveAssignment.clearZone(squadId, 4), 75, 150, 225,
                CommandDirective.Status.ACTIVE, "committed");
    }

    private static String directiveJson(int squadId, String escapedReason) {
        return "{\"squadId\":" + squadId
                + ",\"issuer\":\"conquest\",\"authority\":\"MISSION_COMMAND\""
                + ",\"status\":\"ACTIVE\",\"reason\":\"" + escapedReason + "\""
                + ",\"disposition\":\"committed\",\"issuedTick\":75"
                + ",\"stableUntilTick\":150,\"leaseUntilTick\":225"
                + ",\"assignment\":{\"kind\":\"CLEAR_ZONE\",\"targetZoneId\":4"
                + ",\"targetNode\":null,\"objectiveId\":-1"
                + ",\"targetCellX\":-1,\"targetCellY\":-1}}";
    }
}
