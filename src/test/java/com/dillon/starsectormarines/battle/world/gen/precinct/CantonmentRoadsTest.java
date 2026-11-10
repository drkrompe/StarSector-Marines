package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkSegment;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cantonment's square and its one road in: the square slides off ground another place holds, every road across its
 * claim is taken up, one straight road runs from the gate to the nearest road outside, and a weld goes round.
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
        assertTrue(blocked[12][14] && !blocked[27][20], "a weld goes round it, all but its road in");
        // Two sides as near: the one facing the map's middle, east.
        int gate = 29 - CantonmentRoads.LANE;                          // the patrol lane inside the east wall
        for (int x = gate; x < 40; x++) {
            for (int dy = -CantonmentRoads.HALF; dy <= CantonmentRoads.HALF; dy++) {
                assertEquals(0, owner[x][20 + dy], "the road runs straight east at " + x + "," + (20 + dy));
            }
        }
        assertEquals(GrownTrunkPlan.UNOWNED, owner[gate - 1][20], "and ends at the gate, short of the parade");
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

    /** Every gate's road meets the town's: the road grid is one network with nothing welded in. */
    @Test
    void everyGateJoinsTheNetwork() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 10; x < 30; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        for (int y = 0; y < H; y++) owner[40][y] = 1;
        for (int x = 0; x < W; x++) owner[x][2] = 1;
        for (int x = 0; x < W; x++) owner[x][36] = 1;
        CantonmentRoads.lay(claim, owner, 0, W, H, 4, 2);
        int[][] copy = new int[W][];
        for (int x = 0; x < W; x++) copy[x] = owner[x].clone();
        assertEquals(1, PrecinctInterconnect.weld(copy, W, H).componentsBefore(), "a gate's road is an island");
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
        assertArrayEquals(new int[]{20, 5}, PrecinctClaim.clearSquare(claim, null, 20, 10, 5, W, H));
        assertArrayEquals(new int[]{30, 5}, PrecinctClaim.clearSquare(claim, null, 20, 30, 5, W, H), "clear stays put");
    }

    @Test
    void aSquareSlidesOffGroundTheLandscapeHoldsAsIfAnotherPlaceHeldIt() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        boolean[][] held = new boolean[W][H];
        for (int x = 40; x < W; x++) for (int y = 0; y < H; y++) held[x][y] = true;   // a storm bank's windward band
        assertArrayEquals(new int[]{20, 5}, PrecinctClaim.clearSquare(claim, held, 20, 30, 5, W, H));
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
        for (int x = 19 - CantonmentRoads.LANE; x < W; x++) east &= owner[x][20] == 0;
        assertTrue(east, "with no road anywhere it runs toward the map's middle and on to the edge");
    }

    /**
     * Of two sides as near a road, the road in takes the one meeting a planned street square: east would tee into its
     * street six cells off another street's tee, so it goes north, where the street runs clear across its way.
     */
    @Test
    void theRoadInMeetsAPlannedStreetSquareRatherThanBesideAnotherTee() {
        List<TrunkSegment> streets = List.of(up(40, 0, 39), across(41, 59, 26), across(0, 59, 36));
        int[][] claim = square();
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int y = 0; y < H; y++) owner[40][y] = 1;
        for (int x = 41; x < W; x++) owner[x][26] = 1;
        for (int x = 0; x < W; x++) owner[x][36] = 1;
        int[][] plain = copy(owner);
        CantonmentRoads.lay(square(), plain, 0, W, H, 1, 2, List.of());
        assertEquals(0, plain[35][20], "with no plan it goes east, facing the map's middle");
        CantonmentRoads.lay(claim, owner, 0, W, H, 1, 2, streets);
        assertEquals(0, owner[20][35], "it goes north, to the street running square across its way");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[35][20], "and not east, beside the other tee");
    }

    /** A further gate goes where its road meets a planned street square before a nearer one that would not. */
    @Test
    void aFurtherGateMeetsAPlannedStreetSquareBeforeANearerOffsetTee() {
        List<TrunkSegment> streets = List.of(up(40, 0, 39), across(0, 59, 4), up(26, 5, 9), across(0, 59, 36));
        int[][] owner = grid(GrownTrunkPlan.UNOWNED);
        for (int y = 0; y < H; y++) owner[40][y] = 1;
        for (int x = 0; x < W; x++) owner[x][4] = 1;
        for (int x = 0; x < W; x++) owner[x][36] = 1;
        int[][] plain = copy(owner);
        CantonmentRoads.lay(square(), plain, 0, W, H, 2, 2, List.of());
        assertEquals(0, plain[20][6], "with no plan the nearer road, south, gets the gate");
        CantonmentRoads.lay(square(), owner, 0, W, H, 2, 2, streets);
        assertEquals(0, owner[20][34], "with the plan the north road meets its street square");
        assertEquals(GrownTrunkPlan.UNOWNED, owner[20][6], "and the south, an offset tee, gets none");
    }

    private static int[][] square() {
        int[][] claim = grid(GrownTrunkPlan.UNOWNED);
        for (int x = 10; x < 30; x++) for (int y = 10; y < 30; y++) claim[x][y] = 0;
        return claim;
    }

    private static int[][] copy(int[][] g) {
        int[][] c = new int[g.length][];
        for (int x = 0; x < g.length; x++) c[x] = g[x].clone();
        return c;
    }

    private static TrunkSegment across(int x0, int x1, int y) {
        return new TrunkSegment(x0, y, x1, y, TrunkKind.TERTIARY, true);
    }

    private static TrunkSegment up(int x, int y0, int y1) {
        return new TrunkSegment(x, y0, x, y1, TrunkKind.TERTIARY, false);
    }
}
