package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepClimbTest {

    /** A wall's north side: its outer side along y = 10 from x 4 to 5, looking out north (-y). */
    private static final float AX = 4f, AY = 10f, BX = 5f, BY = 10f, NX = 0f, NY = -1f;

    @Test
    void aFaceReadsInsideItsWallFromTheOuterSideInAsItRises() {
        float[] out = new float[8];
        CreepClimb.faceCells(AX, AY, BX, BY, NX, NY, 0f, 1f, 0f, 1f, out);
        // The foot reads just inside the wall, never the ground beside it.
        assertTrue(out[1] > AY && out[3] > AY, "foot inside the wall: " + out[1]);
        assertEquals(AY + CreepClimb.SKIN, out[1], 1e-6f);
        // The top reads further in, by the face's depth.
        assertEquals(AY + CreepClimb.SKIN + CreepClimb.FACE_DEPTH, out[5], 1e-6f);
        assertEquals(out[5], out[7], 0f);
        assertTrue(out[5] < AY + 1f, "the whole face reads its own wall cell");
        // Along the face as it runs.
        assertEquals(AX, out[0], 0f);
        assertEquals(BX, out[2], 0f);
        assertEquals(BX, out[4], 0f);
        assertEquals(AX, out[6], 0f);
    }

    @Test
    void aCutAwayStubReadsOnlyAsFarInAsItStands() {
        float[] whole = new float[8], stub = new float[8];
        CreepClimb.faceCells(AX, AY, BX, BY, NX, NY, 0f, 1f, 0f, 1f, whole);
        CreepClimb.faceCells(AX, AY, BX, BY, NX, NY, 0f, 1f, 0f, .25f, stub);
        assertEquals(whole[1], stub[1], 0f, "the same foot");
        assertTrue(stub[5] < whole[5], "a stub's top reads short of a whole wall's");
    }

    @Test
    void aTopReadsTheCellsItCoversInFromItsOuterSide() {
        float[] out = new float[8];
        CreepClimb.topCells(AX, AY, BX, BY, NX, NY, 1f, out);
        assertEquals(AX, out[0], 0f);
        assertEquals(AY, out[1], 0f);
        assertEquals(BX, out[2], 0f);
        assertEquals(BY + 1f, out[5], 0f, "a cell in from the outer side");
        assertEquals(AY + 1f, out[7], 0f);
    }
}
