package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkKind;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkSegment;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.gen.road.StreetGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A town's grown streets are planned as one network: no stubs, no crossings pulled apart, no crowded streets. */
class StreetPlanTest {

    private static final int W = 80, H = 50;

    /** Grown streets from segments, every cell owned by place 0, with the map's perimeter ring as growth lays it. */
    private static GrownTrunkPlan.Grown grown(TrunkSegment... segments) {
        boolean[][] road = new boolean[W][H];
        int[][] owner = new int[W][H];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 0; x < W; x++) { road[x][0] = true; road[x][H - 1] = true; }
        for (int y = 0; y < H; y++) { road[0][y] = true; road[W - 1][y] = true; }
        for (TrunkSegment s : segments) for (int x = s.left; x <= s.right; x++) for (int y = s.top; y <= s.bottom; y++) {
            road[x][y] = true;
            owner[x][y] = 0;
        }
        return new GrownTrunkPlan.Grown(new TrunkPlan.Plan(road, List.of(), new ArrayList<>(List.of(segments)), null,
                W, H), new ArrayList<>(), owner);
    }

    private static TrunkSegment across(int x0, int x1, int y, int breadth) {
        return new TrunkSegment(x0, y - breadth / 2, x1, y + breadth / 2, TrunkKind.TERTIARY, true);
    }

    private static TrunkSegment up(int x, int y0, int y1, int breadth) {
        return new TrunkSegment(x - breadth / 2, y0, x + breadth / 2, y1, TrunkKind.TERTIARY, false);
    }

    private static boolean[][] street(GrownTrunkPlan.Grown g) {
        boolean[][] out = new boolean[W][H];
        for (int x = 1; x < W - 1; x++) for (int y = 1; y < H - 1; y++) out[x][y] = g.plan().roadCells[x][y];
        return out;
    }

    @Test
    void aShortDeadEndIsTakenUpAndALongOneStays() {
        var g = grown(across(1, W - 2, 20, 3), up(20, 21, 27, 3), up(50, 21, 45, 3));
        var r = StreetPlan.plan(g, null);
        assertEquals(1, r.stubs());
        assertEquals(2, r.grown().plan().trunks.size(), "the long side street was taken up too");
        assertTrue(!r.grown().plan().roadCells[20][26], "the stub's cells are still road");
        assertEquals(GrownTrunkPlan.UNOWNED, r.grown().owner()[20][26]);
    }

    @Test
    void aCrossingPulledApartBecomesOneCross() {
        // Up from x 30 to the map's top, and down from x 36 into a dead end the plan may move.
        var g = grown(across(1, W - 2, 25, 3), up(30, 26, H - 2, 3), up(36, 10, 24, 3));
        assertEquals(1, StreetGraph.of(street(g)).offsetTees(12));
        var r = StreetPlan.plan(g, null);
        assertEquals(1, r.crosses());
        StreetGraph after = StreetGraph.of(street(r.grown()));
        assertEquals(0, after.offsetTees(12));
        assertEquals(1, after.junctions().stream().filter(j -> j.kind() == StreetGraph.Kind.CROSS).count());
    }

    @Test
    void aSideStreetIsNotMovedOntoBarredGround() {
        var g = grown(across(1, W - 2, 25, 3), up(30, 26, H - 2, 3), up(36, 10, 24, 3));
        boolean[][] barred = new boolean[W][H];
        for (int y = 0; y < H; y++) { barred[30][y] = true; barred[36][y] = true; }
        assertEquals(0, StreetPlan.plan(g, barred).crosses());
    }

    @Test
    void aStreetCrowdingALongerOneIsTakenUpOnlyWhereTheNetworkStaysWhole() {
        // Two streets running across three cells apart, both off the map's edges, joined by a cross street.
        var g = grown(across(1, W - 2, 20, 3), across(10, 40, 26, 3), up(10, 20, 26, 3));
        var r = StreetPlan.plan(g, null);
        assertTrue(r.crowded() + r.stubs() >= 1, "the crowded street stayed");
        assertTrue(!r.grown().plan().roadCells[30][26], "the crowded street is still road");
        StreetPlan.Net net = new StreetPlan.Net(r.grown().plan().roadCells, r.grown().owner(),
                new ArrayList<>(r.grown().plan().trunks), null, W, H);
        assertEquals(1, net.pieces());
    }

    @Test
    void aSideStreetMovesOntoTheCrossOnlyWhereItsFarEndStillMeetsItsStreet() {
        // The side street's far end meets a street across the top that it still meets six cells over.
        var g = grown(across(1, W - 2, 25, 3), up(30, 26, H - 2, 3), up(36, 5, 24, 3), across(1, W - 2, 4, 3));
        assertEquals(1, StreetPlan.plan(g, null).crosses());
        // Each side street ends on a short street it would no longer meet six cells over: neither moves.
        var h = grown(across(1, W - 2, 25, 3), up(30, 26, 44, 3), across(28, 32, 45, 3), up(36, 5, 24, 3),
                across(34, 38, 4, 3));
        assertEquals(0, StreetPlan.plan(h, null).crosses());
    }

    @Test
    void aRoadOutOfAGateMeetsTheNearestStreetAcrossItsWaySquareOnItsCentreLine() {
        // A gate at 10,20 opening east; the street at x 30 runs north-south across its way, one at x 50 beyond it.
        var streets = List.of(up(30, 1, 48, 5), up(50, 1, 48, 3));
        assertArrayEquals(new int[]{30, 20}, StreetPlan.meet(10, 20, Side.EAST, streets));
        // Opening north it meets a street running east-west.
        assertArrayEquals(new int[]{10, 40}, StreetPlan.meet(10, 20, Side.NORTH, List.of(across(1, 78, 40, 3))));
        // Nothing across the way, or a street running the same way reached first: no foot.
        assertNull(StreetPlan.meet(10, 20, Side.WEST, streets));
        assertNull(StreetPlan.meet(10, 20, Side.EAST, List.of(across(15, 40, 20, 3), up(50, 1, 48, 3))));
    }

    @Test
    void aRoadOutOfAGateMakesNoOffsetTeeButMayMakeACross() {
        // A side street leaving the far side 6 cells along from the foot: the tee would make an offset pair.
        var offset = List.of(up(30, 1, 48, 3), across(31, 70, 26, 3));
        assertNull(StreetPlan.meet(10, 20, Side.EAST, offset));
        // One leaving it on the road's own line makes a cross; one 11 cells along is clear of it.
        assertArrayEquals(new int[]{30, 20}, StreetPlan.meet(10, 20, Side.EAST, List.of(up(30, 1, 48, 3),
                across(31, 70, 20, 3), across(31, 70, 31, 3))));
    }
}
