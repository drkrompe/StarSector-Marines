package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontageLineTest {

    @Test
    void withNothingBelievedTheLineFacesAwayFromTheSquad() {
        // A north-south line at x = 20, the squad standing west of it.
        FrontageLine line = FrontageLine.facing(20, 10, 20, 30, 5f, 20f, -1, -1);
        assertEquals(1f, line.frontX(), 1e-4f, "faces east, away from where the squad came from");
        assertEquals(0f, line.frontY(), 1e-4f);
    }

    @Test
    void aBelievedThreatDecidesTheFacing() {
        // The squad stands west, but the enemy it believes in is west too: face the enemy.
        FrontageLine line = FrontageLine.facing(20, 10, 20, 30, 5f, 20f, 2, 20);
        assertEquals(-1f, line.frontX(), 1e-4f);
    }

    @Test
    void theBandReachesThreeCellsEitherSideAndPastTheEnds() {
        FrontageLine line = FrontageLine.facing(20, 10, 20, 30, 5f, 20f, -1, -1);
        assertTrue(line.covers(23, 15));
        assertTrue(line.covers(17, 15));
        assertFalse(line.covers(24, 15));
        assertTrue(line.covers(20, 33));
        assertFalse(line.covers(20, 34));
    }

    @Test
    void theEnclosingCircleHoldsTheWholeBand() {
        FrontageLine line = FrontageLine.facing(0, 0, 20, 0, 10f, -5f, -1, -1);
        int[] circle = line.enclosingCircle();
        for (int x = -3; x <= 23; x++) {
            for (int y = -3; y <= 3; y++) {
                if (!line.covers(x, y)) continue;
                int dx = x - circle[0];
                int dy = y - circle[1];
                assertTrue(dx * dx + dy * dy <= circle[2] * circle[2], "band cell " + x + "," + y);
            }
        }
    }
}
