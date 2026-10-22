package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Runner pack of eight, led by an elder, closes on a lone marine with a
 * fireteam of three standing a few cells beyond (`colony-breach-nouns.md`,
 * Pack movement: the pile-on). Loops on the same ground: {@code split} with
 * {@code battle.swarm.packSplit} on, {@code control} with it off (the whole
 * pack takes the leader's quarry and plants round it), and {@code remerge},
 * where the quarry a sub-pack was sent at is killed and the sub-pack folds
 * back into its pack.
 *
 * <p>Verdicts assert the mechanic: once more of the pack is on one body than
 * can strike it, the surplus is peeled off as a sub-pack onto other quarry
 * within a few seconds; no sub-pack is a lone stray; no Runner in the fight
 * stands still for long, whether it is striking or waiting; a sub-pack whose
 * quarry dies re-merges at once. Who was struck how often is a reading. The
 * marines are held in place and healed and the Runners cannot be killed, so
 * what is judged is movement and targeting.
 */
public final class SwarmPackCrowdScene implements BehaviorScene {

    static final int WIDTH = 64;
    static final int HEIGHT = 40;
    private static final int RUNNERS = 8;
    private static final int RELEASE_SPACING = SwarmReinforcementSystem.SURGE_RELEASE_SPACING_TICKS;
    private static final int MOUTH_X = 6;
    private static final int MOUTH_Y = 22;
    private static final int TICKS = 1350;
    private static final int FRAME_EVERY = 3;
    /** The review opens as the pack comes on; the march across the field is the swarm-pack scene's. */
    private static final int FIRST_FRAME = 360;
    /** The lone marine, then the fireteam beyond it. */
    static final int[][] MARINES = {{38, 22}, {46, 15}, {48, 16}, {46, 17}};
    /** A Runner whose target is within this many cells is in the fight. */
    static final float ENGAGED_CELLS = 5f;
    /** Less than this displacement in a tick is standing still: a tenth of a Runner's stride. */
    static final float STILL_CELLS = 0.01f;
    /** No Runner in the fight may stand still longer than this, striking or waiting. */
    static final int MAX_STILL_TICKS = 45;
    /** Surplus on one body must be peeled onto other quarry within this. */
    static final int RETARGET_TICKS = 90;
    /** The tick after which the remerge loop kills the sub-pack's quarry. */
    private static final int KILL_AFTER_SPLIT_TICKS = 240;
    /** Cells the review lights the ground from. */
    private static final int[][] OBSERVER = {{20, 22}, {44, 18}};

    @Override public String id() { return "swarm-pack-crowd"; }

    @Override public String label() {
        return "A Runner pack piles on one marine: the surplus peels off as a sub-pack, and none stand still";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("split", "control", "remerge")) {
            Run first = run(loop, frames);
            Run repeated = run(loop, FrameSink.NONE);
            List<Verdict> verdicts = new ArrayList<>(first.verdicts);
            verdicts.add(Verdict.of("repeatable", first.trace == repeated.trace,
                    "a replay of the same loop moves every body the same way"));
            reports.add(new SceneReport(id(), loop, TICKS, verdicts, first.metrics));
        }
        return reports;
    }

    static SceneWorld build() {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT)
                .missionCompletion(false)
                .seed(20261022L);
        for (int i = 0; i < MARINES.length; i++) {
            builder.unit("marine" + i, Faction.MARINE, UnitType.MARINE, MARINES[i][0], MARINES[i][1],
                    spec -> spec.health(100_000f));
        }
        return builder.build();
    }

    private Run run(String loop, FrameSink frames) {
        SceneWorld scene = build();
        try (BattleSimulation sim = scene.sim()) {
            sim.swarmPackPlans().setSplitEnabled(!loop.equals("control"));
            for (int[] eye : OBSERVER) sim.getFogOfWar().addEphemeralSource(eye[0], eye[1], 60, 60f);
            long[] marines = new long[MARINES.length];
            for (int i = 0; i < MARINES.length; i++) marines[i] = scene.unit("marine" + i);
            boolean[] held = new boolean[MARINES.length];
            Arrays.fill(held, true);
            long[] runners = new long[RUNNERS];
            float[] lastX = new float[RUNNERS];
            float[] lastY = new float[RUNNERS];
            int[] still = new int[RUNNERS];
            int packId = 0;
            boolean onFloor = true;
            int maxStillStriking = 0;
            int maxStillWaiting = 0;
            int maxStillTick = -1;
            long stillWaitingTicks = 0;
            long engagedTicks = 0;
            long inReachTicks = 0;
            long waitingTicks = 0;
            int crowdedTick = -1;
            int peeledTick = -1;
            int firstSplitTick = -1;
            int maxSubPacks = 0;
            int strayTicks = 0;
            int killTick = -1;
            long killed = 0L;
            int remergedTick = -1;
            int mostOnOne = 0;
            int maxTargets = 0;
            Map<Long, Integer> hitTicks = new HashMap<>();
            long trace = 29L;
            for (int tick = 0; tick < TICKS; tick++) {
                for (int i = 0; i < marines.length; i++) {
                    if (!held[i] || sim.resolveUnit(marines[i]) == 0L) continue;
                    if (sim.world().hp(marines[i]) < sim.world().maxHp(marines[i])) hitTicks.merge((long) i, 1, Integer::sum);
                    sim.world().setCellPos(marines[i], MARINES[i][0], MARINES[i][1]);
                    sim.world().setHp(marines[i], sim.world().maxHp(marines[i]));
                }
                if (tick % RELEASE_SPACING == 0 && tick / RELEASE_SPACING < RUNNERS) {
                    int i = tick / RELEASE_SPACING;
                    EntitySpec spec = new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                            MOUTH_X, MOUTH_Y + (i & 1))
                            .role(UnitRole.SWARM_PRESSURE)
                            .swarmStage(i == 0 ? SwarmStage.ELDER : SwarmStage.ADULT)
                            .health(100_000f);
                    runners[i] = sim.spawn(spec);
                    lastX[i] = sim.world().x(runners[i]);
                    lastY[i] = sim.world().y(runners[i]);
                    if (i == 0) packId = sim.swarmPacks().open(runners[0]);
                    else sim.swarmPacks().join(packId, runners[i]);
                }
                if (loop.equals("remerge") && killTick < 0 && firstSplitTick >= 0
                        && tick >= firstSplitTick + KILL_AFTER_SPLIT_TICKS) {
                    SwarmPackPlans.Plan plan = sim.swarmPackPlans().planFor(packId);
                    if (plan != null && plan.subPackCount() > 0) {
                        killed = plan.subQuarry(0);
                        for (int i = 0; i < marines.length; i++) if (marines[i] == killed) held[i] = false;
                        sim.applyDamage(killed, runners[0], 1_000_000f, 100f, 0f);
                        killTick = tick;
                    }
                }
                sim.advance(BattleSimulation.TICK_DT);

                SwarmPackPlans.Plan plan = sim.swarmPackPlans().planFor(packId);
                int subPacks = plan == null ? 0 : plan.subPackCount();
                maxSubPacks = Math.max(maxSubPacks, subPacks);
                if (subPacks > 0 && firstSplitTick < 0) firstSplitTick = tick;
                for (int s = 0; s < subPacks; s++) if (plan.subSize(s) < 2) strayTicks++;
                if (killTick >= 0 && remergedTick < 0 && plan != null) {
                    boolean gone = true;
                    for (int s = 0; s < plan.subPackCount(); s++) if (plan.subQuarry(s) == killed) gone = false;
                    if (gone) remergedTick = tick;
                }

                Map<Long, Integer> onTarget = new HashMap<>();
                Map<Long, Integer> hunting = new HashMap<>();
                long striker = 0L;
                for (int i = 0; i < RUNNERS; i++) {
                    long r = runners[i];
                    if (r == 0L || sim.resolveUnit(r) == 0L) continue;
                    float x = sim.world().x(r);
                    float y = sim.world().y(r);
                    onFloor &= sim.getGrid().isWalkable((int) Math.floor(x), (int) Math.floor(y));
                    trace = trace * 31 + Float.floatToIntBits(x);
                    trace = trace * 31 + Float.floatToIntBits(y);
                    float moved = (float) Math.hypot(x - lastX[i], y - lastY[i]);
                    lastX[i] = x;
                    lastY[i] = y;
                    long target = sim.resolveUnit(sim.combat().targetId(r));
                    if (target == 0L) {
                        still[i] = 0;
                        continue;
                    }
                    hunting.merge(target, 1, Integer::sum);
                    striker = r;
                    float d = (float) Math.hypot(sim.world().x(target) - x, sim.world().y(target) - y);
                    if (d > ENGAGED_CELLS) {
                        still[i] = 0;
                        continue;
                    }
                    engagedTicks++;
                    boolean inReach = d <= sim.combat().attackRange(r) + 0.05f;
                    onTarget.merge(target, 1, Integer::sum);
                    if (inReach) inReachTicks++;
                    else waitingTicks++;
                    still[i] = moved < STILL_CELLS ? still[i] + 1 : 0;
                    if (still[i] > 0 && !inReach) stillWaitingTicks++;
                    if (inReach && still[i] > maxStillStriking) {
                        maxStillStriking = still[i];
                        maxStillTick = tick;
                    }
                    if (!inReach && still[i] > maxStillWaiting) {
                        maxStillWaiting = still[i];
                        maxStillTick = tick;
                    }
                }
                for (int n : onTarget.values()) mostOnOne = Math.max(mostOnOne, n);
                maxTargets = Math.max(maxTargets, onTarget.size());
                if (crowdedTick < 0) {
                    for (Map.Entry<Long, Integer> e : onTarget.entrySet()) {
                        if (e.getValue() > SwarmPackPlans.strikeSlots(sim, e.getKey(), striker)) crowdedTick = tick;
                    }
                }
                if (crowdedTick >= 0 && peeledTick < 0 && hunting.size() >= 2) {
                    int most = 0;
                    int total = 0;
                    for (int n : hunting.values()) {
                        most = Math.max(most, n);
                        total += n;
                    }
                    if (total - most >= SwarmPackPlans.MIN_SUB_PACK) peeledTick = tick;
                }
                if (tick >= FIRST_FRAME && tick % FRAME_EVERY == 0) {
                    String phase = plan == null ? "-" : plan.phase().name().toLowerCase();
                    frames.frame(loop, sim, tick, loop + " / " + tick + " / " + phase
                            + (subPacks > 0 ? " / sub-packs " + subPacks : ""));
                }
            }

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("stays-on-floor", onFloor, "no runner ever stood off the floor"));
            int maxStill = Math.max(maxStillStriking, maxStillWaiting);
            if (!loop.equals("control")) {
                verdicts.add(Verdict.of("surplus-retargets", crowdedTick >= 0 && peeledTick >= 0
                                && peeledTick - crowdedTick <= RETARGET_TICKS,
                        "more on one body than its strike slots from tick " + crowdedTick + ", "
                                + SwarmPackPlans.MIN_SUB_PACK + "+ runners hunting other quarry from tick " + peeledTick
                                + " (within " + RETARGET_TICKS + ")"));
                verdicts.add(Verdict.of("sub-packs-not-strays", maxSubPacks > 0 && strayTicks == 0,
                        "most sub-packs at once " + maxSubPacks + ", sub-pack-ticks under "
                                + SwarmPackPlans.MIN_SUB_PACK + " bodies " + strayTicks));
                verdicts.add(Verdict.of("keeps-moving", maxStill <= MAX_STILL_TICKS,
                        "longest a runner in the fight stood still: " + maxStillStriking + " ticks striking, "
                                + maxStillWaiting + " waiting (tick " + maxStillTick + "; at most "
                                + MAX_STILL_TICKS + ")"));
            }
            if (loop.equals("remerge")) {
                verdicts.add(Verdict.of("remerges-on-quarry-death", killTick >= 0 && remergedTick >= 0
                                && remergedTick - killTick <= 1,
                        "sub-pack quarry killed on tick " + killTick + ", sub-pack gone on tick " + remergedTick));
            }

            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("maxStillStrikingTicks", maxStillStriking);
            metrics.put("maxStillWaitingTicks", maxStillWaiting);
            metrics.put("stillWaitingTicks", stillWaitingTicks);
            metrics.put("engagedRunnerTicks", engagedTicks);
            metrics.put("inReachShare", engagedTicks == 0 ? 0 : round((float) inReachTicks / engagedTicks));
            metrics.put("waitingShare", engagedTicks == 0 ? 0 : round((float) waitingTicks / engagedTicks));
            metrics.put("mostOnOneTarget", mostOnOne);
            metrics.put("mostTargetsAtOnce", maxTargets);
            metrics.put("crowdedTick", crowdedTick);
            metrics.put("peeledTick", peeledTick);
            metrics.put("firstSplitTick", firstSplitTick);
            metrics.put("maxSubPacks", maxSubPacks);
            metrics.put("killTick", killTick);
            metrics.put("remergedTick", remergedTick);
            for (int i = 0; i < MARINES.length; i++) {
                metrics.put("hitTicks" + (i == 0 ? "Lone" : "Team" + i), hitTicks.getOrDefault((long) i, 0));
            }
            return new Run(trace, verdicts, metrics);
        }
    }

    private static double round(float value) {
        return Math.round(value * 100) / 100.0;
    }

    private record Run(long trace, List<Verdict> verdicts, Map<String, Number> metrics) {}
}
