package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in paired Raid command evidence through production construction. */
@Tag("raid-command-evidence")
class RaidCommandEvidenceTest {
    private static final int DEFAULT_MAX_TICKS = 12_000;
    private static final String DEFAULT_FIXTURE =
            "/battle-fixtures/raid-command-duel-v1.json";

    @Test
    void writesByteStableForcedSerialRaidCommandDuel() throws Exception {
        assertEquals(Integer.MAX_VALUE,
                UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = Integer.getInteger(
                "raid.command.evidence.maxTicks", DEFAULT_MAX_TICKS);
        if (maxTicks < 1) throw new IllegalArgumentException(
                "raid.command.evidence.maxTicks must be positive");
        Path output = Path.of(System.getProperty(
                "raid.command.evidence.outputDir",
                "build/reports/commander/raid")).toAbsolutePath().normalize();
        BattleFixture fixture = loadFixture();
        int repeat = EvidenceFanOut.repeat();
        List<Callable<RunResult>> replays = new ArrayList<>(repeat);
        for (int replica = 0; replica < repeat; replica++) {
            // Only the replay whose trace is published records frames.
            Path visualRoot = replica == 0 ? output : null;
            replays.add(() -> run(fixture, maxTicks, visualRoot,
                    "raid-command-duel"));
        }
        List<RunResult> results = EvidenceFanOut.run(replays);
        RunResult first = results.get(0);
        EvidenceFanOut.assertReplaysByteStable("raid-command-duel",
                "command events",
                results.stream().map(RunResult::trace).toList());
        assertTrue(first.trace().contains("\"strategy\":\"raid-attacker\""));
        assertTrue(first.trace().contains("\"strategy\":\"raid-defender\""));
        assertTrue(first.trace().contains("\"raid\":{"));
        assertTrue(first.trace().contains("\"targetId\":\"RAID-01\""));
        assertTrue(first.trace().contains("\"egressCellX\":-1"),
                "defender trace must preserve egress non-disclosure");

        Files.createDirectories(output.resolve("traces"));
        Files.writeString(output.resolve("traces/raid-command-duel.jsonl"),
                first.trace(), StandardCharsets.UTF_8);
        JSONObject summary = new JSONObject()
                .put("schemaVersion", 1)
                .put("schedulerMode", "SERIAL_DETERMINISTIC")
                .put("fixture", DEFAULT_FIXTURE)
                .put("maxTicks", maxTicks)
                .put("repeatCount", repeat)
                .put("termination", first.complete() ? "COMPLETE" : "TIMEOUT")
                .put("winner", first.winner() != null
                        ? first.winner() : JSONObject.NULL)
                .put("ticks", first.ticks())
                .put("traceEvents", first.trace().lines().count());
        Files.writeString(output.resolve("summary.json"),
                summary.toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"),
                "# Raid commander evidence\n\n"
                        + "Forced-serial, zero-input production Raid "
                        + EvidenceFanOut.replayWording(repeat) + ".\n\n"
                        + "- Maximum ticks: " + maxTicks + "\n"
                        + "- Result: " + (first.complete() ? "COMPLETE" : "TIMEOUT") + "\n"
                        + "- Winner: " + (first.winner() != null ? first.winner() : "—") + "\n"
                        + "- Ticks: " + first.ticks() + "\n",
                StandardCharsets.UTF_8);
        System.out.println("[raid-command-evidence] report "
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
                sim.advance(BattleSimulation.TICK_DT);
                capture.afterAdvance();
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            return new RunResult(sim.getCommandTraceJsonLines(),
                    sim.getSimTickIndex(), sim.isComplete(),
                    sim.getWinner() != null ? sim.getWinner().name() : null);
        }
    }

    private static BattleFixture loadFixture() throws Exception {
        String selected = System.getProperty(
                "raid.command.evidence.fixture.path", "").trim();
        String json;
        if (selected.isBlank()) {
            try (InputStream stream = RaidCommandEvidenceTest.class
                    .getResourceAsStream(DEFAULT_FIXTURE)) {
                if (stream == null) throw new IllegalStateException(
                        "Missing Raid fixture: " + DEFAULT_FIXTURE);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(selected).toAbsolutePath().normalize());
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch
                ? launch.construction() : fixture;
        if (!(construction instanceof RaidBattleFixture)) {
            throw new IllegalArgumentException(
                    "Raid evidence requires a Raid fixture: " + fixture.kind());
        }
        return fixture;
    }

    private record RunResult(String trace, int ticks, boolean complete,
                             String winner) { }
}
