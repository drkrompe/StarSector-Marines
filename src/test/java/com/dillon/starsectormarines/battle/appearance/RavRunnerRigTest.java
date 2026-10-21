package com.dillon.starsectormarines.battle.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RavRunnerRigTest {

    private static final float DT = 1f / 30f;

    /** World position of rig point p: the body plus x to the right and y forward. */
    private static float[] world(RavRunnerRig rig, int p) {
        double rad = Math.toRadians(rig.facingDegrees());
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        return new float[]{rig.bodyX() + rig.x(p) * fy + rig.y(p) * fx,
                rig.bodyY() - rig.x(p) * fx + rig.y(p) * fy};
    }

    private static int foot(int leg) {
        return RavRunnerRig.LEG_START[leg] + RavRunnerRig.LEG_POINTS[leg] - 1;
    }

    @Test
    void aGallopingFootOnTheGroundDoesNotSlide() {
        RavRunnerRig rig = new RavRunnerRig(7L);
        float x = 10f, y = 10f;
        // heading east-north-east, so the check covers a turned frame, not just the axes
        float facing = -70f;
        float vx = (float) -Math.sin(Math.toRadians(facing)) * 3.2f;
        float vy = (float) Math.cos(Math.toRadians(facing)) * 3.2f;
        float[][] last = new float[4][];
        int planted = 0;
        for (int tick = 0; tick < 90; tick++) {
            rig.advance(x, y, facing, 0f, false, DT);
            x += vx * DT;
            y += vy * DT;
            for (int leg = 0; leg < 4; leg++) {
                boolean down = rig.z(foot(leg)) < 1e-4f;
                float[] w = world(rig, foot(leg));
                if (tick > 20 && down && last[leg] != null) {
                    assertEquals(last[leg][0], w[0], 2e-3f, "foot " + leg + " slid at tick " + tick);
                    assertEquals(last[leg][1], w[1], 2e-3f, "foot " + leg + " slid at tick " + tick);
                    planted++;
                }
                last[leg] = down ? w : null;
            }
        }
        assertTrue(planted > 20, "the gallop put feet on the ground");
    }

    @Test
    void aStrikePlantsTheFeetAndDrivesOneArmOnTheDamageTick() {
        RavRunnerRig rig = new RavRunnerRig(3L);
        float cooldown = 0.7f;
        float[][] planted = null;
        int strikes = 0, lastArm = -1;
        for (int tick = 0; tick < 200; tick++) {
            boolean lands = cooldown <= 0f;
            if (lands) cooldown = 0.7f;
            rig.advance(5f, 5f, 90f, cooldown, true, DT);
            cooldown = Math.max(0f, cooldown - DT);
            if (tick == 10) {
                planted = new float[4][];
                for (int leg = 0; leg < 4; leg++) planted[leg] = world(rig, foot(leg));
            } else if (planted != null) {
                for (int leg = 0; leg < 4; leg++) {
                    float[] w = world(rig, foot(leg));
                    assertEquals(planted[leg][0], w[0], 1e-4f, "planted foot " + leg + " moved");
                    assertEquals(planted[leg][1], w[1], 1e-4f, "planted foot " + leg + " moved");
                }
            }
            if (lands) {
                strikes++;
                int arm = rig.strikeArm();
                assertNotEquals(lastArm, arm, "strikes alternate arms");
                lastArm = arm;
                int other = RavRunnerRig.ARM_START[1 - arm] + 3;
                int tip = RavRunnerRig.ARM_START[arm] + 3;
                // the striking spike is out in front of the resting one when the blow lands
                assertTrue(rig.y(tip) > rig.y(other) + 0.05f || Math.abs(rig.x(tip)) > Math.abs(rig.x(other)) + 0.05f,
                        "strike " + strikes + " is not out when it lands");
            }
        }
        assertTrue(strikes >= 6);
    }

    @Test
    void aCarrierFacesItsLoadAndLatchesOn() {
        RavRunnerRig rig = new RavRunnerRig(5L);
        float x = 10f, y = 10f;
        // the carrier walks east toward the bore; the body trails half a cell behind it, to the west
        for (int tick = 0; tick < 60; tick++) {
            rig.advance(x, y, -90f, 1f, false, tick > 5, x - 0.55f, y, DT);
            x += 1.28f * DT;
        }
        double rad = Math.toRadians(rig.facingDegrees());
        assertTrue(-Math.sin(rad) < -0.95, "the carrier faces the body it drags");
        float[] head = world(rig, RavRunnerRig.HEAD);
        assertTrue(head[0] < rig.bodyX() - 0.2f, "the jaws are on the load side");
    }

    @Test
    void aDeadRunnerFallsAndLiesLow() {
        RavRunnerRig rig = new RavRunnerRig(9L);
        for (int tick = 0; tick < 10; tick++) rig.advance(3f, 3f, 0f, 0f, false, DT);
        for (int tick = 0; tick < 30; tick++) rig.advanceDead(3f, 3f, DT);
        assertTrue(rig.dead());
        for (int p = 0; p < RavRunnerRig.POINTS; p++) {
            assertTrue(rig.z(p) < 0.25f, "point " + p + " still stands at " + rig.z(p));
        }
    }

    @Test
    void ikKeepsBoneLengths() {
        float[] j = new float[4];
        RavRunnerRig.ik2(0f, 0.4f, 0.1f, 0f, 0.22f, 0.22f, -1f, j);
        assertEquals(0.22f, Math.hypot(j[0], j[1] - 0.4f), 1e-4);
        assertEquals(0.22f, Math.hypot(j[2] - j[0], j[3] - j[1]), 1e-4);
        assertTrue(j[0] < 0.05f, "a front elbow points back");
    }
}
