package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A drone skimmer's tether: one launch at a time, a fixed complement, losses kept, and nothing after the hull. */
class SkimmerWingTest {

    @Test void launchesOneAtATimeUntilTheComplementIsSpent() {
        SkimmerWing wing = new SkimmerWing(7L, 2, 3.2f);
        assertTrue(wing.readyToLaunch(0f), "the first drone leaves at once");
        wing.launched(101L);
        assertFalse(wing.readyToLaunch(SkimmerWing.LAUNCH_INTERVAL * .5f), "the bay cycles between launches");
        assertTrue(wing.readyToLaunch(SkimmerWing.LAUNCH_INTERVAL * .5f));
        wing.launched(102L);
        assertFalse(wing.readyToLaunch(100f), "the complement is out");
        assertEquals(0, wing.inBay());
        assertEquals(List.of(101L, 102L), wing.drones());
    }

    @Test void aLostDroneStaysLost() {
        SkimmerWing wing = new SkimmerWing(7L, 2, 3.2f);
        wing.readyToLaunch(0f);
        wing.launched(101L);
        wing.lost(101L);
        assertEquals(List.of(), wing.drones());
        assertEquals(1, wing.inBay(), "a loss does not refill the bay");
    }

    @Test void theAnchorDiesWithTheHullAndNothingMoreLaunches() {
        SkimmerWing wing = new SkimmerWing(7L, 4, 3.2f);
        assertTrue(wing.anchorAlive());
        wing.hullLost();
        assertFalse(wing.anchorAlive());
        assertFalse(wing.readyToLaunch(100f));
    }

    @Test void isAnEscortThatFollowsItsHullAndPublishesItsPicture() {
        SkimmerWing wing = new SkimmerWing(7L, 4, 3.2f);
        assertTrue(wing.escort());
        assertEquals(SkimmerWing.LEASH_CELLS, wing.leashCells());
        assertEquals(1.6f, wing.anchorHalfLengthCells(), 1e-6f);
        wing.follow(10f, 12f, 90f);
        assertEquals(10f, wing.anchorX());
        assertEquals(12f, wing.anchorY());
        assertEquals(90f, wing.anchorHeadingDegrees());
        assertFalse(wing.threatened());
        wing.publish(new long[]{101L}, new float[]{9f, 11f}, 55L);
        assertTrue(wing.threatened());
        assertEquals(55L, wing.focusTarget());
        assertArrayEquals(new long[]{101L}, wing.escortIds());
        assertArrayEquals(new float[]{9f, 11f}, wing.escortPositions());
    }

    @Test void refusesANegativeComplement() {
        assertThrows(IllegalArgumentException.class, () -> new SkimmerWing(7L, -1, 3f));
    }
}
