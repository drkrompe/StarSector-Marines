package com.dillon.starsectormarines.battle.evacuation;

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
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pack and its leader (`colony-breach-nouns.md`, Pack, pack leader): an elder
 * Runner leads three whelps and three adults at two Marines standing apart on
 * open ground, with a bore behind the pack. The leader is killed partway in.
 *
 * <p>Verdicts assert the mechanic, never who wins: while the leader lives, a
 * member on its leash with nothing of its own close by hunts the leader's
 * quarry and strikes at the pack's tempo; within a tick of the leader's death
 * the pack's bonuses are gone, it has broken once and elected no new leader,
 * and its whelps head for the bore. Kills, losses and how far the whelps ran
 * are metrics.
 */
public final class PackLeaderScene implements BehaviorScene {

    private static final int WIDTH = 56;
    private static final int HEIGHT = 30;
    private static final int TICKS = 900;
    /** The tick the leader is shot, if the fight has not killed it first. */
    private static final int KILL_TICK = 360;
    private static final int BORE_X = 50;
    private static final int BORE_Y = 15;
    private static final float DEEP = 50_000f;

    @Override public String id() { return "pack-leader"; }

    @Override public String label() {
        return "Pack leader: an elder leads a pack's quarry and tempo; its death breaks the pack and the whelps bolt";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne(frames));
    }

    private SceneReport playOne(FrameSink frames) throws Exception {
        String loop = "leader-killed";
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).seed(20261021L).missionCompletion(false);
        builder.unit("north", Faction.MARINE, UnitType.MARINE, 12, 9, spec -> spec.health(DEEP))
                .unit("south", Faction.MARINE, UnitType.MARINE, 12, 21, spec -> spec.health(DEEP));
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            SwarmPacks packs = sim.swarmPacks();
            long leader = SwarmStrains.spawn(sim, "Elder", UnitType.SWARM_RUNNER, 28, 13, SwarmStage.ELDER);
            int pack = packs.open(leader);
            long[] members = new long[6];
            for (int i = 0; i < members.length; i++) {
                SwarmStage stage = i < 3 ? SwarmStage.WHELP : SwarmStage.ADULT;
                members[i] = SwarmStrains.spawn(sim, stage.name().toLowerCase() + " " + i, UnitType.SWARM_RUNNER,
                        30 + (i % 3), 11 + i * 2, stage);
                packs.join(pack, members[i]);
            }
            Trace trace = new Trace(leader, members, bore);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 3, tick -> loop + " / " + (trace.brokeTick < 0 ? "led" : "broken")
                            + " / tick " + tick)
                    .run(TICKS);
            return report(loop, trace, sim);
        }
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("members-share-the-leaders-quarry", trace.cohesiveChoices > 0 && trace.strayChoices == 0,
                trace.cohesiveChoices + " cohesive member-ticks with a quarry, " + trace.strayChoices
                        + " on something else with nothing of their own within " + SwarmPacks.OWN_QUARRY_CELLS + " cells"));
        verdicts.add(Verdict.of("a-cohesive-member-strikes-faster", trace.tempoChecks > 0 && trace.tempoMisses == 0,
                trace.tempoChecks + " cohesive member-ticks read, " + trace.tempoMisses + " at the plain cooldown"));
        verdicts.add(Verdict.of("the-pack-breaks-once", sim.swarmPacks().breaks().size() == 1,
                sim.swarmPacks().breaks().size() + " breaks"));
        verdicts.add(Verdict.of("bonuses-gone-within-a-tick", trace.brokeTick >= 0 && trace.bonusAfterBreak == 0,
                "broke at tick " + trace.brokeTick + ", " + trace.bonusAfterBreak + " member-ticks still cohesive after"));
        verdicts.add(Verdict.of("no-new-leader", trace.leaderAfterBreak == 0,
                trace.leaderAfterBreak + " member-ticks leading after the break"));
        // Only whelps alive at the break can bolt; with none alive there is nothing to see.
        verdicts.add(Verdict.of("whelps-head-boreward", trace.whelpsAtBreak == 0 || trace.whelpsCloser == trace.whelpsAtBreak,
                trace.whelpsAtBreak == 0 ? "no whelp alive at the break"
                        : trace.whelpsCloser + " of " + trace.whelpsAtBreak + " whelps nearer the bore after their bolt"));
        verdicts.add(Verdict.of("a-bolting-whelp-hunts-nothing", trace.boltingWithQuarry == 0,
                trace.boltingWithQuarry + " bolting whelp-ticks with a quarry"));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("leaderDiedTick", trace.brokeTick);
        metrics.put("membersAlive", trace.alive(sim));
        metrics.put("whelpMeanBoltCells", trace.whelpsAtBreak == 0 ? 0f : trace.boltCells / trace.whelpsAtBreak);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Watches the pack's members, its leader and the bore tick by tick. */
    private static final class Trace implements TickObserver {
        private final long leader;
        private final long[] members;
        private final SwarmBore bore;
        private final float[] boltFrom;
        int brokeTick = -1;
        int cohesiveChoices, strayChoices, tempoChecks, tempoMisses;
        int bonusAfterBreak, leaderAfterBreak, boltingWithQuarry;
        int whelpsAtBreak, whelpsCloser;
        float boltCells;
        private boolean measured;
        /** The leader's quarry a tick ago: the pack hears it at the next serial pass. */
        private long heardQuarry;

        Trace(long leader, long[] members, SwarmBore bore) {
            this.leader = leader;
            this.members = members;
            this.bore = bore;
            boltFrom = new float[members.length];
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            SwarmPacks packs = sim.swarmPacks();
            boolean leaderLives = sim.getRoster().isAliveById(leader);
            if (leaderLives && tick >= KILL_TICK) {
                sim.applyDamage(leader, 0L, DEEP, DEEP, 0f);
                return;
            }
            if (leaderLives) {
                long quarry = sim.combat().targetId(leader);
                long heard = heardQuarry;
                heardQuarry = quarry;
                for (long m : members) {
                    if (!sim.getRoster().isAliveById(m) || !packs.isCohesive(m, sim)) continue;
                    tempoChecks++;
                    if (packs.cooldown(m, 1f, sim) != SwarmPacks.TEMPO) tempoMisses++;
                    long target = sim.combat().targetId(m);
                    if (target == 0L || quarry == 0L || heard == 0L) continue;
                    cohesiveChoices++;
                    if (target != quarry && target != heard
                            && distance(sim, m, target) > SwarmPacks.OWN_QUARRY_CELLS) strayChoices++;
                }
                return;
            }
            if (brokeTick < 0) {
                brokeTick = tick;
                for (int i = 0; i < members.length; i++) {
                    long m = members[i];
                    if (!sim.getRoster().isAliveById(m) || sim.identity().swarmStage(m) != SwarmStage.WHELP) {
                        boltFrom[i] = Float.NaN;
                        continue;
                    }
                    whelpsAtBreak++;
                    boltFrom[i] = toBore(sim, m);
                }
            }
            for (long m : members) {
                if (!sim.getRoster().isAliveById(m)) continue;
                if (packs.isCohesive(m, sim) || packs.cooldown(m, 1f, sim) != 1f) bonusAfterBreak++;
                if (packs.isLeader(m)) leaderAfterBreak++;
                if (packs.bolting(m) && sim.combat().targetId(m) != 0L) boltingWithQuarry++;
            }
            int boltEnd = brokeTick + Math.round(SwarmPacks.BOLT_SECONDS / BattleSimulation.TICK_DT) - 1;
            if (!measured && tick >= boltEnd) {
                measured = true;
                for (int i = 0; i < members.length; i++) {
                    if (Float.isNaN(boltFrom[i])) continue;
                    // A whelp killed mid-bolt ran as far as it got; it is judged where it fell.
                    float now = sim.world().hasPosition(members[i]) ? toBore(sim, members[i]) : boltFrom[i];
                    if (now < boltFrom[i]) whelpsCloser++;
                    boltCells += boltFrom[i] - now;
                }
            }
        }

        private float toBore(BattleSimulation sim, long body) {
            return (float) Math.hypot(sim.world().x(body) - (bore.x() + .5f), sim.world().y(body) - (bore.y() + .5f));
        }

        private static float distance(BattleSimulation sim, long a, long b) {
            if (sim.resolveUnit(b) == 0L) return Float.MAX_VALUE;
            return (float) Math.hypot(sim.world().x(a) - sim.world().x(b), sim.world().y(a) - sim.world().y(b));
        }

        int alive(BattleSimulation sim) {
            int n = 0;
            for (long m : members) if (sim.getRoster().isAliveById(m)) n++;
            return n;
        }
    }
}
