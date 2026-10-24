package com.dillon.starsectormarines.battle.fire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A light-walled house of six rooms, three by two, with a doorway in every inner wall, on a lawn. */
class FlashoverTest {

    private static final int W = 40, H = 26, X0 = 4, Y0 = 4, RW = 7, RH = 6, COLS = 3, ROWS = 2;

    private static boolean wall(int x, int y) {
        int lx = x - X0, ly = y - Y0;
        if (lx < 0 || ly < 0 || lx > COLS * RW || ly > ROWS * RH) return false;
        boolean vertical = lx % RW == 0, horizontal = ly % RH == 0;
        if (!vertical && !horizontal) return false;
        boolean outer = lx == 0 || ly == 0 || lx == COLS * RW || ly == ROWS * RH;
        if (!outer && vertical && !horizontal && ly % RH == RH / 2) return false;
        return outer || !horizontal || vertical || lx % RW != RW / 2;
    }

    private static boolean inside(int x, int y) {
        int lx = x - X0, ly = y - Y0;
        return lx > 0 && ly > 0 && lx < COLS * RW && ly < ROWS * RH;
    }

    private static FireField house() {
        return new FireField(W, H, 1L, new FireTerrain() {
            @Override public int fuelMask(int idx) {
                int x = idx % W, y = idx / W;
                if (wall(x, y)) return FireFuel.STRUCTURE.bit;
                return inside(x, y) ? FireFuel.FURNISHING.bit : FireFuel.GRASS.bit;
            }
            @Override public boolean blocksHeat(int idx) { return wall(idx % W, idx / W); }
        });
    }

    /** Cells caught in a room's interior (doorways left out), and the interior's size. */
    private static int[] caught(FireField f, int col, int row) {
        int n = 0, size = 0;
        for (int y = Y0 + row * RH + 1; y < Y0 + (row + 1) * RH; y++) {
            for (int x = X0 + col * RW + 1; x < X0 + (col + 1) * RW; x++) {
                size++;
                if (f.stateAt(y * W + x) != FireField.UNBURNT) n++;
            }
        }
        return new int[]{n, size};
    }

    private static void light(FireField f, int col, int row) {
        f.addHeat(X0 + col * RW + RW / 2f + 0.5f, Y0 + row * RH + RH / 2f + 0.5f, 1.3f, 2200);
    }

    private static void burn(FireField f) {
        for (int t = 0; t < 30 * 240; t++) f.tick(t);
    }

    @Test
    void aChargeInARoomBurnsAllOfItAndLeavesTheHouseStanding() {
        FireField f = house();
        light(f, 0, 0);
        burn(f);
        int[] own = caught(f, 0, 0);
        assertEquals(own[1], own[0], "the room the charge landed in flashed over, every cell of it");
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (r == 0 && c == 0) continue;
            int[] other = caught(f, c, r);
            assertTrue(other[0] * 2 < other[1], "room " + c + "," + r + " did not flash over: " + other[0] + " of " + other[1]);
        }
    }

    @Test
    void aRoomIsHeldAtItsDoorways() {
        // Fire set in two rooms at once still flashes only those two: a doorway is not a room's floor.
        FireField f = house();
        light(f, 0, 0);
        light(f, 2, 1);
        burn(f);
        int[] a = caught(f, 0, 0), b = caught(f, 2, 1), between = caught(f, 1, 0);
        assertEquals(a[1], a[0]);
        assertEquals(b[1], b[0]);
        assertTrue(between[0] * 2 < between[1], "a room between them is not swept up with them: " + between[0]);
    }
}
