package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictLegRigTest {

    private static final DerelictLegRig.Solid OPEN = (x, y) -> false;
    /** A corridor two cells wide, floor at y 50 and 51, walls either side. */
    private static final DerelictLegRig.Solid CORRIDOR = (x, y) -> y < 50 || y > 51;
    private static final float DT = 1f / 30f;

    @Test
    void fourLegsStepInDiagonalPairsSoTwoFeetAlwaysStand() {
        DerelictLegRig rig = new DerelictLegRig(4, false);
        boolean stepped = false;
        // Walking along +x, facing +x (sprite angle -90).
        for (int tick = 0; tick < 300; tick++) {
            rig.advance(10f + tick * 0.03f, 10f, -90f, DT, OPEN);
            int lifted = 0;
            for (int i = 0; i < 4; i++) {
                if (!rig.stepping(i)) continue;
                lifted++;
                stepped = true;
                for (int j = 0; j < 4; j++) {
                    if (rig.stepping(j)) assertEquals(rig.group(i), rig.group(j), "only one diagonal pair lifts");
                }
            }
            assertTrue(lifted <= 2);
        }
        assertTrue(stepped, "a walking torso steps");
        assertEquals(rig.group(0), rig.group(2), "front-left steps with back-right");
    }

    @Test
    void threeLegsStepOneAtATime() {
        DerelictLegRig rig = new DerelictLegRig(3, false);
        for (int tick = 0; tick < 300; tick++) {
            rig.advance(10f + tick * 0.03f, 10f, -90f, DT, OPEN);
            int lifted = 0;
            for (int i = 0; i < 3; i++) if (rig.stepping(i)) lifted++;
            assertTrue(lifted <= 1);
        }
    }

    @Test
    void aPlantedFootHoldsWhileTheTorsoMovesALittle() {
        DerelictLegRig rig = new DerelictLegRig(4, false);
        rig.advance(10f, 10f, 0f, DT, OPEN);
        float x = rig.footX(0);
        float y = rig.footY(0);
        rig.advance(10.2f, 10f, 0f, DT, OPEN);
        assertEquals(x, rig.footX(0));
        assertEquals(y, rig.footY(0));
    }

    @Test
    void reachingLegsBraceOnACorridorsWallsAndFloorLegsNeverEnterThem() {
        DerelictLegRig reaching = new DerelictLegRig(4, true);
        reaching.advance(20f, 51f, -90f, DT, CORRIDOR);
        int onWall = 0;
        for (int i = 0; i < 4; i++) {
            float y = reaching.footY(i);
            assertTrue(y > 50f && y < 52f, "a foot on a face stands on the floor side of it: " + y);
            if (reaching.onWall(i)) onWall++;
        }
        assertEquals(4, onWall, "in a two-cell corridor every leg finds a wall");

        DerelictLegRig floor = new DerelictLegRig(4, false);
        floor.advance(20f, 51f, -90f, DT, CORRIDOR);
        for (int i = 0; i < 4; i++) {
            assertFalse(floor.onWall(i));
            assertFalse(CORRIDOR.at((int) Math.floor(floor.footX(i)), (int) Math.floor(floor.footY(i))));
        }
    }

    @Test
    void aTorsoThatJumpsFurtherThanALegReachesStandsFresh() {
        DerelictLegRig rig = new DerelictLegRig(4, false);
        rig.advance(10f, 10f, 0f, DT, OPEN);
        rig.advance(40f, 10f, 0f, 0f, OPEN);
        for (int i = 0; i < 4; i++) {
            float dx = rig.footX(i) - 40f;
            float dy = rig.footY(i) - 10f;
            assertEquals(DerelictLegRig.REST_CELLS, (float) Math.sqrt(dx * dx + dy * dy), 1e-3f);
            assertFalse(rig.stepping(i));
        }
    }

    @Test
    void inTheOpenAReachingLegRestsOnTheFloor() {
        DerelictLegRig rig = new DerelictLegRig(3, true);
        rig.advance(10f, 10f, 0f, DT, OPEN);
        for (int i = 0; i < 3; i++) {
            assertFalse(rig.onWall(i));
            float dx = rig.footX(i) - 10f;
            float dy = rig.footY(i) - 10f;
            assertEquals(DerelictLegRig.REST_CELLS, (float) Math.sqrt(dx * dx + dy * dy), 1e-3f);
        }
    }
}
