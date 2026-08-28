package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ExtractionBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in neutral evidence for the generic Extraction objective contract. */
@Tag("extraction-objective-evidence")
class ExtractionObjectiveEvidenceTest {
    private static final int DEFAULT_MAX_TICKS = 12_000;
    private static final String DEFAULT_FIXTURE =
            "/battle-fixtures/extraction-objective-v1.json";

    @Test
    void writesByteStableForcedSerialExtractionObjectiveEvidence()
            throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = Integer.getInteger(
                "extraction.objective.evidence.maxTicks", DEFAULT_MAX_TICKS);
        if (maxTicks < 1) throw new IllegalArgumentException(
                "extraction.objective.evidence.maxTicks must be positive");
        BattleFixture fixture = loadFixture();
        RunResult first = run(fixture, maxTicks);
        RunResult second = run(fixture, maxTicks);
        assertEquals(first.trace(), second.trace(),
                "same Extraction fixture must produce byte-stable neutral events");
        assertTrue(first.trace().contains(
                "\"event\":\"extraction-payload-state\""));
        assertTrue(first.trace().contains(
                "\"payloadId\":\"EXTRACTION-01\""));

        Path output = Path.of(System.getProperty(
                "extraction.objective.evidence.outputDir",
                "build/reports/commander/extraction"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.resolve("traces"));
        Files.writeString(output.resolve(
                        "traces/extraction-objective.jsonl"),
                first.trace(), StandardCharsets.UTF_8);
        JSONObject summary = new JSONObject()
                .put("schemaVersion", 1)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("fixture", DEFAULT_FIXTURE)
                .put("maxTicks", maxTicks)
                .put("repeatCount", 2)
                .put("termination", first.complete()
                        ? "COMPLETE" : "TIMEOUT")
                .put("winner", first.winner() != null
                        ? first.winner() : JSONObject.NULL)
                .put("ticks", first.ticks())
                .put("traceEvents", first.trace().lines().count());
        Files.writeString(output.resolve("summary.json"),
                summary.toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),
                "# Extraction objective evidence\n\n"
                        + "Forced-serial, zero-input production Extraction "
                        + "replayed twice with byte-identical neutral traces.\n\n"
                        + "- Maximum ticks: " + maxTicks + "\n"
                        + "- Result: " + (first.complete()
                        ? "COMPLETE" : "TIMEOUT") + "\n"
                        + "- Winner: " + (first.winner() != null
                        ? first.winner() : "—") + "\n"
                        + "- Ticks: " + first.ticks() + "\n",
                StandardCharsets.UTF_8);
        System.out.println("[extraction-objective-evidence] report "
                + output.resolve("summary.md"));
    }

    private static RunResult run(BattleFixture fixture, int maxTicks) {
        try (BattleSimulation sim = fixture.build()) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            return new RunResult(sim.getCommandTraceJsonLines(),
                    sim.getSimTickIndex(), sim.isComplete(),
                    sim.getWinner() != null ? sim.getWinner().name() : null);
        }
    }

    private static BattleFixture loadFixture() throws Exception {
        String selected = System.getProperty(
                "extraction.objective.evidence.fixture.path", "").trim();
        String json;
        if (selected.isBlank()) {
            try (InputStream stream = ExtractionObjectiveEvidenceTest.class
                    .getResourceAsStream(DEFAULT_FIXTURE)) {
                if (stream == null) throw new IllegalStateException(
                        "Missing Extraction fixture: " + DEFAULT_FIXTURE);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(selected)
                    .toAbsolutePath().normalize());
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch
                ? launch.construction() : fixture;
        if (!(construction instanceof ExtractionBattleFixture)) {
            throw new IllegalArgumentException(
                    "Extraction evidence requires an Extraction fixture: "
                            + fixture.kind());
        }
        return fixture;
    }

    private record RunResult(String trace, int ticks, boolean complete,
                             String winner) { }
}
