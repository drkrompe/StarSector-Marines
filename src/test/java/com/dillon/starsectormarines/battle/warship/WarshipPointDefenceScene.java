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
 * A hostile Onslaught shelling a cluster of the company's marines from beyond
 * the marines' reach, recorded twice: with nothing over the marines, and with
 * the company's own Onslaught hanging above them on {@code HoldFire}, its flak
 * and vulcans the only thing it uses.
 *
 * <p>It asks whether point defence is a defence rather than a decoration: the
 * rounds the hostile ship fires cross the friendly umbrella and a measurable
 * share are shot out of the air and never land, while the ship's area guns stay
 * silent because nobody ordered them, and the control shows the same barrage
 * landing in full.
 */
public final class WarshipPointDefenceScene implements BehaviorScene {

    private static final int WIDTH = 160;
    /** Room north of the target for the friendly hull, whose bow sits over the cluster: grows with presence. */
    private static final int HEIGHT = Math.round(80 * WarshipScale.HULL_PRESENCE / 4f);
    private static final int TICKS = 600;
    /**
     * The hostile Onslaught lies bow east toward the cluster; it moves west by the growth of its half-length (taken as
     * 6.75 cells a unit of presence) over a presence of four, so its bow stands where the scene was laid.
     */
    private static final float HOSTILE_X = 50f - (WarshipScale.HULL_PRESENCE - 4f) * WarshipScale.cells(150f);
    private static final float HOSTILE_Y = HEIGHT / 2f;
    /** Forward is (-sin h, cos h), so the cluster due east is a bearing of -90; the barrage turns the hull on its own. */
    private static final float HOSTILE_HEADING = -90f;
    private static final float TARGET_X = 100f;
    private static final float TARGET_Y = HEIGHT / 2f;
    private static final float AREA_RADIUS = 10f;
    private static final String[] CLUSTER = {"m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8"};
    /** Laid out round (100, 40); shifted with the target's row. */
    private static final int[][] CLUSTER_AT = {
            {98, 38}, {99, 41}, {100, 37}, {101, 40}, {102, 43}, {100, 44}, {103, 38}, {97, 42}};

    /**
     * An Onslaught carries its point defence in its bow, about this far ahead of its centre at the hull's size, so the
     * friendly ship hangs south of the marines with its bow over them.
     */
    private static final float BOW_PD_CELLS = 7f * WarshipScale.HULL_PRESENCE;
    /**
     * Where the friendly hull's centre stands along the lane: just short of the target at a presence of four, and
     * further west as the hull grows (by one and a half times the growth of its half-length, taken as 6.75 cells a unit
     * of presence), so its bow mounts, spread wider on a bigger hull, still lie under the rounds' last stretch.
     */
    private static final float FRIENDLY_X = TARGET_X - 4f
            - 1.5f * (WarshipScale.HULL_PRESENCE - 4f) * WarshipScale.cells(150f);

    private float controlLandedShare = 1f;

    @Override public String id() { return "warship-point-defence"; }

    @Override public String label() {
        return "Warship point defence: flak and vulcans shoot a hostile barrage out of the air above the ship's own marines";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("control", "defended")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean defended = loop.equals("defended");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        for (int i = 0; i < CLUSTER.length; i++) {
            builder.unit(CLUSTER[i], Faction.MARINE, UnitType.MARINE, CLUSTER_AT[i][0], CLUSTER_AT[i][1] + Math.round(TARGET_Y) - 40);
        }
        // Something on the hostile side to keep the simulation ticking, far from the guns.
        builder.unit("anchor", Faction.DEFENDER, UnitType.MARINE, 4, 4);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        Warship hostile = Warship.fit("onslaught", "onslaught_Standard", Faction.DEFENDER,
                HOSTILE_X, HOSTILE_Y, HOSTILE_HEADING);
        hostile.order(new WarshipOrder.FireOnArea(TARGET_X, TARGET_Y, AREA_RADIUS, true));
        sim.addWarship(hostile);
        Warship friendly = null;
        if (defended) {
            friendly = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE,
                    FRIENDLY_X, TARGET_Y - BOW_PD_CELLS, 0f);
            sim.addWarship(friendly);
            // The question is the friendly ship's point defence, not the two hulls fighting each other.
            sim.warships().holdHullFire(HullFireHold.untilReleased("scene: point defence, not ship against ship"));
        }
        Trace trace = new Trace(world, List.of(CLUSTER));
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 3, tick -> loop + " / hostile barrage / tick " + tick)
                .run(TICKS);
        return report(loop, trace, sim, hostile, friendly);
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim, Warship hostile, Warship friendly) {
        int fired = 0;
        for (Warship.Mount m : hostile.mounts()) if (!m.pointDefence()) fired += m.shotsFired;
        int intercepts = sim.warships().pointDefence().intercepts();
        int inFlight = sim.warships().shells().size();
        int landed = fired - intercepts - inFlight;
        List<Verdict> verdicts = new ArrayList<>();
        if (friendly == null) {
            controlLandedShare = fired == 0 ? 1f : (float) landed / fired;
            verdicts.add(Verdict.of("barrage-lands", fired >= 10 && intercepts == 0 && !trace.deaths.isEmpty(),
                    fired + " rounds fired, " + landed + " landed, " + trace.deaths.size() + " marines dead"));
        } else {
            long pdMounts = friendly.mounts().stream().filter(Warship.Mount::pointDefence).count();
            int areaShots = 0;
            for (Warship.Mount m : friendly.mounts()) if (!m.pointDefence()) areaShots += m.shotsFired;
            float share = fired == 0 ? 0f : (float) intercepts / fired;
            verdicts.add(Verdict.of("point-defence-mounted", pdMounts >= 4,
                    pdMounts + " point-defence mounts on the friendly hull"));
            verdicts.add(Verdict.of("area-guns-stay-silent", areaShots == 0,
                    areaShots + " area-fire rounds from the friendly ship, which has no order"));
            verdicts.add(Verdict.of("shoots-down-a-share", share >= 0.10f,
                    intercepts + " of " + fired + " rounds shot down (" + Math.round(share * 100f) + "%)"));
            float landedShare = fired == 0 ? 1f : (float) landed / fired;
            verdicts.add(Verdict.of("smaller-share-lands-than-control", landedShare < controlLandedShare,
                    Math.round(landedShare * 100f) + "% of rounds landed against the control's "
                            + Math.round(controlLandedShare * 100f) + "%"));
            verdicts.add(Verdict.of("not-all-stopped", intercepts < fired,
                    "the umbrella is not a wall: " + (fired - intercepts) + " rounds still got through"));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("hostileRounds", fired);
        metrics.put("intercepted", intercepts);
        metrics.put("landed", landed);
        metrics.put("engagements", sim.warships().pointDefence().engagements());
        metrics.put("marinesDead", trace.deaths.size());
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
    }
}
