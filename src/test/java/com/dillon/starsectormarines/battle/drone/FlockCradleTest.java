package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
