package com.dillon.starsectormarines.battle.world.gen.road;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A flood that pays for its bends walks back a planner's road: straight runs and few bends, never a staircase. */
class BendFloodTest {

    private static final int W = 30, H = 20;

    private static int[][] sources(int[]... cells) {
        int[][] out = new int[W][H];
        for (int[] column : out) Arrays.fill(column, -1);
        for (int i = 0; i < cells.length; i++) out[cells[i][0]][cells[i][1]] = i;
        return out;
    }

    /** Changes of direction along a path of unit steps. */
    static int bends(List<int[]> path) {
        int bends = 0;
        for (int k = 1; k + 1 < path.size(); k++) {
            int[] p = path.get(k - 1), c = path.get(k), n = path.get(k + 1);
            if (c[0] - p[0] != n[0] - c[0] || c[1] - p[1] != n[1] - c[1]) bends++;
        }
        return bends;
    }

    @Test
    void aWayOffBothAxesTurnsOnceInsteadOfStepping() {
        BendFlood flood = BendFlood.from(sources(new int[]{2, 2}), (x, y) -> 1, BendFlood.BEND);
        List<int[]> way = flood.back(20, 15);
        assertEquals(18 + 13 + 1, way.size(), "the way is not the shortest");
        assertEquals(1, bends(way), "a planner's road off both axes is one bend, not a staircase");
        assertEquals(2, way.get(way.size() - 1)[0]);
        assertEquals(2, way.get(way.size() - 1)[1]);
    }

    @Test
    void aWayRoundAWallTurnsOnlyWhereItMust() {
        // A wall down x 10 from the bottom to y 14: the way from one side to the other goes round its top.
        BendFlood flood = BendFlood.from(sources(new int[]{2, 2}), (x, y) -> x == 10 && y <= 14 ? -1 : 1,
                BendFlood.BEND);
        List<int[]> way = flood.back(20, 2);
        for (int[] c : way) assertFalse(c[0] == 10 && c[1] <= 14, "the way crossed the wall at " + c[1]);
        assertEquals(2, bends(way), "round the wall's end is two bends");
    }

    @Test
    void eachCellKnowsItsNearestSource() {
        BendFlood flood = BendFlood.from(sources(new int[]{2, 10}, new int[]{27, 10}), (x, y) -> 1, BendFlood.BEND);
        assertEquals(0, flood.label(5, 10));
        assertEquals(1, flood.label(24, 12));
        assertEquals(0, flood.cost(2, 10));
        assertTrue(flood.back(5, 10).size() == 4, "the way back runs to the source and stops");
    }
}
