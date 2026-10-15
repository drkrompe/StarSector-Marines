package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCards;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fights loadouts against each other, headless, and reports who won and at what cost. Evidence rather than a test:
 * it asserts only that the same fight played twice comes out the same.
 *
 * <p>The default matrix is a ladder. Every weapon card (on starter armor) and every tactic sheet (with starter
 * weapons) fights the starter issue, once attacking and once defending, in each scenario at several seeds. Starter
 * against starter is the control every row is read against: a card is strong or weak <em>relative to it</em>, and
 * the control itself shows what the ground does to two equal squads. An explicit matchup replaces the ladder.
 *
 * <p>System properties (all optional): {@code loadoutMatchup.dir}, {@code .scenarios} (comma list of ids),
 * {@code .seeds} (count, default 3), {@code .parallelism} (default half the processors; 1 is the serial control),
 * {@code .attacker} / {@code .defender} ({@code weapons:x+armor:y}).
 */
@Tag("loadout-matchup")
class LoadoutMatchupEvidence {

    private static final long SEED_BASE = 20261001L;

    @Test
    void fightsTheMatrix() throws Exception {
        Path out = Path.of(System.getProperty("loadoutMatchup.dir", "build/reports/loadout-matchups"));
        Files.createDirectories(out);
        List<MatchupScenario> scenarios = scenarios();
        int seedCount = Integer.getInteger("loadoutMatchup.seeds", 3);
        int parallelism = Integer.getInteger("loadoutMatchup.parallelism",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        List<Pairing> pairings = pairings();
        List<Job> jobs = new ArrayList<>();
        for (Pairing pairing : pairings) {
            for (MatchupScenario scenario : scenarios) {
                for (int s = 0; s < seedCount; s++) {
                    jobs.add(new Job(pairing, scenario, SEED_BASE + s));
                }
            }
        }
        long started = System.nanoTime();
        List<MatchupPlay.Result> results = play(jobs, parallelism);
        String determinism = determinism(scenarios, parallelism);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;

        Map<String, List<MatchupPlay.Result>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            grouped.computeIfAbsent(job.pairing.name + "|" + job.scenario.id(), k -> new ArrayList<>())
                    .add(results.get(i));
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<MatchupPlay.Result>> e : grouped.entrySet()) {
            rows.add(Row.of(e.getKey().substring(0, e.getKey().indexOf('|')), e.getValue()));
        }

        Files.writeString(out.resolve("matchups.md"), markdown(rows, scenarios, seedCount, determinism, seconds));
        Files.writeString(out.resolve("matchups.csv"), csv(rows));
        Files.writeString(out.resolve("runs.csv"), runs(results));
        System.out.println("Loadout matchups: " + out.toAbsolutePath() + " (" + results.size() + " fights, "
                + seconds + "s, " + determinism + ")");
    }

    // ---- matrix ----

    private record Pairing(String name, String role, MatchupSide attacker, MatchupSide defender) {}

    private record Job(Pairing pairing, MatchupScenario scenario, long seed) {}

    private static List<MatchupScenario> scenarios() {
        String selected = System.getProperty("loadoutMatchup.scenarios");
        if (selected == null || selected.isBlank() || selected.equals("all")) return MatchupScenarios.all();
        List<MatchupScenario> chosen = new ArrayList<>();
        for (String id : selected.split(",")) chosen.add(MatchupScenarios.byId(id.trim()));
        return chosen;
    }

    private static List<Pairing> pairings() {
        String attacker = System.getProperty("loadoutMatchup.attacker");
        String defender = System.getProperty("loadoutMatchup.defender");
        List<Pairing> pairings = new ArrayList<>();
        if (attacker != null || defender != null) {
            MatchupSide a = attacker == null ? MatchupSide.STARTER : MatchupSide.parse(attacker);
            MatchupSide d = defender == null ? MatchupSide.STARTER : MatchupSide.parse(defender);
            pairings.add(new Pairing(a.label() + " vs " + d.label(), "explicit", a, d));
            return pairings;
        }
        MatchupSide starter = MatchupSide.STARTER;
        pairings.add(new Pairing("control: starter vs starter", "control", starter, starter));
        for (LoadoutCard card : LoadoutCards.all()) {
            MatchupSide subject = card.kind() == LoadoutCard.Kind.WEAPON
                    ? new MatchupSide(card.definitionId(), starter.sheetId())
                    : new MatchupSide(starter.weaponsId(), card.definitionId());
            pairings.add(new Pairing(card.displayName() + " (" + card.id() + ") attacking", "attacker", subject, starter));
            pairings.add(new Pairing(card.displayName() + " (" + card.id() + ") defending", "defender", starter, subject));
        }
        return pairings;
    }

