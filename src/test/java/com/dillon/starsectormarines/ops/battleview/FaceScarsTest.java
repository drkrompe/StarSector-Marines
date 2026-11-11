package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.ImpactMark;
import com.dillon.starsectormarines.battle.combat.fx.WallScars;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FaceScarsTest {

    private static final int W = 16;

    /** A round is kept on the wall cell it was entering, and a cell keeps only its latest hits. */
    @Test
    void aHitIsKeptOnTheWallItStruck() {
        WallScars scars = new WallScars();
        // Fired north at the wall cell (5, 6): it stops on the cell's south side, y = 6.
        scars.strike(W, 5.3f, 1f, 5.3f, 6f, 0f, WallScars.Mark.CHIP, .6f);
        assertEquals(1, scars.count(5 + 6 * W));
        assertEquals(0, scars.count(5 + 5 * W), "not the open cell it came from");
        for (int i = 0; i < 20; i++) scars.strike(W, 5.5f, 1f, 5.5f, 6f, 0f, WallScars.Mark.CHIP, .6f);
        assertEquals(WallScars.PER_CELL, scars.count(5 + 6 * W));
        assertEquals(WallScars.Mark.BLAST, WallScars.of(ImpactMark.SHELL));
        assertNull(WallScars.of(ImpactMark.ACID), "acid leaves no scar on a wall");
    }

    /**
     * A hit shows on the face it struck, where along it it struck, and on no other face of the cell: a wall shot from
     * the south is marked on its south face only.
     */
    @Test
    void aHitShowsOnTheFaceItStruckWhereItStruck() {
        WallScars scars = new WallScars();
        scars.strike(W, 5.3f, 1f, 5.3f, 6f, 0f, WallScars.Mark.GOUGE, .6f);
        int cell = 5 + 6 * W;
        float[] out = new float[WallScars.PER_CELL * FaceScars.STRIDE];
        // The south face runs west to east along y = 6, turned south.
        int south = FaceScars.lay(scars, cell, W, 5f, 6f, 6f, 6f, 0f, -1f, out);
        assertEquals(1, south);
        assertEquals(.3f, out[0], 1e-4f, "where along the face it struck");
        assertEquals(WallScars.Mark.GOUGE.ordinal(), (int) out[3]);
        assertEquals(0, FaceScars.lay(scars, cell, W, 6f, 7f, 5f, 7f, 0f, 1f, out), "the north face is untouched");
    }
}
