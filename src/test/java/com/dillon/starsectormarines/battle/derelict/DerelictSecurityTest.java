package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(DerelictSecurity.POSTS_PER_ZONE, zones.get(0).posts().stream().mapToInt(p -> p[0]).distinct()
                .count(), "the vault zone's posts are distinct though the route starts there");
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

    private static RuinsDelve withInterior(List<Area> chambers) {
        RuinsDelve.Vault vault = new RuinsDelve.Vault(new Area(90, 40, 99, 60), new Cell(90, 50), new Cell(95, 50),
                new Cell(88, 50), new Cell(97, 50));
        RuinsDelve.Interior interior = new RuinsDelve.Interior(RuinsDelve.INTERIOR, new Area(60, 0, 99, 99), 2,
                List.of(new Area(60, 48, 61, 51)), chambers);
        return new RuinsDelve(3, new Area(0, 40, 10, 60), List.of(), vault, List.of(), null, List.of(), null, null,
                interior);
    }

    @Test
    void onAnInteriorTheVaultFrameGuardsTheVaultLineFramesHoldChambersAndTheSiegeFrameTheEntrance() {
        List<DerelictWalkerFrame> heavies = List.of(DerelictWalkerFrame.VAULT, DerelictWalkerFrame.LINE,
                DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
        int[][] posts = DerelictSecurity.interiorPosts(heavies, withInterior(List.of(new Area(70, 20, 79, 29),
                new Area(70, 70, 79, 79))));
        assertArrayEquals(new int[] {88, 50}, posts[0]);
        assertArrayEquals(new int[] {74, 24}, posts[1]);
        assertArrayEquals(new int[] {74, 74}, posts[2]);
        assertArrayEquals(new int[] {60, 49}, posts[3]);
    }

    @Test
    void groundWithNoInteriorLeavesTheRoutesPosts() {
        assertNull(DerelictSecurity.interiorPosts(List.of(DerelictWalkerFrame.LINE), null));
    }

    @Test
    void anInteriorZonesFlockIsHomedOnTheFirstOpenSkyCellTowardTheLanding() {
        int[] route = straight();
        DerelictSecurity.ZonePlan vaultZone = DerelictSecurity.zonesAlong(route, 3).get(0);
        int[] home = DerelictSecurity.openSkyHome(route, vaultZone, (x, y) -> x < 30);
        assertArrayEquals(new int[] {30, 50}, home);
        DerelictSecurity.ZonePlan outside = DerelictSecurity.zonesAlong(route, 3).get(2);
        assertEquals(outside.nodeX(), DerelictSecurity.openSkyHome(route, outside, (x, y) -> x < 30)[0]);
    }
}
