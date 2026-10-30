package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.mech.MechDuelRange;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Fights machines against machines, headless, and reports who won, how fast, and what it cost. The sibling of
 * {@link LoadoutMatchupEvidence} for the heavy-equipment progression: evidence rather than a test, asserting only that
 * the same fight played twice comes out the same (reported, not thrown).
 *
 * <p>The default ladder: every mech chassis at stock and at full refit against every other such fit, both ways round;
 * each stock chassis against each armed vehicle line, attacking and defending; every vehicle line against every other;
 * and stock Hound against stock Hound, the control the rows are read against (it shows what the ground does to two
 * equal machines). An explicit {@code attacker}/{@code defender} replaces the ladder. Sides are written as
 * {@link ArmorSide} parses them.
 *
 * <p>System properties (all optional): {@code armorMatchup.dir}, {@code .scenarios} (comma list of ids),
 * {@code .seeds} (count, default 2), {@code .parallelism} (default half the processors; 1 is the serial control),
 * {@code .ladder} (comma list of {@code control,mech,mixed,vehicle}), {@code .attacker} / {@code .defender}
 * (several sides separated by {@code ;} fight every pairing), {@code .gifs} ({@code true}/{@code false}/{@code auto}),
 * {@code .gifSeeds}.
 */
@Tag("armor-matchup")
class ArmorMatchupEvidence {

    private static final long SEED_BASE = 20261018L;

    /**
     * Vehicle lines the ladder fights: every hull a company or its enemy fields that carries a gun. An unarmed hull (the
     * scout car today) can only ever time out, so it is left out; name it explicitly to read how long it
     * lasts under fire. A hull that gains a turret joins the ladder on its own.
     */
    static final List<VehicleType> LADDER_VEHICLES = Stream.of(
                    VehicleType.HEAVY_APC, VehicleType.FIRE_SUPPORT_APC,
                    VehicleType.LRM_CARRIER, VehicleType.BREAKTHROUGH_TANK,
                    VehicleType.SCREENED_TANK, VehicleType.LASER_TANK,
                    VehicleType.DOZER_BREACHER, VehicleType.MILITIA_TECHNICAL, VehicleType.SCOUT_CAR,
                    VehicleType.LRM_CARRIER, VehicleType.SRM_TECHNICAL)
            .filter(VehicleType::hasTurretWeapon)
            .toList();

    private static Path gifDir;
    private static int gifSeeds;
    private static final Map<String, String> GIFS = new ConcurrentHashMap<>();

