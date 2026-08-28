package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.ExtractionObjectiveDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionObjectiveFacts;
import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Extraction-family evidence for the authored Civilian Rescue adapter. */
@Tag("extraction-command-evidence")
class RescueCommandEvidenceTest {
    private static final String DEFAULT_FIXTURE =
            "/battle-fixtures/civilian-rescue-v1.json";
    private static final int DEFAULT_MAX_TICKS = 12_000;

    @Test
    void writesByteStableMarineCorridorAndSwarmDirectorEvidence()
            throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = configuredMaxTicks();
        BattleFixture fixture = loadFixture();
        Assumptions.assumeTrue(fixture instanceof CivilianRescueBattleFixture,
                "selected Extraction-family fixture is not Civilian Rescue");

        RunResult first = run(fixture, maxTicks);
        RunResult second = run(fixture, maxTicks);
        assertEquals(first.trace(), second.trace(),
                "Civilian Rescue must replay byte-identically");
        assertTrue(first.trace().contains(
                "\"strategy\":\"rescue-corridor\""));
        assertTrue(first.trace().contains("\"rescue\":{"));
        assertTrue(first.trace().contains("\"role\":\"COHORT_ESCORT\""));
        assertTrue(first.trace().contains("\"role\":\"LEAD_SCREEN\""),
                "two authored shuttle squads should divide escort and screen duty");
        assertTrue(first.trace().contains(
                "\"director\":\"rescue-swarm-pressure\""));
        assertTrue(first.trace().contains("\"approaches\":["));
        assertTrue(first.alarmRaised() || first.complete(),
                "zero-input rescue must release the cohort or reach an explained terminal outcome");
        for (String line : first.trace().lines().filter(row -> row.contains(
                "\"director\":\"rescue-swarm-pressure\""))
                .toList()) {
            assertTrue(!line.contains("controllingSquadId")
                            && !line.contains("cohortCellX")
                            && !line.contains("marineCellX"),
                    "swarm director leaked Marine corridor truth: " + line);
        }

        Path output = Path.of(System.getProperty(
                "extraction.command.evidence.outputDir",
                "build/reports/commander/extraction"))
                .toAbsolutePath().normalize().resolve("rescue");
        Files.createDirectories(output);
        Files.writeString(output.resolve("civilian-rescue.jsonl"),
                first.trace(), StandardCharsets.UTF_8);
        JSONObject summary = new JSONObject()
                .put("schemaVersion", 1)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("fixture", selectedFixture())
                .put("maxTicks", maxTicks)
                .put("repeatCount", 2)
                .put("termination", first.complete()
                        ? "COMPLETE" : "TIMEOUT")
                .put("winner", first.winner() != null
                        ? first.winner() : JSONObject.NULL)
                .put("ticks", first.ticks())
                .put("cohortPhase", first.phase())
                .put("cohortProgress", first.progress())
                .put("alarmRaised", first.alarmRaised())
                .put("directorEvents", first.directorEvents());
        Files.writeString(output.resolve("summary.json"),
                summary.toString(2) + '\n', StandardCharsets.UTF_8);
        System.out.println("[rescue-command-evidence] report "
                + output.resolve("summary.json"));
    }

    private static RunResult run(BattleFixture fixture, int maxTicks) {
        try (BattleSimulation sim = fixture.build()) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            ExtractionObjectiveFacts cohort = ExtractionObjectiveDisclosure
                    .freezeNeutral(sim).stream()
                    .filter(row -> row.kind()
                            == ExtractionPayloadObjective.Kind.COHORT)
                    .findFirst().orElseThrow();
            String trace = sim.getCommandTraceJsonLines();
            return new RunResult(trace, sim.getSimTickIndex(),
                    sim.isComplete(),
                    sim.getWinner() != null ? sim.getWinner().name() : null,
                    cohort.phase(), cohort.progress(), cohort.alarmActive(),
                    (int) trace.lines().filter(line -> line.contains(
                            "\"director\":\"rescue-swarm-pressure\""))
                            .count());
        }
    }

    private static BattleFixture loadFixture() throws Exception {
        String selected = selectedFixture();
        String json;
        if (selected.startsWith("/")) {
            try (InputStream stream = RescueCommandEvidenceTest.class
                    .getResourceAsStream(selected)) {
                if (stream == null) throw new IllegalStateException(
                        "Missing Rescue fixture: " + selected);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(selected)
                    .toAbsolutePath().normalize());
        }
        return BattleFixtureJson.fromJson(new JSONObject(json));
    }

    private static String selectedFixture() {
        String selected = System.getProperty(
                "extraction.command.evidence.fixture.path", "").trim();
        return selected.isBlank() ? DEFAULT_FIXTURE : selected;
    }

    private static int configuredMaxTicks() {
        String configured = System.getProperty(
                "extraction.command.evidence.maxTicks", "").trim();
        int result = configured.isBlank() ? DEFAULT_MAX_TICKS
                : Integer.parseInt(configured);
        if (result < 1) throw new IllegalArgumentException(
                "extraction.command.evidence.maxTicks must be positive");
        return result;
    }

    private record RunResult(
            String trace, int ticks, boolean complete, String winner,
            String phase, float progress, boolean alarmRaised,
            int directorEvents) { }
}
