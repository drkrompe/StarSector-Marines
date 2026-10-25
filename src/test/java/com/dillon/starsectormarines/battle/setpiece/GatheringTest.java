package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatheringTest {

    @Test
    void peopleStandApartOnOpenGroundAndNeverInADoorway() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int x = 0; x < 12; x++) for (int y = 0; y < 12; y++) grid.setWalkableFloor(x, y);
        grid.setDoorway(6, 6, true);
        CellTopology topology = new CellTopology(12, 12);
        topology.setGroundKind(7, 6, CellTopology.GroundKind.INDOOR);
        List<int[]> places = Gathering.placesAround(grid, topology, 6, 6, 6);
        assertEquals(6, places.size());
        for (int i = 0; i < places.size(); i++) {
            int[] p = places.get(i);
            assertFalse(p[0] == 6 && p[1] == 6, "not in the doorway");
            assertFalse(p[0] == 7 && p[1] == 6, "not indoors");
            for (int j = 0; j < i; j++) {
                int[] q = places.get(j);
                assertTrue(Math.max(Math.abs(p[0] - q[0]), Math.abs(p[1] - q[1])) >= 2, "no two touching");
            }
        }
    }
}
