package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickProfile;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Same-build quiet-travel controls for lateral squad spacing. Four squads start
 * in a compact column and retain one common destination. The passage variant
 * adds a five-cell-wide neck, with enough open ground on either side to observe
 * spreading independently of arrival compression. No observer authors movement.
 *
 * <p>All movement uses production AttackMove, formation, separation and routing.
 * This is opt-in battle evidence, not a default unit test. Timings include JIT
 * and scheduler noise and are readings, never portable pass/fail thresholds.
 * Controls carry the same safety bars; only the subject must improve lateral
 * separation. Failure is retained rather than relabelled as successful traffic.
 */
public final class SquadTrafficScene implements BehaviorScene {
    private static final String SWITCH = "battle.squad.traffic";
    private static final int WIDTH = 160;
    private static final int HEIGHT = 48;
    private static final int SQUADS = 4;
    private static final int MEMBERS = 6;
    private static final int TICKS = 3300;
    private static final int GOAL_X = 149;
    private static final int GOAL_Y = 24;
    private static final int NECK_START = 70;
    private static final int NECK_END = 86;

    @Override public String id() { return "squad-traffic"; }
    @Override public String label() {
        return "Squad traffic: open spreading, corridor compression, and recovery";
    }

    @Override public List<SceneReport> play(FrameSink frames) {
        String previous = System.getProperty(SWITCH);
        try {
            List<SceneReport> reports = new ArrayList<>();
            for (boolean passage : new boolean[]{false, true}) {
                Reading control = run(frames, passage, false);
                Reading subject = run(frames, passage, true);
                reports.add(report(control, null));
                reports.add(report(subject, control));
            }
            Reading crossingControl = runCrossing(frames, false);
            Reading crossingSubject = runCrossing(frames, true);
            reports.add(report(crossingControl, null));
            reports.add(report(crossingSubject, crossingControl));
            return reports;
        } finally {
            if (previous == null) System.clearProperty(SWITCH);
            else System.setProperty(SWITCH, previous);
        }
    }

