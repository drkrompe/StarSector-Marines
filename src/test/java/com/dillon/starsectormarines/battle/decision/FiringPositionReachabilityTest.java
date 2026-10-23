package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The firing picker needs a boolean reachability proof, which must agree with the infantry A*. */
class FiringPositionReachabilityTest {

    @Test
    void componentProofMatchesSearchWithBothMovementPolicies() {
        boolean originalPolicy = GridPathfinder.USE_CARDINAL_NAVIGATION;
        try {
            for (boolean cardinal : new boolean[]{false, true}) {
                GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
                for (int seed = 0; seed < 12; seed++) {
                    NavigationGrid grid = randomGrid(seed);
                    for (int from = 0; from < 20; from++) {
                        for (int to = 0; to < 20; to++) {
                            int sx = from % 5, sy = from / 5;
                            int gx = to % 5, gy = to / 5;
                            assertEquals(searched(grid, sx, sy, gx, gy),
                                    proof(grid, sx, sy, gx, gy),
                                    "cardinal=" + cardinal + ", seed=" + seed
                                            + ", from=" + from + ", to=" + to);
                        }
                    }
                }
            }
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = originalPolicy;
        }
    }

    @Test
    void sameCellMustBeWalkableAndEndpointsMustBeInBounds() {
        NavigationGrid grid = new NavigationGrid(2, 1);
        grid.setWalkableFloor(0, 0);
        assertTrue(proof(grid, 0, 0, 0, 0));
        assertFalse(proof(grid, 1, 0, 1, 0));
        assertFalse(proof(grid, 0, 0, 1, 0));
        assertFalse(proof(grid, 1, 0, 0, 0));
        assertFalse(proof(grid, -1, 0, 0, 0));
        assertFalse(proof(grid, 0, 0, 2, 0));
    }

    @Test
    void warmedProofTracksBothOneSidedEdgeAndWalkabilityChanges() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, 0);
        assertProof(grid, true);

        grid.setEdgePassable(1, 0, Direction.W, false);
        assertProof(grid, false);
        grid.setEdgePassable(1, 0, Direction.W, true);
        assertProof(grid, true);

        grid.setWalkable(1, 0, false);
        assertProof(grid, false);
        grid.setWalkableFloor(1, 0);
        assertProof(grid, true);
    }

    @Test
    void proofReadsCurrentMovementPolicyWithoutReusingTheOtherPoliciesLabels() {
        boolean originalPolicy = GridPathfinder.USE_CARDINAL_NAVIGATION;
        try {
            NavigationGrid grid = new NavigationGrid(2, 2);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) grid.setWalkableFloor(x, y);
            }
            grid.setEdgePassable(1, 0, Direction.W, false);
            grid.setEdgePassable(0, 1, Direction.S, false);
            for (boolean cardinal : new boolean[]{false, true, false}) {
                GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
                assertEquals(!cardinal, proof(grid, 0, 0, 1, 1));
            }
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = originalPolicy;
        }
    }

    private static void assertProof(NavigationGrid grid, boolean expected) {
        assertEquals(expected, proof(grid, 0, 0, 2, 0));
        assertEquals(expected, searched(grid, 0, 0, 2, 0));
        assertEquals(expected, proof(grid, 2, 0, 0, 0));
    }

    /** The component proof the firing picker asks. */
    private static boolean proof(NavigationGrid grid, int sx, int sy, int gx, int gy) {
        return grid.arePathConnected(sx, sy, gx, gy, GridPathfinder.USE_CARDINAL_NAVIGATION);
    }

    /** The infantry A* the proof must agree with. */
    private static boolean searched(NavigationGrid grid, int sx, int sy, int gx, int gy) {
        return GridPathfinder.findPath(grid, sx, sy, gx, gy).length > 0;
    }

    private static NavigationGrid randomGrid(int seed) {
        Random random = new Random(seed);
        NavigationGrid grid = new NavigationGrid(5, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 5; x++) {
                grid.setWalkable(x, y, random.nextInt(100) < 75);
                for (Direction direction : Direction.ALL) {
                    grid.setEdgePassable(x, y, direction, random.nextInt(100) < 85);
                }
            }
        }
        return grid;
    }
}
