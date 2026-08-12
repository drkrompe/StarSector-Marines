package com.dillon.starsectormarines.battle.setup;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingPadSelectorTest {

    @Test
    void authoredClearBerthsWinBeforeFallbackCells() {
        NavigationGrid grid = openGrid(30, 20);
        CellTopology topology = new CellTopology(30, 20);
        LandingPad near = LandingPad.civilian(8, 8, LandingPad.Approach.WEST);
        LandingPad far = LandingPad.civilian(22, 12, LandingPad.Approach.EAST);
        MapResult map = map(grid, topology, 4, 8, List.of(far, near));

        List<LandingPad> selected = LandingPadSelector.select(map, 2, 8);

        assertEquals(near, selected.get(0));
        assertEquals(far, selected.get(1));
        assertEquals(LandingPad.Approach.WEST, selected.get(0).approach);
    }

    @Test
    void blockedAuthoredBerthIsRejectedAndFilledByLegacySearch() {
        NavigationGrid grid = openGrid(24, 18);
        CellTopology topology = new CellTopology(24, 18);
        LandingPad blocked = LandingPad.civilian(10, 9, LandingPad.Approach.NORTH);
        grid.setWalkable(11, 9, false);
        MapResult map = map(grid, topology, 3, 3, List.of(blocked));

        List<LandingPad> selected = LandingPadSelector.select(map, 1, 8);

        assertEquals(1, selected.size());
        assertFalse(selected.get(0) == blocked);
        assertTrue(grid.isWalkable(selected.get(0).centerX, selected.get(0).centerY));
        assertEquals(0, selected.get(0).halfWidth);
    }

    private static NavigationGrid openGrid(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static MapResult map(NavigationGrid grid, CellTopology topology,
                                 int spawnX, int spawnY, List<LandingPad> pads) {
        return new MapResult(grid, topology, spawnX, spawnY, 20, 10,
                Collections.emptyList(), Collections.emptyList(),
                new TacticalMap(Collections.emptyList()), Buildings.EMPTY,
                Collections.emptyList(), RoadGraph.EMPTY, pads);
    }
}
