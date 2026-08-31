package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The forward-only solve — a Dubins path — for callers that cannot reverse.
 *
 * <p>Reverse is a legitimate manoeuvre for something on wheels and is not one
 * for something in the air. Filtering before the minimum is taken rather than
 * rejecting the shortest path afterwards is what makes this usable as a
 * planner: the forward optimum exists for any two poses, so a caller that asks
 * gets an answer.
 */
class ReedsSheppForwardTest {

    private static boolean hasReverse(ReedsShepp.Path path) {
        for (ReedsShepp.Element e : path.elements) {
            if (!e.forward && e.length > 1e-5f) return true;
        }
        return false;
    }

    /** Nothing it hands back goes backwards, from anywhere to anywhere. */
    @Test
    void theForwardSolveNeverGoesIntoReverse() {
        for (int b = 0; b < 16; b++) {
            double angle = b * Math.PI / 8.0;
            for (int h = 0; h < 8; h++) {
                Pose start = new Pose(0f, 0f, 0f);
                Pose goal = new Pose(12f * (float) Math.cos(angle),
                        12f * (float) Math.sin(angle), h * 45f);
                ReedsShepp.Path path = ReedsShepp.shortestForward(start, goal, 5f);
                assertNotNull(path, "no forward path to " + goal);
                assertTrue(!hasReverse(path), "forward solve returned a cusp for " + goal);
            }
        }
    }

    /**
     * A U-turn onto a reversed heading has a forward answer.
     *
     * <p>The case the landing approach is made of: an aircraft coming home
     * heading one way has to arrive on a threshold heading the other. Both
     * arc-straight-arc families can be infeasible there, and the family that
     * covers it — a left turn most of the way round, a straight, and a left
     * turn to finish — was being rejected outright because its first arc was
     * being measured with a raw {@code atan2} and came back negative. A cusp
     * was always shorter, so nothing noticed while reverse was allowed.
     */
    @Test
    void aUTurnOntoAReversedHeadingHasAForwardAnswer() {
        Pose start = new Pose(0f, 0f, 0f);
        Pose goal = new Pose(0f, -30f, 180f);
        ReedsShepp.Path path = ReedsShepp.shortestForward(start, goal, 12f);
        assertNotNull(path, "no forward path for a U-turn");
        assertTrue(!hasReverse(path));
        Pose arrived = ReedsShepp.sample(start, 12f, path, path.lengthCells(12f));
        assertTrue(Math.hypot(arrived.x - goal.x, arrived.y - goal.y) < 0.05,
                "the forward path does not end at the goal: " + arrived);
        float heading = ((arrived.facingDeg - goal.facingDeg) % 360f + 540f) % 360f - 180f;
        assertTrue(Math.abs(heading) < 1f,
                "the forward path ends pointing " + heading + " degrees off");
    }
}
