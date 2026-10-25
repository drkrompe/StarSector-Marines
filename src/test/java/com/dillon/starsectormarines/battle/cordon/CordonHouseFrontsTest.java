package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which windows a flank's house team holds ({@link CordonHouseFronts#posts}). */
class CordonHouseFrontsTest {

    // A redoubt from x 80 to 100, front row 200. A house front east of it at x 104-107 on row 201, and a farther one
    // at x 114-117.
    private static boolean window(int x, int y) {
        return y == 201 && (x >= 104 && x <= 107 || x >= 114 && x <= 117);
    }

    @Test
    void theNearestHouseFrontPastTheFlankHoldsTheTeam() {
        List<int[]> east = CordonHouseFronts.posts(CordonHouseFrontsTest::window, 80, 100, 200, 1, 4);
        assertEquals(4, east.size());
        for (int[] c : east) assertTrue(c[0] >= 104 && c[0] <= 107, "the near house, not the far one");
        assertArrayEquals(new int[]{104, 201}, east.get(0));
    }

    @Test
    void aFlankWithTooFewWindowsInOneHouseHasNoTeam() {
        assertNull(CordonHouseFronts.posts(CordonHouseFrontsTest::window, 80, 100, 200, -1, 4), "nothing to the west");
        CordonHouseFronts.CellTest scattered = (x, y) -> y == 201 && (x == 104 || x == 112);
        assertNull(CordonHouseFronts.posts(scattered, 80, 100, 200, 1, 2), "two windows a street apart are not a house");
    }
}
