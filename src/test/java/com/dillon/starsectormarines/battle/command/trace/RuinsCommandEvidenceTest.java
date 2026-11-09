package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.RuinsObjectiveDisclosure;
import com.dillon.starsectormarines.battle.command.RuinsObjectiveFacts;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in evidence that every Ruins grade sets up and can end ({@code ruins-expedition-battle.md}, slice 8): one fixture
 * per grade on the ruins ground, each from a few seeds. A run either sets up or fails to (its ground cannot be laid),
 * and a run that set up either ends (the haul boarded or lost, or the company beaten) or is stuck at the tick limit.
 *
 * <p>Nothing else is reported. The company here is Extraction's autopilot, not anything a player would do, so who
 * wins and how long it takes say nothing about the mission.
 */
@Tag("ruins-command-evidence")
class RuinsCommandEvidenceTest {
    private static final String PREFIX = "ruins.command.evidence";
    /** Fifteen minutes of battle: the vast grade's breach alone is ninety seconds. */
    private static final int DEFAULT_MAX_TICKS = 27_000;
    /** Seeds per grade, the fixture's own and the ones after it. */
    private static final int DEFAULT_SEEDS = 3;
    private static final List<String> DEFAULT_FIXTURES = List.of(
            "/battle-fixtures/ruins-scavengers-scattered-v1.json",
            "/battle-fixtures/ruins-scavengers-widespread-v1.json",
            "/battle-fixtures/ruins-scavengers-extensive-v1.json",
            "/battle-fixtures/ruins-scavengers-vast-v1.json");

    @Test
    void everyGradeSetsUpAndEnds() throws Exception {
        assertEquals(Integer.MAX_VALUE, UnitUpdateSystem.configuredMinimumParallelUnits());
        int maxTicks = positive(PREFIX + ".maxTicks", DEFAULT_MAX_TICKS);
        int seeds = positive(PREFIX + ".seeds", DEFAULT_SEEDS);
        Path output = Path.of(System.getProperty(PREFIX + ".outputDir", "build/reports/commander/ruins"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.resolve("traces"));

        String selected = System.getProperty(PREFIX + ".fixture.path", "").trim();
        List<String> locations = selected.isBlank() ? DEFAULT_FIXTURES : List.of(selected);
        List<RuinsBattleFixture> runs = new ArrayList<>();
        for (String location : locations) {
            RuinsBattleFixture fixture = load(location, selected.isBlank());
            for (int s = 0; s < seeds; s++) runs.add(reseeded(fixture, fixture.seed() + s));
        }
        List<Callable<RunResult>> calls = new ArrayList<>();
        for (RuinsBattleFixture fixture : runs) calls.add(() -> run(fixture, maxTicks));
        List<RunResult> results = EvidenceFanOut.run(calls);

        StringBuilder markdown = new StringBuilder()
                .append("# Ruins command evidence\n\n")
                .append("Whether each grade's battle sets up on the ruins ground, and whether it ends within ")
                .append(maxTicks).append(" ticks, from ").append(seeds).append(" seed(s) per grade.\n\n")
                .append("| Grade | Seed | Setup | End |\n")
                .append("|---|---:|---|---|\n");
        JSONArray rows = new JSONArray();
        List<String> setupFailures = new ArrayList<>();
        List<String> stuck = new ArrayList<>();
        for (RunResult result : results) {
            String id = result.grade().toLowerCase(Locale.ROOT) + "-" + result.seed();
            if (result.setupFailure() != null) {
                setupFailures.add(id + ": " + result.setupFailure());
            } else {
                Files.writeString(output.resolve("traces").resolve(id + ".jsonl"), result.trace(),
                        StandardCharsets.UTF_8);
                if (!result.ended()) stuck.add(id);
            }
            markdown.append('|').append(result.grade()).append('|').append(result.seed())
                    .append('|').append(result.setupFailure() == null ? "set up" : "failed: " + result.setupFailure())
                    .append('|').append(result.setupFailure() != null ? "-" : result.ended() ? "ended" : "stuck")
                    .append("|\n");
            rows.put(new JSONObject().put("grade", result.grade()).put("seed", result.seed())
                    .put("setUp", result.setupFailure() == null)
                    .put("setupFailure", result.setupFailure() == null ? JSONObject.NULL : result.setupFailure())
                    .put("ended", result.ended()));
        }
        Files.writeString(output.resolve("summary.json"), new JSONObject()
                .put("schemaVersion", 2).put("maxTicks", maxTicks).put("seedsPerGrade", seeds)
                .put("runs", rows).toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"), markdown.toString(), StandardCharsets.UTF_8);
        System.out.println("[ruins-command-evidence] report " + output.resolve("summary.md"));

        assertTrue(setupFailures.isEmpty(), "expeditions whose battle could not be set up: " + setupFailures);
        assertTrue(stuck.isEmpty(), "expeditions stuck at the tick limit: " + stuck);
    }

    private static RunResult run(RuinsBattleFixture fixture, int maxTicks) throws Exception {
        BattleSimulation built;
        try {
            built = fixture.build();
        } catch (RuntimeException setup) {
            // A ground that cannot be laid is reported beside the runs that played, not instead of them.
            return new RunResult(fixture.grade().name(), fixture.seed(), String.valueOf(setup.getMessage()), false, "");
        }
        try (BattleSimulation sim = built) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            sim.installFieldSalvage();
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            RuinsObjectiveFacts vault = RuinsObjectiveDisclosure.freeze(sim, Faction.MARINE);
            // Ended: the haul boarded or lost, or the battle decided some other way (the company beaten).
            boolean ended = vault.complete() || vault.failed() || sim.isComplete();
            return new RunResult(fixture.grade().name(), fixture.seed(), null, ended, sim.getCommandTraceJsonLines());
        }
    }

    private static RuinsBattleFixture reseeded(RuinsBattleFixture f, long seed) {
        return new RuinsBattleFixture(seed, f.manifest(), f.enemyHasHeavyArmor(), f.tier(), f.risk(),
                f.targetProfile(), f.marineFighterSupport(), f.enemyFighterSupport(), f.grade(), f.occupant());
    }

    private static RuinsBattleFixture load(String location, boolean classpath) throws Exception {
        String json;
        if (classpath) {
            try (InputStream stream = RuinsCommandEvidenceTest.class.getResourceAsStream(location)) {
                if (stream == null) throw new IllegalStateException("Missing Ruins fixture: " + location);
                json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            json = Files.readString(Path.of(location).toAbsolutePath().normalize());
        }
        BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
        BattleFixture construction = fixture instanceof BattleLaunchFixture launch ? launch.construction() : fixture;
        if (!(construction instanceof RuinsBattleFixture ruins)) {
            throw new IllegalArgumentException(location + " is not a Ruins fixture");
        }
        return ruins;
    }

    private static int positive(String property, int fallback) {
        String value = System.getProperty(property, "").trim();
        int parsed = value.isBlank() ? fallback : Integer.parseInt(value);
        if (parsed < 1) throw new IllegalArgumentException(property + " must be positive");
        return parsed;
    }

    /** @param setupFailure why the battle could not be set up, or null when it was */
    private record RunResult(String grade, long seed, String setupFailure, boolean ended, String trace) { }
}
