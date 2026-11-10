package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A building its builder marked with a roof kind wears that kind's roof, ahead of what its kind would give it. */
class RoofStyleTest {

    @Test
    void aMarkedRoofKindDecidesTheRoof() {
        CellTopology topology = new CellTopology(6, 6);
        Building b = new Building(1, BuildingKind.FORTIFIED, 1, 2, 1, 2,
                new int[]{1, 2, 1, 2}, new int[]{1, 1, 2, 2}, 1f, 1f, 1f);
        assertEquals(RoofStyle.PLATE, RoofStyle.of(b, topology));
        topology.setRoofKind(2, 2, CellTopology.RoofKind.HANGAR);
        assertEquals(RoofStyle.QUONSET, RoofStyle.of(b, topology));
        topology.setRoofKind(2, 2, CellTopology.RoofKind.HUT);
        assertEquals(RoofStyle.BARRACKS, RoofStyle.of(b, topology));
        topology.setRoofKind(2, 2, CellTopology.RoofKind.HULL);
        assertEquals(RoofStyle.HULL, RoofStyle.of(b, topology));
        topology.setRoofKind(2, 2, null);
        assertEquals(RoofStyle.PLATE, RoofStyle.of(b, topology));
    }
}
