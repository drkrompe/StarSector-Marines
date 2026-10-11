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
    private static final int TICKS = 450;
    private static final float SHIP_X = 38f;
    private static final float SHIP_Y = 40f;
    /** Forward is (-sin h, cos h), so the compound due east is a bearing of -90; the port battery sits at +80 off the bow, so the ship turns to -170 to bring it to bear. */
    private static final float PORT_BROADSIDE = -170f;
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
        for (String loop : List.of("control", "barrage")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean barrage = loop.equals("barrage");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(WALL_X, 30, WALL_X, 50);
        int[][] cluster = {{66, 38}, {67, 40}, {66, 42}, {69, 39}, {70, 41}, {68, 37}};
        for (int i = 0; i < CLUSTER.length; i++) {
            builder.unit(CLUSTER[i], Faction.DEFENDER, UnitType.MARINE, cluster[i][0], cluster[i][1]);
        }
        builder.unit("own-beside", Faction.MARINE, UnitType.MARINE, 68, 41);
        builder.unit("far-enemy", Faction.DEFENDER, UnitType.MARINE, 150, 40);
        builder.unit("own-behind", Faction.MARINE, UnitType.MARINE, 8, 40);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261011L);
        if (barrage) {
            sim.addWarship(Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                    SHIP_X, SHIP_Y, PORT_BROADSIDE));
        }
        List<String> keys = new ArrayList<>(List.of(CLUSTER));
        keys.addAll(List.of("own-beside", "far-enemy", "own-behind"));
        Trace trace = new Trace(world, keys);
        if (barrage) trace.earliestLandingTick = earliestLandingTick(sim);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / onslaught / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim);
    }

    /** The soonest any round could land on the nearest cluster member: distance over that weapon's own speed. */
    private static int earliestLandingTick(BattleSimulation sim) {
        float best = Float.MAX_VALUE;
        Warship ship = sim.warships().ships().get(0);
        for (Warship.Mount m : ship.mounts()) {
            float speed = WarshipScale.cells(m.weapon.projectileSpeedPx());
            for (float[] c : new float[][]{{66, 38}, {67, 40}, {66, 42}}) {
                float dx = c[0] - ship.mountX(m);
                float dy = c[1] - ship.mountY(m);
                float seconds = (float) Math.sqrt(dx * dx + dy * dy) / speed;
                best = Math.min(best, seconds);
            }
        }
        return Math.max(0, (int) Math.floor(best / BattleSimulation.TICK_DT) - 1);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        boolean barrage = loop.equals("barrage");
        List<Verdict> verdicts = new ArrayList<>();
        int breached = 0;
        for (int y = 30; y <= 50; y++) if (sim.getGrid().isWalkable(WALL_X, y)) breached++;
        boolean wallStands = breached == 0;
        if (!barrage) {
            verdicts.add(Verdict.of("nobody-dies", trace.deaths.isEmpty(),
                    "deaths with no ship: " + trace.deaths));
            verdicts.add(Verdict.of("wall-stands", wallStands, "the wall is intact with no ship"));
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
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("deaths", trace.deaths.size());
        metrics.put("firstDeathTick", trace.firstDeathTick());
        if (barrage) {
            for (Warship.Mount m : sim.warships().ships().get(0).mounts()) {
                metrics.merge("shots." + m.weapon.id(), m.shotsFired, (x, y) -> x.intValue() + y.intValue());
            }
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<String> keys;
        private final Map<String, Integer> deaths = new LinkedHashMap<>();
        int earliestLandingTick;

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
    }
}
