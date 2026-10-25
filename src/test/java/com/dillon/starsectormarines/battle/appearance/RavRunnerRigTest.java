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
    void aSkittererPutsItsHeadDownOnlyWhileItStandsAtWork() {
        RavRunnerRig rig = RavRunnerRig.worker(9L);
        rig.setWork(RavRunnerRig.WORK_HEAD_DOWN);
        float x = 10f;
        for (int tick = 0; tick < 30; tick++) {
            rig.advance(x, 10f, -90f, 1f, false, DT);
            x += 2.5f * DT;
        }
        assertTrue(rig.headDip() < 0.05f, "a skitterer on the move carries its head");
        for (int tick = 0; tick < 30; tick++) rig.advance(x, 10f, -90f, 1f, false, DT);
        assertTrue(rig.headDip() > 0.5f, "standing at the work, its head goes down");
        rig.setWork(RavRunnerRig.WORK_PULP);
        assertTrue(rig.held() == RavRunnerRig.WORK_PULP);
        rig.setWork(RavRunnerRig.WORK_NONE);
        for (int tick = 0; tick < 30; tick++) rig.advance(x, 10f, -90f, 1f, false, DT);
        assertTrue(rig.headDip() < 0.05f && rig.held() == RavRunnerRig.WORK_NONE, "done, it lifts its head");
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
    void aDeadRunnerBurstsAndItsPiecesComeToRestOnTheGround() {
        RavRunnerRig rig = new RavRunnerRig(9L);
        for (int tick = 0; tick < 10; tick++) rig.advance(3f, 3f, 0f, 0f, false, DT);
        float[] p = new float[3];
        rig.advanceDead(3f, 3f, DT);
        rig.deadPoint(RavRunnerRig.PIECE_HEAD, RavRunnerRig.HEAD, 0f, 0f, p);
        float headX = p[0], headY = p[1], headZ = p[2];
        boolean airborne = false;
        for (int tick = 0; tick < 4; tick++) {
            rig.advanceDead(3f, 3f, DT);
            rig.deadPoint(RavRunnerRig.PIECE_HEAD, RavRunnerRig.HEAD, 0f, 0f, p);
            airborne |= p[2] > headZ + 0.01f;
        }
        assertTrue(airborne, "the burst throws the head up");
        for (int tick = 0; tick < 120; tick++) rig.advanceDead(3f, 3f, DT);
        assertTrue(rig.dead());
        rig.deadPoint(RavRunnerRig.PIECE_HEAD, RavRunnerRig.HEAD, 0f, 0f, p);
        assertTrue(Math.hypot(p[0] - headX, p[1] - headY) > 0.2f, "the head came away");
        float[] still = new float[3];
        for (int piece = 0; piece < RavRunnerRig.PIECES; piece++) {
            for (int point = 0; point < RavRunnerRig.POINTS; point++) {
                rig.deadPoint(piece, point, 0f, 0f, p);
                assertTrue(p[2] < 1e-3f, "piece " + piece + " point " + point + " still off the ground at " + p[2]);
                rig.advanceDead(3f, 3f, DT);
                rig.deadPoint(piece, point, 0f, 0f, still);
                assertEquals(p[0], still[0], 0f, "piece " + piece + " still moving");
            }
        }
        for (int i = 0; i < rig.dropCount(); i++) assertTrue(rig.dropLanded(i), "droplet " + i + " landed");
    }

    @Test
    void aTurningBodyBowsIntoTheTurn() {
        RavRunnerRig rig = new RavRunnerRig(4L);
        float x = 0f, y = 0f, facing = 0f;
        for (int tick = 0; tick < 20; tick++) {
            // running and turning left at 180 degrees a second
            rig.advance(x, y, facing, 1f, false, DT);
            double rad = Math.toRadians(facing);
            x += (float) -Math.sin(rad) * 3f * DT;
            y += (float) Math.cos(rad) * 3f * DT;
            facing += 180f * DT;
        }
        assertTrue(rig.bendDegrees() > 10f, "the spine bends with the turn: " + rig.bendDegrees());
        float mid = rig.x(RavRunnerRig.MID);
        assertTrue(rig.x(RavRunnerRig.SHOULDER) < mid && rig.x(RavRunnerRig.HIP) < mid,
                "shoulders and hips both bow toward the inside of a left turn");
    }

    @Test
    void theBodyWindsBackAndDrivesThroughEachBlow() {
        RavRunnerRig rig = new RavRunnerRig(3L);
        float cooldown = 0.7f;
        float wound = 0f;
        int blows = 0;
        for (int tick = 0; tick < 120; tick++) {
            boolean lands = cooldown <= 0f;
            if (lands) cooldown = 0.7f;
            rig.advance(5f, 5f, 0f, cooldown, true, DT);
            cooldown = Math.max(0f, cooldown - DT);
            // positive: the striking arm's shoulder is turned back
            float side = rig.strikeArm() == 0 ? 1f : -1f;
            float back = rig.twistDegrees() * side;
            if (lands) {
                assertTrue(wound > 5f, "blow " + blows + " wound the shoulder back first: " + wound);
                assertTrue(back < 0f, "blow " + blows + " drives the shoulder through as it lands: " + back);
                blows++;
                wound = 0f;
            } else if (cooldown < 0.25f) {
                wound = Math.max(wound, back);
            }
        }
        assertTrue(blows >= 3);
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
