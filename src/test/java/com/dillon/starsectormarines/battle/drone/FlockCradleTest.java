package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockCradleTest {

    private static int built(FlockCradle.Clock clock, float seconds, boolean cradle, boolean node, boolean home,
                             int size, int strength) {
        int n = 0;
        for (float t = 0f; t < seconds; t += 0.5f) {
            if (clock.advance(0.5f, cradle, node, home, size + n, strength)) n++;
        }
        return n;
    }

    @Test
    void aCradleBuildsOneDroneEachIntervalWhileTheFlockIsHomeAndShort() {
        assertEquals(3, built(new FlockCradle.Clock(), 3 * FlockCradle.REBUILD_SECONDS + 1f, true, true, true, 4, 14));
    }

    @Test
    void aCradleNeverBuildsPastTheFlocksStrength() {
        assertEquals(1, built(new FlockCradle.Clock(), 5 * FlockCradle.REBUILD_SECONDS, true, true, true, 13, 14));
    }

    @Test
    void nothingIsBuiltWithTheCradleOrItsNodeDownOrTheFlockAway() {
        float long_ = 5 * FlockCradle.REBUILD_SECONDS;
        assertEquals(0, built(new FlockCradle.Clock(), long_, false, true, true, 4, 14));
        assertEquals(0, built(new FlockCradle.Clock(), long_, true, false, true, 4, 14));
        assertEquals(0, built(new FlockCradle.Clock(), long_, true, true, false, 4, 14));
    }

    @Test
    void timeAwayDoesNotCountTowardTheNextDrone() {
        FlockCradle.Clock clock = new FlockCradle.Clock();
        built(clock, FlockCradle.REBUILD_SECONDS - 1f, true, true, true, 4, 14);
        built(clock, 1f, true, true, false, 4, 14);
        assertEquals(0, built(clock, 2f, true, true, true, 4, 14), "the clock started again when the flock left");
    }

    /**
     * A megastructure wall along y = 10 with a one-cell door at x = 10; the building (y > 10) is roofed, so not open;
     * outside (y < 10) is open ground from x = 0 to 20.
     */
    private static final FlockField.Open DOORWAY = (x, y) -> x >= 0 && x <= 20 && y >= 0 && (y < 10 || (y == 10 && x == 10));

    @Test
    void aCradleAtADoorwayStandsBackInTheOpenClearOfTheDoorAndWall() {
        int[] site = FlockCradle.site(10, 9, DOORWAY, 6);
        for (int dx = -FlockCradle.CLEARANCE_CELLS; dx <= FlockCradle.CLEARANCE_CELLS; dx++) {
            for (int dy = -FlockCradle.CLEARANCE_CELLS; dy <= FlockCradle.CLEARANCE_CELLS; dy++) {
                assertTrue(DOORWAY.open(site[0] + dx, site[1] + dy), "housing over open ground only");
            }
        }
        assertArrayEquals(new int[]{10, 8}, site, "one cell back from the threshold, straight out from the door");
        assertTrue(Math.abs(site[1] - 10) > FlockCradle.CLEARANCE_CELLS, "the housing does not cover the door");
    }

    @Test
    void aCradleInTheOpenStandsAtHomeAndNoClearCellLeavesItAtHome() {
        assertArrayEquals(new int[]{5, 5}, FlockCradle.site(5, 5, DOORWAY, 6));
        FlockField.Open alley = (x, y) -> x == 3 && y >= 0 && y <= 20;
        assertArrayEquals(new int[]{3, 4}, FlockCradle.site(3, 4, alley, 6), "no clear cell: home itself");
        assertNull(FlockCradle.site(10, 15, DOORWAY, 6), "home under the roof has no site");
    }
}
