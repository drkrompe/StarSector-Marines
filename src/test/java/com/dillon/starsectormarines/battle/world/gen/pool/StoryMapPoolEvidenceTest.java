package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.cordon.CordonCorridor;
import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonRedoubt;
import com.dillon.starsectormarines.battle.cordon.CordonSequence;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.setup.BloomHeartSetup;
import com.dillon.starsectormarines.battle.cordon.CordonGapLaw;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regeneration check ({@code story-map-pools.md}): every pooled seed of
 * every story map is generated again, with its patch, and must still pass its
 * mission's acceptance on the first generation and stand its battle up. Run by
 * {@code storyMapPools}; with {@code -Pcandidates} it also screens seeds that
 * are not pooled yet, reported without failing.
 */
@Tag("story-map-pool-evidence")
class StoryMapPoolEvidenceTest {

    @Test
    void everyPooledSeedStillCarriesItsMission() throws Exception {
        Path output = Path.of(System.getProperty("storyMapPools.outputDir", "build/reports/story-map-pools"));
        int parallelism = Integer.getInteger("storyMapPools.parallelism", 2);
        List<Job> jobs = new ArrayList<>();
        for (StoryMap map : StoryMap.values()) {
            for (SurfacePalette biome : SurfacePalette.values()) {
                for (StoryMapPool.Entry entry : StoryMapPools.pool(map).entries(biome)) {
                    jobs.add(new Job(map, biome, entry, true));
                }
            }
        }
        jobs.addAll(candidates(System.getProperty("storyMapPools.candidates", "")));

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
        List<Future<Row>> futures = new ArrayList<>();
        try {
            for (Job job : jobs) futures.add(pool.submit(() -> check(job)));
            List<Row> rows = new ArrayList<>();
            for (Future<Row> f : futures) rows.add(f.get());
            Files.createDirectories(output);
            Files.writeString(output.resolve("summary.md"), report(rows), StandardCharsets.UTF_8);
            System.out.println("[story-map-pools] report " + output.resolve("summary.md").toAbsolutePath());
            List<String> broken = new ArrayList<>();
            for (Row row : rows) if (row.job().pooled() && row.failure() != null) broken.add(row.label() + ": " + row.failure());
            assertTrue(broken.isEmpty(), "pooled seeds no longer carry their mission: " + broken);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Job(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry, boolean pooled) { }

    private record Row(Job job, String failure, String detail, long millis) {
        String label() { return job.map().id + " " + job.biome() + " " + job.entry().seed(); }
    }

    private static Row check(Job job) {
        long start = System.nanoTime();
        String failure = null, detail = "";
        try {
            Form form = BloomHeartSetup.formOf(job.map());
            detail = form != null ? heart(job.map(), job.biome(), job.entry()) : cordon(job.map(), job.biome(), job.entry());
        } catch (Exception | AssertionError e) {
            failure = String.valueOf(e.getMessage());
        }
        return new Row(job, failure, detail, (System.nanoTime() - start) / 1_000_000L);
    }

    /** Generates the pooled map (which throws on a failed acceptance), checks the line and stands the battle up. */
    private static String cordon(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry) throws Exception {
        CordonVariant variant = CordonSetup.variantOf(map);
        CordonSetup.CordonMap cordon = CordonSetup.map(variant, CordonSetup.densityOf(map), biome, entry);
        List<Precinct> redoubts = CordonPlan.redoubts(cordon.plan());
        if (redoubts.size() != CordonPlan.REDOUBTS) throw new AssertionError(redoubts.size() + " redoubts");
        for (int i = 1; i < redoubts.size(); i++) {
            if (redoubts.get(i).seedX() <= redoubts.get(i - 1).seedX()) throw new AssertionError("redoubts out of order");
        }
        int spread = redoubts.get(redoubts.size() - 1).seedX() - redoubts.get(0).seedX();
        if (spread <= CordonSetup.WIDTH / 2) throw new AssertionError("the line spans only " + spread + " cells");
        Precinct base = null;
        for (Precinct p : cordon.plan().precincts()) if (p.name().equals(CordonPlan.BASE_PLACE)) base = p;
        String detail = String.format(Locale.ROOT, "rear %s; base %d,%d; line row %d to %d",
                cordon.plan().attackerFrom().y0() == 0f ? "south" : "north", base.seedX(), base.seedY(),
                redoubts.stream().mapToInt(Precinct::seedY).min().orElse(0),
                redoubts.stream().mapToInt(Precinct::seedY).max().orElse(0));
        if (cordon.corridor() != null) {
            detail += String.format(Locale.ROOT, "; corridor %d cells, %.0f%% on road", cordon.corridor().size(),
                    100f * CordonCorridor.pavedShare(cordon.map().topology, cordon.corridor()));
        }
        StringBuilder works = new StringBuilder();
        for (Laid laid : cordon.map().layout.pieces()) {
            works.append(works.length() == 0 ? "" : ", ").append(laid.name()).append(' ').append(laid.status().name().toLowerCase(Locale.ROOT));
        }
        detail += "; layout " + works;
        StringBuilder gaps = new StringBuilder();
        for (CordonGapLaw.Gap gap : CordonSetup.gaps(cordon.map(), cordon.plan())) {
            gaps.append(gaps.length() == 0 ? "" : " ").append(String.format(Locale.ROOT, "%d-%d %.0f%s (towers %.0f)",
                    gap.west(), gap.east(), gap.widestStep(), gap.holds() ? "" : "!", gap.towerToTower()));
        }
        detail += String.format(Locale.ROOT, "; gaps %s, law %.1f", gaps, CordonGapLaw.LIMIT);
        try (BattleSimulation sim = CordonSetup.create(cordon, variant, List.of(), CordonSequence.Timetable.STANDARD)) {
            if (sim.getCordon() == null) throw new AssertionError("the battle has no cordon");
            StringBuilder guns = new StringBuilder();
            for (CordonRedoubt r : sim.getCordon().redoubts()) guns.append(guns.length() == 0 ? "" : "-").append(r.guns().size());
            detail += "; redoubt guns " + guns;
        }
        return detail;
    }

    private static String heart(StoryMap map, SurfacePalette biome, StoryMapPool.Entry entry) throws Exception {
        BloomHeartSetup.Built built = BloomHeartSetup.country(BloomHeartSetup.formOf(map), biome,
                BloomHeartSetup.densityOf(map), entry);
        try (BattleSimulation sim = built.sim()) {
            int laid = 0, pieces = 0;
            StringBuilder missing = new StringBuilder();
            for (Laid piece : built.map().layout.pieces()) {
                pieces++;
                if (piece.isLaid()) laid++;
                else missing.append(missing.length() == 0 ? "" : ", ").append(piece.name());
            }
            return built.heart().sites().size() + " bores (" + built.heart().mothers().size() + " mothers); landing " + built.plan().landingPlace().seedX() + ","
                    + built.plan().landingPlace().seedY() + "; layout " + laid + " of " + pieces + " laid"
                    + (missing.length() == 0 ? "" : " (not " + missing + ")");
        }
    }

    /**
     * {@code cordon-line:1-40,heart-burn/frozen:3-9}: seeds screened as candidates,
     * not pooled, on the named biome or on every biome.
     */
    private static List<Job> candidates(String spec) {
        List<Job> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.split(",")) {
            String[] mapAndRange = part.trim().split(":");
            String[] mapAndBiome = mapAndRange[0].split("/");
            StoryMap map = null;
            for (StoryMap m : StoryMap.values()) if (m.id.equals(mapAndBiome[0])) map = m;
            if (map == null) throw new IllegalArgumentException("no story map " + mapAndBiome[0]);
            List<SurfacePalette> biomes = mapAndBiome.length > 1
                    ? List.of(SurfacePalette.valueOf(mapAndBiome[1].toUpperCase(Locale.ROOT)))
                    : List.of(SurfacePalette.values());
            String[] range = mapAndRange[1].split("-");
            long from = Long.parseLong(range[0]), to = range.length > 1 ? Long.parseLong(range[1]) : from;
            StoryMapPool pooled = StoryMapPools.pool(map);
            for (SurfacePalette biome : biomes) {
                for (long seed = from; seed <= to; seed++) {
                    if (pooled.entry(biome, seed) == null) {
                        out.add(new Job(map, biome, new StoryMapPool.Entry(seed, "", MapPatch.NONE), false));
                    }
                }
            }
        }
        return out;
    }

    private static String report(List<Row> rows) {
        StringBuilder md = new StringBuilder("# Story map pools\n\n")
                .append("Every pooled seed regenerated with its patch (`story-map-pools.md`). A pooled seed that fails ")
                .append("fails the run; candidates are reported only.\n\n")
                .append("| map | biome | seed | pooled | verdict | detail | ms |\n|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            md.append("| ").append(row.job().map().id).append(" | ").append(row.job().biome()).append(" | ")
                    .append(row.job().entry().seed()).append(" | ")
                    .append(row.job().pooled() ? "yes" : "candidate").append(" | ")
                    .append(row.failure() == null ? "PASS" : "FAIL: " + row.failure().replace('|', '/')).append(" | ")
                    .append(row.detail().replace('|', '/')).append(" | ").append(row.millis()).append(" |\n");
        }
        return md.toString();
    }
}
