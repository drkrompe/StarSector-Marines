package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An Onslaught directed over a whole district rather than one cluster.
 *
 * <p>Six enemy clusters stand spread across ground within the ship's reach. The
 * same world is played twice, once with the director limited to one area at a
 * time (the old behaviour) and once holding three. It asks whether the ship
 * really holds several areas together, whether more than one cluster is dying
 * at the same moment, whether every cluster is eventually cleared, and whether
 * working the district in parallel clears it sooner than working it a cluster
 * at a time.
 */
public final class OnslaughtDistrictScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 5400;
    private static final float START_X = 28f;
    private static final float STATION_X = 38f;
    private static final float STATION_Y = 40f;
    private static final float AREA_RADIUS = 8f;
    /** Cluster centres, each a tight knot of four. */
    private static final int[][] CENTRES = {{62, 24}, {62, 40}, {62, 56}, {80, 24}, {80, 40}, {80, 56}};
    private static final int[][] OFFSETS = {{0, 0}, {1, 2}, {-1, 2}, {2, -1}};

    @Override public String id() { return "onslaught-district"; }

    @Override public String label() {
        return "Onslaught district: a directed ship works several clusters at once and clears the district sooner";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Trace single = playOne("single-area", 1, frames);
        Trace multi = playOne("three-areas", 3, frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(report("single-area", single, null));
        reports.add(report("three-areas", multi, single));
        return reports;
    }

    private Trace playOne(String loop, int maxAreas, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = new ArrayList<>();
        for (int c = 0; c < CENTRES.length; c++) {
            for (int m = 0; m < OFFSETS.length; m++) {
                String key = "c" + c + "m" + m;
                keys.add(key);
                builder.unit(key, Faction.DEFENDER, UnitType.MARINE,
                        CENTRES[c][0] + OFFSETS[m][0], CENTRES[c][1] + OFFSETS[m][1]);
            }
        }
        // Nobody can reach this one; it keeps the simulation from ending.
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Trace trace = new Trace(world, keys);
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, START_X, STATION_Y, -90f);
        ship.stationTo(STATION_X, STATION_Y);
        ship.direct(AREA_RADIUS, true, maxAreas);
        sim.addWarship(ship);
        trace.ship = ship;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 6, tick -> loop + " / district / tick " + tick)
                .run(TICKS);
        return trace;
    }

    private SceneReport report(String loop, Trace trace, Trace baseline) {
        List<Verdict> verdicts = new ArrayList<>();
        boolean multi = baseline != null;
        if (multi) {
            verdicts.add(Verdict.of("holds-several-areas", trace.maxAreas > 1,
                    "most areas held at once: " + trace.maxAreas));
            verdicts.add(Verdict.of("several-clusters-under-fire-together", trace.maxClustersAtOnce > 1,
                    "most clusters with a death started and not yet cleared at one tick: " + trace.maxClustersAtOnce));
        } else {
            verdicts.add(Verdict.of("holds-one-area", trace.maxAreas == 1,
                    "most areas held at once: " + trace.maxAreas));
        }
        verdicts.add(Verdict.of("clears-every-cluster", trace.clustersCleared() == CENTRES.length,
                trace.clustersCleared() + " of " + CENTRES.length + " cleared; cleared at " + trace.clearTicks()));
        verdicts.add(Verdict.of("ignores-what-it-cannot-reach", !trace.deaths.containsKey("far"),
                "the enemy 112 cells out lives"));
        if (multi) {
            verdicts.add(Verdict.of("clears-sooner-than-one-area",
                    trace.clearedAll() >= 0 && baseline.clearedAll() >= 0 && trace.clearedAll() < baseline.clearedAll(),
                    "district cleared at tick " + trace.clearedAll() + " against " + baseline.clearedAll()
                            + " with one area at a time"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("clearedAllTick", trace.clearedAll());
        metrics.put("maxAreas", trace.maxAreas);
        metrics.put("maxClustersAtOnce", trace.maxClustersAtOnce);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        private final int[] firstDeath = new int[CENTRES.length];
        private final int[] lastDeath = new int[CENTRES.length];
        private final int[] dead = new int[CENTRES.length];
        Warship ship;
        int maxAreas;
        int maxClustersAtOnce;

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
            Arrays.fill(firstDeath, -1);
            Arrays.fill(lastDeath, -1);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            maxAreas = Math.max(maxAreas, ship.areas().size());
            for (String key : keys) {
                if (deaths.containsKey(key) || sim.getRoster().isAliveById(world.unit(key))) continue;
                deaths.put(key, tick);
                int c = Integer.parseInt(key.substring(1, key.indexOf('m')));
                if (firstDeath[c] < 0) firstDeath[c] = tick;
                lastDeath[c] = tick;
                dead[c]++;
            }
            int underFire = 0;
            for (int c = 0; c < CENTRES.length; c++) {
                if (firstDeath[c] >= 0 && dead[c] < OFFSETS.length) underFire++;
            }
            maxClustersAtOnce = Math.max(maxClustersAtOnce, underFire);
        }

        int clustersCleared() {
            int n = 0;
            for (int c = 0; c < CENTRES.length; c++) if (dead[c] == OFFSETS.length) n++;
            return n;
        }

        List<Integer> clearTicks() {
            List<Integer> out = new ArrayList<>();
            for (int c = 0; c < CENTRES.length; c++) out.add(dead[c] == OFFSETS.length ? lastDeath[c] : -1);
            return out;
        }

        /** The tick the last cluster fell, or -1 if one never did. */
        int clearedAll() {
            if (clustersCleared() != CENTRES.length) return -1;
            int last = 0;
            for (int t : lastDeath) last = Math.max(last, t);
            return last;
        }
    }
}