    @Test
    void fightsTheMatrix() throws Exception {
        Path out = Path.of(System.getProperty("armorMatchup.dir", "build/reports/armor-matchups"));
        Files.createDirectories(out);
        List<ArmorScenarios.ArmorScenario> scenarios = scenarios();
        List<Pairing> pairings = pairings();
        String gifSetting = System.getProperty("armorMatchup.gifs", "auto");
        boolean explicit = pairings.size() <= 4 && pairings.get(0).group.equals("explicit");
        boolean gifs = gifSetting.equals("true") || gifSetting.equals("auto") && explicit;
        gifDir = gifs ? out.resolve("gifs") : null;
        gifSeeds = Integer.getInteger("armorMatchup.gifSeeds", 1);
        GIFS.clear();
        int seedCount = Integer.getInteger("armorMatchup.seeds", 2);
        int parallelism = Integer.getInteger("armorMatchup.parallelism",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        List<Job> jobs = new ArrayList<>();
        for (Pairing pairing : pairings) {
            for (ArmorScenarios.ArmorScenario scenario : scenarios) {
                for (int s = 0; s < seedCount; s++) jobs.add(new Job(pairing, scenario, SEED_BASE + s, s));
            }
        }
        long started = System.nanoTime();
        List<ArmorPlay.Result> results = play(jobs, parallelism);
        String determinism = determinism(scenarios, parallelism);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;

        Map<String, List<ArmorPlay.Result>> grouped = new LinkedHashMap<>();
        Map<String, Pairing> pairingOf = new LinkedHashMap<>();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            String key = job.pairing.name + "|" + job.scenario.id();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(results.get(i));
            pairingOf.put(key, job.pairing);
        }
        List<ArmorReport.Row> rows = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (Map.Entry<String, List<ArmorPlay.Result>> e : grouped.entrySet()) {
            String gif = null;
            for (ArmorPlay.Result r : e.getValue()) {
                gif = GIFS.get(key(r));
                if (gif != null) break;
            }
            rows.add(ArmorReport.Row.of(pairingOf.get(e.getKey()).name, e.getValue(), gif));
            groups.add(pairingOf.get(e.getKey()).group);
        }
        Files.writeString(out.resolve("armor-matchups.md"),
                markdown(rows, groups, scenarios, seedCount, determinism, seconds));
        StringBuilder csv = new StringBuilder(ArmorReport.CSV_HEADER);
        for (ArmorReport.Row r : rows) csv.append(ArmorReport.csvRow(r));
        Files.writeString(out.resolve("armor-matchups.csv"), csv.toString());
        StringBuilder runs = new StringBuilder(ArmorReport.RUNS_HEADER);
        for (ArmorPlay.Result r : results) runs.append(ArmorReport.runRow(r, GIFS.get(key(r))));
        Files.writeString(out.resolve("runs.csv"), runs.toString());
        System.out.println("Armor matchups: " + out.toAbsolutePath() + " (" + results.size() + " fights, "
                + seconds + "s, " + determinism + ")");
    }

    // ---- matrix ----

    record Pairing(String name, String group, ArmorSide attacker, ArmorSide defender) {}

    private record Job(Pairing pairing, ArmorScenarios.ArmorScenario scenario, long seed, int seedIndex) {}

    private static List<ArmorScenarios.ArmorScenario> scenarios() {
        String selected = System.getProperty("armorMatchup.scenarios");
        if (selected == null || selected.isBlank() || selected.equals("all")) return ArmorScenarios.all();
        List<ArmorScenarios.ArmorScenario> chosen = new ArrayList<>();
        for (String id : selected.split(",")) chosen.add(ArmorScenarios.byId(id.trim()));
        return chosen;
    }

    private static List<Pairing> pairings() {
        String attacker = System.getProperty("armorMatchup.attacker");
        String defender = System.getProperty("armorMatchup.defender");
        if (attacker != null || defender != null) {
            List<Pairing> pairings = new ArrayList<>();
            for (String at : (attacker == null ? "hound" : attacker).split(";")) {
                for (String de : (defender == null ? "hound" : defender).split(";")) {
                    ArmorSide a = ArmorSide.parse(at.trim());
                    ArmorSide d = ArmorSide.parse(de.trim());
                    pairings.add(new Pairing(a.label() + " vs " + d.label(), "explicit", a, d));
                }
            }
            return pairings;
        }
        String ladder = System.getProperty("armorMatchup.ladder", "control,mech,mixed,vehicle");
        Set<String> groups = new HashSet<>();
        for (String g : ladder.split(",")) groups.add(g.trim().toLowerCase(Locale.ROOT));
        return ladder(groups);
    }

