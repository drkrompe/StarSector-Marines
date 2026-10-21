package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Spitter on open ground against held-fire infantry.
 * <ul>
 *   <li>{@code lob}: a tight three-marine cluster and a lone marine, both in
 *       range. It stops at range, lobs at the cluster rather than the lone body,
 *       the splash reaches more than one of them, and a Runner beside the
 *       cluster is never hurt by it.</li>
 *   <li>{@code charged}: a marine starts inside its charge radius. It backs off
 *       and does not lob until it has opened the gap.</li>
 * </ul>
 * The marines are squadless and hold their fire; the scene is about what the
 * Spitter does, not who wins.
 */
public final class SwarmSpitterScene implements BehaviorScene {

    private static final int TICKS = 600;

    @Override public String id() { return "swarm-spitter-lob"; }

    @Override public String label() {
        return "Swarm Spitter: holds at range, lobs at the densest infantry, splashes, backs off when charged";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(lob(frames), charged(frames));
    }

    private SceneReport lob(FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(48, 28).seed(20261021L).missionCompletion(false)
                .unit("lone", Faction.MARINE, UnitType.MARINE, 17, 8, spec -> spec.health(5000f))
                .unit("c1", Faction.MARINE, UnitType.MARINE, 22, 14, spec -> spec.health(5000f))
                .unit("c2", Faction.MARINE, UnitType.MARINE, 22, 15, spec -> spec.health(5000f))
                .unit("c3", Faction.MARINE, UnitType.MARINE, 23, 14, spec -> spec.health(5000f))
                .build();
        try (BattleSimulation sim = world.sim()) {
            long spitter = sim.spawn(SwarmStrains.spec("Spitter", UnitType.SWARM_SPITTER, 10, 14)
                    .health(5000f));
            // A harmless Runner that will close on the cluster: the splash must never touch it.
            long runner = sim.spawn(SwarmStrains.spec("Runner", UnitType.SWARM_RUNNER, 26, 16)
                    .health(5000f).attackDamage(0f));
            long lone = world.unit("lone");
            long[] cluster = {world.unit("c1"), world.unit("c2"), world.unit("c3")};
            long[] marines = {lone, cluster[0], cluster[1], cluster[2]};
            Trace trace = new Trace(spitter, marines, sim);
            float runnerHp = sim.world().hp(runner);
            SceneRun.of(world).observe(trace)
                    .frames(frames, "lob", 15, tick -> "lob / spitter / tick " + tick)
                    .run(TICKS);

            Set<Long> clusterIds = Set.of(cluster[0], cluster[1], cluster[2]);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("lobs", trace.lobs > 0, trace.lobs + " lobs"));
            verdicts.add(Verdict.of("lobs-at-densest", trace.firstLobTarget != 0L
                            && clusterIds.contains(trace.firstLobTarget),
                    "first lob at " + trace.firstLobTarget + "; cluster " + clusterIds
                            + ", lone " + lone));
            verdicts.add(Verdict.of("holds-at-range",
                    trace.closest > SwarmSpitterBehavior.CHARGED_RADIUS,
                    "closest approach to any marine " + trace.closest));
            verdicts.add(Verdict.of("splash-reaches-several", trace.damaged.size() >= 2,
                    trace.damaged.size() + " marines hurt"));
            float runnerDamage = runnerHp - sim.world().hp(runner);
            verdicts.add(Verdict.of("acid-spares-swarm", runnerDamage == 0f,
                    "runner beside the cluster took " + runnerDamage));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("lobs", trace.lobs);
            metrics.put("firstLobTick", trace.firstLobTick);
            metrics.put("closestApproach", trace.closest);
            metrics.put("marinesHurt", trace.damaged.size());
            metrics.put("marineDamage", trace.totalDamage(sim));
            return new SceneReport(id(), "lob", TICKS, verdicts, metrics);
        }
    }

    private SceneReport charged(FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(48, 28).seed(20261021L).missionCompletion(false)
                .unit("m", Faction.MARINE, UnitType.MARINE, 20, 14, spec -> spec.health(5000f))
                .build();
        try (BattleSimulation sim = world.sim()) {
            long spitter = sim.spawn(SwarmStrains.spec("Spitter", UnitType.SWARM_SPITTER, 22, 14)
                    .health(5000f));
            long marine = world.unit("m");
            Trace trace = new Trace(spitter, new long[]{marine}, sim);
            SceneRun.of(world).observe(trace)
                    .frames(frames, "charged", 15, tick -> "charged / spitter / tick " + tick)
                    .run(TICKS);
            float gap = (float) Math.hypot(sim.world().x(spitter) - sim.world().x(marine),
                    sim.world().y(spitter) - sim.world().y(marine));
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("backs-off", trace.openedGapTick >= 0,
                    "gap past " + SwarmSpitterBehavior.CHARGED_RADIUS + " at tick "
                            + trace.openedGapTick + "; final gap " + gap));
            verdicts.add(Verdict.of("no-lob-while-charged", trace.lobsWhileCharged == 0,
                    trace.lobsWhileCharged + " lobs inside the charge radius"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("openedGapTick", trace.openedGapTick);
            metrics.put("finalGap", gap);
            metrics.put("lobs", trace.lobs);
            return new SceneReport(id(), "charged", TICKS, verdicts, metrics);
        }
    }

    /** Counts the Spitter's lobs and what they were aimed at. */
    static final class Trace implements TickObserver {
        private final long spitter;
        private final long[] marines;
        private final float[] initialHp;
        final Set<Long> damaged = new HashSet<>();
        int lobs;
        int lobsWhileCharged;
        int firstLobTick = -1;
        long firstLobTarget;
        float closest = Float.MAX_VALUE;
        int openedGapTick = -1;

        Trace(long spitter, long[] marines, BattleSimulation sim) {
            this.spitter = spitter;
            this.marines = marines;
            initialHp = new float[marines.length];
            for (int i = 0; i < marines.length; i++) initialHp[i] = sim.world().hp(marines[i]);
        }

        float totalDamage(BattleSimulation sim) {
            float total = 0f;
            for (int i = 0; i < marines.length; i++) total += initialHp[i] - sim.world().hp(marines[i]);
            return total;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < marines.length; i++) {
                float d = (float) Math.hypot(sim.world().x(spitter) - sim.world().x(marines[i]),
                        sim.world().y(spitter) - sim.world().y(marines[i]));
                nearest = Math.min(nearest, d);
                if (sim.world().hp(marines[i]) < initialHp[i]) damaged.add(marines[i]);
            }
            if (tick > 0) closest = Math.min(closest, nearest);
            if (openedGapTick < 0 && nearest > SwarmSpitterBehavior.CHARGED_RADIUS) {
                openedGapTick = tick;
            }
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (shot.shooterId != spitter) continue;
                lobs++;
                if (nearest <= SwarmSpitterBehavior.CHARGED_RADIUS) lobsWhileCharged++;
                if (firstLobTick < 0) {
                    firstLobTick = tick;
                    firstLobTarget = sim.combat().targetId(spitter);
                }
            }
        }
    }
}
