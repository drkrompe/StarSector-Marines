package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The firing picker needs a boolean proof, never the route it used to discard. */
class FiringPositionReachabilityTest {

    @Test
    void componentProofMatchesDiscardedSearchWithBothMovementPolicies() {
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
                            assertEquals(reachable(grid, sx, sy, gx, gy, false),
                                    reachable(grid, sx, sy, gx, gy, true),
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
        for (boolean components : new boolean[]{false, true}) {
            assertTrue(reachable(grid, 0, 0, 0, 0, components));
            assertFalse(reachable(grid, 1, 0, 1, 0, components));
            assertFalse(reachable(grid, 0, 0, 1, 0, components));
            assertFalse(reachable(grid, 1, 0, 0, 0, components));
            assertFalse(reachable(grid, -1, 0, 0, 0, components));
            assertFalse(reachable(grid, 0, 0, 2, 0, components));
        }
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
                for (boolean components : new boolean[]{true, false}) {
                    assertEquals(!cardinal, reachable(grid, 0, 0, 1, 1, components));
                }
            }
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = originalPolicy;
        }
    }

    private static void assertProof(NavigationGrid grid, boolean expected) {
        // Ask the component path first so the control cannot hide stale labels.
        assertEquals(expected, reachable(grid, 0, 0, 2, 0, true));
        assertEquals(expected, reachable(grid, 0, 0, 2, 0, false));
        assertEquals(expected, reachable(grid, 2, 0, 0, 0, true));
    }

    private static boolean reachable(NavigationGrid grid, int sx, int sy,
                                     int gx, int gy, boolean components) {
        return TacticalScoring.isFiringPositionReachable(grid, sx, sy, gx, gy, components);
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
