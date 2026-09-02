package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.AssaultBattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in paired Assault command evidence through production construction. */
@Tag("assault-command-evidence")
class AssaultCommandEvidenceTest {

    private static final int DEFAULT_MAX_TICKS = 12_000;
    private static final String DEFAULT_FIXTURE =
            "/battle-fixtures/assault-command-duel-v1.json";

    @Test
    void writesByteStableForcedSerialAssaultCommandDuel() throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits(),
                "canonical command evidence must use the serial scheduler");
        int maxTicks = Integer.getInteger(
                "assault.command.evidence.maxTicks", DEFAULT_MAX_TICKS);
        if (maxTicks < 1) {
            throw new IllegalArgumentException(
                    "assault.command.evidence.maxTicks must be positive");
        }
        Path output = Path.of(System.getProperty(
                        "assault.command.evidence.outputDir",
                        "build/reports/commander/assault"))
                .toAbsolutePath().normalize();
        LoadedFixture loaded = loadFixture();
        int repeat = EvidenceFanOut.repeat();
        List<Callable<RunResult>> replays = new ArrayList<>(repeat);
        for (int replica = 0; replica < repeat; replica++) {
            // Only the replay whose trace is published records frames.
            Path visualRoot = replica == 0 ? output : null;
            replays.add(() -> run(loaded.fixture(), maxTicks, visualRoot,
                    "assault-command-duel"));
        }
        List<RunResult> results = EvidenceFanOut.run(replays);
        RunResult first = results.get(0);
        TraceMetrics firstMetrics = analyze(first.trace());
        EvidenceFanOut.assertReplaysByteStable("assault-command-duel",
                "command events",
                results.stream().map(RunResult::trace).toList());
        for (int replica = 1; replica < results.size(); replica++) {
            assertEquals(firstMetrics, analyze(results.get(replica).trace()),
                    "same Assault fixture must produce byte-stable command metrics");
        }
        assertTrue(first.trace().contains("\"perspective\":\"MARINE\""));
        assertTrue(first.trace().contains("\"perspective\":\"DEFENDER\""));
        assertTrue(first.trace().contains("\"assault\":{"));
        assertTrue(first.trace().contains("\"assaultDefense\":{"));
        assertTrue(firstMetrics.maxAssignedSearchSectors() > 0,
                "attacker commander must allocate live search coverage");
        assertTrue(firstMetrics.reportWithBoundedResponse(),
                "a legal defender report must produce a response order");
        assertTrue(firstMetrics.maxRespondersPerArea()
                        <= com.dillon.starsectormarines.battle.command
                        .AssaultDefenderCommand.MAX_RESPONDERS_PER_AREA,
                "defender response must remain bounded per area");
        assertTrue(firstMetrics.coverageSurvivedResponse(),
                "reported response must preserve unrelated-area coverage");
        if (maxTicks == DEFAULT_MAX_TICKS && !loaded.customFixture()) {
            assertTrue(firstMetrics.maxVisitedSearchLegs() > 0,
                    "canonical duration must show search-leg progress");
        }

        Path traces = output.resolve("traces");
        Files.createDirectories(traces);
        Files.writeString(traces.resolve("assault-command-duel.jsonl"),
                first.trace(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.json"),
                summaryJson(loaded, first, firstMetrics, maxTicks, repeat),
                StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),
                summaryMarkdown(loaded, first, firstMetrics, maxTicks, repeat),
                StandardCharsets.UTF_8);
        System.out.println("[assault-command-evidence] report "
                + output.resolve("summary.md"));
    }

    private static RunResult run(BattleFixture fixture, int maxTicks,
                                 Path visualRoot, String runId)
            throws Exception {
        try (BattleSimulation sim = fixture.build();
             CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                     visualRoot, runId, sim)) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                int before = sim.getSimTickIndex();
                sim.advance(BattleSimulation.TICK_DT);
                capture.afterAdvance();
                assertEquals(before + 1, sim.getSimTickIndex());
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            return new RunResult(sim.getCommandTraceJsonLines(),
                    sim.getSimTickIndex(), sim.isComplete(),
                    sim.getWinner() != null ? sim.getWinner().name() : null);
        }
    }

    private static LoadedFixture loadFixture() throws Exception {
        String selected = System.getProperty(
                "assault.command.evidence.fixture.path", "").trim();
        String json;
        String source;
        if (selected.isBlank()) {
            source = DEFAULT_FIXTURE;
            try (InputStream stream = AssaultCommandEvidenceTest.class
                    .getResourceAsStream(DEFAULT_FIXTURE)) {
                if (stream == null) throw new IllegalStateException(
                        "Missing Assault fixture: " + DEFAULT_FIXTURE);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            Path path = Path.of(selected).toAbsolutePath().normalize();
            source = path.toString();
            json = Files.readString(path);
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch
                ? launch.construction() : fixture;
        if (!(construction instanceof AssaultBattleFixture)) {
            throw new IllegalArgumentException(
                    "Assault evidence requires an Assault fixture: "
                            + fixture.kind());
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json.getBytes(StandardCharsets.UTF_8)));
        return new LoadedFixture(fixture, source, sha, !selected.isBlank());
    }

    private static String summaryJson(LoadedFixture fixture, RunResult run,
                                      TraceMetrics metrics,
                                      int maxTicks, int repeat)
            throws Exception {
        boolean canonical = maxTicks == DEFAULT_MAX_TICKS
                && !fixture.customFixture();
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("fixture", fixture.source())
                .put("fixtureSha256", fixture.sha256())
                .put("runMode", canonical ? "CANONICAL" : "AD_HOC")
                .put("maxTicks", maxTicks)
                .put("repeatCount", repeat)
                .put("termination", run.complete() ? "COMPLETE" : "TIMEOUT")
                .put("winner", run.winner() != null
                        ? run.winner() : JSONObject.NULL)
                .put("ticks", run.ticks())
                .put("traceEvents", lineCount(run.trace()))
                .put("metrics", metrics.toJson())
                .toString(2) + '\n';
    }

    private static String summaryMarkdown(LoadedFixture fixture, RunResult run,
                                          TraceMetrics metrics,
                                          int maxTicks, int repeat) {
        boolean canonical = maxTicks == DEFAULT_MAX_TICKS
                && !fixture.customFixture();
        return "# Assault commander evidence\n\n"
                + "Forced-serial, zero-input production construction "
                + EvidenceFanOut.replayWording(repeat) + ".\n\n"
                + "- Fixture: `" + fixture.source() + "`\n"
                + "- Fixture SHA-256: `" + fixture.sha256() + "`\n"
                + "- Run mode: " + (canonical ? "CANONICAL" : "AD_HOC") + "\n"
                + "- Maximum ticks: " + maxTicks + "\n"
                + "- Result: " + (run.complete() ? "COMPLETE" : "TIMEOUT")
                + "\n- Winner: " + (run.winner() != null ? run.winner() : "—")
                + "\n- Ticks: " + run.ticks()
                + "\n- Trace events: " + lineCount(run.trace())
                + "\n- Maximum assigned search sectors: "
                + metrics.maxAssignedSearchSectors()
                + "\n- Maximum visited search legs: "
                + metrics.maxVisitedSearchLegs()
                + "\n- Report produced bounded response: "
                + metrics.reportWithBoundedResponse()
                + "\n- Unrelated coverage survived response: "
                + metrics.coverageSurvivedResponse()
                + "\n- Maximum responders in one area: "
                + metrics.maxRespondersPerArea()
                + "\n- Observed responder release: "
                + metrics.responderReleased() + "\n";
    }

    private static TraceMetrics analyze(String trace) throws Exception {
        int maxAssigned = 0;
        int maxVisited = 0;
        int maxResponders = 0;
        boolean reportWithResponse = false;
        boolean coverageSurvived = false;
        boolean responderReleased = false;
        Set<Integer> priorResponders = Set.of();
        for (String line : trace.lines().toList()) {
            if (line.isBlank()) continue;
            JSONObject event = new JSONObject(line);
            JSONObject assault = event.optJSONObject("assault");
            if (assault != null) {
                int assigned = 0;
                JSONArray sectors = assault.getJSONArray("sectors");
                for (int i = 0; i < sectors.length(); i++) {
                    JSONObject sector = sectors.getJSONObject(i);
                    if (sector.getInt("assignedSquads") > 0) assigned++;
                    maxVisited = Math.max(maxVisited,
                            sector.getInt("visitedLegs"));
                }
                maxAssigned = Math.max(maxAssigned, assigned);
            }
            JSONObject defense = event.optJSONObject("assaultDefense");
            if (defense == null) continue;
            JSONArray areas = defense.getJSONArray("areas");
            boolean responseThisPulse = false;
            boolean unrelatedCoverage = false;
            for (int i = 0; i < areas.length(); i++) {
                JSONObject area = areas.getJSONObject(i);
                int responders = area.getInt("respondingSquads");
                maxResponders = Math.max(maxResponders, responders);
                if (!"QUIET".equals(area.getString("reportState"))
                        && responders > 0) {
                    reportWithResponse = true;
                    responseThisPulse = true;
                }
                if (responders == 0 && area.getInt("garrisonSquads")
                        + area.getInt("routineSquads") > 0) {
                    unrelatedCoverage = true;
                }
            }
            coverageSurvived |= responseThisPulse && unrelatedCoverage;
            Set<Integer> responders = new HashSet<>();
            Set<Integer> present = new HashSet<>();
            JSONArray actions = defense.getJSONArray("actions");
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                int squadId = action.getInt("squadId");
                present.add(squadId);
                if ("RESPONDER".equals(action.getString("role"))) {
                    responders.add(squadId);
                }
            }
            for (int squadId : priorResponders) {
                if (present.contains(squadId) && !responders.contains(squadId)) {
                    responderReleased = true;
                }
            }
            priorResponders = responders;
        }
        return new TraceMetrics(maxAssigned, maxVisited, maxResponders,
                reportWithResponse, coverageSurvived, responderReleased);
    }

    private static int lineCount(String value) {
        return (int) value.lines().count();
    }

    private record LoadedFixture(BattleFixture fixture, String source,
                                 String sha256, boolean customFixture) { }
    private record RunResult(String trace, int ticks, boolean complete,
                             String winner) { }
    private record TraceMetrics(int maxAssignedSearchSectors,
                                int maxVisitedSearchLegs,
                                int maxRespondersPerArea,
                                boolean reportWithBoundedResponse,
                                boolean coverageSurvivedResponse,
                                boolean responderReleased) {
        JSONObject toJson() throws Exception {
            return new JSONObject()
                    .put("maxAssignedSearchSectors", maxAssignedSearchSectors)
                    .put("maxVisitedSearchLegs", maxVisitedSearchLegs)
                    .put("maxRespondersPerArea", maxRespondersPerArea)
                    .put("reportWithBoundedResponse", reportWithBoundedResponse)
                    .put("coverageSurvivedResponse", coverageSurvivedResponse)
                    .put("responderReleased", responderReleased);
        }
    }
}
