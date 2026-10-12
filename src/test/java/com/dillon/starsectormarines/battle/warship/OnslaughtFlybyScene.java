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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An Onslaught flying one scripted pass across a battlefield without stopping,
 * its director choosing ground as the hull goes by.
 *
 * <p>Three enemy clusters stand along the route, spaced so that each is in reach
 * at a different part of the flight. It asks whether the pass is continuous (the
 * hull never stops at a waypoint), whether the hull ends on its last waypoint,
 * whether the clusters die in the order the ship passes them, and whether the
 * ship fires while moving rather than pausing to fire.
 */
public final class OnslaughtFlybyScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int TICKS = 6000;
    private static final float[][] ROUTE = {{15f, 20f}, {60f, 30f}, {120f, 30f}, {180f, 20f}};
    private static final String[][] CLUSTERS = {
            {"a1", "a2", "a3", "a4"}, {"b1", "b2", "b3", "b4"}, {"c1", "c2", "c3", "c4"}};
    private static final int[][] CENTRES = {{55, 60}, {100, 64}, {150, 60}};

    @Override public String id() { return "onslaught-flyby"; }

    @Override public String label() {
        return "Onslaught flyby: one continuous pass, firing as it goes, clusters falling in the order it passes";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "flyby")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean flyby = loop.equals("flyby");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        List<String> keys = new ArrayList<>();
        int[][] offsets = {{0, 0}, {2, 1}, {-2, 2}, {1, -2}};
        for (int c = 0; c < CLUSTERS.length; c++) {
            for (int i = 0; i < CLUSTERS[c].length; i++) {
                builder.unit(CLUSTERS[c][i], Faction.DEFENDER, UnitType.MARINE,
                        CENTRES[c][0] + offsets[i][0], CENTRES[c][1] + offsets[i][1]);
                keys.add(CLUSTERS[c][i]);
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Trace trace = new Trace(world, keys);
        if (flyby) {
            Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                    ROUTE[0][0], ROUTE[0][1], 0f);
            ship.flyRoute(ROUTE, false);
            ship.direct(8f, false);
            sim.addWarship(ship);
            trace.ship = ship;
        }
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 12, tick -> loop + " / flyby / tick " + tick)
                .run(TICKS);
        return report(loop, trace);
    }

    private SceneReport report(String loop, Trace trace) {
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(), "deaths with no ship: " + trace.deaths));
        } else {
            Warship ship = trace.ship;
            float[] last = ROUTE[ROUTE.length - 1];
            verdicts.add(Verdict.of("ends-on-its-last-waypoint", ship.x == last[0] && ship.y == last[1],
                    "ended at (" + ship.x + ", " + ship.y + ")"));
            verdicts.add(Verdict.of("never-stops-mid-route",
                    trace.slowestMidRoute >= ship.maxSpeed * 0.5f,
                    "slowest speed between the first and last leg " + trace.slowestMidRoute
                            + " cells/s against the hull's " + ship.maxSpeed));
            int a = trace.lastOf(CLUSTERS[0]);
            int b = trace.lastOf(CLUSTERS[1]);
            int c = trace.lastOf(CLUSTERS[2]);
            verdicts.add(Verdict.of("cleared-in-passing-order", a >= 0 && b >= 0 && c >= 0 && a < b && b < c,
                    "last death of each cluster at ticks " + a + ", " + b + ", " + c));
            verdicts.add(Verdict.of("fires-while-moving", trace.firstDeathSpeed > ship.maxSpeed * 0.3f,
                    "first death while the hull was making " + trace.firstDeathSpeed + " cells/s"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        Warship ship;
        private float lastX = Float.NaN;
        private float lastY;
        private boolean gotGoing;
        float slowestMidRoute = Float.MAX_VALUE;
        float firstDeathSpeed = -1f;

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            float speed = 0f;
            if (ship != null) {
                if (!Float.isNaN(lastX)) {
                    speed = (float) Math.hypot(ship.x - lastX, ship.y - lastY) / BattleSimulation.TICK_DT;
                    // Mid-route: once up to speed, and until it is on the last leg that brakes.
                    if (speed >= ship.maxSpeed * 0.9f) gotGoing = true;
                    if (gotGoing && ship.isPassingThrough()) {
                        slowestMidRoute = Math.min(slowestMidRoute, speed);
                    }
                }
                lastX = ship.x;
                lastY = ship.y;
            }
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) {
                    deaths.put(key, tick);
                    if (firstDeathSpeed < 0f) firstDeathSpeed = speed;
                }
            }
        }

        int lastOf(String[] group) {
            int last = -1;
            for (String k : group) {
                Integer t = deaths.get(k);
                if (t == null) return -1;
                last = Math.max(last, t);
            }
            return last;
        }
    }
}
