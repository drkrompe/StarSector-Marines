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
 * A Paragon, fitted as its stock Elite variant, ordered to fire on an area: its
 * two Tachyon Lances lay sweeps across the ground, and its lesser beams stay
 * silent.
 *
 * <p>The world holds three enemies in a column across the area, a wall in the
 * beam's way, one of the ship's own marines among them, and an enemy in reach
 * but off to the side of the circle. It asks what the lance has to be on a
 * ground battle: drawn before it is lit, walking the area in bearing order,
 * razing what is in its way, blind to colours, and confined to the ground it was
 * given. The control plays the same world with no ship.
 */
public final class ParagonSweepScene implements BehaviorScene {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 90;
    private static final int TICKS = 600;
    private static final float SHIP_X = 24f;
    private static final float SHIP_Y = 45f;
    private static final float AREA_X = 66f;
    private static final float AREA_Y = 45f;
    private static final float AREA_RADIUS = 12f;
    private static final int WALL_X = 50;

    @Override public String id() { return "paragon-sweep"; }

    @Override public String label() {
        return "Paragon sweep: tachyon lances walk the ordered area, raze its wall and spare what is outside";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "sweep")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean sweep = loop.equals("sweep");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(WALL_X, 40, WALL_X, 50);
        builder.unit("low", Faction.DEFENDER, UnitType.MARINE, 66, 36);
        builder.unit("mid", Faction.DEFENDER, UnitType.MARINE, 66, 45);
        builder.unit("high", Faction.DEFENDER, UnitType.MARINE, 66, 54);
        builder.unit("own", Faction.MARINE, UnitType.MARINE, 70, 50);
        builder.unit("off-area", Faction.DEFENDER, UnitType.MARINE, 66, 76);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        Trace trace = new Trace(world, List.of("low", "mid", "high", "own", "off-area"));
        if (sweep) {
            Warship ship = Warship.fit("paragon", "paragon_Elite", Faction.MARINE, SHIP_X, SHIP_Y, -90f);
            ship.order(new WarshipOrder.FireOnArea(AREA_X, AREA_Y, AREA_RADIUS, true));
            sim.addWarship(ship);
        }
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / paragon / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        boolean sweep = loop.equals("sweep");
        List<Verdict> verdicts = new ArrayList<>();
        int breached = 0;
        for (int y = 40; y <= 50; y++) if (sim.getGrid().isWalkable(WALL_X, y)) breached++;
        if (!sweep) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(), "deaths with no ship: " + trace.deaths));
            verdicts.add(Verdict.of("wall-stands", breached == 0, "the wall is intact with no ship"));
        } else {
            int first = trace.firstDeathTick();
            verdicts.add(Verdict.of("telegraph-first", first >= 45,
                    "first death at tick " + first + "; the beam lights at tick 45 at the earliest"));
            verdicts.add(Verdict.of("area-cleared", trace.diedAll("low", "mid", "high"),
                    "the column across the area: " + trace.deaths));
            verdicts.add(Verdict.of("walks-the-area", trace.diedInOrder("low", "mid", "high"),
                    "deaths follow the beam's bearing: " + trace.deaths));
            verdicts.add(Verdict.of("friendly-fire", trace.diedAll("own"),
                    "the ship's own marine in the area: " + trace.deaths.get("own")));
            verdicts.add(Verdict.of("off-area-spared", trace.survivedAll("off-area"),
                    "an enemy in reach but outside the circle: " + trace.deaths));
            verdicts.add(Verdict.of("wall-razed", breached >= 2, breached + " of 11 wall cells breached"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firstDeathTick", trace.firstDeathTick());
        if (sweep) {
            int lances = 0;
            for (Warship.Mount m : sim.warships().ships().get(0).mounts()) {
                if (m.weapon.beam()) lances += m.shotsFired;
            }
            metrics.put("lancesLaid", lances);
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (String key : keys) {
                if (!deaths.containsKey(key) && !sim.getRoster().isAliveById(world.unit(key))) {
                    deaths.put(key, tick);
                }
            }
        }

        int firstDeathTick() {
            return deaths.values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        }

        boolean diedAll(String... names) {
            for (String n : names) if (!deaths.containsKey(n)) return false;
            return true;
        }

        boolean survivedAll(String... names) {
            for (String n : names) if (deaths.containsKey(n)) return false;
            return true;
        }

        boolean diedInOrder(String... names) {
            int previous = -1;
            for (String n : names) {
                Integer t = deaths.get(n);
                if (t == null || t < previous) return false;
                previous = t;
            }
            return true;
        }
    }
}
