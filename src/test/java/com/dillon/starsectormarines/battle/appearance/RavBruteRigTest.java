package com.dillon.starsectormarines.battle.appearance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavBruteRigTest {

    private static final float DT = 1f / 30f;

    @Test
    void aDeadBruteBurstsAndSettlesOnTheGround() {
        RavBruteRig rig = new RavBruteRig(11L);
        for (int t = 0; t < 20; t++) rig.advance(0f, t * 0.06f, 0f, 1f, RavBruteRig.IDLE, false, DT);
        for (int t = 0; t < 100; t++) rig.advanceDead(0f, 1.2f, DT);
        RavBurst burst = rig.burst();
        assertTrue(rig.dead() && burst.settled());
        assertTrue(burst.pieceResting(RavBruteRig.PIECE_HEAD), "the head comes away and lands");
        float[] at = new float[3];
        for (int piece = 0; piece < burst.pieceCount(); piece++) {
            burst.deadPoint(piece, RavBruteRig.SHOULDER, 0f, 0f, at);
            assertTrue(at[2] < 1e-3f, "piece " + piece + " lies on the ground");
        }
        assertFalse(burst.poolCells() <= 0f, "a pool spreads");
    }
}
