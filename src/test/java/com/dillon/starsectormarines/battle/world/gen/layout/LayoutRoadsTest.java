package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A road carved to a walled place's gate is carried through it to the place's own roads. */
class LayoutRoadsTest {

    private static final int W = 40, H = 40;
    private static final int PLACE = 1;

    /** A square place from 10 to 29 with a street of its own down x 19..21, y 14..25, and no road anywhere else. */
    private static int[][] claim() {
        int[][] claim = new int[W][H];
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) claim[x][y] = x >= 10 && x <= 29 && y >= 10 && y <= 29 ? PLACE : GrownTrunkPlan.UNOWNED;
        return claim;
    }

    private static int[][] owner() {
        int[][] owner = new int[W][H];
        for (int[] row : owner) Arrays.fill(row, GrownTrunkPlan.UNOWNED);
        for (int x = 19; x <= 21; x++) for (int y = 14; y <= 25; y++) owner[x][y] = PLACE;
        return owner;
    }

    @Test
    void aRoadToTheWallIsCarriedOnToThePlacesStreet() {
        int[][] claim = claim(), owner = owner();
        // The feeder: in from the west edge along y 19..21, ending against the place's west side.
        for (int x = 1; x <= 9; x++) for (int y = 19; y <= 21; y++) owner[x][y] = 0;
        List<int[]> mouth = List.of(new int[]{9, 19}, new int[]{9, 20}, new int[]{9, 21});
        assertTrue(!LayoutRoads.enter(claim, owner, null, PLACE, mouth, W, H).isEmpty(), "nothing was cut");
        assertTrue(joined(owner, 1, 20, 20, 20), "the feeder still stops at the wall");
    }

    @Test
    void aRoadThatAlreadyMeetsThePlacesStreetIsLeftAlone() {
        int[][] claim = claim(), owner = owner();
        for (int x = 10; x <= 18; x++) for (int y = 19; y <= 21; y++) owner[x][y] = PLACE;
        for (int x = 1; x <= 9; x++) for (int y = 19; y <= 21; y++) owner[x][y] = 0;
        List<int[]> mouth = List.of(new int[]{9, 19}, new int[]{9, 20}, new int[]{9, 21});
        assertTrue(LayoutRoads.enter(claim, owner, null, PLACE, mouth, W, H).isEmpty(), "a second way in was cut");
    }

    /**
     * Three settled places in a row along y 15: a road from the first to the last is laid on open country only, so
     * across the middle place's ground it is missing unless it is laid through.
     */
    private static int[][] across(Road road) {
        int w = 60, h = 30;
        GrownTrunkPlan.Profile hamlet = GrownTrunkPlan.Profile.hamlet();
        PrecinctPlan plan = new PrecinctPlan(List.of(Precinct.settlement("a", 6, 15, hamlet),
                Precinct.settlement("b", 28, 15, hamlet), Precinct.settlement("c", 50, 15, hamlet)))
                .withLayout(new MissionLayout(List.of(road)));
        int[][] claim = new int[w][h], owner = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                claim[x][y] = x >= 2 && x <= 10 ? 0 : x >= 20 && x <= 35 && y >= 4 && y <= 26 ? 1
                        : x >= 45 && x <= 55 ? 2 : GrownTrunkPlan.UNOWNED;
                // Each end place has a street along the road's line; the middle one's runs elsewhere.
                boolean street = (claim[x][y] == 0 || claim[x][y] == 2) && y >= 14 && y <= 16
                        || claim[x][y] == 1 && y >= 5 && y <= 7;
                owner[x][y] = street ? claim[x][y] : GrownTrunkPlan.UNOWNED;
            }
        }
        LayoutRoads.carve(plan, claim, owner, null, w, h);
        return owner;
    }

    @Test
    void aRoadLaidThroughShowsAcrossASettledPlace() {
        Road road = new Road("line", Endpoint.beside("a", Side.EAST, 0, 0), Endpoint.beside("c", Side.WEST, 0, 0), 3,
                false);
        int[][] open = across(road), through = across(road.laidThrough());
        assertTrue(open[28][15] == GrownTrunkPlan.UNOWNED, "a road on open country crossed the place");
        for (int x = 11; x <= 44; x++) {
            assertTrue(through[x][15] != GrownTrunkPlan.UNOWNED, "the road laid through stops at " + x);
        }
        assertTrue(through[28][15] == 1, "across the place the road is not its street");
    }

    private static boolean joined(int[][] owner, int ax, int ay, int bx, int by) {
        boolean[][] seen = new boolean[W][H];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{ax, ay});
        seen[ax][ay] = true;
        while (!open.isEmpty()) {
            int[] c = open.poll();
            if (c[0] == bx && c[1] == by) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= W || y >= H || seen[x][y] || owner[x][y] == GrownTrunkPlan.UNOWNED) continue;
                seen[x][y] = true;
                open.add(new int[]{x, y});
            }
        }
        return false;
    }
}