    // ---- playing ----

    private static List<MatchupPlay.Result> play(List<Job> jobs, int parallelism) throws Exception {
        List<MatchupPlay.Result> results = new ArrayList<>();
        if (parallelism <= 1) {
            for (Job job : jobs) results.add(run(job));
            return results;
        }
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<MatchupPlay.Result>> futures = new ArrayList<>();
            for (Job job : jobs) futures.add(pool.submit(() -> run(job)));
            for (Future<MatchupPlay.Result> f : futures) results.add(f.get());
        } finally {
            pool.shutdown();
        }
        return results;
    }

    private static MatchupPlay.Result run(Job job) {
        return MatchupPlay.play(job.scenario, job.pairing.attacker, job.pairing.defender, job.seed);
    }

    /**
     * The same control fight replayed twice side by side must agree to the last figure; a matrix whose repeats
     * disagree cannot say a card changed anything. Reported rather than thrown, so the evidence is still written.
     */
    private static String determinism(List<MatchupScenario> scenarios, int parallelism) throws Exception {
        List<Job> twice = new ArrayList<>();
        Pairing control = new Pairing("control", "control", MatchupSide.STARTER, MatchupSide.STARTER);
        for (MatchupScenario scenario : scenarios) {
            twice.add(new Job(control, scenario, SEED_BASE));
            twice.add(new Job(control, scenario, SEED_BASE));
        }
        List<MatchupPlay.Result> r = play(twice, Math.max(2, parallelism));
        for (int i = 0; i < r.size(); i += 2) {
            if (!r.get(i).equals(r.get(i + 1))) {
                return "DETERMINISM FAILED in " + r.get(i).scenario() + ": " + r.get(i) + " vs " + r.get(i + 1);
            }
        }
        return "determinism ok (" + scenarios.size() + " control fights replayed twice, side by side)";
    }

    // ---- aggregation ----

    private record Row(String pairing, String scenario, String attacker, String defender, int fights,
                       int attackerWins, int defenderWins, int mutual, int stalemate, int unresolved,
                       float attackerSurvivors, float defenderSurvivors, float meanTicks,
                       float attackerDamageTaken, float defenderDamageTaken, float exchange) {

        static Row of(String pairing, List<MatchupPlay.Result> r) {
            int aw = 0, dw = 0, bd = 0, sm = 0, un = 0;
            float as = 0, ds = 0, ticks = 0, at = 0, dt = 0, ex = 0;
            for (MatchupPlay.Result x : r) {
                switch (x.winner()) {
                    case "ATTACKERS" -> aw++;
                    case "DEFENDERS" -> dw++;
                    case "BOTH_DESTROYED" -> bd++;
                    case "STALEMATE" -> sm++;
                    default -> un++;
                }
                as += x.attackerSurvivors();
                ds += x.defenderSurvivors();
                ticks += x.ticks();
                at += x.attackerDamageTaken();
                dt += x.defenderDamageTaken();
                ex += x.exchange();
            }
            int n = r.size();
            MatchupPlay.Result first = r.get(0);
            return new Row(pairing, first.scenario(), first.attacker(), first.defender(), n, aw, dw, bd, sm, un,
                    as / n, ds / n, ticks / n, at / n, dt / n, ex / n);
        }
    }

    private static String markdown(List<Row> rows, List<MatchupScenario> scenarios, int seeds,
                                   String determinism, long seconds) {
        StringBuilder md = new StringBuilder("# Loadout matchups\n\n")
                .append("Generated by `gradlew loadoutMatchup` in ").append(seconds).append("s. ")
                .append(determinism).append(".\n\n")
                .append("Twelve regular marines a side, kit exactly as the cards issue it. Each row is the mean of ")
                .append(seeds).append(" seeded fights; the seed changes every hit and morale roll and nothing else. ")
                .append("**Read each row against the control row**, not in isolation: a scenario's ground favours ")
                .append("one side, and only the difference from starter-vs-starter is the card. A win is the other ")
                .append("side wiped out; a stalemate is both sides standing with no damage for forty seconds and a timeout is the tick limit. Neither is a draw: read the survivors. Survivors ")
                .append("are of twelve. Exchange is enemy killed per own lost. Behaviour tuning shows here: a row ")
                .append("that moves a lot between two builds is a loadout whose result depends on a behaviour.\n\n")
                .append("Scenarios:\n");
        for (MatchupScenario s : scenarios) md.append("- `").append(s.id()).append("` — ").append(s.label()).append('\n');
        for (MatchupScenario s : scenarios) {
            md.append("\n## ").append(s.id()).append("\n\n")
                    .append("| Matchup | Att wins | Def wins | Both dead | Stalemate | Timeout | Att left | Def left | Exchange | Ticks | Att dmg taken | Def dmg taken |\n")
                    .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (Row r : rows) {
                if (!r.scenario.equals(s.id())) continue;
                md.append("| ").append(r.pairing).append(" | ").append(r.attackerWins).append("/").append(r.fights)
                        .append(" | ").append(r.defenderWins).append("/").append(r.fights)
                        .append(" | ").append(r.mutual).append(" | ").append(r.stalemate).append(" | ").append(r.unresolved)
                        .append(" | ").append(f1(r.attackerSurvivors)).append(" | ").append(f1(r.defenderSurvivors))
                        .append(" | ").append(f1(r.exchange)).append(" | ").append(Math.round(r.meanTicks))
                        .append(" | ").append(Math.round(r.attackerDamageTaken))
                        .append(" | ").append(Math.round(r.defenderDamageTaken)).append(" |\n");
            }
        }
        return md.toString();
    }

    private static String csv(List<Row> rows) {
        StringBuilder csv = new StringBuilder("pairing,scenario,attacker,defender,fights,attacker_wins,defender_wins,"
                + "both_destroyed,stalemate,timeout,attacker_survivors,defender_survivors,exchange,mean_ticks,"
                + "attacker_damage_taken,defender_damage_taken\n");
        for (Row r : rows) {
            csv.append(q(r.pairing)).append(',').append(r.scenario).append(',').append(q(r.attacker)).append(',')
                    .append(q(r.defender)).append(',').append(r.fights).append(',').append(r.attackerWins).append(',')
                    .append(r.defenderWins).append(',').append(r.mutual).append(',').append(r.stalemate).append(',').append(r.unresolved).append(',')
                    .append(f1(r.attackerSurvivors)).append(',').append(f1(r.defenderSurvivors)).append(',')
                    .append(f1(r.exchange)).append(',').append(Math.round(r.meanTicks)).append(',')
                    .append(Math.round(r.attackerDamageTaken)).append(',')
                    .append(Math.round(r.defenderDamageTaken)).append('\n');
        }
        return csv.toString();
    }

    private static String runs(List<MatchupPlay.Result> results) {
        StringBuilder csv = new StringBuilder("scenario,attacker,defender,seed,winner,ticks,attacker_survivors,"
                + "defender_survivors,attacker_damage_taken,defender_damage_taken,attacker_damage_mitigated,"
                + "defender_damage_mitigated,attacker_rounds,defender_rounds\n");
        for (MatchupPlay.Result r : results) {
            csv.append(r.scenario()).append(',').append(q(r.attacker())).append(',').append(q(r.defender()))
                    .append(',').append(r.seed()).append(',').append(r.winner()).append(',').append(r.ticks())
                    .append(',').append(r.attackerSurvivors()).append(',').append(r.defenderSurvivors())
                    .append(',').append(f1(r.attackerDamageTaken())).append(',').append(f1(r.defenderDamageTaken()))
                    .append(',').append(f1(r.attackerDamageMitigated())).append(',')
                    .append(f1(r.defenderDamageMitigated())).append(',').append(r.attackerRounds())
                    .append(',').append(r.defenderRounds()).append('\n');
        }
        return csv.toString();
    }

    private static String q(String s) { return '"' + s.replace("\"", "\"\"") + '"'; }

    private static String f1(float v) { return String.format(Locale.ROOT, "%.1f", v); }
}
