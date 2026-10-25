package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BorePressureTest {

    /** A street one cell wide along row 2, from x = 0 to x = 39. */
    private static NavigationGrid street() {
        NavigationGrid grid = new NavigationGrid(40, 5);
        for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, 2);
        return grid;
    }

    @Test
    void theBoreOpensAsFarFromTheYardAsTheWalkAllows() {
        // From the gate at x = 10: the street runs 10 steps west and 20 east within reach; east is farther.
        assertArrayEquals(new int[]{30, 2}, BorePressure.site(street(), new CellTopology(40, 5), 10, 2, 5, 20, 10, 2));
    }

    @Test
    void theWalkStaysOutOfBuildings() {
        CellTopology topology = new CellTopology(40, 5);
        topology.setBuildingKindHint(14, 2, BuildingKind.RESIDENTIAL);
        assertArrayEquals(new int[]{0, 2}, BorePressure.site(street(), topology, 10, 2, 5, 20, 10, 2));
    }

    @Test
    void theWalkStaysOffIndoorFloor() {
        CellTopology topology = new CellTopology(40, 5);
        topology.setGroundKind(14, 2, CellTopology.GroundKind.INDOOR);
        assertArrayEquals(new int[]{0, 2}, BorePressure.site(street(), topology, 10, 2, 5, 20, 10, 2));
    }

    @Test
    void aLaneStartsOutInTheStreetBeyondItsWayIn() {
        List<int[]> lane = BorePressure.lane(street(), 10, 2, 1f, 0f, 3, 2);
        assertArrayEquals(new int[]{16, 2}, lane.get(0));
        assertArrayEquals(new int[]{10, 2}, lane.get(1));
        assertArrayEquals(new int[]{3, 2}, lane.get(2));
    }

    @Test
    void noStreetLongEnoughMeansNoBore() {
        CellTopology topology = new CellTopology(40, 5);
        topology.setBuildingKindHint(7, 2, BuildingKind.RESIDENTIAL);
        topology.setBuildingKindHint(13, 2, BuildingKind.RESIDENTIAL);
        assertNull(BorePressure.site(street(), topology, 10, 2, 5, 20, 10, 2));
    }
}
