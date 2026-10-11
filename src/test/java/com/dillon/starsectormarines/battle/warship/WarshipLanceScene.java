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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A warship's lance swung once across open ground that holds, in order of
 * bearing: three enemies inside the arc, a wall across it, one of the ship's
 * own marines far down it, and two enemies well outside it.
 *
 * <p>It asks the questions only a swept beam can: that the line is drawn before
 * it is lit, that it kills <em>along</em> its arc rather than everywhere at
 * once, that a wall in the way is razed, that friendly fire is real, and that
 * ground off the arc is untouched. The control plays the identical world with
 * no ship, because "everyone survived" means nothing until the same people
 * survive on their own.
 */
public final class WarshipLanceScene implements BehaviorScene {

    private static final int WIDTH = 90;
    private static final int HEIGHT = 60;
    private static final int TICKS = 300;
    private static final float ORIGIN_X = 4f;
    private static final float ORIGIN_Y = 30f;
    private static final float ARC_DEGREES = 35f;
    private static final int WALL_X = 40;

    @Override public String id() { return "warship-lance"; }

    @Override public String label() {
        return "Warship lance: a telegraphed sweep razes its arc, wall and friendly fire included";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "sweep")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private static int cellX(float radius, float degrees) {
        return Math.round(ORIGIN_X + radius * (float) Math.cos(Math.toRadians(degrees)));
    }

    private static int cellY(float radius, float degrees) {
        return Math.round(ORIGIN_Y + radius * (float) Math.sin(Math.toRadians(degrees)));
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean sweep = loop.equals("sweep");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(WALL_X, 20, WALL_X, 40);
        String[] arcKeys = {"arc-low", "arc-mid", "arc-high"};
        float[] arcDegrees = {-25f, 0f, 25f};
        for (int i = 0; i < arcKeys.length; i++) {
            builder.unit(arcKeys[i], Faction.DEFENDER, UnitType.MARINE,
                    cellX(30f, arcDegrees[i]), cellY(30f, arcDegrees[i]));
        }
        builder.unit("own", Faction.MARINE, UnitType.MARINE, cellX(45f, 10f), cellY(45f, 10f));
        builder.unit("off-low", Faction.DEFENDER, UnitType.MARINE, cellX(30f, -60f), cellY(30f, -60f));
        builder.unit("off-high", Faction.DEFENDER, UnitType.MARINE, cellX(30f, 60f), cellY(30f, 60f));
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);

        Trace trace = new Trace(world, List.of(
                "arc-low", "arc-mid", "arc-high", "own", "off-low", "off-high"));
        if (sweep) {
            sim.startLanceSweep(new LanceSweep(1L, Faction.MARINE, ORIGIN_X, ORIGIN_Y,
                    (float) Math.toRadians(-ARC_DEGREES), (float) Math.toRadians(ARC_DEGREES),
                    60f, 2f, 1f, 6f, 400f, 8f, 100));
        }
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / lance sweep / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        boolean sweep = loop.equals("sweep");
        List<Verdict> verdicts = new ArrayList<>();
        boolean wallStands = !sim.getGrid().isWalkable(WALL_X, 30);
        if (!sweep) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(),
                    "deaths with no ship: " + trace.deaths));
            verdicts.add(Verdict.of("wall-stands", wallStands, "the wall is intact with no ship"));
        } else {
            int firstDeath = trace.firstDeathTick();
            verdicts.add(Verdict.of("telegraph-first", firstDeath >= 30,
                    "first death at tick " + firstDeath + "; the beam lights at tick 30"));
            verdicts.add(Verdict.of("arc-cleared", trace.diedAll("arc-low", "arc-mid", "arc-high"),
                    "arc deaths " + trace.deaths));
            verdicts.add(Verdict.of("walks-the-arc", trace.diedInOrder("arc-low", "arc-mid", "arc-high"),
                    "deaths follow the beam's bearing: " + trace.deaths));
            verdicts.add(Verdict.of("friendly-fire", trace.diedAll("own"),
                    "the ship's own marine in the arc: " + trace.deaths.get("own")));
            verdicts.add(Verdict.of("off-arc-spared", trace.survivedAll("off-low", "off-high"),
                    "enemies 60 degrees off the arc: " + trace.deaths));
            verdicts.add(Verdict.of("wall-razed", !wallStands, "the wall across the arc is breached"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firstDeathTick", trace.firstDeathTick());
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        /** Tick of death by unit key, in the order the deaths were seen. */
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
                if (t == null || t <= previous) return false;
                previous = t;
            }
            return true;
        }
    }
}
