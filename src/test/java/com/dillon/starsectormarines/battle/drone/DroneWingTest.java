package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dillon.starsectormarines.battle.drone.DroneRole.ESCORT;
import static com.dillon.starsectormarines.battle.drone.DroneRole.HALO;
import static com.dillon.starsectormarines.battle.drone.DroneRole.SENSOR;
import static com.dillon.starsectormarines.battle.drone.DroneRole.STRIKE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull's drone bay: one launch at a time in role order, losses kept, the strike drone rebuilt, stations by role. */
class DroneWingTest {

    @Test void launchesOneAtATimeInBayOrderUntilTheComplementIsSpent() {
        DroneWing wing = new DroneWing(7L, List.of(ESCORT, HALO), 3.2f);
        assertEquals(ESCORT, wing.readyToLaunch(0f, false), "the first drone leaves at once");
        wing.launched(101L, ESCORT);
        assertNull(wing.readyToLaunch(DroneWing.LAUNCH_INTERVAL * .5f, false), "the bay cycles between launches");
        assertEquals(HALO, wing.readyToLaunch(DroneWing.LAUNCH_INTERVAL * .5f, false));
        wing.launched(102L, HALO);
        assertNull(wing.readyToLaunch(100f, true), "the complement is out");
        assertEquals(0, wing.inBay());
        assertEquals(List.of(101L, 102L), wing.drones());
    }

    @Test void aLostDroneStaysLost() {
        DroneWing wing = new DroneWing(7L, List.of(ESCORT, ESCORT), 3.2f);
        wing.readyToLaunch(0f, false);
        wing.launched(101L, ESCORT);
        wing.lost(101L);
        assertEquals(List.of(), wing.drones());
        assertEquals(1, wing.inBay(), "a loss does not refill the bay");
    }

    @Test void theStrikeDroneWaitsForATargetAndIsRebuiltWhenSpent() {
        DroneWing wing = new DroneWing(7L, List.of(STRIKE, ESCORT), 3.2f);
        assertEquals(ESCORT, wing.readyToLaunch(0f, true), "the strike slot waits behind the others");
        wing.launched(101L, ESCORT);
        assertNull(wing.readyToLaunch(100f, false), "no target, no strike");
        assertEquals(STRIKE, wing.readyToLaunch(0f, true));
        wing.launched(102L, STRIKE);
        assertEquals(102L, wing.strikeDrone());
        assertEquals(0, wing.strikesReady());
        wing.lost(102L);
        assertEquals(DroneWing.STRIKE_REBUILD_SECONDS, wing.rebuildRemaining(), 1e-4f);
        assertNull(wing.readyToLaunch(DroneWing.STRIKE_REBUILD_SECONDS - 1f, true), "still building");
        assertEquals(STRIKE, wing.readyToLaunch(1.01f, true), "rebuilt and away");
        assertEquals(1, wing.strikesReady());
    }

    @Test void theAnchorDiesWithTheHullAndNothingMoreLaunches() {
        DroneWing wing = new DroneWing(7L, List.of(ESCORT, ESCORT), 3.2f);
        assertTrue(wing.anchorAlive());
        wing.hullLost();
        assertFalse(wing.anchorAlive());
        assertNull(wing.readyToLaunch(100f, true));
    }

    @Test void onlyEscortsFlyTheFormationAndTheOthersHaveStations() {
        DroneWing wing = new DroneWing(7L, List.of(ESCORT, SENSOR, HALO, STRIKE), 3.2f);
        wing.launched(101L, ESCORT);
        wing.launched(102L, SENSOR);
        wing.launched(103L, HALO);
        wing.follow(10f, 12f, 0f);
        wing.publish(new long[]{101L, 102L, 103L}, new float[]{9f, 11f, 10f, 30f, 12f, 12f}, 55L, 0f);
        assertTrue(wing.escort());
        assertTrue(wing.threatened());
        assertEquals(55L, wing.focusTarget());
        assertArrayEquals(new long[]{101L}, wing.escortIds());
        assertArrayEquals(new float[]{9f, 11f}, wing.escortPositions());
        assertNull(wing.sortieGoal(101L), "an escort goes about its business");
        assertNotNull(wing.sortieGoal(102L));
        assertNotNull(wing.sortieGoal(103L));
        assertSame(Drone.HANDLING, wing.handling(101L));
        assertSame(DroneWing.FAST_HANDLING, wing.handling(102L));
    }

    @Test void theSensorPairHoldsStationAheadEitherSideOfTheLine() {
        // Heading 0 points along +Y.
        float[] port = DroneWing.sensorStation(10f, 10f, 0f, 1.5f, 0, 2);
        float[] starboard = DroneWing.sensorStation(10f, 10f, 0f, 1.5f, 1, 2);
        float ahead = 1.5f + DroneWing.SENSOR_AHEAD_CELLS;
        assertEquals(10f + ahead, port[1], 1e-4f);
        assertEquals(10f + ahead, starboard[1], 1e-4f);
        assertEquals(2f * DroneWing.SENSOR_SPREAD_CELLS, Math.abs(port[0] - starboard[0]), 1e-4f);
        assertEquals(10f, (port[0] + starboard[0]) / 2f, 1e-4f);
    }

    @Test void theHaloOrbitsTightAndEvenlySpaced() {
        float r = 1.6f + DroneWing.HALO_STANDOFF_CELLS;
        float[] a = DroneWing.haloStation(0f, 0f, 1.6f, 0, 2, 3f);
        float[] b = DroneWing.haloStation(0f, 0f, 1.6f, 1, 2, 3f);
        assertEquals(r, Math.hypot(a[0], a[1]), 1e-4f);
        assertEquals(-a[0], b[0], 1e-4f, "two halo drones sit opposite each other");
        assertEquals(-a[1], b[1], 1e-4f);
    }

    @Test void aStrikeDroneNeverFiresItsGun() {
        DroneWing wing = new DroneWing(7L, List.of(STRIKE), 3.2f);
        wing.launched(101L, STRIKE);
        assertFalse(wing.mayFire(101L));
        assertTrue(wing.mayFire(999L), "a drone the wing does not know keeps its gun");
    }
}
