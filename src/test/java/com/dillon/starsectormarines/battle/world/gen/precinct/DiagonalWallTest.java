package com.dillon.starsectormarines.battle.world.gen.precinct;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiagonalWallTest {
    @Test void joinsStayInsideClaimAndYieldToGateReservation() {
        for (boolean gate : new boolean[]{false, true}) {
            boolean[][] wall = {{true, false}, {false, true}};
            int[][] claim = {{0, -1}, {0, 0}};
            boolean[][] reserved = {{false, false}, {gate, false}};
            PrecinctBoundary.joinDiagonalSteps(wall, claim, 0, reserved);
            assertTrue(wall[0][0]);
            assertTrue(wall[1][1]);
            assertFalse(wall[0][1], "never enlarge the compound onto unclaimed ground");
            assertEquals(!gate, wall[1][0], "join the inner corner unless the route owns it");
        }
    }
}
