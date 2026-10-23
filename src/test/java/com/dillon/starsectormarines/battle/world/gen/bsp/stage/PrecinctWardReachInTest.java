package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctBoundary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A square place that streets were laid round, and whose own arms never grew in,
 * is given its ways in off the road that runs past it.
 */
class PrecinctWardReachInTest {

    private static final int W = 40, H = 40;

    @Test
    void aSquareNoRoadReachesGetsAGateOffTheRoadPastItsGlacis() {
        int[][] claim = new int[W][H];
        int[][] own = new int[W][H];
        int[][] road = new int[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                claim[x][y] = inside(x, y, 7, 32) ? 1 : GrownTrunkPlan.UNOWNED;
                own[x][y] = inside(x, y, 10, 29) ? 1 : GrownTrunkPlan.UNOWNED;
                // Streets past the west and south sides, beyond the glacis, none into the square.
                road[x][y] = x >= 4 && x <= 6 || y >= 33 && y <= 35 ? 7 : GrownTrunkPlan.UNOWNED;
            }
        }

        PrecinctWardStage.reachIn(claim, own, road, 1, 2, W, H);

        List<PrecinctBoundary.Gate> gates = PrecinctBoundary.gates(own, road, 1, W, H);
        assertEquals(2, gates.size(), "a square with streets past two sides came out with "
                + gates.size() + " ways in");
        for (PrecinctBoundary.Gate gate : gates) assertTrue(gate.drivable(), "a way in too narrow to drive");
        boolean middle = false;
        for (int x = 18; x <= 21; x++) for (int y = 18; y <= 21; y++) middle |= road[x][y] == 7;
        assertTrue(middle, "the arms do not reach the middle of the place");
    }

    @Test
    void aSquareWithItsOwnRoadIsLeftAsItGrew() {
        int[][] own = new int[W][H];
        int[][] road = new int[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                own[x][y] = inside(x, y, 10, 29) ? 1 : GrownTrunkPlan.UNOWNED;
                road[x][y] = x == 9 || y == 20 && x <= 15 ? 7 : GrownTrunkPlan.UNOWNED;
            }
        }
        PrecinctWardStage.reachIn(own, own, road, 1, 2, W, H);
        int roads = 0;
        for (int x = 10; x <= 29; x++) for (int y = 10; y <= 29; y++) if (road[x][y] != GrownTrunkPlan.UNOWNED) roads++;
        assertEquals(6, roads, "a place with a road of its own was given another");
    }

    private static boolean inside(int x, int y, int lo, int hi) {
        return x >= lo && x <= hi && y >= lo && y <= hi;
    }
}
