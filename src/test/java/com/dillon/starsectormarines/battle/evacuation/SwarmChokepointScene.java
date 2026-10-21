package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A swarm that has to funnel through a two-cell door in a sealed wall, and
 * then round a block, to reach Marines it cannot see: the case a shared flow
 * field could get wrong and per-body pathing does not. Two loops on the same
 * ground — {@code field} descends the prey field, {@code control} is the same
 * battle with {@code battle.swarm.flowField} off.
 *
 * <p>The door is off the straight line between the two, so a body that
 * steered toward its goal rather than down a field would pile against the
 * wall; that is the wall-hugging regression the scene watches for. Marines
 * are unarmed, held in place and healed: what is being judged is movement, and
 * a fight that kills the swarm would end the question early.
 */
public final class SwarmChokepointScene implements BehaviorScene {

    static final int WIDTH = 72;
    static final int HEIGHT = 36;
    static final int WALL_X = 30;
    static final int DOOR_Y = 6;
    private static final int RUNNERS = 40;
    private static final int TICKS = 1500;
    /** A body out of contact that moved less than this over {@link #STALL_WINDOW} ticks is stuck. */
    private static final float STALL_DISTANCE = 1f;
    private static final int STALL_WINDOW = 300;
    /** Bodies this close to a Marine are in the scrum, waiting for a slot rather than stuck. */
    private static final float SCRUM_RADIUS = 4f;
    private static final int[][] PREY = {{64, 18}, {64, 19}, {65, 18}};

    @Override public String id() { return "swarm-chokepoint"; }

    @Override public String label() {
        return "Swarm through a door and round a block on its flow field, against per-body paths";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("field", "control")) {
            Run first = run(loop, loop.equals("field"), frames);
            Run repeated = run(loop, loop.equals("field"), FrameSink.NONE);
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
                .seed(20261021L)
                .seal(WALL_X)
                .doorway(WALL_X, DOOR_Y)
                .doorway(WALL_X, DOOR_Y + 1)
                // A block between the door and the Marines, so the way on is
                // round it rather than straight across.
                .wall(44, 8, 50, 28);
        for (int i = 0; i < PREY.length; i++) {
            // Enough health that the scrum cannot kill them between two heals.
            builder.unit("prey" + i, Faction.MARINE, UnitType.MARINE, PREY[i][0], PREY[i][1],
                    spec -> spec.health(100_000f));
        }
        return builder.build();
    }

