package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Analysis;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Termination;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTraceAnalyzerTest {

    @Test
    void derivesCommandTerritoryCasualtyAndResponseMetrics() throws Exception {
        String trace = String.join("\n",
                header(),
                compound(0, "ARMORY@4,4", "ARMORY", "DEFENDER_HELD"),
                compound(0, "BARRACKS@8,8", "BARRACKS", "DEFENDER_HELD"),
                compound(0, "COMMAND_POST@12,12", "COMMAND_POST",
                        "DEFENDER_HELD"),
                perspective(75, "MARINE", 0,
                        directive("ACTIVE", 75, 1),
                        action("TRACK_ADVANCE", 0), tracks(0, 8, 4, 0)),
                perspective(75, "DEFENDER", 2,
                        directive("ACTIVE", 75, 1),
                        action("DEFENDER_RESERVE_HOLD", 0), tracks(0, 6, 6, 0)),
                perspective(150, "MARINE", 0,
                        directive("RETAINED", 75, 1),
                        action("NO_REACHABLE_COMPOUND_TARGET", 0),
                        tracks(0, 8, 4, 0)),
                perspective(150, "DEFENDER", 1,
                        directive("ACTIVE", 75, 1),
                        action("DEFENDER_TRACK_RESPONSE", 0), tracks(2, 6, 6, 0)),
                casualty(175, 9001, "MARINE", true),
                compound(200, "ARMORY@4,4", "ARMORY", "MARINE_HELD"),
                perspective(225, "MARINE", 0,
                        directive("ACTIVE", 225, 2),
                        action("NO_ACTIONABLE_TRACK_TARGET", 0),
                        tracks(0, 4, 4, 4)),
                perspective(225, "DEFENDER", 0,
                        directive("ACTIVE", 75, 1),
                        action("DEFENDER_LOCAL_CONTACT", 0), tracks(0, 6, 6, 0)),
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"terminal\",\"winner\":\"MARINE\"}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(Termination.TERMINAL, analysis.run().termination());
        assertTrue(analysis.run().fullBattle());
        assertEquals("MARINE", analysis.run().winner());
        assertEquals(1, analysis.run().combatantCasualties()
                .get(Faction.MARINE));
        assertEquals(1, analysis.factions().get(Faction.MARINE).retargets());
        assertEquals(1,
                analysis.factions().get(Faction.MARINE).stabilityHolds());
        assertEquals(2, analysis.factions().get(Faction.MARINE)
                .unassignedSquadPulses());
        assertEquals(150, analysis.factions().get(Faction.MARINE)
                .unassignedSquadTicks());
        assertEquals(2, analysis.factions().get(Faction.MARINE)
                .commandInactivity().unclassifiedSquadPulses());
        assertEquals(0, analysis.factions().get(Faction.MARINE)
                .commandInactivity().genuineIdleSquadPulses());
        assertEquals(6_667, analysis.factions().get(Faction.MARINE)
                .peakPublishedTrackShareBasisPoints());
        assertEquals(225L, analysis.factions().get(Faction.DEFENDER)
                .reserveSquadTicks());
        assertEquals(List.of(0), analysis.factions().get(Faction.DEFENDER)
                .publishedMobilizationLatenciesTicks());
        assertEquals(3, analysis.conquest().compoundCount());
        assertEquals(1, analysis.conquest().captures());
        assertEquals(1, analysis.conquest().finalMarineHeld());
        assertEquals(200, analysis.conquest().longestObservedCaptureGapTicks());
        assertEquals(analysis.canonicalJson(),
                CommandTraceAnalyzer.analyze(trace).canonicalJson());
    }

    @Test
    void classifiesCommandUnassignedCausesWithStrictPrecedence()
            throws Exception {
        String trace = String.join("\n",
                header().replace("\"schemaVersion\":5",
                        "\"schemaVersion\":6"),
                commandInactivityPerspective(0),
                commandInactivityPerspective(75),
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}", "");

        var metrics = CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE);
        var inactivity = metrics.commandInactivity();

        assertEquals(5, metrics.unassignedSquadPulses());
        assertEquals(750, metrics.unassignedSquadTicks());
        assertEquals(1, inactivity.lifecycleSquadPulses());
        assertEquals(150, inactivity.lifecycleSquadTicks());
        assertEquals(1, inactivity.executionSuspendedSquadPulses());
        assertEquals(150, inactivity.executionSuspendedSquadTicks());
        assertEquals(1, inactivity.localContactSquadPulses());
        assertEquals(150, inactivity.localContactSquadTicks());
        assertEquals(1, inactivity.usefulMovementSquadPulses());
        assertEquals(150, inactivity.usefulMovementSquadTicks());
        assertEquals(1, inactivity.genuineIdleSquadPulses());
        assertEquals(150, inactivity.genuineIdleSquadTicks());
        assertEquals(0, inactivity.unclassifiedSquadPulses());
        assertEquals(0, inactivity.unclassifiedSquadTicks());
    }

    @Test
    void timeoutIsNotInventedAsACompletedBattle() throws Exception {
        String trace = header() + "\n"
                + compound(0, "COMMAND_POST@1,1", "COMMAND_POST",
                        "DEFENDER_HELD") + "\n"
                + "{\"stream\":\"referee\",\"tick\":600,"
                + "\"event\":\"timeout\",\"maxTicks\":600}\n";

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(Termination.TIMEOUT, analysis.run().termination());
        assertFalse(analysis.run().fullBattle());
        assertEquals(null, analysis.run().winner());
        assertEquals(600, analysis.run().durationTicks());
        assertTrue(analysis.conquest().territorialProgressStalled());
    }

    @Test
    void captureGapCensorsAssignmentAndCompoundChanges() throws Exception {
        String trace = String.join("\n",
                header(),
                compound(0, "COMMAND_POST@1,1", "COMMAND_POST",
                        "DEFENDER_HELD"),
                perspective(75, "MARINE", 0,
                        directive("ACTIVE", 75, 1),
                        action("TRACK_ADVANCE", 0), tracks(0, 8, 0, 0)),
                "{\"stream\":\"control\",\"tick\":100,"
                        + "\"event\":\"capture-paused\"}",
                "{\"stream\":\"control\",\"tick\":200,"
                        + "\"event\":\"capture-resumed\"}",
                compound(200, "COMMAND_POST@1,1", "COMMAND_POST",
                        "MARINE_HELD"),
                perspective(75, 200, "MARINE", 0,
                        directive("REJECTED", 225, 2),
                        action("NO_ACTIONABLE_TRACK_TARGET", 0),
                        tracks(0, 8, 0, 0)),
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"terminal\",\"winner\":\"MARINE\"}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(0, analysis.factions().get(Faction.MARINE).retargets());
        assertEquals(0, analysis.factions().get(Faction.MARINE)
                .rejectedProposals());
        assertEquals(0, analysis.factions().get(Faction.MARINE)
                .unassignedSquadPulses());
        assertEquals(100, analysis.factions().get(Faction.MARINE)
                .unassignedSquadTicks());
        assertEquals(0, analysis.conquest().captures());
        assertEquals(1, analysis.conquest().finalMarineHeld());
        assertEquals(100, analysis.conquest().longestObservedCaptureGapTicks());
        assertFalse(analysis.conquest().territorialProgressStalled());
        assertEquals(2, analysis.run().observationWindows());
        assertFalse(analysis.run().fullBattle());
    }

    @Test
    void terminalCountsAnObservedThreatThatNeverMobilized() throws Exception {
        String trace = String.join("\n",
                header(),
                perspective(75, "DEFENDER", 1,
                        directive("ACTIVE", 75, 1),
                        action("DEFENDER_RESERVE_HOLD", 0),
                        tracks(0, 6, 6, 0)),
                perspective(150, "DEFENDER", 1,
                        directive("RETAINED", 75, 1),
                        action("DEFENDER_RESERVE_HOLD", 0),
                        tracks(3, 6, 6, 0)),
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"terminal\",\"winner\":\"MARINE\"}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(1, analysis.factions().get(Faction.DEFENDER)
                .unmobilizedThreatEpisodes());
    }

    @Test
    void firstRealPulseAfterCaptureStartCountsAsAnEvent() throws Exception {
        String trace = String.join("\n",
                header(),
                perspective(75, "MARINE", 0,
                        directive("REJECTED", 75, 1),
                        action("NO_ACTIONABLE_TRACK_TARGET", 0),
                        tracks(0, 4, 0, 0)),
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(1, analysis.factions().get(Faction.MARINE)
                .rejectedProposals());
        assertEquals(1, analysis.factions().get(Faction.MARINE)
                .noActionableSquadPulses());
    }

    @Test
    void countsPublishedDistantCaptureDeferrals() throws Exception {
        String trace = String.join("\n",
                header(),
                perspective(75, "MARINE", 0,
                        directive("ACTIVE", 75, 1),
                        action("TRACK_ADVANCE", 0, true),
                        tracks(1, 4, 0, 0)),
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(1, analysis.factions().get(Faction.MARINE)
                .distantCaptureDeferredSquadPulses());
    }

    @Test
    void terminalAfterEmptyResumedWindowDoesNotUncensorOldThreat()
            throws Exception {
        String trace = String.join("\n",
                header(),
                perspective(75, "DEFENDER", 1,
                        directive("ACTIVE", 75, 1),
                        action("DEFENDER_RESERVE_HOLD", 0),
                        tracks(0, 6, 6, 0)),
                perspective(150, "DEFENDER", 1,
                        directive("RETAINED", 75, 1),
                        action("DEFENDER_RESERVE_HOLD", 0),
                        tracks(3, 6, 6, 0)),
                "{\"stream\":\"control\",\"tick\":200,"
                        + "\"event\":\"capture-paused\"}",
                "{\"stream\":\"control\",\"tick\":250,"
                        + "\"event\":\"capture-resumed\"}",
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"terminal\",\"winner\":\"MARINE\"}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(0, analysis.factions().get(Faction.DEFENDER)
                .unmobilizedThreatEpisodes());
    }

    @Test
    void incompleteLiveTraceDoesNotClaimTerritorialStall() throws Exception {
        String trace = String.join("\n",
                header(),
                compound(0, "COMMAND_POST@1,1", "COMMAND_POST",
                        "DEFENDER_HELD"),
                perspective(75, "MARINE", 0,
                        directive("ACTIVE", 75, 1),
                        action("TRACK_ADVANCE", 0), tracks(0, 4, 0, 0)),
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(Termination.INCOMPLETE, analysis.run().termination());
        assertFalse(analysis.conquest().territorialProgressStalled());
    }

    @Test
    void measuresMarkerClosureTargetZoneArrivalAndCompoundPresence()
            throws Exception {
        String trace = String.join("\n",
                header(),
                compound(0, "COMMAND_POST@20,20", "COMMAND_POST",
                        "DEFENDER_HELD"),
                presence(0, "COMMAND_POST@20,20", "DEFENDER_ONLY", 0, 4, 0),
                physicalPerspective(75, 10f, 10f, 1, false,
                        "COMPOUND_ASSAULT_ADJACENT"),
                presence(100, "COMMAND_POST@20,20", "MIXED", 2, 4, 0),
                physicalPerspective(150, 14f, 14f, 1, false,
                        "COMPOUND_CAPTURE_PRESERVED"),
                presence(180, "COMMAND_POST@20,20", "MARINE_ONLY", 3, 0,
                        2_500),
                physicalPerspective(225, 20.5f, 20.5f, 5, true,
                        "COMPOUND_CAPTURE_PRESERVED"),
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"timeout\",\"maxTicks\":300}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);
        var physical = analysis.factions().get(Faction.MARINE)
                .physicalProgress();

        assertEquals(1, physical.movementEpisodes());
        assertEquals(1, physical.maximumConcurrentAliveSquads());
        assertEquals(4, physical.maximumConcurrentAliveMembers());
        assertEquals(1, physical.episodesWithMarkerClosure());
        assertEquals(1, physical.episodesObservedInTargetZone());
        assertEquals(1, physical.compoundAssaultThresholdCommitments());
        assertEquals(1, physical.secureCompoundEpisodes());
        assertEquals(1,
                physical.secureCompoundEpisodesObservedInTargetZone());
        assertEquals(150L, physical.comparableTravelSquadTicks());
        assertEquals(150L, physical.markerClosingSquadTicks());
        assertEquals(75L, physical.targetZoneSquadTicks());
        assertEquals(List.of(150), physical.targetZoneEntryLatenciesTicks());

        var presence = analysis.conquest().physicalPresence();
        assertEquals(1, presence.compoundsWithMarinePresence());
        assertEquals(300L, presence.observedCompoundTicks());
        assertEquals(200L, presence.marinePresentCompoundTicks());
        assertEquals(120L, presence.marineOnlyCompoundTicks());
        assertEquals(80L, presence.mixedCompoundTicks());
        assertEquals(100L, presence.defenderOnlyCompoundTicks());
        assertEquals(120, presence.longestMarineOnlyPresenceRunTicks());
        assertEquals(3, presence.maximumMarineUnits());
        assertEquals(2_500, presence.maximumCaptureProgressBasisPoints());
    }

    @Test
    void suspensionAndObservationGapCensorMovementAndEntryLatency()
            throws Exception {
        String forming = physicalPerspective(75, 10f, 10f, 1, false,
                "COMPOUND_CAPTURE_PRESERVED")
                .replace("\"executionSuspension\":null",
                        "\"executionSuspension\":\"FORMING_UP\"");
        String resumed = physicalPerspective(75, 20.5f, 20.5f, 5, false,
                "COMPOUND_CAPTURE_PRESERVED")
                .replace("\"observedTick\":75", "\"observedTick\":200");
        String trace = String.join("\n",
                header(),
                forming,
                "{\"stream\":\"control\",\"tick\":100,"
                        + "\"event\":\"capture-paused\"}",
                "{\"stream\":\"control\",\"tick\":200,"
                        + "\"event\":\"capture-resumed\"}",
                resumed,
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"timeout\",\"maxTicks\":300}",
                "");

        var physical = CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE).physicalProgress();

        assertEquals(25L, physical.suspendedAssignmentSquadTicks());
        assertEquals(1, physical.movementEpisodes());
        assertEquals(0L, physical.comparableTravelSquadTicks());
        assertEquals(1, physical.episodesObservedInTargetZone());
        assertTrue(physical.targetZoneEntryLatenciesTicks().isEmpty(),
                "a resumed in-zone baseline is left-censored, not a zero-latency entry");
    }

    @Test
    void countsLateAdjacentCommitmentOnceAndSeparatesPresenceAcrossGaps()
            throws Exception {
        String trace = String.join("\n",
                header(),
                presence(0, "COMMAND_POST@20,20", "MARINE_ONLY", 2, 0,
                        1_000),
                physicalPerspective(0, 10f, 10f, 1, false,
                        "COMPOUND_CAPTURE_PRESERVED"),
                physicalPerspective(75, 12f, 12f, 1, false,
                        "COMPOUND_ASSAULT_ADJACENT"),
                "{\"stream\":\"control\",\"tick\":100,"
                        + "\"event\":\"capture-paused\"}",
                "{\"stream\":\"control\",\"tick\":200,"
                        + "\"event\":\"capture-resumed\"}",
                presence(200, "COMMAND_POST@20,20", "MARINE_ONLY", 2, 0,
                        1_000),
                physicalPerspective(200, 14f, 14f, 1, false,
                        "COMPOUND_ASSAULT_ADJACENT"),
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"timeout\",\"maxTicks\":300}",
                "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(1, analysis.factions().get(Faction.MARINE)
                .physicalProgress().compoundAssaultThresholdCommitments());
        assertEquals(200L,
                analysis.conquest().physicalPresence().marineOnlyCompoundTicks());
        assertEquals(100, analysis.conquest().physicalPresence()
                .longestMarineOnlyPresenceRunTicks());
    }

    @Test
    void resumedBaselineCannotManufactureMarkerClosureAcrossAGap()
            throws Exception {
        String resumed = physicalPerspective(75, 19f, 19f, 1, false,
                "COMPOUND_CAPTURE_PRESERVED")
                .replace("\"observedTick\":75", "\"observedTick\":200")
                .replace("\"tick\":75", "\"tick\":200");
        String trace = String.join("\n",
                header(),
                physicalPerspective(75, 5f, 5f, 1, false,
                        "COMPOUND_CAPTURE_PRESERVED"),
                "{\"stream\":\"control\",\"tick\":100,"
                        + "\"event\":\"capture-paused\"}",
                "{\"stream\":\"control\",\"tick\":200,"
                        + "\"event\":\"capture-resumed\"}",
                resumed,
                "{\"stream\":\"referee\",\"tick\":300,"
                        + "\"event\":\"timeout\",\"maxTicks\":300}",
                "");

        var physical = CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE).physicalProgress();

        assertEquals(1, physical.movementEpisodes());
        assertEquals(0, physical.episodesWithMarkerClosure());
        assertEquals(0L, physical.comparableTravelSquadTicks());
    }

    @Test
    void rejectedPlanDoesNotBecomePhysicalProgress() throws Exception {
        String rejected = physicalPerspective(75, 10f, 10f, 1, false,
                "COMPOUND_ASSAULT_ADJACENT")
                .replace("\"status\":\"ACTIVE\"",
                        "\"status\":\"REJECTED\"");
        String trace = String.join("\n",
                header(),
                rejected,
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}",
                "");

        var physical = CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE).physicalProgress();

        assertEquals(0, physical.movementEpisodes());
        assertEquals(0, physical.compoundAssaultThresholdCommitments());
    }

    @Test
    void retargetClosesTheIntervalGovernedByThePriorDirective()
            throws Exception {
        String retargeted = physicalPerspective(150, 15f, 15f, 1, false,
                "COMPOUND_CAPTURE_PRESERVED")
                .replace("\"issuedTick\":75", "\"issuedTick\":150")
                .replace("\"targetZoneId\":5", "\"targetZoneId\":6")
                .replace("\"markerCellX\":20", "\"markerCellX\":40")
                .replace("\"markerCellY\":20", "\"markerCellY\":40");
        String trace = String.join("\n",
                header(),
                physicalPerspective(75, 10f, 10f, 1, false,
                        "COMPOUND_CAPTURE_PRESERVED"),
                retargeted,
                "{\"stream\":\"referee\",\"tick\":225,"
                        + "\"event\":\"timeout\",\"maxTicks\":225}",
                "");

        var physical = CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE).physicalProgress();

        assertEquals(2, physical.movementEpisodes());
        assertEquals(1, physical.episodesWithMarkerClosure());
        assertEquals(75L, physical.comparableTravelSquadTicks());
        assertEquals(75L, physical.markerClosingSquadTicks());
    }

    @Test
    void acceptsLegacyV2WithoutPhysicalRowsAndRejectsV3EventUnderV2()
            throws Exception {
        String legacyHeader = header().replace("\"schemaVersion\":5",
                "\"schemaVersion\":2");
        String legacyPerspective = perspective(75, "MARINE", 0,
                directive("ACTIVE", 75, 1),
                action("TRACK_ADVANCE", 0), tracks(0, 4, 0, 0))
                .replace(",\"squads\":[]", "");
        String trace = String.join("\n",
                legacyHeader,
                legacyPerspective,
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}",
                "");

        assertEquals(0, CommandTraceAnalyzer.analyze(trace).factions()
                .get(Faction.MARINE).physicalProgress().squadSamples());
        String mislabeledTrace = String.join("\n",
                legacyHeader,
                physicalPerspective(75, 10f, 10f, 1, false,
                        "COMPOUND_CAPTURE_PRESERVED"),
                "{\"stream\":\"referee\",\"tick\":150,"
                        + "\"event\":\"timeout\",\"maxTicks\":150}",
                "");
        assertEquals(0, CommandTraceAnalyzer.analyze(mislabeledTrace)
                .factions().get(Faction.MARINE).physicalProgress()
                .squadSamples());
        assertThrows(IllegalArgumentException.class,
                () -> CommandTraceAnalyzer.analyze(String.join("\n",
                        legacyHeader,
                        presence(0, "COMMAND_POST@20,20", "EMPTY",
                                0, 0, 0),
                        "")));
    }

    @Test
    void rejectsUnsupportedSchemasAndDuplicateHeaders() {
        String old = header().replace("\"schemaVersion\":5",
                "\"schemaVersion\":1");
        assertThrows(IllegalArgumentException.class,
                () -> CommandTraceAnalyzer.analyze(old));
        assertThrows(IllegalArgumentException.class,
                () -> CommandTraceAnalyzer.analyze(header() + "\n" + header()));
    }

    @Test
    void acceptsSabotageChargeSiteRefereeRows() throws Exception {
        String trace = String.join("\n",
                header().replace("CONQUEST", "SABOTAGE"),
                "{\"stream\":\"referee\",\"tick\":1,"
                        + "\"event\":\"charge-site-state\","
                        + "\"siteId\":\"SAB-01\",\"cellX\":12,\"cellY\":7,"
                        + "\"progressBasisPoints\":2500,"
                        + "\"planterOnSite\":true,\"complete\":false}",
                "{\"stream\":\"referee\",\"tick\":2,"
                        + "\"event\":\"timeout\",\"maxTicks\":2}", "");

        Analysis analysis = CommandTraceAnalyzer.analyze(trace);

        assertEquals(Termination.TIMEOUT, analysis.run().termination());
    }

    private static String header() {
        return "{\"stream\":\"run\",\"tick\":0,\"schemaVersion\":5,"
                + "\"fixtureKind\":\"CONQUEST\","
                + "\"schedulerMode\":\"SERIAL_DETERMINISTIC\"}";
    }

    private static String compound(int tick, String subject, String kind,
                                   String state) {
        return "{\"stream\":\"referee\",\"tick\":" + tick
                + ",\"event\":\"compound-state\",\"subject\":\""
                + subject + "\",\"compoundKind\":\"" + kind
                + "\",\"anchorX\":0,\"anchorY\":0,\"state\":\""
                + state + "\"}";
    }

    private static String casualty(int tick, long id, String faction,
                                   boolean combatant) {
        return "{\"stream\":\"referee\",\"tick\":" + tick
                + ",\"event\":\"casualty\",\"unitId\":" + id
                + ",\"faction\":\"" + faction
                + "\",\"unitType\":\"MARINE\",\"combatant\":"
                + combatant + ",\"cellX\":0,\"cellY\":0}";
    }

    private static String perspective(int tick, String faction, int reserve,
                                      String directive, String action,
                                      String tracks) {
        return perspective(tick, tick, faction, reserve, directive, action,
                tracks);
    }

    private static String perspective(int tick, int observedTick,
                                      String faction, int reserve,
                                      String directive, String action,
                                      String tracks) {
        return "{\"stream\":\"perspective\",\"tick\":" + tick
                + ",\"observedTick\":" + observedTick
                + ",\"perspective\":\"" + faction
                + "\",\"strategy\":\"conquest\",\"phase\":\"LANE_ADVANCE\""
                + ",\"influenceTick\":" + tick
                + ",\"commandPoolSize\":1,\"reserveCount\":" + reserve
                + ",\"objectives\":[],\"directives\":[" + directive + "]"
                + ",\"conquest\":{\"axis\":\"SOUTH_TO_NORTH\""
                + ",\"phase\":\"LANE_ADVANCE\",\"remainingCompounds\":3"
                + ",\"keepZoneId\":9,\"keepState\":\"DEFENDER_HELD\""
                + ",\"tracks\":" + tracks + ",\"squads\":[]"
                + ",\"actions\":[" + action
                + "]}}";
    }

    private static String physicalPerspective(
            int tick, float centroidX, float centroidY, int currentZone,
            boolean localContact, String reason) {
        return "{\"stream\":\"perspective\",\"tick\":" + tick
                + ",\"observedTick\":" + tick
                + ",\"perspective\":\"MARINE\",\"strategy\":\"conquest\""
                + ",\"phase\":\"LANE_ADVANCE\",\"influenceTick\":" + tick
                + ",\"commandPoolSize\":1,\"reserveCount\":0"
                + ",\"objectives\":[],\"directives\":["
                + secureDirective() + "]"
                + ",\"conquest\":{\"axis\":\"SOUTH_TO_NORTH\""
                + ",\"phase\":\"LANE_ADVANCE\",\"remainingCompounds\":1"
                + ",\"keepZoneId\":5,\"keepState\":\"DEFENDER_HELD\""
                + ",\"tracks\":[],\"squads\":[{\"squadId\":1"
                + ",\"aliveMembers\":4,\"centroidX\":" + centroidX
                + ",\"centroidY\":" + centroidY
                + ",\"currentZoneId\":" + currentZone
                + ",\"executionSuspension\":null,\"localContact\":"
                + localContact + "}],\"actions\":[{\"squadId\":1"
                + ",\"preferredTrack\":0,\"effectiveTrack\":0,\"reason\":\""
                + reason + "\",\"assignmentKind\":\"SECURE_COMPOUND\""
                + ",\"targetZoneId\":5,\"targetCellX\":-1"
                + ",\"targetCellY\":-1,\"markerCellX\":20"
                + ",\"markerCellY\":20}]}}";
    }

    private static String commandInactivityPerspective(int tick) {
        String states = "[{\"squadId\":1,\"aliveMembers\":0,"
                + "\"centroidX\":0,\"centroidY\":0,\"currentZoneId\":-1,"
                + "\"executionSuspension\":null,\"localContact\":false,"
                + "\"activePathMembers\":0},"
                + "{\"squadId\":2,\"aliveMembers\":4,\"centroidX\":0,"
                + "\"centroidY\":0,\"currentZoneId\":0,"
                + "\"executionSuspension\":\"FORMING_UP\","
                + "\"localContact\":true,\"activePathMembers\":4},"
                + "{\"squadId\":3,\"aliveMembers\":4,\"centroidX\":0,"
                + "\"centroidY\":0,\"currentZoneId\":0,"
                + "\"executionSuspension\":null,\"localContact\":true,"
                + "\"activePathMembers\":4},"
                + "{\"squadId\":4,\"aliveMembers\":4,\"centroidX\":0,"
                + "\"centroidY\":0,\"currentZoneId\":0,"
                + "\"executionSuspension\":null,\"localContact\":false,"
                + "\"activePathMembers\":2},"
                + "{\"squadId\":5,\"aliveMembers\":4,\"centroidX\":0,"
                + "\"centroidY\":0,\"currentZoneId\":0,"
                + "\"executionSuspension\":null,\"localContact\":false,"
                + "\"activePathMembers\":0}]";
        StringBuilder actions = new StringBuilder();
        for (int squad = 1; squad <= 5; squad++) {
            if (squad > 1) actions.append(',');
            actions.append(action("NO_ACTIONABLE_TRACK_TARGET", 0)
                    .replace("\"squadId\":1", "\"squadId\":" + squad));
        }
        return "{\"stream\":\"perspective\",\"tick\":" + tick
                + ",\"observedTick\":" + tick
                + ",\"perspective\":\"MARINE\",\"strategy\":\"conquest\""
                + ",\"phase\":\"LANE_ADVANCE\",\"influenceTick\":" + tick
                + ",\"commandPoolSize\":5,\"reserveCount\":0"
                + ",\"objectives\":[],\"directives\":[]"
                + ",\"conquest\":{\"axis\":\"SOUTH_TO_NORTH\""
                + ",\"phase\":\"LANE_ADVANCE\",\"remainingCompounds\":3"
                + ",\"keepZoneId\":9,\"keepState\":\"DEFENDER_HELD\""
                + ",\"tracks\":[],\"squads\":" + states
                + ",\"actions\":[" + actions + "]}}";
    }

    private static String secureDirective() {
        return "{\"squadId\":1,\"issuer\":\"conquest\""
                + ",\"authority\":\"MISSION_COMMAND\",\"status\":\"ACTIVE\""
                + ",\"reason\":\"COMPOUND_CAPTURE_PRESERVED\""
                + ",\"disposition\":\"committed\",\"issuedTick\":75"
                + ",\"stableUntilTick\":300,\"leaseUntilTick\":-1"
                + ",\"assignment\":{\"kind\":\"SECURE_COMPOUND\""
                + ",\"targetZoneId\":5,\"targetNode\":\"COMMAND_POST\""
                + ",\"objectiveId\":-1,\"targetCellX\":-1"
                + ",\"targetCellY\":-1}}";
    }

    private static String presence(int tick, String subject, String occupancy,
                                   int marines, int defenders, int progress) {
        return "{\"stream\":\"referee\",\"tick\":" + tick
                + ",\"event\":\"compound-presence\",\"subject\":\""
                + subject + "\",\"compoundKind\":\"COMMAND_POST\""
                + ",\"anchorX\":20,\"anchorY\":20,\"anchorZoneId\":5"
                + ",\"occupancy\":\"" + occupancy + "\",\"marineUnits\":"
                + marines + ",\"defenderUnits\":" + defenders
                + ",\"captureProgressBasisPoints\":" + progress + '}';
    }

    private static String directive(String status, int issuedTick,
                                    int targetZone) {
        String disposition = "RETAINED".equals(status)
                ? "stable through tick 225" : "committed";
        return "{\"squadId\":1,\"issuer\":\"conquest\""
                + ",\"authority\":\"MISSION_COMMAND\",\"status\":\""
                + status + "\",\"reason\":\"TRACK_ADVANCE\""
                + ",\"disposition\":\"" + disposition
                + "\",\"issuedTick\":" + issuedTick
                + ",\"stableUntilTick\":300,\"leaseUntilTick\":-1"
                + ",\"assignment\":{\"kind\":\"CLEAR_ZONE\""
                + ",\"targetZoneId\":" + targetZone
                + ",\"targetNode\":null,\"objectiveId\":-1"
                + ",\"targetCellX\":-1,\"targetCellY\":-1}}";
    }

    private static String action(String reason, int track) {
        return action(reason, track, false);
    }

    private static String action(String reason, int track,
                                 boolean distantCaptureDeferred) {
        return "{\"squadId\":1,\"preferredTrack\":" + track
                + ",\"effectiveTrack\":" + track + ",\"reason\":\""
                + reason + "\",\"assignmentKind\":null,\"targetZoneId\":-1"
                + ",\"targetCellX\":-1,\"targetCellY\":-1"
                + ",\"markerCellX\":-1,\"markerCellY\":-1"
                + ",\"distantCaptureDeferred\":"
                + distantCaptureDeferred + '}';
    }

    private static String tracks(int contacts, int first, int second,
                                 int third) {
        return "[" + track(0, contacts, first) + ','
                + track(1, 0, second) + ',' + track(2, 0, third) + ']';
    }

    private static String track(int index, int contacts, int members) {
        return "{\"index\":" + index + ",\"effectiveLiveMembers\":"
                + members + ",\"knownHostileContacts\":" + contacts + '}';
    }
}