    private Reading run(FrameSink frames, boolean passage, boolean enabled) {
        System.setProperty(SWITCH, Boolean.toString(enabled));
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        if (passage) {
            builder.wall(NECK_START, 0, NECK_END, 21);
            builder.wall(NECK_START, 27, NECK_END, HEIGHT - 1);
        }
        for (int s = 0; s < SQUADS; s++) {
            builder.squad(key(s)).faction(Faction.MARINE).type(UnitType.MARINE)
                    .size(MEMBERS).at(14 + 5 * s, GOAL_Y).primary("weapon.smg")
                    .assigned(id -> ObjectiveAssignment.attackMove(id, GOAL_X, GOAL_Y)).done();
        }
        // Sealed and outside sight/reach: present, but not an attractor or contact.
        builder.wall(154, 0, 154, 8).wall(154, 8, 159, 8);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 157, 3,
                spec -> spec.moveSpeed(0f));
        SceneWorld world = builder.build();
        return play(world, frames, new Reading(world, passage, false, enabled));
    }

    private Reading runCrossing(FrameSink frames, boolean enabled) {
        System.setProperty(SWITCH, Boolean.toString(enabled));
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, WIDTH).missionCompletion(false);
        for (int s = 0; s < SQUADS; s++) {
            boolean northbound = s >= 2;
            int x = northbound ? 80 : 14 + 5 * s;
            int y = northbound ? 146 - 5 * (s - 2) : 80;
            int gx = northbound ? 80 : GOAL_X;
            int gy = northbound ? 11 : 80;
            builder.squad(key(s)).faction(Faction.MARINE).type(UnitType.MARINE)
                    .size(MEMBERS).at(x, y).primary("weapon.smg")
                    .assigned(id -> ObjectiveAssignment.attackMove(id, gx, gy)).done();
        }
        builder.wall(154, 0, 154, 8).wall(154, 8, 159, 8);
        builder.unit("far", Faction.DEFENDER, UnitType.MARINE, 157, 3,
                spec -> spec.moveSpeed(0f));
        SceneWorld world = builder.build();
        return play(world, frames, new Reading(world, false, true, enabled));
    }

    private Reading play(SceneWorld world, FrameSink frames, Reading reading) {
        SceneRun run = SceneRun.of(world).frames(frames, reading.loop, 45,
                tick -> String.format(Locale.ROOT,
                        "%s | tick %d | front %.1f | lateral span %.2f | crossed %d/%d",
                        reading.loop, tick, reading.meanX, reading.span,
                        reading.crossed, SQUADS));
        try {
            reading.sample(0);
            for (int tick = 1; tick <= TICKS; tick++) {
                // SceneRun owns the production tick, including its rendering seam.
                // Phase timing below excludes rendering and this observer's work.
                run.step();
                reading.sample(tick);
                reading.profile();
            }
            return reading;
        } finally {
            world.sim().close();
        }
    }

    private SceneReport report(Reading r, Reading control) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("intact", r.alive == SQUADS * MEMBERS,
                "alive=" + r.alive + ", expected=" + SQUADS * MEMBERS));
        verdicts.add(Verdict.of("legal-ground", r.illegalSamples == 0,
                "member samples on blocked terrain=" + r.illegalSamples));
        verdicts.add(Verdict.of("crossed", r.crossed == SQUADS,
                "squads beyond " + (r.crossing ? "crossing" : "neck") + "="
                        + r.crossed + "/" + SQUADS));
        verdicts.add(Verdict.of("arrived", r.arrived == SQUADS,
                "squad centroids within six cells=" + r.arrived + "/" + SQUADS));
        verdicts.add(Verdict.of("bounded-wait", r.longestWait <= 300,
                "longest nonprogress interval before arrival=" + r.longestWait
                        + " ticks; a 30-tick checkpoint must advance 0.2 cells"));
        if (r.crossing) {
            verdicts.add(Verdict.of("crossing-encounter", r.crossEncounterTicks > 0,
                    "ticks with perpendicular-group centroids within eight cells="
                            + r.crossEncounterTicks + "; minimum separation="
                            + r.minimumCrossGroupDistance));
        }
        if (control != null) {
            if (!r.crossing) {
                double before = control.preOpen.mean();
                double after = r.preOpen.mean();
                verdicts.add(Verdict.of("open-ground-spread", r.preOpen.count > 0
                                && after >= before + 0.5,
                        "mean simultaneous pre-neck lateral centroid span=" + after
                                + " cells, control=" + before + "; wanted +0.5"));
            }
            if (r.passage) {
                verdicts.add(Verdict.of("spread-again", r.postOpen.count > 0
                                && r.postOpen.mean() >= control.postOpen.mean() + 0.5,
                        "post-neck lateral span=" + r.postOpen.mean() + ", control="
                                + control.postOpen.mean() + "; wanted +0.5"));
            }
            verdicts.add(Verdict.of("throughput-retained", r.arrived == SQUADS && r.lastArrival > 0
                            && r.lastArrival <= control.lastArrival * 1.25 + 60,
                    "last centroid arrival=" + r.lastArrival + ", control="
                            + control.lastArrival + "; wanted <=25% + 60 ticks slower"));
        }
        return new SceneReport(id(), r.loop, TICKS, verdicts, r.metrics());
    }

    private static String key(int s) { return "squad-" + s; }

    private static final class Mean {
        double sum;
        double max;
        int count;
        void add(double value) { sum += value; max = Math.max(max, value); count++; }
        double mean() { return count == 0 ? 0 : sum / count; }
    }

    private static final class Reading {
        final SceneWorld world;
        final boolean passage;
        final boolean crossing;
        final String loop;
        final long[][] members = new long[SQUADS][];
        final float[] x = new float[SQUADS];
        final float[] y = new float[SQUADS];
        final float[] checkpointDistance = new float[SQUADS];
        final int[] wait = new int[SQUADS];
        final int[] crossingTick = new int[SQUADS];
        final int[] arrivalTick = new int[SQUADS];
        final Mean preOpen = new Mean();
        final Mean preOpenNearestLateral = new Mean();
        final Mean preOpenLongitudinal = new Mean();
        final Mean postOpen = new Mean();
        final Mean postOpenNearestLateral = new Mean();
        final Mean postOpenLongitudinal = new Mean();
        final Mean[] preLaneY = means();
        final Mean[] postLaneY = means();
        final Mean neck = new Mean();
        final Mean beforeNeck = new Mean();
        final long[] ticksNanos = new long[TICKS];
        int alive;
        int crossed;
        int arrived;
        int longestWait;
        int lastArrival = -1;
        int crossEncounterTicks;
        float minimumCrossGroupDistance = WIDTH * 2f;
        long illegalSamples;
        long closePairSamples;
        long pairSamples;
        long pathCalls;
        long pathNanos;
        long expanded;
        long fallbackCalls;
        long fallbackNanos;
        long trafficPrepareNanos;
        long trafficMoveNanos;
        long trafficReturnNanos;
        long trafficReturns;
        long trafficMoves;
        long trafficSquads;
        long trafficCandidates;
        long unitNanos;
        long totalNanos;
        int profiledTicks;
        float meanX;
        float span;

        Reading(SceneWorld world, boolean passage, boolean crossing, boolean enabled) {
            this.world = world;
            this.passage = passage;
            this.crossing = crossing;
            loop = (crossing ? "crossing" : passage ? "passage" : "open")
                    + (enabled ? "-subject" : "-control");
            Arrays.fill(crossingTick, -1);
            Arrays.fill(arrivalTick, -1);
            for (int s = 0; s < SQUADS; s++) members[s] = world.members(key(s));
        }

        void sample(int tick) {
            BattleSimulation sim = world.sim();
            alive = 0;
            meanX = 0;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            for (int s = 0; s < SQUADS; s++) {
                float sx = 0, sy = 0;
                int count = 0;
                for (long member : members[s]) {
                    if (!sim.world().isAlive(member)) continue;
                    float ux = sim.world().x(member), uy = sim.world().y(member);
                    sx += ux; sy += uy; count++; alive++;
                    if (!sim.getGrid().isWalkable((int) Math.floor(ux), (int) Math.floor(uy))) {
                        illegalSamples++;
                    }
                }
                if (count == 0) continue;
                x[s] = sx / count; y[s] = sy / count;
                meanX += x[s] / SQUADS;
                minY = Math.min(minY, y[s]); maxY = Math.max(maxY, y[s]);
                minX = Math.min(minX, x[s]); maxX = Math.max(maxX, x[s]);
                boolean northbound = crossing && s >= 2;
                if (crossingTick[s] < 0 && (northbound ? y[s] < 70 : x[s] > NECK_END + 4)) {
                    crossingTick[s] = tick; crossed++;
                }
                float dx = x[s] - (northbound ? 80 : GOAL_X);
                float dy = y[s] - (crossing ? northbound ? 11 : 80 : GOAL_Y);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (arrivalTick[s] < 0 && dx * dx + dy * dy <= 36f) {
                    arrivalTick[s] = tick; arrived++; lastArrival = tick;
                }
                if (tick == 0) checkpointDistance[s] = distance;
                if (tick > 0 && tick % 30 == 0) {
                    if (arrivalTick[s] < 0 && checkpointDistance[s] - distance < 0.2f) wait[s] += 30;
                    else wait[s] = 0;
                    longestWait = Math.max(longestWait, wait[s]);
                    checkpointDistance[s] = distance;
                }
            }
            span = maxY - minY;
            if (crossing) {
                float nearest = WIDTH * 2f;
                for (int a = 0; a < 2; a++) for (int b = 2; b < SQUADS; b++) {
                    float dx = x[a] - x[b], dy = y[a] - y[b];
                    nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
                }
                minimumCrossGroupDistance = Math.min(minimumCrossGroupDistance, nearest);
                if (nearest < 8f) crossEncounterTicks++;
            }
            // All cohort members must occupy the stage: a stretched-out column
            // straddling the doorway is not mistaken for open-ground spreading.
            if (!crossing && minX >= 35 && maxX <= 60) {
                preOpen.add(span);
                preOpenNearestLateral.add(nearestLateral());
                preOpenLongitudinal.add(maxX - minX);
                for (int s = 0; s < SQUADS; s++) preLaneY[s].add(y[s]);
            }
            if (!crossing && minX >= 59 && maxX < NECK_START) beforeNeck.add(span);
            if (!crossing && minX >= NECK_START && maxX <= NECK_END) neck.add(span);
            if (!crossing && minX >= 100 && maxX <= 130) {
                postOpen.add(span);
                postOpenNearestLateral.add(nearestLateral());
                postOpenLongitudinal.add(maxX - minX);
                for (int s = 0; s < SQUADS; s++) postLaneY[s].add(y[s]);
            }
            if (tick % 15 == 0 && arrived < SQUADS) {
                for (int a = 0; a < SQUADS; a++) {
                    for (int b = a + 1; b < SQUADS; b++) {
                        for (long u : members[a]) for (long v : members[b]) {
                            if (!sim.world().isAlive(u) || !sim.world().isAlive(v)) continue;
                            float dx = sim.world().x(u) - sim.world().x(v);
                            float dy = sim.world().y(u) - sim.world().y(v);
                            pairSamples++;
                            // Explicit proximity proxy, not a collision-radius claim.
                            if (dx * dx + dy * dy < 1f) closePairSamples++;
                        }
                    }
                }
            }
        }

        private static Mean[] means() {
            Mean[] result = new Mean[SQUADS];
            for (int s = 0; s < SQUADS; s++) result[s] = new Mean();
            return result;
        }

        private float nearestLateral() {
            float sum = 0;
            for (int a = 0; a < SQUADS; a++) {
                float nearest = Float.POSITIVE_INFINITY;
                for (int b = 0; b < SQUADS; b++) {
                    if (a != b) nearest = Math.min(nearest, Math.abs(y[a] - y[b]));
                }
                sum += nearest;
            }
            return sum / SQUADS;
        }

        void profile() {
            TickInnerProfile inner = world.sim().getTickInnerProfile();
            pathCalls += inner.countOf(TickInnerProfile.Bucket.PATHFIND);
            pathNanos += inner.nanosOf(TickInnerProfile.Bucket.PATHFIND);
            expanded += inner.pathfindExpandedNodes();
            fallbackCalls += inner.countOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK);
            fallbackNanos += inner.nanosOf(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK);
            trafficPrepareNanos += inner.nanosOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_PREPARE);
            trafficMoveNanos += inner.nanosOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_MOVE);
            trafficMoves += inner.countOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_MOVE);
            trafficReturnNanos += inner.nanosOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_RETURN);
            trafficReturns += inner.countOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_RETURN);
            trafficSquads += inner.countOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_SQUAD);
            trafficCandidates += inner.countOf(TickInnerProfile.Bucket.SQUAD_TRAFFIC_CANDIDATE);
            TickProfile profile = world.sim().getTickProfile();
            unitNanos += profile.lastTickNanos(TickProfile.Phase.UPDATE_UNITS);
            long total = 0;
            for (TickProfile.Phase phase : TickProfile.Phase.values()) total += profile.lastTickNanos(phase);
            totalNanos += total;
            ticksNanos[profiledTicks++] = total;
        }

        Map<String, Number> metrics() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("aliveAtEnd", alive);
            m.put("meanFinalX", meanX);
            m.put("preOpenLateralSpanCells", preOpen.mean());
            m.put("preOpenMaxLateralSpanCells", preOpen.max);
            m.put("preOpenMeanNearestLateralCells", preOpenNearestLateral.mean());
            m.put("preOpenLongitudinalSpanCells", preOpenLongitudinal.mean());
            m.put("preOpenSamples", preOpen.count);
            m.put("approachLateralSpanCells", beforeNeck.mean());
            m.put("approachMaxLateralSpanCells", beforeNeck.max);
            m.put("approachSamples", beforeNeck.count);
            m.put("neckLateralSpanCells", neck.mean());
            m.put("neckMaxLateralSpanCells", neck.max);
            m.put("neckSamples", neck.count);
            m.put("postOpenLateralSpanCells", postOpen.mean());
            m.put("postOpenMaxLateralSpanCells", postOpen.max);
            m.put("postOpenMeanNearestLateralCells", postOpenNearestLateral.mean());
            m.put("postOpenLongitudinalSpanCells", postOpenLongitudinal.mean());
            m.put("postOpenSamples", postOpen.count);
            m.put("crossedSquads", crossed);
            m.put("arrivedSquads", arrived);
            m.put("lastArrivalTick", arrived == SQUADS ? lastArrival : -1);
            m.put("lastObservedArrivalTick", lastArrival);
            m.put("longestNonprogressTicks", longestWait);
            if (crossing) {
                m.put("crossingEncounterTicks", crossEncounterTicks);
                m.put("minimumCrossGroupDistanceCells", minimumCrossGroupDistance);
            }
            m.put("blockedTerrainMemberSamples", illegalSamples);
            m.put("interSquadPairsBelowOneCell", closePairSamples);
            m.put("interSquadPairSamples", pairSamples);
            m.put("pathfindCalls", pathCalls);
            m.put("pathfindSummedWorkerMs", pathNanos / 1e6);
            m.put("profiledFlatExpandedNodes", expanded);
            m.put("squadFallbackCalls", fallbackCalls);
            m.put("squadFallbackSummedWorkerMs", fallbackNanos / 1e6);
            m.put("trafficPrepareHostMs", trafficPrepareNanos / 1e6);
            m.put("trafficMoveSummedWorkerMs", trafficMoveNanos / 1e6);
            m.put("trafficMoveCalls", trafficMoves);
            m.put("trafficReturnSummedWorkerMs", trafficReturnNanos / 1e6);
            m.put("trafficReturnCalls", trafficReturns);
            m.put("trafficSquadObservations", trafficSquads);
            m.put("trafficCandidateVisits", trafficCandidates);
            m.put("updateUnitsTotalMs", unitNanos / 1e6);
            m.put("tickTotalMs", totalNanos / 1e6);
            long[] sorted = ticksNanos.clone();
            Arrays.sort(sorted);
            m.put("tickP99Ms", sorted[(int) (sorted.length * 0.99)] / 1e6);
            m.put("tickMaxMs", sorted[sorted.length - 1] / 1e6);
            for (int s = 0; s < SQUADS; s++) {
                m.put("squad" + s + "CrossingTick", crossingTick[s]);
                m.put("squad" + s + "ArrivalTick", arrivalTick[s]);
                m.put("squad" + s + "FinalX", x[s]);
                m.put("squad" + s + "FinalY", y[s]);
                if (!crossing) {
                    m.put("squad" + s + "PreOpenMeanY", preLaneY[s].mean());
                    m.put("squad" + s + "PostOpenMeanY", postLaneY[s].mean());
                }
            }
            return m;
        }
    }
}
