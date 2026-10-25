package com.dillon.starsectormarines.battle.setpiece;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.MainStreet;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreetMouthsTest {

    /**
     * A main street along row 15 from x = 5 to x = 34, three wide, with a pavement two cells deep each side, a
     * cross street running north from it at x = 19..21, and a shallow alcove at x = 10 too short to be a way in.
     */
    private static NavigationGrid street() {
        NavigationGrid grid = new NavigationGrid(40, 30);
        for (int x = 5; x <= 34; x++) for (int y = 12; y <= 18; y++) grid.setWalkableFloor(x, y);
        for (int x = 19; x <= 21; x++) for (int y = 0; y < 12; y++) grid.setWalkableFloor(x, y);
        for (int y = 8; y < 12; y++) grid.setWalkableFloor(10, y);
        return grid;
    }

    private static final MainStreet STREET = new MainStreet(5.5f, 15.5f, 34.5f, 15.5f, 3, 0, 0, 0, 0, -1, -1);

    @Test
    void aCrossStreetIsOneWayInAtItsMiddle() {
        List<StreetMouths.Mouth> mouths = StreetMouths.find(street(), new CellTopology(40, 30), STREET, 8);
        assertEquals(List.of(new StreetMouths.Mouth(20, 13, 0, -1, 20, 15)), mouths);
    }

    @Test
    void aGapIntoABuildingIsNoWayIn() {
        NavigationGrid grid = street();
        CellTopology topology = new CellTopology(40, 30);
        for (int x = 19; x <= 21; x++) topology.setGroundKind(x, 6, CellTopology.GroundKind.INDOOR);
        assertEquals(List.of(), StreetMouths.find(grid, topology, STREET, 8));
    }
}
