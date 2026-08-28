package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Analysis;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.CaptureZoneCohortMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.CaptureZonePublishedSquadMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.ConquestMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.CommandInactivityMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.FactionMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.RunMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Termination;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureTestSupport;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConquestCommandBalanceReportTest {

    @TempDir
    Path tempDir;

    @Test
    void publishesACompleteStagedReportOverPriorEvidence() throws Exception {
        Path output = tempDir.resolve("conquest");
        Files.createDirectories(output);
        Files.writeString(output.resolve("summary.md"), "prior evidence");
        Files.writeString(output.resolve("old-marker"), "old");
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("traces"));
        Files.writeString(staging.resolve("summary.md"), "new evidence");
        Files.writeString(staging.resolve("summary.json"), "{}\n");
        Files.writeString(staging.resolve("traces/run.jsonl"), "trace\n");

        assertEquals("prior evidence",
                Files.readString(output.resolve("summary.md")));
        ConquestCommandBalanceTest.publishReports(staging, output);

        assertEquals("new evidence",
                Files.readString(output.resolve("summary.md")));
        assertTrue(Files.exists(output.resolve("summary.json")));
        assertTrue(Files.exists(output.resolve("traces/run.jsonl")));
        assertFalse(Files.exists(output.resolve("old-marker")));
        assertFalse(Files.exists(staging));
        try (var siblings = Files.list(tempDir)) {
            assertEquals(List.of("conquest"), siblings
                    .map(path -> path.getFileName().toString())
                    .sorted().toList());
        }
    }

    @Test
    void reportLabelsProvenanceAndMobilizationPrecisely() throws Exception {
        ConquestBattleFixture fixture =
                BattleFixtureTestSupport.loadConquestFixture();
        FactionMetrics marine = factionMetrics(List.of());
        FactionMetrics defender = factionMetrics(List.of(75, 150));
        Analysis analysis = new Analysis(
                new RunMetrics("CONQUEST", "SERIAL_DETERMINISTIC",
                        0, 600, 600, Termination.TIMEOUT, null, false, 1,
                        Map.of(Faction.MARINE, 2, Faction.DEFENDER, 3)),
                Map.of(Faction.MARINE, marine, Faction.DEFENDER, defender),
                new ConquestMetrics(4, 0, 1, 1, 1, 0, -1, 600, false));
        String sha = "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef";
        ConquestCommandBalanceTest.ReportRow row =
                new ConquestCommandBalanceTest.ReportRow(
                        "fixture", sha, fixture, 204, 17, analysis);

        String json = ConquestCommandBalanceTest.summaryJson(
                List.of(row), 600, false);
        String markdown = ConquestCommandBalanceTest.summaryMarkdown(
                List.of(row), 600, false);

        assertTrue(json.contains("\"schedulerMode\":\"SERIAL_DETERMINISTIC\""));
        assertTrue(json.contains("\"schemaVersion\":8"));
        assertTrue(json.contains("\"maxTicks\":600"));
        assertTrue(json.contains("\"repeatCount\":2"));
        assertTrue(json.contains("\"canonicalMatrix\":false"));
        assertTrue(json.contains("\"fixtureSha256\":\"" + sha + "\""));
        assertTrue(json.contains("\"transportSeatCapacity\":112"));
        assertTrue(json.contains("\"marineCommitments\":204"));
        assertTrue(json.contains("\"marineSquads\":17"));
        assertTrue(json.contains("\"commandInactivity\":{"
                + "\"lifecycleSquadPulses\":1"));
        assertTrue(json.contains("\"genuineIdleSquadTicks\":300"));
        assertTrue(json.contains("\"secureTravelEpisodes\":{"
                + "\"started\":0,\"finalized\":0,\"open\":0,\"exits\":{"
                + "\"targetEntry\":0,\"retarget\":0,\"release\":0,"
                + "\"squadLoss\":0,\"executionSuspension\":0,"
                + "\"observationGap\":0,\"timeout\":0,"
                + "\"terminalResult\":0},\"retargetProvenance\":{"
                + "\"objectiveChanged\":0,\"markerChanged\":0,"
                + "\"assignmentChanged\":0,\"unclassified\":0},"
                + "\"portalProgress\":{\"classifiedExits\":0,"
                + "\"neverAtPortalExits\":0,"
                + "\"atPortalNotEnteredExits\":0,\"enteredExits\":0,"
                + "\"episodesObservedAtPortal\":0},"
                + "\"squadLossLastDistancesDecicells\":[],"
                + "\"squadLossApproachProgressBasisPoints\":[],"
                + "\"squadLossFrontContext\":{\"observed\":0,"
                + "\"unknown\":0,\"localContact\":0,"
                + "\"trackBeliefOnly\":0,\"noPublishedContact\":0,"
                + "\"unknownTrack\":0}"));
        assertTrue(json.contains("\"captureZoneCohorts\":{"
                + "\"available\":false,\"observed\":0"));
        assertTrue(markdown.contains("Evidence mode: ad hoc override"));
        assertTrue(markdown.contains("production launch fixtures"));
        assertTrue(markdown.contains("mobilization latencies: [75, 150]"));
        assertTrue(markdown.contains("Marine physical progress:"));
        assertTrue(markdown.contains("useful active-path movement 3 / 225"));
        assertTrue(markdown.contains("genuine idle 4 / 300"));
        assertTrue(markdown.contains("peak live members"));
        assertTrue(markdown.contains("Marine secure-travel episodes: "
                + "0/0 finalized, 0 open; exits: target entry 0"));
        assertTrue(markdown.contains("Marine secure portal classification: "
                + "0 exits; never at portal 0, at portal but not entered 0, "
                + "entered 0; episodes observed at portal 0"));
        assertTrue(markdown.contains("Retarget provenance: objective changed 0, "
                + "marker changed 0, assignment changed 0, unclassified 0"));
        assertTrue(markdown.contains("Squad-loss last distances (0.1 cells): "
                + "[]; approach progress (bp): []"));
        assertTrue(markdown.contains("Last-alive front context: observed 0, "
                + "unknown location 0, local contact 0, track belief only 0, "
                + "no published contact 0, unknown track 0"));
        assertTrue(markdown.contains("Context may overlap: local contact 0, "
                + "active path 0, quiet travel 0"));
        assertTrue(markdown.contains("Capture-zone presence:"));
        assertTrue(markdown.contains("Capture-zone cohorts: unavailable before "
                + "exact capture-zone trace schema 7"));
        assertTrue(markdown.contains("territorial progress: OBSERVED"));
        assertFalse(markdown.contains("response latencies"));
        assertEquals("fixture-0123456789ab",
                ConquestCommandBalanceTest.reportId("fixture", true, sha));
        assertEquals("fixture",
                ConquestCommandBalanceTest.reportId("fixture", false, sha));
    }

    @Test
    void reportPublishesCaptureZoneCohortLifecycle() throws Exception {
        ConquestBattleFixture fixture =
                BattleFixtureTestSupport.loadConquestFixture();
        FactionMetrics marine = factionMetrics(List.of());
        FactionMetrics defender = factionMetrics(List.of());
        CaptureZoneCohortMetrics cohorts = new CaptureZoneCohortMetrics(
                true, 2, 2, 0, 2, 0,
                1, 1, 0, 0, 0, 0, 0, 0,
                1, 1, 1,
                List.of(1, 2), List.of(1, 4), List.of(0, 2),
                List.of(0, 5), List.of(30), List.of(60),
                List.of(10, 30), 30,
                new CaptureZonePublishedSquadMetrics(
                        1, 1, 4, 1, 1,
                        List.of(1), List.of(2),
                        List.of(2), List.of(6),
                        List.of(2), List.of(6)));
        Analysis analysis = new Analysis(
                new RunMetrics("CONQUEST", "SERIAL_DETERMINISTIC",
                        0, 100, 100, Termination.TIMEOUT, null, false, 1,
                        Map.of()),
                Map.of(Faction.MARINE, marine, Faction.DEFENDER, defender),
                new ConquestMetrics(4, 0, 1, 1, 1, 0, -1, 100,
                        false, null, cohorts));
        String sha = "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef";
        var row = new ConquestCommandBalanceTest.ReportRow(
                "fixture", sha, fixture, 204, 17, analysis);

        String markdown = ConquestCommandBalanceTest.summaryMarkdown(
                List.of(row), 100, false);
        String json = ConquestCommandBalanceTest.summaryJson(
                List.of(row), 100, false);

        assertTrue(markdown.contains("Capture-zone cohorts: 2 observed "
                + "(2 entries, 0 left-censored), 2 finalized / 0 open"));
        assertTrue(markdown.contains("exits captured 1, defender-present 1"));
        assertTrue(markdown.contains("observed-entry Marine units [1, 2]"));
        assertTrue(markdown.contains("all-cohort peak Marine units [1, 4]"));
        assertTrue(markdown.contains("entry-to-capture ticks [60]"));
        assertTrue(markdown.contains("mixed durations [10, 30], longest mixed "
                + "run 30 ticks"));
        assertTrue(markdown.contains("Published in-zone squads: 1 cohorts "
                + "observed / 1 unobserved, 4 squad-pulses"));
        assertTrue(json.contains("\"captureZoneCohorts\":{"
                + "\"available\":true,\"observed\":2,"
                + "\"entriesObserved\":2,\"leftCensored\":0,"
                + "\"finalized\":2,\"open\":0,\"exits\":{"
                + "\"captured\":1,\"defenderPresent\":1,\"empty\":0,"
                + "\"unresolved\":0,\"zoneChanged\":0,"
                + "\"observationGap\":0,\"timeout\":0,"
                + "\"terminalResult\":0},\"uncontestedObserved\":1,"
                + "\"withZoneMemberAdditions\":1,"
                + "\"withDefenderReduction\":1,"
                + "\"entryMarineUnits\":[1,2],"
                + "\"peakMarineUnits\":[1,4],"
                + "\"zoneMemberAdditions\":[0,2],"
                + "\"defenderUnitsClearedFromEntry\":[0,5],"
                + "\"entryToUncontestedTicks\":[30],"
                + "\"entryToCaptureTicks\":[60],"
                + "\"observedMixedTicks\":[10,30],"
                + "\"longestMixedRunTicks\":30,\"publishedSquads\":{"
                + "\"observedCohorts\":1,\"unobservedCohorts\":1,"
                + "\"inZoneSquadPulses\":4,"
                + "\"cohortsWithMultipleSquads\":1,"
                + "\"cohortsWithAddedSquads\":1,"
                + "\"firstInZoneSquads\":[1],"
                + "\"peakInZoneSquads\":[2],"
                + "\"firstInZoneMembers\":[2],"
                + "\"peakInZoneMembers\":[6],"
                + "\"firstAssignedAliveMembers\":[2],"
                + "\"peakAssignedAliveMembers\":[6]}}"));
    }

    @Test
    void failedPublishRestoresPriorEvidenceAndCleansRollbackDirectory()
            throws Exception {
        Path output = tempDir.resolve("conquest");
        Files.createDirectories(output);
        Files.writeString(output.resolve("summary.md"), "prior evidence");

        assertThrows(Exception.class, () ->
                ConquestCommandBalanceTest.publishReports(
                        tempDir.resolve("missing-staging"), output));

        assertEquals("prior evidence",
                Files.readString(output.resolve("summary.md")));
        try (var siblings = Files.list(tempDir)) {
            assertEquals(List.of("conquest"), siblings
                    .map(path -> path.getFileName().toString())
                    .sorted().toList());
        }
    }

    private static FactionMetrics factionMetrics(List<Integer> latencies) {
        return new FactionMetrics(3, 1, 0, 0, 0, 2,
                15, 1_125, 0, 15, 0, 75L, latencies, 0, 6_000,
                null, new CommandInactivityMetrics(
                1, 75, 2, 150, 3, 225, 3, 225,
                4, 300, 2, 150));
    }
}
