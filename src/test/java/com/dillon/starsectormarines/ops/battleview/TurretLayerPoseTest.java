package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurretLayerPoseTest {

    private static final float EPS = 1e-5f;
    private static final float DURATION = 0.12f;
    private static final float DISTANCE_FRACTION = 0.10f;

    @Test
    void restPoseKeepsBothLayersCenteredAndPreservesSizeAndFacing() {
        TurretLayerPose pose = TurretLayerPose.resolve(
                120f, 80f, 37f, 2.2f, 16f,
                DURATION, DURATION, DISTANCE_FRACTION);

        assertEquals(120f, pose.baseCenterX(), EPS);
        assertEquals(80f, pose.baseCenterY(), EPS);
        assertEquals(120f, pose.recoilCenterX(), EPS);
        assertEquals(80f, pose.recoilCenterY(), EPS);
        assertEquals(35.2f, pose.spriteHeightPx(), EPS);
        assertEquals(37f, pose.facingDegrees(), EPS);
    }

    @Test
    void peakRecoilMatchesTheSharedScreenSpaceFacingConvention() {
        TurretLayerPose north = TurretLayerPose.resolve(
                120f, 80f, 0f, 2f, 20f,
                0f, DURATION, DISTANCE_FRACTION);
        TurretLayerPose east = TurretLayerPose.resolve(
                120f, 80f, -90f, 2f, 20f,
                0f, DURATION, DISTANCE_FRACTION);

        assertEquals(120f, north.recoilCenterX(), EPS);
        assertEquals(76f, north.recoilCenterY(), EPS);
        assertEquals(116f, east.recoilCenterX(), EPS);
        assertEquals(80f, east.recoilCenterY(), EPS);
        assertEquals(north.spriteHeightPx(), east.spriteHeightPx(), EPS);
    }

    @Test
    void expiredRecoilStaysAtRestAfterTheWindow() {
        TurretLayerPose pose = TurretLayerPose.resolve(
                33f, 44f, 215f, 1.8f, 24f,
                1f, DURATION, DISTANCE_FRACTION);

        assertEquals(pose.baseCenterX(), pose.recoilCenterX(), EPS);
        assertEquals(pose.baseCenterY(), pose.recoilCenterY(), EPS);
        assertEquals(43.2f, pose.spriteHeightPx(), EPS);
    }

    @Test
    void callerSuppliedShuttleScaleChangesSizeAndRecoilTogether() {
        TurretLayerPose ground = TurretLayerPose.resolve(
                0f, 0f, 90f, 2f, 20f,
                0f, DURATION, DISTANCE_FRACTION);
        TurretLayerPose shuttle = TurretLayerPose.resolve(
                0f, 0f, 90f, 2f * 0.75f, 20f,
                0f, DURATION, DISTANCE_FRACTION);

        assertEquals(40f, ground.spriteHeightPx(), EPS);
        assertEquals(30f, shuttle.spriteHeightPx(), EPS);
        assertEquals(4f, ground.recoilCenterX(), EPS);
        assertEquals(3f, shuttle.recoilCenterX(), EPS);
    }
}
