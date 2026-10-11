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
 * An Onslaught ordered to take station and left to choose its own ground.
 *
 * <p>Two enemy clusters stand within its reach, a large one and a small one, and
 * a lone enemy far beyond any gun it carries. The ship starts short of its
 * station, moves onto it at its own hover speed, and its director is given
 * nothing but an area size. It asks whether the director fires on the dense
 * cluster before the small one, keeps going until both are gone, never reaches
 * for ground it cannot hit, and whether the hull gets to its station without
 * outrunning its own table speed.
 */
public final class OnslaughtDirectorScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;
    private static final int TICKS = 2700;
    private static final float START_X = 28f;
    private static final float STATION_X = 38f;
    private static final float STATION_Y = 40f;
    private static final float AREA_RADIUS = 10f;
    private static final String[] BIG = {"b1", "b2", "b3", "b4", "b5", "b6"};
    private static final String[] SMALL = {"s1", "s2", "s3"};

    @Override public String id() { return "onslaught-director"; }

    @Override public String label() {
        return "Onslaught director: takes station, fires on the densest ground first, then the rest";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "directed")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean directed = loop.equals("directed");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        int[][] big = {{70, 38}, {71, 40}, {70, 42}, {73, 39}, {74, 41}, {72, 37}};
        for (int i = 0; i < BIG.length; i++) {
            builder.unit(BIG[i], Faction.DEFENDER, UnitType.MARINE, big[i][0], big[i][1]);
        }
        int[][] small = {{62, 62}, {63, 64}, {61, 64}};
        for (int i = 0; i < SMALL.length; i++) {
            builder.unit(SMALL[i], Faction.DEFENDER, UnitType.MARINE, small[i][0], small[i][1]);
        }
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        List<String> keys = new ArrayList<>(List.of(BIG));
        keys.addAll(List.of(SMALL));
        keys.add("far");
        Trace trace = new Trace(world, keys);
        if (directed) {
            Warship ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, START_X, STATION_Y, -90f);
            ship.stationTo(STATION_X, STATION_Y);
            ship.direct(AREA_RADIUS, true);
            sim.addWarship(ship);
            trace.ship = ship;
        }
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 6, tick -> loop + " / director / tick " + tick)
                .run(TICKS);
        return report(loop, trace);
    }

    private SceneReport report(String loop, Trace trace) {
        List<Verdict> verdicts = new ArrayList<>();
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(), "deaths with no ship: " + trace.deaths));
        } else {
            Warship ship = trace.ship;
            verdicts.add(Verdict.of("takes-its-station",
                    ship.x == STATION_X && ship.y == STATION_Y,
                    "ended at (" + ship.x + ", " + ship.y + ") of station (" + STATION_X + ", " + STATION_Y + ")"));
            verdicts.add(Verdict.of("hover-speed-is-the-hulls",
                    trace.maxSpeed <= ship.maxSpeed * 1.02f && trace.maxSpeed > 0f,
                    "peak " + trace.maxSpeed + " cells/s against the hull's " + ship.maxSpeed));
            int bigFirst = trace.firstOf(BIG);
            int smallFirst = trace.firstOf(SMALL);
            verdicts.add(Verdict.of("densest-first", bigFirst >= 0 && (smallFirst < 0 || bigFirst < smallFirst),
                    "first of six at tick " + bigFirst + ", first of three at tick " + smallFirst));
            long dead = trace.deaths.keySet().stream().filter(k -> !k.equals("far")).count();
            verdicts.add(Verdict.of("clears-both", dead == BIG.length + SMALL.length,
                    dead + " of " + (BIG.length + SMALL.length) + " in reach died: " + trace.deaths));
            verdicts.add(Verdict.of("ignores-what-it-cannot-reach", !trace.deaths.containsKey("far"),
                    "the enemy 112 cells out lives: " + trace.deaths));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firstDeathTick", trace.firstDeathTick());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        Warship ship;
        private float lastX = Float.NaN;
        private float lastY;
        float maxSpeed;

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (ship != null) {
                if (!Float.isNaN(lastX)) {
                    float speed = (float) Math.hypot(ship.x - lastX, ship.y - lastY) / BattleSimulation.TICK_DT;
                    maxSpeed = Math.max(maxSpeed, speed);
                }
                lastX = ship.x;
                lastY = ship.y;
            }
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) {
                    deaths.put(key, tick);
                }
            }
        }

        int firstDeathTick() {
            return deaths.values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        }

        int firstOf(String[] group) {
            int first = -1;
            for (String k : group) {
                Integer t = deaths.get(k);
                if (t != null && (first < 0 || t < first)) first = t;
            }
            return first;
        }
    }
}
