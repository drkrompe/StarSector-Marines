package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictSecurityTest {

    /** A straight route from the vault at x=0 to the landing at x=100. */
    private static int[] straight() {
        int[] route = new int[101 * 2];
        for (int i = 0; i <= 100; i++) {
            route[i * 2] = i;
            route[i * 2 + 1] = 50;
        }
        return route;
    }

    @Test
    void zonesStartAtTheVaultStopShortOfTheLandingAndKeepTheRouteEndToEnd() {
        List<DerelictSecurity.ZonePlan> zones = DerelictSecurity.zonesAlong(straight(), 4);
        assertEquals(0, zones.get(0).nodeX(), "the first zone keeps the vault");
        assertTrue(zones.get(3).nodeX() < 100, "the last stretch is left to the company");
        for (int i = 1; i < zones.size(); i++) {
            DerelictSecurity.ZonePlan a = zones.get(i - 1);
            DerelictSecurity.ZonePlan b = zones.get(i);
            assertTrue(a.perimeterCells() + b.perimeterCells() >= b.nodeX() - a.nodeX(), "no gap between zones");
        }
        for (int[] post : zones.get(1).posts()) assertEquals(50, post[1], "posts are on the route");
    }

    @Test
    void egressCellsLieOnTheWayOutPastTheMiddle() {
        for (int[] cell : DerelictSecurity.egressAlong(straight())) {
            assertTrue(cell[0] > 50 && cell[0] < 100);
        }
    }

    @Test
    void theVaultFrameKeepsTheVaultAndSiegeFramesTheMiddleZone() {
        List<DerelictWalkerFrame> heavies = List.of(DerelictWalkerFrame.VAULT, DerelictWalkerFrame.LINE,
                DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
        assertArrayEquals(new int[] {0, 0, 1, 2, 2}, DerelictSecurity.zoneOf(heavies, 4));
    }

    @Test
    void mergeKeepsEachUnitsNewestSighting() {
        List<Contact> merged = DerelictSecurityDirector.merge(
                List.of(new Contact(2, 1, 1, 10), new Contact(1, 0, 0, 5)),
                List.of(new Contact(2, 9, 9, 20)));
        assertEquals(2, merged.size());
        assertEquals(1L, merged.get(0).unitId());
        assertEquals(9, merged.get(1).cellX());
    }
}