    private Run run(String loop, boolean field, FrameSink frames) {
        SceneWorld scene = build();
        try (BattleSimulation sim = scene.sim()) {
            sim.setSwarmFlowFieldEnabled(field);
            long[] prey = new long[PREY.length];
            for (int i = 0; i < PREY.length; i++) prey[i] = scene.unit("prey" + i);
            long[] runners = new long[RUNNERS];
            for (int i = 0; i < RUNNERS; i++) {
                runners[i] = sim.spawn(new EntitySpec("Runner " + i, Faction.DEFENDER,
                        UnitType.SWARM_RUNNER, 4 + (i % 8), 10 + (i / 8) * 4)
                        .role(UnitRole.SWARM_PRESSURE));
            }
            int[] arrival = new int[RUNNERS];
            Arrays.fill(arrival, -1);
            float[][] history = new float[STALL_WINDOW + 1][];
            boolean onFloor = true;
            boolean atDoor = true;
            int stalled = 0;
            String stallWhere = "";
            int builds = 0;
            int swarmPaths = 0;
            float[] travelled = new float[RUNNERS];
            float[] last = positions(sim, runners);
            long trace = 17L;
            for (int tick = 0; tick < TICKS; tick++) {
                for (int i = 0; i < prey.length; i++) {
                    if (sim.resolveUnit(prey[i]) == 0L) continue;
                    sim.world().setCellPos(prey[i], PREY[i][0], PREY[i][1]);
                    sim.world().setHp(prey[i], sim.world().maxHp(prey[i]));
                }
                sim.advance(BattleSimulation.TICK_DT);
                TickInnerProfile profile = sim.getTickInnerProfile();
                builds += profile.countOf(TickInnerProfile.Bucket.SWARM_FLOW_FIELD_BUILD);
                swarmPaths += profile.countOf(TickInnerProfile.Bucket.SWARM_PATHFIND);
                float[] now = positions(sim, runners);
                for (int i = 0; i < RUNNERS; i++) {
                    if (sim.resolveUnit(runners[i]) == 0L) continue;
                    float x = now[i * 2];
                    float y = now[i * 2 + 1];
                    int cx = (int) Math.floor(x);
                    int cy = (int) Math.floor(y);
                    onFloor &= sim.getGrid().isWalkable(cx, cy);
                    if (cx == WALL_X) atDoor &= cy == DOOR_Y || cy == DOOR_Y + 1;
                    travelled[i] += (float) Math.hypot(x - last[i * 2], y - last[i * 2 + 1]);
                    if (arrival[i] < 0 && nearestPrey(x, y) <= SwarmMovement.LOCAL_PURSUIT_RANGE) {
                        arrival[i] = tick;
                    }
                    trace = trace * 31 + Float.floatToIntBits(x);
                    trace = trace * 31 + Float.floatToIntBits(y);
                }
                history[tick % history.length] = now;
                if (tick >= STALL_WINDOW) {
                    float[] then = history[(tick - STALL_WINDOW) % history.length];
                    for (int i = 0; i < RUNNERS; i++) {
                        if (sim.resolveUnit(runners[i]) == 0L) continue;
                        if (nearestPrey(now[i * 2], now[i * 2 + 1]) <= SCRUM_RADIUS) continue;
                        if (Math.hypot(now[i * 2] - then[i * 2], now[i * 2 + 1] - then[i * 2 + 1])
                                < STALL_DISTANCE) {
                            int count = countStalled(sim, runners, now, then);
                            if (count > stalled) {
                                stalled = count;
                                stallWhere = " (first at " + (int) now[i * 2] + "," + (int) now[i * 2 + 1]
                                        + " on tick " + tick + ")";
                            }
                            break;
                        }
                    }
                }
                last = now;
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / " + tick);
            }
            int arrived = 0;
            for (int a : arrival) if (a >= 0) arrived++;
            int[] sorted = Arrays.stream(arrival).filter(a -> a >= 0).sorted().toArray();
            double meanTravel = 0;
            for (float t : travelled) meanTravel += t / RUNNERS;

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("stays-on-floor", onFloor,
                    "no runner ever stood in a wall or the block"));
            verdicts.add(Verdict.of("crosses-at-the-door", atDoor,
                    "every crossing of the wall column was through its door"));
            verdicts.add(Verdict.of("reaches-the-goal", arrived == RUNNERS,
                    arrived + " of " + RUNNERS + " came within local-pursuit range of the Marines"));
            verdicts.add(Verdict.of("nobody-stuck", stalled == 0,
                    stalled + " runners out of contact moved under " + STALL_DISTANCE
                            + " cells in some " + STALL_WINDOW + "-tick window" + stallWhere));
            if (field) {
                verdicts.add(Verdict.of("field-in-use", builds > 0,
                        builds + " field builds; " + swarmPaths + " per-body swarm path calls"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("arrived", arrived);
            metrics.put("medianArrivalTick", sorted.length == 0 ? -1 : sorted[sorted.length / 2]);
            metrics.put("lastArrivalTick", sorted.length == 0 ? -1 : sorted[sorted.length - 1]);
            metrics.put("meanCellsTravelled", Math.round(meanTravel * 10) / 10.0);
            metrics.put("stalled", stalled);
            metrics.put("fieldBuilds", builds);
            metrics.put("swarmPathCalls", swarmPaths);
            return new Run(trace, verdicts, metrics);
        }
    }

    private static int countStalled(BattleSimulation sim, long[] runners, float[] now, float[] then) {
        int count = 0;
        for (int i = 0; i < runners.length; i++) {
            if (sim.resolveUnit(runners[i]) == 0L) continue;
            if (nearestPrey(now[i * 2], now[i * 2 + 1]) <= SCRUM_RADIUS) continue;
            if (Math.hypot(now[i * 2] - then[i * 2], now[i * 2 + 1] - then[i * 2 + 1])
                    < STALL_DISTANCE) count++;
        }
        return count;
    }

    private static float nearestPrey(float x, float y) {
        float best = Float.MAX_VALUE;
        for (int[] p : PREY) {
            best = Math.min(best, (float) Math.hypot(x - (p[0] + 0.5f), y - (p[1] + 0.5f)));
        }
        return best;
    }

    private static float[] positions(BattleSimulation sim, long[] runners) {
        float[] out = new float[runners.length * 2];
        for (int i = 0; i < runners.length; i++) {
            if (sim.resolveUnit(runners[i]) == 0L) continue;
            out[i * 2] = sim.world().x(runners[i]);
            out[i * 2 + 1] = sim.world().y(runners[i]);
        }
        return out;
    }

    private record Run(long trace, List<Verdict> verdicts, Map<String, Number> metrics) {}
}
