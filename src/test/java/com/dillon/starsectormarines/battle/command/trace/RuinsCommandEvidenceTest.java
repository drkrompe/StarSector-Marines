package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.RuinsObjectiveDisclosure;
import com.dillon.starsectormarines.battle.command.RuinsObjectiveFacts;
import com.dillon.starsectormarines.battle.decision.UnitUpdateSystem;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.RuinsBattleFixture;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
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
 * Opt-in evidence that the Ruins family plays to an end ({@code ruins-expedition-battle.md}, slice 8): one fixture per
 * grade on the ruins ground, each played headless from a few seeds. Every run must end with the haul boarded or the
 * expedition lost through one of the family's terminals (the haul failed, or the company beaten); a run that only
 * times out fails. It proves the mission works, not that it is hard enough: the company is the autopilot here, and
 * balance is tuned against a player.
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
    void everyGradePlaysToOneOfTheFamilysTerminals() throws Exception {
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
                .append("Forced-serial, zero-input Ruins expeditions on the ruins ground: Extraction's commanders ")
                .append("play the company, the occupant's side plays as the battle installs it. ")
                .append(seeds).append(" seed(s) per grade, at most ").append(maxTicks).append(" ticks.\n\n")
                .append("| Grade | Occupant | Seed | Result | Seconds | Haul | Breach | Caches secured |\n")
                .append("|---|---|---:|---|---:|---|---:|---:|\n");
        JSONArray rows = new JSONArray();
        List<String> undecided = new ArrayList<>();
        List<String> setupFailures = new ArrayList<>();
        for (RunResult result : results) {
            String id = result.grade().toLowerCase(Locale.ROOT) + "-" + result.seed();
            Files.writeString(output.resolve("traces").resolve(id + ".jsonl"), result.trace(), StandardCharsets.UTF_8);
            if (result.outcome().equals("TIMEOUT")) undecided.add(id);
            if (result.outcome().equals("SETUP_FAILED")) setupFailures.add(id + ": " + result.failure());
            markdown.append('|').append(result.grade()).append('|').append(result.occupant())
                    .append('|').append(result.seed()).append('|').append(result.outcome())
                    .append('|').append(Math.round(result.ticks() * BattleSimulation.TICK_DT))
                    .append('|').append(result.phase()).append(result.failure().equals("NONE") ? "" : " (" + result.failure() + ")")
                    .append('|').append(Math.round(result.breachProgress() * 100f)).append('%')
                    .append('|').append(result.cachesSecured()).append('/').append(result.caches())
                    .append("|\n");
            rows.put(new JSONObject()
                    .put("grade", result.grade()).put("occupant", result.occupant()).put("seed", result.seed())
                    .put("outcome", result.outcome()).put("winner", result.winner() == null ? JSONObject.NULL : result.winner())
                    .put("ticks", result.ticks()).put("phase", result.phase()).put("failure", result.failure())
                    .put("breachProgress", result.breachProgress())
                    .put("caches", result.caches()).put("cachesSecured", result.cachesSecured()));
        }
        markdown.append('\n');
        for (String grade : results.stream().map(RunResult::grade).distinct().toList()) {
            List<RunResult> ofGrade = results.stream().filter(r -> r.grade().equals(grade))
                    .filter(r -> !r.outcome().equals("SETUP_FAILED")).toList();
            long won = ofGrade.stream().filter(r -> r.outcome().equals("HAUL_BOARDED")).count();
            double meanSeconds = ofGrade.stream().mapToInt(RunResult::ticks).average().orElse(0) * BattleSimulation.TICK_DT;
            markdown.append("- ").append(grade).append(": ").append(won).append('/').append(ofGrade.size())
                    .append(" boarded, mean ").append(Math.round(meanSeconds)).append(" s\n");
        }
        Files.writeString(output.resolve("summary.json"), new JSONObject()
                .put("schemaVersion", 1).put("maxTicks", maxTicks).put("seedsPerGrade", seeds)
                .put("runs", rows).toString(2) + '\n', StandardCharsets.UTF_8);
        Files.writeString(output.resolve("summary.md"), markdown.toString(), StandardCharsets.UTF_8);
        System.out.println("[ruins-command-evidence] report " + output.resolve("summary.md"));

        assertTrue(setupFailures.isEmpty(), "expeditions whose battle could not be set up: " + setupFailures);
        assertTrue(undecided.isEmpty(), "expeditions that reached no terminal: " + undecided);
    }

    private static RunResult run(RuinsBattleFixture fixture, int maxTicks) throws Exception {
        BattleSimulation built;
        try {
            built = fixture.build();
        } catch (RuntimeException setup) {
            // A ground that cannot be laid is a setup failure: report it beside the runs that played.
            return new RunResult(fixture.grade().name(), fixture.occupant().name(), fixture.seed(), "SETUP_FAILED",
                    null, 0, "-", String.valueOf(setup.getMessage()), 0f, 0, 0, "");
        }
        try (BattleSimulation sim = built) {
            sim.setCommandTraceEnabled(true, fixture.kind());
            sim.installFieldSalvage();
            while (!sim.isComplete() && sim.getSimTickIndex() < maxTicks) {
                sim.advance(BattleSimulation.TICK_DT);
            }
            if (!sim.isComplete()) sim.recordCommandTraceTimeout(maxTicks);
            RuinsObjectiveFacts vault = RuinsObjectiveDisclosure.freeze(sim, Faction.MARINE);
            String outcome;
            if (vault.complete()) outcome = "HAUL_BOARDED";
            else if (vault.failed()) outcome = "HAUL_LOST";
            else if (sim.isComplete()) outcome = "COMPANY_BEATEN";
            else outcome = "TIMEOUT";
            List<SalvageCache> caches = sim.fieldSalvage().caches();
            return new RunResult(fixture.grade().name(), fixture.occupant().name(), fixture.seed(), outcome,
                    sim.getWinner() != null ? sim.getWinner().name() : null, sim.getSimTickIndex(),
                    vault.phase(), vault.failure() != null ? vault.failure().name() : "NONE",
                    vault.breachDuration() > 0f ? vault.breachProgress() / vault.breachDuration() : 0f,
                    caches.size(), (int) caches.stream().filter(SalvageCache::secured).count(),
                    sim.getCommandTraceJsonLines());
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

    private record RunResult(String grade, String occupant, long seed, String outcome, String winner, int ticks,
                             String phase, String failure, float breachProgress, int caches, int cachesSecured,
                             String trace) { }
}
