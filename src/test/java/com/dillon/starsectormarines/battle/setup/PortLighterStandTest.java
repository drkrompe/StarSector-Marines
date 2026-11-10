package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ParkedAircraft;
import com.dillon.starsectormarines.battle.air.ShuttleType;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A port's lighter stands ({@link LandingPad.Purpose#PORT_LIGHTER}): the planet's traffic stands on them, so no
 * company lands there, and setup parks a freighter on each.
 */
class PortLighterStandTest {

    private static final Set<ShuttleType> FREIGHTERS = Set.of(ShuttleType.BUFFALO, ShuttleType.MULE,
            ShuttleType.TARSUS);

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
    void aFreighterStandsOnEveryLighterStand() {
        LandingPad a = new LandingPad(20, 6, 1, 1, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        LandingPad b = new LandingPad(20, 18, 1, 1, LandingPad.Approach.EAST, LandingPad.Purpose.PORT_LIGHTER);
        MapResult map = map(List.of(a, b));
        List<ParkedAircraft> parked = BattleSetup.stampParkedAircraft(map, List.of(), new Random(3L));
        assertEquals(2, parked.size());
        for (ParkedAircraft craft : parked) {
            assertTrue(FREIGHTERS.contains(craft.type), "a lighter is a freighter: " + craft.type);
            assertFalse(map.grid.isWalkable(craft.centerX, craft.centerY), "its hull stands on its stand");
        }
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