    /** The default ladder, restricted to {@code groups}. */
    static List<Pairing> ladder(Set<String> groups) {
        List<Pairing> pairings = new ArrayList<>();
        if (groups.contains("control")) pairings.add(pairing("control: ", "control", "hound", "hound"));
        List<String> fits = new ArrayList<>();
        for (MechVariant v : MechVariant.values()) {
            fits.add(v.id);
            fits.add(v.id + ":refit");
        }
        if (groups.contains("mech")) {
            for (String a : fits) for (String d : fits) if (!a.equals(d)) pairings.add(pairing("", "mech", a, d));
        }
        if (groups.contains("mixed")) {
            for (MechVariant v : MechVariant.values()) {
                for (VehicleType t : LADDER_VEHICLES) {
                    String vehicle = ArmorSide.vehicleId(t);
                    pairings.add(pairing("", "mixed", v.id, vehicle));
                    pairings.add(pairing("", "mixed", vehicle, v.id));
                }
            }
        }
        if (groups.contains("vehicle")) {
            for (VehicleType a : LADDER_VEHICLES) {
                for (VehicleType d : LADDER_VEHICLES) {
                    if (a != d) pairings.add(pairing("", "vehicle", ArmorSide.vehicleId(a), ArmorSide.vehicleId(d)));
                }
            }
        }
        if (pairings.isEmpty()) throw new IllegalArgumentException("Empty ladder: " + groups);
        return pairings;
    }

    private static Pairing pairing(String prefix, String group, String attacker, String defender) {
        ArmorSide a = ArmorSide.parse(attacker);
        ArmorSide d = ArmorSide.parse(defender);
        return new Pairing(prefix + a.label() + " vs " + d.label(), group, a, d);
    }

    // ---- playing ----

