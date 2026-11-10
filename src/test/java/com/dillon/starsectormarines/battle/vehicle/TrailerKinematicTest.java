package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerKinematicTest {

    /** A cab and one module: hinge 0.4 ahead of the centre, the module's axle 1.2 behind that. */
    private static final Articulation TWO = new Articulation(new float[]{.4f}, new float[]{1f, -.8f});
    /** A cab, a power module and a mission module. */
    private static final Articulation THREE =
            new Articulation(new float[]{1.2f, 0f}, new float[]{1.8f, .6f, -1.2f});

    @Test
    void aStraightDriveKeepsTheTrainStraightAndDrawsItAsTheSheetDoes() {
        TrailerKinematic train = new TrailerKinematic(THREE);
        float facing = 30f;
        for (int i = 0; i <= 60; i++) {
            float d = i * .05f;
            train.update(-(float) Math.sin(Math.toRadians(facing)) * d, (float) Math.cos(Math.toRadians(facing)) * d, facing);
        }
        assertEquals(0f, train.bendDeg(1), 1e-3f);
        assertEquals(0f, train.bendDeg(2), 1e-3f);
        float d = 60 * .05f;
        float bx = -(float) Math.sin(Math.toRadians(facing)) * d, by = (float) Math.cos(Math.toRadians(facing)) * d;
        float[] drawn = train.toWorld(bx, by, .3f, -1.7f, new float[2]);
        float rad = (float) Math.toRadians(facing);
        assertEquals(bx + .3f * (float) Math.cos(rad) + 1.7f * (float) Math.sin(rad), drawn[0], 1e-3f);
        assertEquals(by + .3f * (float) Math.sin(rad) - 1.7f * (float) Math.cos(rad), drawn[1], 1e-3f);
    }

    @Test
    void aModuleOnASteadyTurnSettlesAtTheTowedTrailersAngle() {
        float radius = 3f;
        TrailerKinematic train = circle(TWO, radius, 1f / 240f, 900);
        // The hinge runs a circle of radius Rh; a trailer of length L behind it settles asin(L / Rh) inside its path.
        float h = TWO.hingeY()[0];
        float rh = (float) Math.hypot(radius, h);
        float expected = (float) Math.toDegrees(Math.asin(TWO.trailerLength(1) / rh));
        float hingeTangent = train.headingDeg(0) + (float) Math.toDegrees(Math.atan2(h, radius));
        float inside = hingeTangent - train.headingDeg(1);
        assertEquals(expected, inside, .5f);
        assertTrue(train.bendDeg(1) < 0f, "turning left, the module swings out to the right of the cab's line");
    }

    @Test
    void theBendDependsOnThePathNotTheFrameRate() {
        TrailerKinematic fine = circle(THREE, 4f, 1f / 240f, 296);
        TrailerKinematic coarse = circle(THREE, 4f, 1f / 30f, 37);
        assertEquals(fine.bendDeg(1), coarse.bendDeg(1), 1f);
        assertEquals(fine.bendDeg(2), coarse.bendDeg(2), 1f);
    }

    @Test
    void backingUpEasesTheModuleBackIntoLineAndNoBendPassesTheStop() {
        TrailerKinematic train = circle(TWO, 1.2f, 1f / 60f, 400);
        assertTrue(Math.abs(train.bendDeg(1)) <= Articulation.MAX_BEND_DEG + 1e-3f, "a tight turn bottoms out the hinge");
        float bent = Math.abs(train.bendDeg(1));
        assertTrue(bent > 10f);
        // Back straight down the cab's line for a few drag lengths.
        float facing = train.headingDeg(0);
        float x = lastX, y = lastY;
        for (int i = 0; i < 120; i++) {
            x += (float) Math.sin(Math.toRadians(facing)) * .05f;
            y -= (float) Math.cos(Math.toRadians(facing)) * .05f;
            train.update(x, y, facing);
            assertTrue(Math.abs(train.bendDeg(1)) <= bent + 1e-3f, "reversing never folds the train further");
        }
        assertTrue(Math.abs(train.bendDeg(1)) < 1f);
    }

    @Test
    void aJumpLaysTheTrainOutStraight() {
        TrailerKinematic train = circle(TWO, 2f, 1f / 60f, 200);
        assertTrue(Math.abs(train.bendDeg(1)) > 5f);
        train.update(lastX + 10f, lastY, 90f);
        assertEquals(0f, train.bendDeg(1), 1e-3f);
        assertEquals(90f, train.headingDeg(1), 1e-3f);
    }

    private float lastX, lastY;

    /** Drives the body round a left-hand circle of {@code radius} at 2 cells a second, {@code steps} steps of {@code dt}. */
    private TrailerKinematic circle(Articulation layout, float radius, float dt, int steps) {
        TrailerKinematic train = new TrailerKinematic(layout);
        float omega = 2f / radius;
        for (int i = 0; i <= steps; i++) {
            float a = omega * i * dt;
            // Centre at (-radius, 0); facing 0 (+Y) at the start, turning left (counter-clockwise).
            lastX = -radius + radius * (float) Math.cos(a);
            lastY = radius * (float) Math.sin(a);
            train.update(lastX, lastY, (float) Math.toDegrees(a));
        }
        return train;
    }
}
