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
    void rejectsUnsupportedSchemasAndDuplicateHeaders() {
        String old = header().replace("\"schemaVersion\":2",
                "\"schemaVersion\":1");
        assertThrows(IllegalArgumentException.class,
                () -> CommandTraceAnalyzer.analyze(old));
        assertThrows(IllegalArgumentException.class,
                () -> CommandTraceAnalyzer.analyze(header() + "\n" + header()));
    }

    private static String header() {
        return "{\"stream\":\"run\",\"tick\":0,\"schemaVersion\":2,"
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
                + ",\"tracks\":" + tracks + ",\"actions\":[" + action
                + "]}}";
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
        return "{\"squadId\":1,\"preferredTrack\":" + track
                + ",\"effectiveTrack\":" + track + ",\"reason\":\""
                + reason + "\",\"assignmentKind\":null,\"targetZoneId\":-1"
                + ",\"targetCellX\":-1,\"targetCellY\":-1"
                + ",\"markerCellX\":-1,\"markerCellY\":-1}";
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
