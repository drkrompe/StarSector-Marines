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
 * An Onslaught, fitted as its stock Standard variant, hanging over open ground
 * and shelling a walled compound with the guns the game gave it.
 *
 * <p>The world holds, in order of distance from the ship: a cluster of enemies
 * behind a wall with one of the ship's own marines beside them, a lone enemy
 * out past the longest reach of the ship's heavy guns, and one of the ship's own
 * marines behind the hull. The control plays the identical world with no ship,
 * because "everyone survived" means nothing until the same people survive on
 * their own.
 *
 * <p>It asks what a barrage has to be to be a barrage: shells that take time to
 * arrive, that kill what they land on whichever side it is, that break the wall
 * they land beside, and that stop at the weapon's own reach.
 */
public final class OnslaughtBarrageScene implements BehaviorScene {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 80;
    private static final int TICKS = 900;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    /** Forward is (-sin h, cos h), so the compound due east is a bearing of -90; the port battery sits at +80 off the bow, so the ship turns to -170 to bring it to bear. */
    private static final float START_HEADING = -120f;
    private static final float AREA_X = 68f;
    private static final float AREA_Y = 40f;
    private static final float AREA_RADIUS = 12f;
    private static final int WALL_X = 64;
    private static final String[] CLUSTER = {"e1", "e2", "e3", "e4", "e5", "e6"};

    @Override public String id() { return "onslaught-barrage"; }

    @Override public String label() {
        return "Onslaught barrage: shells that fall, kill either side, break walls and stop at their reach";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "hold-fire", "barrage")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean barrage = loop.equals("barrage");
        boolean ship = !loop.equals("control");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(WALL_X, 30, WALL_X, 50);
        int[][] cluster = {{66, 38}, {67, 40}, {66, 42}, {69, 39}, {70, 41}, {68, 37}};
        for (int i = 0; i < CLUSTER.length; i++) {
            builder.unit(CLUSTER[i], Faction.DEFENDER, UnitType.MARINE, cluster[i][0], cluster[i][1]);
        }
        builder.unit("own-beside", Faction.MARINE, UnitType.MARINE, 68, 41);
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        builder.unit("off-area", Faction.DEFENDER, UnitType.MARINE, 70, 62);
        builder.unit("own-behind", Faction.MARINE, UnitType.MARINE, 8, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        if (ship) {
            Warship onslaught = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                    SHIP_X, SHIP_Y, START_HEADING);
            if (barrage) onslaught.order(new WarshipOrder.FireOnArea(AREA_X, AREA_Y, AREA_RADIUS, true));
            sim.addWarship(onslaught);
        }
        List<String> keys = new ArrayList<>(List.of(CLUSTER));
        keys.addAll(List.of("own-beside", "far-enemy", "off-area", "own-behind"));
        Trace trace = new Trace(world, keys);
        if (ship) trace.ship = sim.warships().ships().get(0);
        if (barrage) trace.earliestLandingTick = earliestLandingTick(sim);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / onslaught / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim);
    }

    /**
     * The soonest any round could land on the nearest cluster member: the gap
     * from the hull's centre less half its length (no mount is further forward
     * than that), over the fastest weapon's own speed.
     */
    private static int earliestLandingTick(BattleSimulation sim) {
        Warship ship = sim.warships().ships().get(0);
        float nearest = Float.MAX_VALUE;
        for (float[] c : new float[][]{{66, 38}, {67, 40}, {66, 42}}) {
            nearest = Math.min(nearest, (float) Math.hypot(c[0] - ship.x, c[1] - ship.y));
        }
        float fastest = 1f;
        for (Warship.Mount m : ship.mounts()) {
            fastest = Math.max(fastest, WarshipScale.cells(m.weapon.projectileSpeedPx()));
        }
        float seconds = Math.max(0f, nearest - ship.lengthCells / 2f) / fastest;
        return Math.max(0, (int) Math.floor(seconds / BattleSimulation.TICK_DT) - 1);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        boolean barrage = loop.equals("barrage");
        boolean holding = loop.equals("hold-fire");
        List<Verdict> verdicts = new ArrayList<>();
        int breached = 0;
        for (int y = 30; y <= 50; y++) if (sim.getGrid().isWalkable(WALL_X, y)) breached++;
        boolean wallStands = breached == 0;
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(),
                    "deaths with no ship: " + trace.deaths));
            verdicts.add(Verdict.of("wall-stands", wallStands, "the wall is intact with no ship"));
        } else if (holding) {
            int shots = totalShots(sim);
            verdicts.add(Verdict.of("silent-on-hold-fire", shots == 0 && trace.deaths.isEmpty() && wallStands,
                    shots + " rounds fired, deaths " + trace.deaths));
        } else {
            long clusterDead = List.of(CLUSTER).stream().filter(trace.deaths::containsKey).count();
            int first = trace.firstDeathTick();
            verdicts.add(Verdict.of("shells-take-time", first >= trace.earliestLandingTick,
                    "first death at tick " + first + "; no shell can land before tick "
                            + trace.earliestLandingTick + " (nearest mount, its own weapon's speed)"));
            verdicts.add(Verdict.of("cluster-destroyed", clusterDead == CLUSTER.length,
                    clusterDead + " of " + CLUSTER.length + " enemies behind the wall: " + trace.deaths));
            verdicts.add(Verdict.of("friendly-fire", trace.deaths.containsKey("own-beside"),
                    "the ship's own marine beside them: " + trace.deaths.get("own-beside")));
            verdicts.add(Verdict.of("wall-razed", breached >= 3, breached + " of 21 wall cells beside the cluster are breached"));
            verdicts.add(Verdict.of("reach-is-the-weapons",
                    !trace.deaths.containsKey("far-enemy") && !trace.deaths.containsKey("own-behind"),
                    "the enemy 120 cells out and the marine behind the hull both live: " + trace.deaths));
            verdicts.add(Verdict.of("area-is-the-limit", !trace.deaths.containsKey("off-area"),
                    "an enemy in reach but outside the ordered circle lives: " + trace.deaths));
            Warship onslaught = sim.warships().ships().get(0);
            verdicts.add(Verdict.of("turns-at-its-own-rate",
                    trace.maxTurnPerSecond <= onslaught.maxTurnDegPerSec * 1.02f && trace.totalTurn >= 10f,
                    "turned " + trace.totalTurn + " deg, at most " + trace.maxTurnPerSecond
                            + " deg/s against the hull's " + onslaught.maxTurnDegPerSec));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firstDeathTick", trace.firstDeathTick());
        if (!loop.equals("control")) {
            for (Warship.Mount m : sim.warships().ships().get(0).mounts()) {
                metrics.merge("shots." + m.weapon.id(), m.shotsFired, (x, y) -> x.intValue() + y.intValue());
            }
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static int totalShots(BattleSimulation sim) {
        int shots = 0;
        for (Warship.Mount m : sim.warships().ships().get(0).mounts()) shots += m.shotsFired;
        return shots;
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        int earliestLandingTick;
        Warship ship;
        private float lastFacing = Float.NaN;
        float maxTurnPerSecond;
        float totalTurn;

        private Trace(SceneWorld world, List<String> keys) {
            this.world = world;
            this.keys = keys;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (ship != null) {
                if (!Float.isNaN(lastFacing)) {
                    float step = Math.abs(WarshipFacing.wrap(ship.facingDegrees - lastFacing));
                    totalTurn += step;
                    maxTurnPerSecond = Math.max(maxTurnPerSecond, step / BattleSimulation.TICK_DT);
                }
                lastFacing = ship.facingDegrees;
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
    }
}