    private static List<ArmorPlay.Result> play(List<Job> jobs, int parallelism) throws Exception {
        List<ArmorPlay.Result> results = new ArrayList<>();
        if (jobs.isEmpty()) return results;
        // The first fight runs alone so every lazily loaded registry is loaded before fights share the process.
        results.add(run(jobs.get(0)));
        if (parallelism <= 1) {
            for (int i = 1; i < jobs.size(); i++) results.add(run(jobs.get(i)));
            return results;
        }
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<ArmorPlay.Result>> futures = new ArrayList<>();
            for (int i = 1; i < jobs.size(); i++) {
                Job job = jobs.get(i);
                futures.add(pool.submit(() -> run(job)));
            }
            for (Future<ArmorPlay.Result> f : futures) results.add(f.get());
        } finally {
            pool.shutdown();
        }
        return results;
    }

    private static ArmorPlay.Result run(Job job) throws Exception {
        if (gifDir == null || job.seedIndex < 0 || job.seedIndex >= gifSeeds) {
            return ArmorPlay.play(job.scenario, job.pairing.attacker, job.pairing.defender, job.seed, null);
        }
        String name = slug(job.pairing.name) + "-seed" + job.seed + ".gif";
        Path file = gifDir.resolve(job.scenario.id()).resolve(name);
        MatchupRecording recording = new MatchupRecording(Path.of("mod"), file,
                job.scenario.id() + "   " + job.pairing.name, job.scenario.annotations());
        ArmorPlay.Result result = ArmorPlay.play(job.scenario, job.pairing.attacker, job.pairing.defender,
                job.seed, recording);
        recording.write();
        GIFS.put(key(result), "gifs/" + job.scenario.id() + "/" + name);
        return result;
    }

    private static String key(ArmorPlay.Result r) {
        return r.scenario() + "|" + r.attacker() + "|" + r.defender() + "|" + r.seed();
    }

    static String slug(String text) {
        String slug = text.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.length() > 90 ? slug.substring(0, 90) : slug;
    }

    /**
     * The control fight replayed twice side by side in every scenario must agree to the last figure; a matrix whose
     * repeats disagree cannot say a fit changed anything. Reported rather than thrown, so the evidence is still written.
     */
    private static String determinism(List<ArmorScenarios.ArmorScenario> scenarios, int parallelism) throws Exception {
        Pairing control = pairing("control: ", "control", "hound", "hound");
        List<Job> twice = new ArrayList<>();
        for (ArmorScenarios.ArmorScenario scenario : scenarios) {
            twice.add(new Job(control, scenario, SEED_BASE, -1));
            twice.add(new Job(control, scenario, SEED_BASE, -1));
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Math.min(parallelism, twice.size())));
        List<ArmorPlay.Result> r = new ArrayList<>();
        try {
            List<Future<ArmorPlay.Result>> futures = new ArrayList<>();
            for (Job job : twice) futures.add(pool.submit(() -> run(job)));
            for (Future<ArmorPlay.Result> f : futures) r.add(f.get());
        } finally {
            pool.shutdown();
        }
        for (int i = 0; i < r.size(); i += 2) {
            if (!r.get(i).equals(r.get(i + 1))) {
                return "DETERMINISM FAILED in " + r.get(i).scenario() + ": " + r.get(i) + " vs " + r.get(i + 1);
            }
        }
        return "determinism ok (" + scenarios.size() + " control fights replayed twice, side by side)";
    }

    // ---- text ----

    private static String markdown(List<ArmorReport.Row> rows, List<String> groups,
                                   List<ArmorScenarios.ArmorScenario> scenarios, int seeds,
                                   String determinism, long seconds) {
        StringBuilder md = new StringBuilder("# Armor matchups\n\n")
                .append("Generated by `gradlew armorMatchup` in ").append(seconds).append("s. ")
                .append(determinism).append(".\n\n")
                .append("Machines against machines. Attackers start at the west edge and are driven to 70% of their ")
                .append("longest reach off the nearest enemy; inside reach it is production doctrine (attacking lances' ")
                .append("order `").append(ArmorPlay.MECH_ORDER).append("`, mech duel range ")
                .append(MechDuelRange.isEnabled() ? "on" : "off").append("), ")
                .append("defenders hold eighty cells east, set down facing the attackers")
                .append(ArmorPlay.DEFENDER_FACING == 0f ? "" : " turned " + ArmorPlay.DEFENDER_FACING + "°")
                .append(ArmorPlay.ATTACKER_SPREAD == 0f ? ""
                        : ", attackers fanned across " + ArmorPlay.ATTACKER_SPREAD + "° around them"
                        + (ArmorPlay.ATTACKER_RADIUS > 0f ? " within " + ArmorPlay.ATTACKER_RADIUS + " cells" : ""))
                .append(". Each row is the mean of ").append(seeds)
                .append(" seeded fights. **Read rows against the control row** (stock Hound vs stock Hound): the ")
                .append("ground favours one side. A win is the other side destroyed; a stalemate is forty seconds ")
                .append("without damage after the first hit, a timeout the tick limit. TTK is seconds from the first ")
                .append("hit to the decision, over decided fights. Range is the median distance between the nearest two ")
                .append("opposing machines from the first hit on, the range the fight was fought at. Left is machines standing; integrity is the ")
                .append("armor+structure a side kept. Damage by weapon is the side's dealt damage apportioned by ")
                .append("hits times nominal damage (a contact warhead counted at its payload), with each weapon's hit rate; flux-limited is seconds a mech ")
                .append("spent holding fire to dissipate or venting (autonomous fire held). Per-fight detail, survivors' sections ")
                .append("included, is in `runs.csv`. Counts are one machine a side unless the matchup says `Nx`; ")
                .append("no cost or lift parity is applied.\n\nScenarios:\n");
        for (ArmorScenarios.ArmorScenario s : scenarios) {
            md.append("- `").append(s.id()).append("` — ").append(s.label()).append('\n');
        }
        for (ArmorScenarios.ArmorScenario s : scenarios) {
            md.append("\n## ").append(s.id()).append('\n');
            String lastGroup = null;
            for (int i = 0; i < rows.size(); i++) {
                ArmorReport.Row r = rows.get(i);
                if (!r.scenario().equals(s.id())) continue;
                if (!groups.get(i).equals(lastGroup)) {
                    lastGroup = groups.get(i);
                    md.append("\n### ").append(lastGroup).append("\n\n").append(ArmorReport.markdownHeader());
                }
                md.append(ArmorReport.markdownRow(r));
            }
        }
        return md.toString();
    }
}
