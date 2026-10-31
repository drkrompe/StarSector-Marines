package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Road's fixed truck fleet ({@link CordonFleet}). */
class CordonFleetTest {

    private static final int TRUCKS = CordonConvoys.TRUCKS, SIZE = CordonConvoys.FLEET * TRUCKS, DELAY = 100;

    @Test
    void fortyEightPeopleTakeThreeTripsOfThreeTrucks() {
        assertEquals(3, CordonFleet.tripsFor(CordonConvoys.TOWNSPEOPLE, TRUCKS, CordonConvoys.SEATS));
        assertTrue(CordonFleet.tripsFor(CordonConvoys.TOWNSPEOPLE, TRUCKS, CordonConvoys.SEATS) <= CordonConvoys.MAX_CONVOYS);
    }

    @Test
    void theSameTrucksCycleTripAfterTripAndTheFleetNeverGrowsPastItsSize() {
        CordonFleet fleet = new CordonFleet(SIZE, DELAY);
        Deque<Integer> waiting = new ArrayDeque<>();
        int nextId = 0, tick = 0;
        int[] onRoad = null;
        for (int trip = 0; trip < 20; trip++, tick += 50) {
            assertTrue(fleet.ready(waiting.size(), tick) >= TRUCKS, "a trip always finds its trucks");
            int[] convoy = new int[TRUCKS];
            for (int k = 0; k < TRUCKS; k++) {
                if (!waiting.isEmpty()) {
                    convoy[k] = waiting.poll();
                } else {
                    fleet.spawn(tick);
                    convoy[k] = nextId++;
                }
            }
            assertTrue(fleet.live() <= SIZE);
            // Two convoys staggered: the one before comes home once this one is on its way.
            if (onRoad != null) for (int id : onRoad) waiting.add(id);
            onRoad = convoy;
        }
        assertEquals(SIZE, fleet.spawned(), "twenty trips, and only the fleet's own trucks ever came on");
        assertEquals(SIZE, nextId);
    }

    @Test
    void aLostTruckIsReplacedOnceAfterTheDelay() {
        CordonFleet fleet = new CordonFleet(SIZE, DELAY);
        for (int k = 0; k < SIZE; k++) fleet.spawn(0);
        assertEquals(0, fleet.spawnable(10), "the fleet is full");
        fleet.lose(20);
        assertEquals(0, fleet.spawnable(20 + DELAY - 1), "not before the delay");
        assertEquals(1, fleet.spawnable(20 + DELAY));
        fleet.spawn(20 + DELAY);
        assertEquals(0, fleet.spawnable(10_000), "replaced once");
        assertEquals(SIZE + 1, fleet.spawned());
        assertThrows(IllegalStateException.class, () -> fleet.spawn(10_000));
    }
}
