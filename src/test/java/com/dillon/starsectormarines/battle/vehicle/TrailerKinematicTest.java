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
    void backingUpPushesABentModuleFurtherRoundUntilItsRamsHoldIt() {
        TrailerKinematic train = circle(TWO, 4f, 1f / 60f, 200);
        float bent = Math.abs(train.bendDeg(1));
        assertTrue(bent > 5f && bent < Articulation.MAX_BEND_DEG - 5f, "a gentle turn leaves the hinge off its stop");
        float facing = train.headingDeg(0);
        float x = lastX, y = lastY;
        for (int i = 0; i < 120; i++) {
            x += (float) Math.sin(Math.toRadians(facing)) * .05f;
            y -= (float) Math.cos(Math.toRadians(facing)) * .05f;
            train.update(x, y, facing);
            float now = Math.abs(train.bendDeg(1));
            assertTrue(now >= bent - 1e-3f, "pushed back, the module folds further round, never back into line");
            assertTrue(now <= Articulation.MAX_BEND_DEG + 1e-3f, "no bend passes the stop");
            bent = now;
        }
        assertEquals(Articulation.MAX_BEND_DEG, bent, 1e-3f, "backed far enough, the rams bottom out");
    }

    @Test
    void backingUpAStraightTrainKeepsItStraight() {
        TrailerKinematic train = new TrailerKinematic(THREE);
        for (int i = 0; i <= 80; i++) train.update(0f, -i * .05f, 0f);
        assertEquals(0f, train.bendDeg(1), 1e-3f);
        assertEquals(0f, train.bendDeg(2), 1e-3f);
    }

    @Test
    void aModuleAxleRollsAlongItsHeadingAndNeverSlidesSidewaysEitherWay() {
        TrailerKinematic train = circle(TWO, 3f, 1f / 120f, 150);
        float[] was = train.toWorld(lastX, lastY, 1, 0f, TWO.axleY()[1], new float[2]);
        float facing = train.headingDeg(0);
        float x = lastX, y = lastY;
        // Back up a little while still turning, then drive forward again on the opposite lock.
        for (int i = 0; i < 40; i++) {
            float dir = i < 20 ? -1f : 1f;
            facing += dir * (i < 20 ? .4f : -.4f);
            x += -(float) Math.sin(Math.toRadians(facing)) * .02f * dir;
            y += (float) Math.cos(Math.toRadians(facing)) * .02f * dir;
            float before = train.headingDeg(1);
            train.update(x, y, facing);
            assertTrue(Math.abs(train.bendDeg(1)) < Articulation.MAX_BEND_DEG, "off the stop, where the rule is pure");
            // The axle rolls along the heading it stood on, then the module turns to its new place.
            float[] now = train.toWorld(x, y, 1, 0f, TWO.axleY()[1], new float[2]);
            float mid = (float) Math.toRadians(before);
            float sideways = (now[0] - was[0]) * (float) Math.cos(mid) + (now[1] - was[1]) * (float) Math.sin(mid);
            assertEquals(0f, sideways, 2e-4f, "step " + i);
            was = now;
        }
    }

    @Test
    void theHingeSectionStretchesOnTheOutsideOfABendAndPressesOnTheInside() {
        Articulation joints = new Articulation(TWO.hingeY(), TWO.axleY(), .26f);
        TrailerKinematic train = new TrailerKinematic(joints);
        train.update(0f, 0f, 0f);
        float[] edges = new float[4];
        for (float side : new float[]{-.5f, 0f, .5f}) {
            train.hingeEdges(0f, 0f, 0, side, edges);
            assertEquals(.26f, (float) Math.hypot(edges[0] - edges[2], edges[1] - edges[3]), 1e-4f, "straight, as painted");
        }
        TrailerKinematic bent = circle(joints, 2f, 1f / 60f, 200);
        assertTrue(bent.bendDeg(1) < -10f, "turning left, the module swings out right");
        // The outside of a left-hand bend is the hull's right, +x.
        bent.hingeEdges(lastX, lastY, 0, .5f, edges);
        float outside = (float) Math.hypot(edges[0] - edges[2], edges[1] - edges[3]);
        bent.hingeEdges(lastX, lastY, 0, -.5f, edges);
        float inside = (float) Math.hypot(edges[0] - edges[2], edges[1] - edges[3]);
        assertTrue(outside > .26f + .05f, "outside stretched: " + outside);
        assertTrue(inside < .26f - .05f, "inside pressed: " + inside);
        // Each edge stays on its plate, so no seam opens between plate and section.
        float[] plate = bent.toWorld(lastX, lastY, 1, .5f, joints.gapRearY(0), new float[2]);
        bent.hingeEdges(lastX, lastY, 0, .5f, edges);
        assertEquals(plate[0], edges[2], 1e-5f);
        assertEquals(plate[1], edges[3], 1e-5f);
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
