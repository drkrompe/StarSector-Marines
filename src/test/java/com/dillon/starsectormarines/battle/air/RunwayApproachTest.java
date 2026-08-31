package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.vehicle.Pose;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solved arrival, asked of the solver alone.
 *
 * <p>No simulation and no aircraft: what this pins is that the geometry handed
 * to the follower is a thing the aircraft can actually fly and that ends
 * pointing down the runway. Whether the follower then flies it is the state
 * machine's question and {@code RunwayProcedureTest}'s.
 */
class RunwayApproachTest {

    /** East-west, thirty cells, on a map with room around it. */
    private static final Runway STRIP = new Runway(10.5f, 6.5f, 40.5f, 6.5f, 4f);

    /** The west end, so a roll from it runs east. */
    private static final float[] WEST = {10.5f, 6.5f};

    private static float wrap180(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    /**
     * The path starts where the aircraft is, so the follower has nothing to
     * close before it starts tracking.
     */
    @Test
    void theSolvedPathStartsUnderTheAircraft() {
        RunwayApproach approach = RunwayApproach.plan(
                new Pose(30f, 30f, 0f), STRIP, WEST, 6f, 60, 40);
        assertTrue(approach.solved, "no curvature-feasible path onto an open strip");
        assertEquals(30f, approach.xs[0], 1e-3f);
        assertEquals(30f, approach.ys[0], 1e-3f);
    }

    /**
     * It ends on the threshold, and carries on past it down the strip.
     *
     * <p>Both halves matter. Where the arrival <em>is</em> is the threshold,
     * which is what the state machine tests; the run-out past it exists so the
     * follower's carrot always has somewhere to slide. A carrot pinned to the
     * last point of a path is a body chasing a fixed point, and a body with a
     * turn radius chasing a point it is offset from swings its nose further off
     * the closer it gets.
     */
    @Test
    void itEndsOnTheThresholdAndRunsOnDownTheStrip() {
        RunwayApproach approach = RunwayApproach.plan(
                new Pose(30f, 30f, 0f), STRIP, WEST, 6f, 60, 40);
        int last = approach.xs.length - 1;
        assertEquals(WEST[0], approach.xs[approach.thresholdIdx], 1e-3f);
        assertEquals(WEST[1], approach.ys[approach.thresholdIdx], 1e-3f);
        assertTrue(approach.thresholdIdx < last, "nothing for the carrot to slide onto");
        assertEquals(40.5f, approach.xs[last], 1e-3f, "the run-out is not down the strip");
        assertTrue(approach.runOutCells > 1f, "a run-out with no length in it");
    }

    /**
     * The leg into the threshold is the runway axis, whichever direction the
     * aircraft came home from.
     *
     * <p>This is the whole property. A craft that reaches the threshold by
     * flying this path is lined up on arrival because the last thing it flew
     * was the centreline, and nothing has to put it straight afterwards.
     */
    @Test
    void theLastLegIsTheRunwayAxisFromEveryDirection() {
        float axis = AirBody.facingToward(40.5f - 10.5f, 0f);
        for (int b = 0; b < 8; b++) {
            double angle = b * Math.PI / 4.0;
            float fromX = 30f + 20f * (float) Math.cos(angle);
            float fromY = 25f + 15f * (float) Math.sin(angle);
            for (int f = 0; f < 4; f++) {
                RunwayApproach approach = RunwayApproach.plan(
                        new Pose(fromX, fromY, f * 90f), STRIP, WEST, 6f, 60, 40);
                int fix = approach.thresholdIdx - 1;
                float legDeg = AirBody.facingToward(
                        approach.xs[approach.thresholdIdx] - approach.xs[fix],
                        approach.ys[approach.thresholdIdx] - approach.ys[fix]);
                assertEquals(0f, wrap180(legDeg - axis), 1e-2f,
                        "the final leg from " + fromX + "," + fromY + " is not the centreline");
            }
        }
    }

    /**
     * Nothing on the path turns tighter than the aircraft can.
     *
     * <p>The reason for solving it at all. A path is only worth following if
     * the machine that has to follow it could have flown it, and the way that
     * fails is silent: a follower simply falls outside a corner it cannot make
     * and arrives crabbed. Measured as heading change per cell against the
     * curvature bound, with slack for the sampling — the flown part only, since
     * the run-out is a straight line down a strip the craft is on its wheels
     * for.
     */
    @Test
    void nothingOnThePathTurnsTighterThanTheAircraftCan() {
        float radius = 6f;
        RunwayApproach approach = RunwayApproach.plan(
                new Pose(30f, 30f, 45f), STRIP, WEST, radius, 60, 40);
        assertTrue(approach.solved);
        float previous = Float.NaN;
        for (int i = 1; i <= approach.thresholdIdx; i++) {
            float dx = approach.xs[i] - approach.xs[i - 1];
            float dy = approach.ys[i] - approach.ys[i - 1];
            float step = (float) Math.hypot(dx, dy);
            if (step < 1e-3f) continue;
            float heading = AirBody.facingToward(dx, dy);
            if (!Float.isNaN(previous)) {
                float turnPerCell = Math.abs(wrap180(heading - previous)) / step;
                // The bound is the plan radius, which carries slack over the
                // hull's own tightest circle so the follower has something to
                // correct with; a segment tighter than the hull's own arc is
                // the failure worth catching.
                float tightest = (float) Math.toDegrees(1f / radius);
                assertTrue(turnPerCell <= tightest + 1f,
                        "segment " + i + " turns " + turnPerCell
                                + " deg/cell, tighter than the " + tightest + " the craft can fly");
            }
            previous = heading;
        }
    }

    /**
     * A craft that cannot turn at all still gets a landing.
     *
     * <p>The fallback is not a case anybody has hit — Dubins has an answer for
     * any two poses — but a landing that will not solve still has to land, so
     * the straight-in is there and is still a path from the aircraft, through
     * the fix, onto the threshold.
     */
    @Test
    void aCraftWithNoSolvableCircuitStillGetsATrackOntoTheThreshold() {
        RunwayApproach approach = RunwayApproach.plan(
                new Pose(30f, 30f, 0f), STRIP, WEST, Float.NaN, 60, 40);
        assertEquals(WEST[0], approach.xs[approach.thresholdIdx], 1e-3f);
        assertEquals(WEST[1], approach.ys[approach.thresholdIdx], 1e-3f);
        assertTrue(approach.lengthCells > 0f);
    }
}
