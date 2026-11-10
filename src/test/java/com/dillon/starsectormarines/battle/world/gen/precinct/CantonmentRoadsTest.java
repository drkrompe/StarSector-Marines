package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
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
        assertEquals(1, owner[40][20], "it ends on the town's road, joining that network");
        for (int y = 3; y < 10; y++) assertEquals(GrownTrunkPlan.UNOWNED, owner[20][y], "not south as well");
    }

    /**
     * A base has more than one way in and out, and each joins the map's roads: the avenue to the nearest road, and a
     * narrower road from the patrol lane out of each further side that faces a road, on until it meets that road. A
     * side whose road would only leave the map gets no gate.
     */
    @Test
    void aBaseOpensAGateOnEverySideThatFacesARoadAndEachMeetsTheTown() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 10; x < 30; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        for (int y = 0; y < H; y++) owner[40][y] = 1;                  // the town's road east
        for (int x = 0; x < W; x++) owner[x][2] = 1;                   // south
        for (int x = 0; x < W; x++) owner[x][36] = 1;                  // and north; nothing west
        CantonmentRoads.lay(claim, owner, 0, W, H, 4, 2);
        // East (nearest, facing the middle) is the avenue; north and south are further gates; west has none.
        assertEquals(0, owner[35][20 + CantonmentRoads.HALF], "the avenue runs east at its full width");
        for (int y = 3; y < 10; y++) assertEquals(0, owner[20][y], "a road runs south to the town's at " + y);
        for (int y = 30; y < 36; y++) assertEquals(0, owner[20][y], "a road runs north to the town's at " + y);
        assertEquals(GrownTrunkPlan.UNOWNED, owner[20 + CantonmentRoads.SIDE_HALF + 1][5], "narrower than the avenue");
        for (int x = 0; x < 10; x++) assertEquals(GrownTrunkPlan.UNOWNED, owner[x][20], "no gate onto nothing west");
        // A further gate's road comes in only across the glacis, the wall and the patrol lane.
        int in = 2 + CantonmentRoads.LANE;
        assertEquals(0, owner[20][10 + in], "the south road reaches the lane");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[20][10 + in + 1], "and stops there, short of the rows");
    }

    @Test
    void theCapKeepsTheNearestWaysOut() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 10; x < 30; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        for (int y = 0; y < H; y++) owner[40][y] = 1;                  // east, the avenue
        for (int x = 0; x < W; x++) owner[x][2] = 1;                   // south, eight cells off
        for (int x = 0; x < W; x++) owner[x][38] = 1;                  // north, nine cells off
        CantonmentRoads.lay(claim, owner, 0, W, H, 2, 2);
        assertEquals(0, owner[20][5], "the nearer further gate, south, is kept");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[20][33], "the farther, north, is not");
    }

    @Test
    void aBigMarketOwesMoreGates() {
        assertEquals(2, CantonmentRoads.gates(FortressProgram.cantonment(4, 1, 1)));
        assertEquals(3, CantonmentRoads.gates(FortressProgram.cantonment(5, 1, 1)));
        assertEquals(4, CantonmentRoads.gates(FortressProgram.cantonment(7, 1, 1)));
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
