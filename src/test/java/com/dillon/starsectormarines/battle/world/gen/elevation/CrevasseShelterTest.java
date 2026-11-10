package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrevasseShelterTest {

    static final int W = 100, H = 70;

    @Test
    void wallsStandAlongBothEdgesAndLeaveTheFloorWhole() {
        boolean[] walls = CrevasseShelter.plan(W, H, true, i -> true, i -> true, i -> true, 3);
        assertNotNull(walls);
        int reach = (int) Math.ceil(CrevasseShelter.DEPTH + CrevasseShelter.WANDER);
        for (int x = 0; x < W; x++) {
            assertTrue(walls[x] || walls[W + x] || walls[2 * W + x] || x < 3 || x > W - 4, "north wall at " + x);
            for (int y = reach; y < H - reach; y++) assertFalse(walls[y * W + x], "the floor stays open");
        }
        int[] floor = OpenPit.label(W, H, i -> !walls[i]);
        int pieces = 0;
        for (int v : floor) pieces = Math.max(pieces, v);
        assertEquals(1, pieces);
    }

    @Test
    void aWallIsNotchedWhereTheTownReachesTheEdgeAndAMapFilledToItsEdgesHasNone() {
        // A road running off the north edge: the wall leaves it a pass.
        boolean[] walls = CrevasseShelter.plan(W, H, true, i -> Math.abs(i % W - 50) > 3, i -> true, i -> true, 3);
        assertNotNull(walls);
        for (int y = 0; y < H; y++) assertFalse(walls[y * W + 50]);
        // Nothing open along the edges: no crevasse.
        assertNull(CrevasseShelter.plan(W, H, true, i -> false, i -> true, i -> true, 3));
    }
}
