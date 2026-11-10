package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cantonment's square and its one road in: the square slides off ground another place holds, every road across its
 * claim is taken up, one straight road runs from the square's middle to the nearest road outside, and a weld goes round.
 */
class CantonmentRoadsTest {

    private static final int W = 60, H = 40;

    @Test
    void theRoadComesStraightInFromTheNearestRoadAndNothingElseOfItsOwnStays() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 10; x < 30; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        for (int x = 12; x < 28; x++) owner[x][14] = 0;                 // its own grown street across the square
        for (int y = 0; y < H; y++) owner[25][y] = 2;                  // and another place's road through it
        for (int y = 0; y < H; y++) owner[40][y] = 1;                  // the town's road ten cells east
        for (int x = 0; x < W; x++) owner[x][2] = 1;                   // and one eight cells south
        CantonmentRoads.lay(claim, owner, 0, W, H);
        assertEquals(GrownTrunkPlan.UNOWNED, owner[12][14], "its own street is taken up");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[25][27], "and the road across it");
        assertEquals(2, owner[25][5], "but not outside it");
        boolean[][] blocked = CantonmentRoads.blocked(null, W, H);
        CantonmentRoads.block(blocked, claim, owner, 0, W, H);
        assertTrue(blocked[12][14] && !blocked[25][20], "a weld goes round it, all but its road in");
        // Two sides as near: the one facing the map's middle, east.
        for (int x = 20; x < 40; x++) {
            for (int dy = -CantonmentRoads.HALF; dy <= CantonmentRoads.HALF; dy++) {
                assertEquals(0, owner[x][20 + dy], "the road runs straight east at " + x + "," + (20 + dy));
            }
        }
        assertEquals(GrownTrunkPlan.UNOWNED, owner[19][20], "and only from the middle out");
        for (int y = 3; y < 10; y++) assertEquals(GrownTrunkPlan.UNOWNED, owner[20][y], "not south as well");
    }

    @Test
    void aSquareSlidesTheLeastItTakesOffGroundAnotherPlaceHolds() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 0; x < 20; x++) for (int y = 0; y < 20; y++) claim[x][y] = 0;   // the works
        assertArrayEquals(new int[]{20, 5}, PrecinctClaim.clearSquare(claim, 20, 10, 5, W, H));
        assertArrayEquals(new int[]{30, 5}, PrecinctClaim.clearSquare(claim, 20, 30, 5, W, H), "clear stays put");
    }

    private static int[][] grid(int fill) {
        int[][] g = new int[W][H];
        for (int[] col : g) Arrays.fill(col, fill);
        return g;
    }

    @Test
    void theRoadNeverLeavesTheMapForwardsWhenNoRoadIsNear() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 0; x < 20; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        CantonmentRoads.lay(claim, owner, 0, W, H);
        boolean east = true;
        for (int x = 10; x < W; x++) east &= owner[x][20] == 0;
        assertTrue(east, "with no road anywhere it runs toward the map's middle and on to the edge");
    }
}
