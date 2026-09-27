package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedStepReachabilityTest {
    @Test
    void respectsCardinalDiagonalAndCornerLegality() {
        NavigationGrid grid = open(3, 3);
        BoundedStepReachability proof = new BoundedStepReachability();
        proof.prepare(grid, 0, 0, true, 3, 100);
        assertTrue(proof.canReject(1, 1, 1));
        assertFalse(proof.canReject(1, 1, 2));
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertFalse(proof.canReject(1, 1, 1));
        grid.setWalkable(1, 0, false);
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertTrue(proof.canReject(1, 1, 1));
        assertFalse(proof.canReject(1, 1, 2));
        grid.setEdgePassable(0, 1, Direction.S, false);
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertTrue(proof.canReject(1, 1, 3));
    }

    @Test
    void truncatedSearchUsesCompletedLayersButLeavesLaterCellsUnknown() {
        NavigationGrid grid = open(9, 9);
        BoundedStepReachability proof = new BoundedStepReachability();
        proof.prepare(grid, 4, 4, true, 4, 1);
        assertEquals(1, proof.expandedNodes());
        assertFalse(proof.canReject(5, 4, 1));
        assertTrue(proof.canReject(5, 5, 1));
        assertFalse(proof.canReject(5, 5, 2));
        assertFalse(proof.canReject(8, 8, 4));
        proof.prepare(grid, 4, 4, true, 4, 0);
        assertEquals(0, proof.expandedNodes());
        assertFalse(proof.canReject(4, 4, 0));
        assertTrue(proof.canReject(5, 4, 0));
        assertFalse(proof.canReject(5, 4, 1));
    }

    @Test
    void clipsStorageAndDoesNotReuseTopologyOrInvalidOriginState() {
        NavigationGrid grid = open(100, 100);
        BoundedStepReachability proof = new BoundedStepReachability();
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertEquals(16, proof.storageCells());
        assertTrue(proof.canReject(4, 0, 3));
        assertFalse(proof.canReject(4, 0, 4));
        assertFalse(proof.canReject(1, 0, 1));
        grid.setWalkable(1, 0, false);
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertTrue(proof.canReject(1, 0, 3));
        proof.prepare(grid, -1, 0, false, 3, 100);
        assertTrue(proof.canReject(0, 0, 3));
        proof.prepare(grid, 0, 0, false, 3, 100);
        assertFalse(proof.canReject(0, 0, 0));
    }

    @Test
    void rejectionsAgreeWithFullStepOracleAcrossObstaclesAndTruncation() {
        NavigationGrid grid = open(11, 9);
        for (int y = 0; y < 8; y++) grid.setWalkable(5, y, false);
        grid.setEdgePassable(2, 3, Direction.E, false);
        grid.setEdgePassable(3, 4, Direction.S, false);
        for (boolean cardinal : new boolean[]{true, false}) {
            int[] expected = oracle(grid, 2, 2, cardinal);
            for (int limit : new int[]{0, 1, 7, 1000}) {
                BoundedStepReachability proof = new BoundedStepReachability();
                proof.prepare(grid, 2, 2, cardinal, 8, limit);
                for (int y = 0; y < 9; y++) {
                    for (int x = 0; x < 11; x++) {
                        for (int budget = 0; budget <= 8; budget++) {
                            int steps = expected[grid.index(x, y)];
                            boolean rejected = proof.canReject(x, y, budget);
                            boolean impossible = steps < 0 || steps > budget;
                            if (rejected) assertTrue(impossible, "rejected a feasible step route");
                            if (limit == 1000) assertEquals(impossible, rejected);
                        }
                    }
                }
            }
        }
    }

    private static int[] oracle(NavigationGrid grid, int x, int y, boolean cardinal) {
        int[] distances = new int[grid.getWidth() * grid.getHeight()];
        Arrays.fill(distances, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = grid.index(x, y);
        distances[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int from = queue.remove();
            int fx = from % grid.getWidth();
            int fy = from / grid.getWidth();
            for (Direction dir : cardinal ? Direction.CARDINALS : Direction.ALL) {
                int nx = fx + dir.dx;
                int ny = fy + dir.dy;
                if (!grid.inBounds(nx, ny)) continue;
                int next = grid.index(nx, ny);
                if (distances[next] >= 0 || !GridPathfinder.canStep(from, fx, fy,
                        next, dir.bit(), grid.getWidth(), grid.getHeight(),
                        grid.getCellFlagsArray(), grid.getEdgePassabilityArray(), null)) continue;
                distances[next] = distances[from] + 1;
                queue.add(next);
            }
        }
        return distances;
    }

    private static NavigationGrid open(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
