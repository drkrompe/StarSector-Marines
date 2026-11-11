package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.PortFreighter;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A port's lighter stands ({@link LandingPad.Purpose#PORT_LIGHTER}): the planet's traffic stands on them, so no
 * company lands there, and setup parks on each the largest ship its berth fits.
 */
class PortLighterStandTest {


    @Test
    void noCompanyLandsOnALighterStand() {
        // The stand is nearer the company than anything else on the map, and still never taken.
        LandingPad stand = new LandingPad(12, 12, 1, 1, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        MapResult map = map(List.of(stand));
        List<LandingPad> chosen = LandingPadSelector.select(map, 2, 4);
        assertEquals(2, chosen.size());
        for (LandingPad pad : chosen) assertFalse(pad.purpose == LandingPad.Purpose.PORT_LIGHTER);
    }

    @Test
    void aStandHoldsTheLargestShipItsBerthFits() {
        assertEquals(PortFreighter.COLOSSUS, PortFreighter.forBerth(25, 17));
        assertEquals(PortFreighter.HOUND, PortFreighter.forBerth(7, 7));
        assertEquals(PortFreighter.SHUTTLE, PortFreighter.forBerth(3, 3));
        assertNull(PortFreighter.forBerth(1, 1));
        // A berth as long as a Colossus but not as broad holds the courier.
        assertEquals(PortFreighter.HOUND, PortFreighter.forBerth(25, 7));
    }

    @Test
    void eachStandParksItsShipClosingTheHullItDraws() {
        // Approach east: a berth's long side runs along x.
        LandingPad heavy = new LandingPad(12, 6, 9, 7, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        LandingPad courier = new LandingPad(30, 6, 3, 3, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        LandingPad pod = new LandingPad(30, 18, 1, 1, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        LandingPad tooSmall = new LandingPad(10, 18, 0, 0, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        MapResult map = map(List.of(heavy, courier, pod, tooSmall));
        List<ParkedAircraft> parked = BattleSetup.stampParkedAircraft(map, List.of(), new Random(3L));
        assertEquals(List.of(PortFreighter.COLOSSUS, PortFreighter.HOUND, PortFreighter.SHUTTLE),
                parked.stream().map(p -> p.type).toList());
        // A Colossus closes most of its length and beam, its wings and nose overhanging open ground.
        assertFalse(map.grid.isWalkable(12 + 6, 6));
        assertTrue(map.grid.isWalkable(12 + 8, 6));
        assertFalse(map.grid.isWalkable(12, 6 + 4));
        assertTrue(map.grid.isWalkable(12, 6 + 6));
        // A courier closes three cells along; a pod only the cell it stands on.
        assertFalse(map.grid.isWalkable(31, 6));
        assertTrue(map.grid.isWalkable(33, 6));
        assertFalse(map.grid.isWalkable(30, 18));
        assertTrue(map.grid.isWalkable(31, 18));
        assertTrue(map.grid.isWalkable(10, 18), "nothing stands on a berth too small for any ship");
    }

    private static MapResult map(List<LandingPad> pads) {
        NavigationGrid grid = new NavigationGrid(40, 24);
        CellTopology topology = new CellTopology(40, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 40; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, CellTopology.GroundKind.STREET);
            }
        }
        return new MapResult(grid, topology, 10, 12, 30, 12,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, pads);
    }
}
