package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandedScaleTest {

    @Test
    void aLandedHullIsMorrowsCellsPerPixel() {
        // Morrow's 92-pixel Shepherd is 24 cells on the ground; a Venture (190 px) and a Hound (72 px) follow.
        assertEquals(24f, 92f * LandedScale.CELLS_PER_HULL_PX, 1e-4f);
        assertEquals(49.6f, 190f * LandedScale.CELLS_PER_HULL_PX, 0.05f);
        assertEquals(18.8f, 72f * LandedScale.CELLS_PER_HULL_PX, 0.05f);
        assertEquals(LandedScale.CELLS_PER_HULL_PX, AirScale.cellsForHeightPx(1f) * LandedScale.berthPresence(), 1e-5f);
    }

    @Test
    void theBerthFactorIsARatioToFlightPresence() {
        assertEquals(LandedScale.berthPresence() / 4f, LandedScale.berthFactor(4f), 1e-5f);
        assertEquals(1f, LandedScale.berthFactor(LandedScale.berthPresence()), 1e-5f);
        assertThrows(IllegalArgumentException.class, () -> LandedScale.berthFactor(0f));
    }

    @Test
    void theBodyEasesWithTheShareOfTheRunFlownWithoutAJump() {
        for (float berth : new float[]{LandedScale.berthFactor(4f), LandedScale.berthFactor(6f)}) {
            assertEquals(berth, LandedScale.bodyFactor(berth, -1f), 0f);
            assertEquals(berth, LandedScale.bodyFactor(berth, 0f), 1e-6f);
            assertEquals(1f, LandedScale.bodyFactor(berth, 1f), 1e-6f);
            assertEquals(1f, LandedScale.bodyFactor(berth, 3f), 1e-6f);
            float previous = berth, largestStep = 0f;
            for (int i = 1; i <= 300; i++) {
                float now = LandedScale.bodyFactor(berth, i / 300f);
                // Monotone toward flight scale, whichever side of it the berth sits.
                assertTrue((now - previous) * (1f - berth) >= -1e-6f, "the body only moves toward flight scale");
                largestStep = Math.max(largestStep, Math.abs(now - previous) / previous);
                previous = now;
            }
            // A three-hundredth of the run is a frame or so of flight: under one percent of the body.
            assertTrue(largestStep < 0.01f, "largest per-step change " + largestStep);
        }
    }

    @Test
    void identityWhenFlightIsAlreadyAtBerthScale() {
        for (float p = -1f; p < 2f; p += 0.25f) assertEquals(1f, LandedScale.bodyFactor(1f, p), 1e-6f);
    }

    @Test
    void theRunGoesAheadFirstThenComesRoundTowardItsTarget() {
        // Nose west, turning toward a point to the south: the first leg is straight ahead, the last leg points south.
        float[][] run = LandedScale.liftRun(100f, 50f, 180f, 60f, -200f, 50f);
        assertEquals(100f - 50f * LandedScale.AHEAD_LENGTHS, run[0][0], 1e-3f);
        assertEquals(50f, run[0][1], 1e-3f);
        float[] a = run[run.length - 2], b = run[run.length - 1];
        double out = Math.toDegrees(Math.atan2(b[1] - a[1], b[0] - a[0]));
        double want = Math.toDegrees(Math.atan2(-200f - run[0][1], 60f - run[0][0]));
        assertEquals(0d, Math.IEEEremainder(out - want, 360d), 0.5d, "the exit leg flies the bearing it turned to");
        // A curve, not a pivot: no leg turns more than one arc step from the leg before it.
        for (int i = 1; i < run.length - 1; i++) {
            float[] p = i == 1 ? new float[]{100f, 50f} : run[i - 2];
            double before = Math.atan2(run[i - 1][1] - p[1], run[i - 1][0] - p[0]);
            double after = Math.atan2(run[i][1] - run[i - 1][1], run[i][0] - run[i - 1][0]);
            assertTrue(Math.abs(Math.toDegrees(Math.IEEEremainder(after - before, 2 * Math.PI)))
                    <= LandedScale.ARC_STEP_DEGREES + 1e-3, "leg " + i);
        }
    }

    @Test
    void aSmallHullStillTurnsOnAVisibleCircle() {
        float[][] run = LandedScale.liftRun(0f, 0f, 0f, 0.8f, 100f, 2f);
        // A quarter turn to port about a circle of at least the minimum radius ends that far north of the leg.
        float[] end = run[run.length - 2];
        assertEquals(LandedScale.MIN_TURN_CELLS, end[1], 1e-3f);
    }

    @Test
    void progressRunsFromZeroAtTheBerthToOneAtTheEnd() {
        float[][] run = LandedScale.liftRun(0f, 0f, 90f, 50f, 0f, 20f);
        float length = LandedScale.runLength(0f, 0f, run);
        assertEquals(0f, LandedScale.runProgress(0f, 0f, run, 0, length), 1e-5f);
        float[] last = run[run.length - 1];
        assertEquals(1f, LandedScale.runProgress(last[0], last[1], run, run.length - 1, length), 1e-5f);
        assertEquals(1f, LandedScale.runProgress(last[0], last[1], run, run.length, length), 0f);
        float half = LandedScale.runProgress(run[1][0], run[1][1], run, 2, length);
        assertTrue(half > 0f && half < 1f, "part way round is part of the lift: " + half);
    }

    @Test
    void aFallingHullComesBackToTrueScaleByTheStrike() {
        for (float ground : new float[]{LandedScale.berthFactor(2f), LandedScale.berthFactor(6f)}) {
            assertEquals(1f, LandedScale.fallFactor(1f, ground, 0f), 1e-6f);
            assertEquals(ground, LandedScale.fallFactor(1f, ground, 1f), 1e-6f);
            assertEquals(ground, LandedScale.fallFactor(1f, ground, 2f), 1e-6f);
            float previous = 1f;
            for (int i = 1; i <= 90; i++) {
                float now = LandedScale.fallFactor(1f, ground, i / 90f);
                assertTrue((now - previous) * (ground - 1f) >= -1e-6f, "the wreck only moves toward true scale");
                previous = now;
            }
        }
        // A hull shot down part way through its lift falls from the size it was drawn at.
        assertEquals(1.3f, LandedScale.fallFactor(1.3f, 2f, 0f), 1e-6f);
    }
}
