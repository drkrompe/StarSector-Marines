package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictLegPoseTest {

    private static float length(float x0, float y0, float z0, float x1, float y1, float z1) {
        return (float) Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
    }

    @Test
    void theThighKeepsItsLengthAndTheKneeBendsUp() {
        float[] k = new float[3];
        float hz = DerelictLegPose.HIP_HEIGHT_CELLS;
        for (float flat : new float[] {0.6f, 1.5f, 2.4f}) {
            DerelictLegPose.knee(0f, 0f, flat, 0f, 0f, k);
            assertEquals(DerelictLegPose.THIGH_CELLS, length(0f, 0f, hz, k[0], k[1], k[2]), 1e-3f);
        }
        DerelictLegPose.knee(0f, 0f, 1.5f, 0f, 0f, k);
        assertTrue(k[2] > hz, "the knee stands above the hip");
        assertEquals(0f, k[1], 1e-5f, "the knee stays in the plane through hip and foot");
    }

    @Test
    void aFootFurtherOutLowersTheKnee() {
        float[] near = new float[3];
        float[] far = new float[3];
        DerelictLegPose.knee(0f, 0f, 1.2f, 0f, 0f, near);
        DerelictLegPose.knee(0f, 0f, 2.4f, 0f, 0f, far);
        assertTrue(far[2] < near[2]);
    }

    /** How far out along hip to foot the knee is seen, and how far off that line, from above. */
    private static float[] seen(float hx, float hy, float fx, float fy, float[] k) {
        float dx = fx - hx;
        float dy = fy - hy;
        float flat2 = dx * dx + dy * dy;
        float share = ((k[0] - hx) * dx + (k[1] - hy) * dy) / flat2;
        float off = Math.abs((k[0] - hx) * dy - (k[1] - hy) * dx) / (float) Math.sqrt(flat2);
        return new float[] {share, off};
    }

    @Test
    void fromAboveTheKneeAlwaysLiesBetweenHipAndFoot() {
        float[] k = new float[3];
        // Every direction (both sides and both diagonals), feet tucked in to stretched out, on the floor, lifted
        // and up a wall face as high as the hip.
        for (int deg = 0; deg < 360; deg += 15) {
            float cx = (float) Math.cos(Math.toRadians(deg));
            float cy = (float) Math.sin(Math.toRadians(deg));
            for (float flat = 0.3f; flat <= 2.8f; flat += 0.1f) {
                for (float footHeight : new float[] {0f, DerelictLegPose.STEP_LIFT_CELLS,
                        DerelictLegPose.WALL_FOOT_HEIGHT_CELLS}) {
                    DerelictLegPose.knee(5f, 5f, 5f + cx * flat, 5f + cy * flat, footHeight, k);
                    float[] s = seen(5f, 5f, 5f + cx * flat, 5f + cy * flat, k);
                    String at = deg + " deg, " + flat + " out, " + footHeight + " up";
                    assertTrue(s[0] >= DerelictLegPose.KNEE_MIN_SHARE - 1e-4f
                            && s[0] <= DerelictLegPose.KNEE_MAX_SHARE + 1e-4f, "knee share " + s[0] + " at " + at);
                    assertEquals(0f, s[1], 1e-4f, "the knee stays on the hip-foot line at " + at);
                    float line = DerelictLegPose.HIP_HEIGHT_CELLS
                            + (footHeight - DerelictLegPose.HIP_HEIGHT_CELLS) * s[0];
                    assertTrue(k[2] >= line - 1e-4f, "the knee bends up off the hip-foot line at " + at);
                }
            }
        }
    }

    @Test
    void aFootComingInRaisesTheKneeOnTheFloorAndOnAWall() {
        float[] k = new float[3];
        for (float footHeight : new float[] {0f, DerelictLegPose.WALL_FOOT_HEIGHT_CELLS}) {
            float last = -1f;
            for (float flat = 2.6f; flat >= 0.3f; flat -= 0.1f) {
                DerelictLegPose.knee(0f, 0f, flat, 0f, footHeight, k);
                assertTrue(k[2] >= last - 1e-4f, "knee height " + k[2] + " at " + flat + " out, " + footHeight
                        + " up");
                last = k[2];
            }
        }
    }

    @Test
    void everyLegOfAWalkingRigKeepsItsKneeBetweenHipAndFoot() {
        DerelictLegRig rig = new DerelictLegRig();
        DerelictLegRig.Solid north = (x, y) -> y >= 52.4f;
        float[] k = new float[3];
        for (int tick = 0; tick < 300; tick++) {
            float speed = tick < 150 ? 0.7f : 2.6f;
            rig.advance(10f + tick * speed / 30f, 50.5f, -90f, 1f / 30f, north);
            for (int i = 0; i < rig.legs(); i++) {
                float footHeight = (rig.onWall(i) ? DerelictLegPose.WALL_FOOT_HEIGHT_CELLS : 0f)
                        + DerelictLegPose.STEP_LIFT_CELLS * rig.lift(i);
                DerelictLegPose.knee(rig.hipX(i), rig.hipY(i), DerelictLegPose.HIP_HEIGHT_CELLS + rig.bodyHeight(),
                        rig.footX(i), rig.footY(i), footHeight, k);
                float dx = rig.footX(i) - rig.hipX(i);
                float dy = rig.footY(i) - rig.hipY(i);
                if (dx * dx + dy * dy < 1e-4f) continue;
                float[] s = seen(rig.hipX(i), rig.hipY(i), rig.footX(i), rig.footY(i), k);
                assertTrue(s[0] >= DerelictLegPose.KNEE_MIN_SHARE - 1e-4f
                        && s[0] <= DerelictLegPose.KNEE_MAX_SHARE + 1e-4f, "leg " + i + " tick " + tick + ": " + s[0]);
            }
        }
    }

    @Test
    void aFootOutOfReachLeavesTheLegStraightTowardIt() {
        float[] k = new float[3];
        DerelictLegPose.knee(0f, 0f, 0f, 10f, 0f, k);
        assertEquals(0f, k[0], 1e-5f);
        assertTrue(k[1] > 0f && k[1] < 10f);
    }
}
