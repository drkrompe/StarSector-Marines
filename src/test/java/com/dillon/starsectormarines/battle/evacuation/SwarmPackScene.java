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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Runner pack led by an elder crosses open ground, threads a street between
 * two blocks and hits a squad in the square beyond (`colony-breach-nouns.md`,
 * Pack movement). Loops on the same ground: {@code pack} with
 * {@code battle.swarm.pack} on, {@code control} with it off (every body
 * descends the prey field from its own cell, which is the file the owner saw),
 * and {@code leader-down}, where the elder is killed on the open ground.
 *
 * <p>Verdicts assert the mechanic: on open ground the pack is spread across
 * its width and never runs single file for more than {@link #MAX_FILE} bodies;
 * it stalks, feints and rushes in that order; its members close on the squad
 * from more than one side; it breaks within a tick of its leader's death.
 * Who hit whom and how often is a reading in the metrics. Runners are given
 * health enough that the squad's fire cannot thin the pack, and the squad is
 * held in place and healed: what is judged is movement.
 */
public final class SwarmPackScene implements BehaviorScene {

    static final int WIDTH = 100;
    static final int HEIGHT = 44;
    /** The street runs between the blocks over these columns and rows. */
    static final int STREET_X0 = 48;
    static final int STREET_X1 = 70;
    static final int STREET_Y0 = 16;
    static final int STREET_Y1 = 20;
    /** Open ground the pack is judged on: its centre between these columns. */
    static final float OPEN_X0 = 14f;
    static final float OPEN_X1 = 40f;
    private static final int RUNNERS = 8;
    private static final int RELEASE_SPACING = SwarmReinforcementSystem.SURGE_RELEASE_SPACING_TICKS;
    private static final int MOUTH_X = 5;
    private static final int MOUTH_Y = 18;
    private static final int TICKS = 1500;
    private static final int FRAME_EVERY = 4;
    /** The longest single file the pack may run on open ground. */
    static final int MAX_FILE = 3;
    /** Bodies within this lateral band of each other, and this close behind, are in file. */
    private static final float FILE_BAND = 0.6f;
    private static final float FILE_GAP = 2f;
    /** Median lateral extent of the pack on open ground, at least. */
    static final float MIN_SPREAD = 3f;
    /** Bearings at which members first come this close to the squad are their approach. */
    private static final float APPROACH_RADIUS = 4f;
    /** The approaches must span at least this many degrees. */
    static final float MIN_APPROACH_SPREAD = 60f;
    private static final int HEADING_WINDOW = 15;
    private static final int[][] SQUAD = {{85, 16}, {85, 20}, {88, 17}, {88, 19}};
    private static final float SQUAD_X = 86.5f;
    private static final float SQUAD_Y = 18.5f;
    private static final float LEADER_DOWN_X = 26f;
    /** Cells the review lights the ground from: the open field, the street, the square. */
    private static final int[][] OBSERVER = {{24, 22}, {59, 18}, {86, 22}};

    @Override public String id() { return "swarm-pack"; }

    @Override public String label() {
        return "A Runner pack crosses open ground and a street to hit a squad: fan, flank, feint, staggered rush";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("pack", "control", "leader-down")) {
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
                .seed(20261022L)
                .wall(STREET_X0, 0, STREET_X1, STREET_Y0 - 1)
                .wall(STREET_X0, STREET_Y1 + 1, STREET_X1, HEIGHT - 1);
        for (int i = 0; i < SQUAD.length; i++) {
            builder.unit("marine" + i, Faction.MARINE, UnitType.MARINE, SQUAD[i][0], SQUAD[i][1],
                    spec -> spec.health(100_000f));
        }
        return builder.build();
    }

    private Run run(String loop, FrameSink frames) {
        SceneWorld scene = build();
        try (BattleSimulation sim = scene.sim()) {
            sim.setSwarmPackEnabled(!loop.equals("control"));
            // A neutral observer: light the whole ground so the pack is drawn before the squad sees it.
            for (int[] eye : OBSERVER) sim.getFogOfWar().addEphemeralSource(eye[0], eye[1], 60, 60f);
            long[] squad = new long[SQUAD.length];
            for (int i = 0; i < SQUAD.length; i++) squad[i] = scene.unit("marine" + i);
            long[] runners = new long[RUNNERS];
            int packId = 0;
            long leader = 0L;
            SwarmPackPlans.Phase phase = SwarmPackPlans.Phase.TRAVEL;
            boolean onFloor = true;
            List<Float> spreads = new ArrayList<>();
            int longestFile = 0;
            int longestFileTick = -1;
            float[] approach = new float[RUNNERS];
            Arrays.fill(approach, Float.NaN);
            int[] contact = new int[RUNNERS];
            Arrays.fill(contact, -1);
            List<SwarmPackPlans.Phase> phases = new ArrayList<>();
            int leaderKilledTick = -1;
            int brokenSeenTick = -1;
            int laneFollowersAfterBreak = 0;
            int squadHits = 0;
            float[][] centroids = new float[TICKS][];
            long trace = 23L;
            for (int tick = 0; tick < TICKS; tick++) {
                for (int i = 0; i < squad.length; i++) {
                    if (sim.resolveUnit(squad[i]) == 0L) continue;
                    if (sim.world().hp(squad[i]) < sim.world().maxHp(squad[i])) squadHits++;
                    sim.world().setCellPos(squad[i], SQUAD[i][0], SQUAD[i][1]);
                    sim.world().setHp(squad[i], sim.world().maxHp(squad[i]));
                }
                if (tick % RELEASE_SPACING == 0 && tick / RELEASE_SPACING < RUNNERS) {
                    // Out of the mouth one body every couple of ticks, as a bore's surge leaves it.
                    int i = tick / RELEASE_SPACING;
                    EntitySpec spec = new EntitySpec("Runner " + i, Faction.DEFENDER, UnitType.SWARM_RUNNER,
                            MOUTH_X, MOUTH_Y + (i & 1))
                            .role(UnitRole.SWARM_PRESSURE)
                            .swarmStage(i == 0 ? SwarmStage.ELDER : SwarmStage.ADULT);
                    if (!loop.equals("leader-down") || i != 0) spec.health(100_000f);
                    runners[i] = sim.spawn(spec);
                    // As a surge stamps it: the elder at the head opens the pack, the rest join.
                    if (i == 0) {
                        packId = sim.swarmPacks().open(runners[0]);
                        leader = sim.swarmPacks().leaderOfPack(packId);
                    } else {
                        sim.swarmPacks().join(packId, runners[i]);
                    }
                }
                if (loop.equals("leader-down") && leaderKilledTick < 0 && sim.resolveUnit(leader) != 0L
                        && sim.world().x(leader) >= LEADER_DOWN_X) {
                    sim.applyDamage(leader, squad[0], 1_000_000f, 100f, 0f);
                    leaderKilledTick = tick;
                }
                sim.advance(BattleSimulation.TICK_DT);
                SwarmPackPlans.Plan plan = sim.swarmPackPlans().planFor(packId);
                if (plan != null) phase = plan.phase();
                if (phases.isEmpty() || phases.get(phases.size() - 1) != phase) phases.add(phase);
                if (leaderKilledTick >= 0 && brokenSeenTick < 0 && !sim.swarmPacks().isStanding(packId)) {
                    brokenSeenTick = tick;
                }
                if (brokenSeenTick >= 0 && tick == brokenSeenTick + 1) {
                    for (long runner : runners) {
                        if (sim.resolveUnit(runner) == 0L) continue;
                        if (sim.swarmPackPlans().planFor(packId) != null) laneFollowersAfterBreak++;
                    }
                }

                float cx = 0f;
                float cy = 0f;
                int alive = 0;
                for (int i = 0; i < RUNNERS; i++) {
                    if (sim.resolveUnit(runners[i]) == 0L) continue;
                    float x = sim.world().x(runners[i]);
                    float y = sim.world().y(runners[i]);
                    onFloor &= sim.getGrid().isWalkable((int) Math.floor(x), (int) Math.floor(y));
                    cx += x;
                    cy += y;
                    alive++;
                    trace = trace * 31 + Float.floatToIntBits(x);
                    trace = trace * 31 + Float.floatToIntBits(y);
                    float dx = x - SQUAD_X;
                    float dy = y - SQUAD_Y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (Float.isNaN(approach[i]) && d <= APPROACH_RADIUS) {
                        approach[i] = (float) Math.toDegrees(Math.atan2(dy, dx));
                    }
                    if (contact[i] < 0 && d <= 3f) contact[i] = tick;
                }
                if (alive == 0) break;
                cx /= alive;
                cy /= alive;
                centroids[tick] = new float[] {cx, cy};
                if (tick >= HEADING_WINDOW && cx >= OPEN_X0 && cx <= OPEN_X1 && alive >= 4
                        && leaderKilledTick < 0) {
                    float[] then = centroids[tick - HEADING_WINDOW];
                    float hx = cx - then[0];
                    float hy = cy - then[1];
                    float length = (float) Math.sqrt(hx * hx + hy * hy);
                    if (length > 0.5f && tick % 5 == 0) {
                        hx /= length;
                        hy /= length;
                        float[] along = new float[alive];
                        float[] lateral = new float[alive];
                        int n = 0;
                        for (long runner : runners) {
                            if (sim.resolveUnit(runner) == 0L) continue;
                            float rx = sim.world().x(runner) - cx;
                            float ry = sim.world().y(runner) - cy;
                            along[n] = rx * hx + ry * hy;
                            lateral[n] = -rx * hy + ry * hx;
                            n++;
                        }
                        float low = Float.MAX_VALUE;
                        float high = -Float.MAX_VALUE;
                        for (float l : lateral) {
                            low = Math.min(low, l);
                            high = Math.max(high, l);
                        }
                        spreads.add(high - low);
                        int file = longestFile(along, lateral);
                        if (file > longestFile) {
                            longestFile = file;
                            longestFileTick = tick;
                        }
                    }
                }
                if (tick % FRAME_EVERY == 0) {
                    frames.frame(loop, sim, tick, loop + " / " + tick + " / " + phase.name().toLowerCase());
                }
            }

            float medianSpread = median(spreads);
            float approachSpread = angularSpread(approach);
            int approached = 0;
            for (float a : approach) if (!Float.isNaN(a)) approached++;
            int firstContact = Integer.MAX_VALUE;
            int lastContact = -1;
            for (int c : contact) {
                if (c < 0) continue;
                firstContact = Math.min(firstContact, c);
                lastContact = Math.max(lastContact, c);
            }

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("stays-on-floor", onFloor, "no runner ever stood in a block"));
            verdicts.add(Verdict.of("elder-leads", leader == runners[0],
                    "the pack's first elder is its leader"));
            if (loop.equals("pack")) {
                verdicts.add(Verdict.of("spread-across-width", medianSpread >= MIN_SPREAD,
                        "median lateral extent on open ground " + round(medianSpread) + " cells over "
                                + spreads.size() + " samples (at least " + MIN_SPREAD + ")"));
                verdicts.add(Verdict.of("no-long-file", longestFile <= MAX_FILE && !spreads.isEmpty(),
                        "longest single file on open ground " + longestFile + " bodies"
                                + (longestFileTick >= 0 ? " (tick " + longestFileTick + ")" : "")
                                + " (at most " + MAX_FILE + ")"));
                boolean ordered = phases.indexOf(SwarmPackPlans.Phase.STALK) >= 0
                        && phases.indexOf(SwarmPackPlans.Phase.FEINT) > phases.indexOf(SwarmPackPlans.Phase.STALK)
                        && phases.indexOf(SwarmPackPlans.Phase.RUSH) > phases.indexOf(SwarmPackPlans.Phase.FEINT);
                verdicts.add(Verdict.of("stalk-feint-rush", ordered, "pack phases in order: " + phases));
                verdicts.add(Verdict.of("several-approaches", approached >= 2
                                && approachSpread >= MIN_APPROACH_SPREAD,
                        approached + " members closed within " + APPROACH_RADIUS + " cells of the squad,"
                                + " their bearings spanning " + round(approachSpread) + " degrees (at least "
                                + MIN_APPROACH_SPREAD + ")"));
            }
            if (loop.equals("leader-down")) {
                boolean broke = leaderKilledTick >= 0 && brokenSeenTick >= 0
                        && brokenSeenTick - leaderKilledTick <= 1;
                verdicts.add(Verdict.of("breaks-on-leader-death", broke,
                        "leader killed on tick " + leaderKilledTick + ", pack broken on tick " + brokenSeenTick));
                verdicts.add(Verdict.of("broken-pack-runs-alone", broke && laneFollowersAfterBreak == 0,
                        laneFollowersAfterBreak + " members still moving with the pack the tick after the break"));
            }

            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("medianSpread", round(medianSpread));
            metrics.put("longestFile", longestFile);
            metrics.put("openGroundSamples", spreads.size());
            metrics.put("approached", approached);
            metrics.put("approachSpreadDegrees", round(approachSpread));
            metrics.put("firstContactTick", firstContact == Integer.MAX_VALUE ? -1 : firstContact);
            metrics.put("contactSpreadTicks", lastContact < 0 ? -1 : lastContact - firstContact);
            metrics.put("squadHitTicks", squadHits);
            metrics.put("leaderKilledTick", leaderKilledTick);
            metrics.put("brokenTick", brokenSeenTick);
            return new Run(trace, verdicts, metrics);
        }
    }

    /**
     * The most bodies in one file: a chain in which each stands within
     * {@link #FILE_BAND} of the last sideways and no more than {@link #FILE_GAP}
     * behind it.
     */
    static int longestFile(float[] along, float[] lateral) {
        int n = along.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Float.compare(along[a], along[b]));
        int[] chain = new int[n];
        int best = 0;
        for (int j = 0; j < n; j++) {
            chain[j] = 1;
            for (int i = 0; i < j; i++) {
                float gap = along[order[j]] - along[order[i]];
                if (gap <= FILE_GAP && Math.abs(lateral[order[j]] - lateral[order[i]]) <= FILE_BAND) {
                    chain[j] = Math.max(chain[j], chain[i] + 1);
                }
            }
            best = Math.max(best, chain[j]);
        }
        return best;
    }

    /** The widest arc the given bearings (degrees, NaN for none) leave between their extremes. */
    static float angularSpread(float[] bearings) {
        List<Float> list = new ArrayList<>();
        for (float b : bearings) if (!Float.isNaN(b)) list.add((b + 360f) % 360f);
        if (list.size() < 2) return 0f;
        list.sort(Float::compare);
        float largestGap = 360f - list.get(list.size() - 1) + list.get(0);
        for (int i = 1; i < list.size(); i++) largestGap = Math.max(largestGap, list.get(i) - list.get(i - 1));
        return 360f - largestGap;
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) return 0f;
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compare);
        return sorted.get(sorted.size() / 2);
    }

    private static double round(float value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Centre of the living Runners, for a camera that follows the pack. */
    static float[] runnerCentre(BattleSimulation sim) {
        float x = 0f;
        float y = 0f;
        int n = 0;
        for (int i = 0, count = sim.liveUnitCount(); i < count; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.SWARM_RUNNER) continue;
            x += sim.world().x(unit);
            y += sim.world().y(unit);
            n++;
        }
        return n == 0 ? null : new float[] {x / n, y / n};
    }

    private record Run(long trace, List<Verdict> verdicts, Map<String, Number> metrics) {}
}
