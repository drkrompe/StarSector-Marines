package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.FactionMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.RunMetrics;
import com.dillon.starsectormarines.battle.command.trace.CommandTraceAnalyzer.Termination;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.Analysis;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.CoverageMetrics;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.RoleTransitionMetrics;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.SabotageMetrics;
import com.dillon.starsectormarines.battle.command.trace.SabotageCommandTraceAnalyzer.SiteMetrics;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabotageCommandEvidenceReportTest {

    @TempDir
    Path tempDir;

    @Test
    void reportLabelsTimeoutProvenanceAndSabotageMetrics() throws Exception {
        SabotageBattleFixture fixture = fixture();
        Analysis analysis = analysis();
        String sha = "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef";
        SabotageCommandEvidenceTest.ReportRow row =
                new SabotageCommandEvidenceTest.ReportRow(
                        "fixture", sha, fixture, analysis);

        String json = SabotageCommandEvidenceTest.summaryJson(
                List.of(row), 600, false);
        String markdown = SabotageCommandEvidenceTest.summaryMarkdown(
                List.of(row), 600, false);

        assertTrue(json.contains("\"schedulerMode\":\"SERIAL_DETERMINISTIC\""));
        assertTrue(json.contains("\"repeatCount\":2"));
        assertTrue(json.contains("\"termination\":\"TIMEOUT\""));
        assertTrue(json.contains("\"completedSites\":1"));
        assertTrue(markdown.contains("A timeout is evidence, not a defender victory"));
        assertTrue(markdown.contains("Site coverage:"));
        assertTrue(markdown.contains("Planter/retriever transitions:"));
        assertTrue(markdown.contains("Directive churn:"));
        assertTrue(markdown.contains("SAB-01 progress 10000 bp, completion tick 525"));
        assertEquals("fixture-0123456789ab",
                SabotageCommandEvidenceTest.reportId("fixture", true, sha));
    }

    @Test
    void publishesAtomicallyAndRestoresPriorEvidenceOnFailure()
            throws Exception {
        Path output = tempDir.resolve("sabotage");
        Files.createDirectories(output);
        Files.writeString(output.resolve("summary.md"), "prior evidence");
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("traces"));
        Files.writeString(staging.resolve("summary.md"), "new evidence");
        Files.writeString(staging.resolve("summary.json"), "{}\n");

        SabotageCommandEvidenceTest.publishReports(staging, output);

        assertEquals("new evidence",
                Files.readString(output.resolve("summary.md")));
        assertFalse(Files.exists(staging));
        assertThrows(Exception.class, () ->
                SabotageCommandEvidenceTest.publishReports(
                        tempDir.resolve("missing"), output));
        assertEquals("new evidence",
                Files.readString(output.resolve("summary.md")));
    }

    private static Analysis analysis() {
        RunMetrics run = new RunMetrics("SABOTAGE", "SERIAL_DETERMINISTIC",
                0, 600, 600, Termination.TIMEOUT, null, false, 1,
                Map.of(Faction.MARINE, 3, Faction.DEFENDER, 8));
        FactionMetrics command = new FactionMetrics(5, 2, 1, 3,
                0, 4, 0, 0, 0, 0, 0L, List.of(), 0, 0);
        CoverageMetrics coverage = new CoverageMetrics(
                5, 5, 2, 3, 1, 3, 2);
        RoleTransitionMetrics transitions = new RoleTransitionMetrics(
                2, 1, 1, 1, 1, 0, 1, 1, 0, 1);
        SiteMetrics site = new SiteMetrics(
                "SAB-01", 3, 1, 4, 1, 10_000, 525);
        return new Analysis(run, command, new SabotageMetrics(
                3, 1, coverage, transitions, Map.of("SAB-01", site)));
    }

    private static SabotageBattleFixture fixture() throws Exception {
        try (InputStream stream = SabotageCommandEvidenceReportTest.class
                .getResourceAsStream(
                        "/battle-fixtures/sabotage-site-groups-v2.json")) {
            if (stream == null) throw new IllegalStateException("fixture missing");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return (SabotageBattleFixture) BattleFixtureJson.fromJson(
                    new JSONObject(json));
        }
    }
}
