package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSquareTest {

    @Test
    void everyPoseLaysTheWholeFootprintOnceAndOpensTheFrontOutward() {
        for (int turn = 0; turn < 4; turn++) {
            MarketSquare.Pose pose = new MarketSquare.Pose(10, 20, turn);
            Set<Long> cells = new HashSet<>();
            for (int y = 0; y < MarketSquare.DEPTH; y++) {
                for (int x = 0; x < MarketSquare.WIDTH; x++) {
                    int[] c = pose.cell(x, y);
                    assertTrue(c[0] >= 10 && c[0] < 10 + pose.width() && c[1] >= 20 && c[1] < 20 + pose.height(),
                            "turn " + turn + " put " + x + "," + y + " outside its footprint");
                    cells.add(key(c[0], c[1]));
                }
            }
            assertEquals(MarketSquare.WIDTH * MarketSquare.DEPTH, cells.size(), "turn " + turn + " laid a cell twice");
            int[] out = pose.cell(MarketSquare.WIDTH / 2, -1);
            assertFalse(cells.contains(key(out[0], out[1])), "turn " + turn + " front opens inward");
        }
    }

    @Test
    void everyMouthReachesEveryShopfrontBetweenTheStalls() {
        for (MarketSquare.Mouths mouths : MarketSquare.Mouths.values()) {
            assertEquals(mouths.count(), MarketSquare.openings(mouths).size(), mouths + " opens what it says");
            for (MarketSquare.Opening opening : MarketSquare.openings(mouths)) {
                Set<Long> reached = walk(mouths, opening.x0(), opening.y0());
                for (MarketSquare.Rect shop : MarketSquare.shops(mouths)) {
                    // A door may be cut anywhere between the frontage's corners, so every cell before it has to
                    // be square a mouth reaches, or the street outside the footprint.
                    for (int[] wall : frontage(shop)) {
                        int fx = wall[0] + shop.faceX(), fy = wall[1] + shop.faceY();
                        boolean outside = fx < 0 || fy < 0 || fx >= MarketSquare.WIDTH || fy >= MarketSquare.DEPTH;
                        assertTrue(outside || reached.contains(key(fx, fy)), mouths + ": " + shop + " fronts " + fx
                                + "," + fy + ", which the mouth at " + opening.x0() + "," + opening.y0() + " does not reach");
                    }
                }
            }
        }
    }

    @Test
    void stallsKeepOffTheShopfrontAisles() {
        for (MarketSquare.Mouths mouths : MarketSquare.Mouths.values()) {
            for (int y = 0; y < MarketSquare.DEPTH; y++) {
                for (int x = 0; x < MarketSquare.WIDTH; x++) {
                    if (!MarketSquare.stall(mouths, x, y)) continue;
                    assertFalse(MarketSquare.shop(mouths, x, y), mouths + " stall inside a shop at " + x + "," + y);
                    for (int d = 1; d <= MarketSquare.AISLE; d++) {
                        assertFalse(MarketSquare.shop(mouths, x - d, y) || MarketSquare.shop(mouths, x + d, y)
                                || MarketSquare.shop(mouths, x, y + d), mouths + " stall in a door's aisle at " + x + "," + y);
                    }
                    assertTrue(y >= MarketSquare.AISLE, mouths + " stall in the front mouth at " + x + "," + y);
                }
            }
        }
    }

    /** Open square reachable from local {@code (x, y)} by four-way steps, round the stalls and the shops. */
    private static Set<Long> walk(MarketSquare.Mouths mouths, int x0, int y0) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{x0, y0});
        seen.add(key(x0, y0));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!open.isEmpty()) {
            int[] at = open.poll();
            for (int[] s : steps) {
                int x = at[0] + s[0], y = at[1] + s[1];
                if (x < 0 || y < 0 || x >= MarketSquare.WIDTH || y >= MarketSquare.DEPTH) continue;
                if (MarketSquare.shop(mouths, x, y) || MarketSquare.stall(mouths, x, y)
                        || MarketSquare.monument(mouths, x, y)) continue;
                if (seen.add(key(x, y))) open.add(new int[]{x, y});
            }
        }
        return seen;
    }

    /** The wall cells of a shop's frontage a door may be cut in: all but its corners. */
    private static List<int[]> frontage(MarketSquare.Rect shop) {
        List<int[]> out = new ArrayList<>();
        if (shop.faceX() != 0) {
            int x = shop.faceX() > 0 ? shop.x1() : shop.x0();
            for (int y = shop.y0() + 1; y < shop.y1(); y++) out.add(new int[]{x, y});
        } else {
            int y = shop.faceY() > 0 ? shop.y1() : shop.y0();
            for (int x = shop.x0() + 1; x < shop.x1(); x++) out.add(new int[]{x, y});
        }
        return out;
    }

    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

    @Test
    void aHostThatNamesAFrontStreetOrAStreetThroughGetsTheSquareRoundThem() {
        boolean[][] road = new boolean[40][30];
        for (boolean[] column : road) Arrays.fill(column, true);
        MarketSquare.Placement square = MarketSquare.choose(40, 30, List.of(), road, null, 8, 6, 31, 17,
                (x, y) -> true, (x, y) -> x >= 8 && x <= 31, (x, y) -> y == 5, (x, y) -> false, MarketSquare.DEFAULT_PREFERENCE);
        assertEquals(new MarketSquare.Pose(8, 6, 0), square.pose(), "the front on the named street");
        assertNull(MarketSquare.choose(40, 30, List.of(), road, null, 8, 6, 31, 17,
                (x, y) -> true, (x, y) -> true, (x, y) -> false, (x, y) -> false, MarketSquare.DEFAULT_PREFERENCE));
    }
}
