package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.ExtractionObjectiveDisclosure;
import com.dillon.starsectormarines.battle.command.ExtractionObjectiveFacts;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ExtractionBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in paired command and neutral evidence for generic Extraction. */
@Tag("extraction-command-evidence")
class ExtractionObjectiveEvidenceTest {
    private static final int DEFAULT_MAX_TICKS = 12_000;
    private static final List<FixtureSpec> DEFAULT_FIXTURES = List.of(
            new FixtureSpec("production-pressure",
                    "/battle-fixtures/extraction-objective-v1.json",
                    true, false),
            new FixtureSpec("alarm-response",
                    "/battle-fixtures/extraction-alarm-response-v1.json",
                    true, true));

    @Test
    void writesByteStableForcedSerialExtractionCommandEvidence()
            throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits());
        String configuredMaxTicks = System.getProperty(
                "extraction.command.evidence.maxTicks", "").trim();
        int maxTicks = configuredMaxTicks.isBlank() ? DEFAULT_MAX_TICKS
                : Integer.parseInt(configuredMaxTicks);
        if (maxTicks < 1) throw new IllegalArgumentException(
                "extraction.command.evidence.maxTicks must be positive");

        Path output = Path.of(System.getProperty(
                "extraction.command.evidence.outputDir",
                "build/reports/commander/extraction"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.resolve("traces"));
        JSONArray summaries = new JSONArray();
        List<FixtureSpec> fixtures = fixtures();
        boolean canonical = configuredMaxTicks.isBlank()
                && fixtures.equals(DEFAULT_FIXTURES);
        StringBuilder markdown = new StringBuilder()
                .append("# Extraction command evidence\n\n")
                .append(canonical ? "Canonical" : "Ad hoc")
                .append(" forced-serial, zero-input production Extraction ")
                .append("fixtures replayed twice with byte-identical ")
                .append("perspective and neutral traces.\n\n")
                .append("| Fixture | Result | Ticks | Payload | Alarm | ")
                .append("Interdiction actions |\n")
                .append("|---|---|---:|---|---|---:|\n");

        for (FixtureSpec spec : fixtures) {
            BattleFixture fixture = loadFixture(spec);
            RunResult first = run(fixture, maxTicks);
            RunResult second = run(fixture, maxTicks);
            assertEquals(first.trace(), second.trace(),
                    spec.id() + " must produce byte-stable command evidence");
            assertCommonEvidence(spec, first);
            if (canonical && spec.requiresAlarmResponse()) {
                assertAlarmResponse(spec, first);
            }

            Files.writeString(output.resolve("traces")
                            .resolve(spec.id() + ".jsonl"),
                    first.trace(), StandardCharsets.UTF_8);
            summaries.put(summary(spec, first));
            markdown.append('|').append(spec.id()).append('|')
                    .append(first.complete() ? "COMPLETE" : "TIMEOUT")
                    .append(first.winner() != null
                            ? " (" + first.winner() + ")" : "")
                    .append('|').append(first.ticks()).append('|')
                    .append(first.payloadPhase()).append(' ')
                    .append(Math.round(first.payloadProgress() * 100f))
                    .append("%|")
                    .append(first.alarmRaised() ? "raised" : "quiet")
                    .append('|').append(first.interdictionActions())
                    .append("|\n");
        }

        JSONObject summary = new JSONObject()
                .put("schemaVersion", 2)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("canonical", canonical)
                .put("maxTicks", maxTicks)
                .put("repeatCount", 2)
                .put("fixtures", summaries);
        Files.writeString(output.resolve("summary.json"),
                summary.toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),
                markdown.toString(), StandardCharsets.UTF_8);
        System.out.println("[extraction-command-evidence] report "
                + output.resolve("summary.md"));
    }

    private static void assertCommonEvidence(FixtureSpec spec,
                                             RunResult result) {
        String trace = result.trace();
        assertTrue(trace.contains("\"event\":\"extraction-payload-state\""),
                spec.id());
        assertTrue(trace.contains("\"payloadId\":\"EXTRACTION-01\""),
                spec.id());
        assertTrue(trace.contains("\"strategy\":\"extraction-attacker\""),
                spec.id());
        assertTrue(trace.contains("\"strategy\":\"extraction-defender\""),
                spec.id());
        assertTrue(trace.contains("\"extraction\":{"), spec.id());
        assertTrue(trace.contains("\"extractionDefense\":{"), spec.id());
        assertTrue(trace.contains("\"role\":\"PAYLOAD_ELEMENT\""), spec.id());
        for (String line : trace.lines().filter(row ->
                row.contains("\"strategy\":\"extraction-defender\""))
                .toList()) {
            assertTrue(!line.contains("\"egressCellX\"")
                            && !line.contains("\"payloadCellX\"")
                            && !line.contains("\"corridorGuideCellX\"")
                            && !line.contains("\"progress\"")
                            && !line.contains("\"controllingSquadId\""),
                    spec.id() + " defender perspective leaked hidden truth: "
                            + line);
        }
    }

    private static void assertAlarmResponse(FixtureSpec spec,
                                            RunResult result) {
        assertTrue(result.alarmRaised(),
                spec.id() + " must raise the public source alarm");
        assertTrue(result.alarmPerspectiveEvents() > 0,
                spec.id() + " must publish ALARM_INTERDICTION");
        assertTrue(result.sourceResponseActions() > 0,
                spec.id() + " must show source-perimeter mobilization");
        assertTrue(result.interdictionActions() > 0,
                spec.id() + " must show belief-driven interdiction");
    }

    private static JSONObject summary(FixtureSpec spec, RunResult result)
            throws Exception {
        return new JSONObject()
                .put("id", spec.id())
                .put("fixture", spec.location())
                .put("requiresAlarmResponse", spec.requiresAlarmResponse())
                .put("termination", result.complete()
                        ? "COMPLETE" : "TIMEOUT")
                .put("winner", result.winner() != null
                        ? result.winner() : JSONObject.NULL)
                .put("ticks", result.ticks())
                .put("payloadPhase", result.payloadPhase())
                .put("payloadProgress", result.payloadProgress())
                .put("payloadFailure", result.payloadFailure())
                .put("alarmRaised", result.alarmRaised())
                .put("alarmPerspectiveEvents",
                        result.alarmPerspectiveEvents())
                .put("sourceResponseActions", result.sourceResponseActions())
                .put("interdictionActions", result.interdictionActions())
                .put("traceEvents", result.trace().lines().count());
    }

    private static RunResult run(BattleFixture fixture, int maxTicks) {
        try (BattleSimulation sim = fixture.build()) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            ExtractionObjectiveFacts payload = ExtractionObjectiveDisclosure
                    .freezeNeutral(sim).stream()
                    .filter(row -> "EXTRACTION-01".equals(row.payloadId()))
                    .findFirst().orElseThrow();
            String trace = sim.getCommandTraceJsonLines();
            return new RunResult(trace, sim.getSimTickIndex(),
                    sim.isComplete(),
                    sim.getWinner() != null ? sim.getWinner().name() : null,
                    payload.phase(), payload.progress(),
                    payload.failure().name(), payload.alarmActive(),
                    countDefenderLines(trace,
                            "\"phase\":\"ALARM_INTERDICTION\""),
                    countDefenderLines(trace,
                            "\"role\":\"ALARM_RESPONDER\""),
                    countDefenderLines(trace,
                            "\"role\":\"INTERDICTION\""));
        }
    }

    private static int countDefenderLines(String trace, String needle) {
        return (int) trace.lines()
                .filter(line -> line.contains(
                        "\"strategy\":\"extraction-defender\""))
                .filter(line -> line.contains(needle))
                .count();
    }

    private static List<FixtureSpec> fixtures() {
        String selected = System.getProperty(
                "extraction.command.evidence.fixture.path", "").trim();
        return selected.isBlank() ? DEFAULT_FIXTURES
                : List.of(new FixtureSpec("adhoc", selected, false, false));
    }

    private static BattleFixture loadFixture(FixtureSpec spec) throws Exception {
        String json;
        if (spec.classpathResource()) {
            try (InputStream stream = ExtractionObjectiveEvidenceTest.class
                    .getResourceAsStream(spec.location())) {
                if (stream == null) throw new IllegalStateException(
                        "Missing Extraction fixture: " + spec.location());
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(spec.location())
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

    private record FixtureSpec(String id, String location,
                               boolean classpathResource,
                               boolean requiresAlarmResponse) { }

    private record RunResult(String trace, int ticks, boolean complete,
                             String winner, String payloadPhase,
                             float payloadProgress, String payloadFailure,
                             boolean alarmRaised, int alarmPerspectiveEvents,
                             int sourceResponseActions,
                             int interdictionActions) { }
}
