package com.dillon.starsectormarines.battle.world.gen.elevation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrevasseTerracesTest {

    static final int W = 60, H = 40;

    /** Walls 10 deep along the low-y and high-y edges, a pass cut through the high one at x 28 to 33. */
    private static boolean[] walls() {
        boolean[] walls = new boolean[W * H];
        for (int x = 0; x < W; x++) for (int d = 0; d < 10; d++) {
            walls[d * W + x] = true;
            if (x < 28 || x > 33) walls[(H - 1 - d) * W + x] = true;
        }
        return walls;
    }

    @Test
    void housesStandAtTheWallsFeetFacingTheFloorAndNeverInAPass() {
        boolean[] walls = walls();
        List<CrevasseTerraces.Site> sites = CrevasseTerraces.plan(W, H, true, walls, new int[]{15, 9},
                i -> !walls[i], i -> !walls[i]);
        assertFalse(sites.isEmpty());
        boolean low = false, high = false;
        for (CrevasseTerraces.Site site : sites) {
            if (site.bottom() == 10) {
                low = true;
                assertEquals(2, site.turns(), "a low wall's houses face up the map");
            } else {
                high = true;
                assertEquals(H - 10 - CrevasseTerraces.DEPTH, site.bottom(), "back against the high wall");
                assertEquals(0, site.turns());
                assertTrue(site.left() + site.length() <= 28 || site.left() > 33, "never across the pass");
            }
        }
        assertTrue(low && high);
    }

    @Test
    void whereTheBankWandersTheHouseIsDugIntoIt() {
        // The low wall reaches two cells further at x 5 to 7.
        boolean[] walls = walls();
        for (int x = 5; x <= 7; x++) for (int y = 10; y < 12; y++) walls[y * W + x] = true;
        CrevasseTerraces.Site first = CrevasseTerraces.plan(W, H, true, walls, new int[]{9},
                i -> !walls[i], i -> !walls[i]).get(0);
        assertEquals(1, first.left());
        assertEquals(10, first.bottom(), "back at the bank's shallowest foot under it");
        assertTrue(first.dig()[10 * W + 5] && first.dig()[11 * W + 7], "the bank it is dug into");
        assertFalse(first.dig()[10 * W + 2], "never open ground");
    }
}
