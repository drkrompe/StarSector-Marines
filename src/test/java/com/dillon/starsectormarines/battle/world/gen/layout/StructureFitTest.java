package com.dillon.starsectormarines.battle.world.gen.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFitTest {

    @Test
    void aFootprintFitsOnlyWhereEveryCellIsGround() {
        LineCourse.CellTest ground = (x, y) -> !(x == 5 && y == 5);
        assertTrue(StructureFit.fits(ground, 0, 0, 5, 5));
        assertFalse(StructureFit.fits(ground, 1, 1, 5, 5));
    }

    @Test
    void theNearestFitToThePointIsTaken() {
        // Open ground everywhere but a pond at x 10..14.
        LineCourse.CellTest ground = (x, y) -> x < 10 || x > 14;
        int[] at = StructureFit.nearest(ground, new int[]{0, 0, 40, 20}, 5, 5, 12, 10, 1);
        // Either side of the pond is as near; the scan meets the west side first.
        assertArrayEquals(new int[]{5, 8}, at);
        assertNull(StructureFit.nearest((x, y) -> false, new int[]{0, 0, 40, 20}, 5, 5, 12, 10, 1));
    }

    @Test
    void aTurnedStructureSwapsItsSides() {
        Structure farm = new Structure("farm", StructureKind.FARMSTEAD, 0, 0, 1, false);
        assertEquals(StructureKind.FARMSTEAD.deep, farm.width());
        assertEquals(StructureKind.FARMSTEAD.along, farm.height());
        assertEquals(3, new Structure("op", StructureKind.OBSERVATION_POST, 0, 0, -1, false).turns());
    }

    @Test
    void aFarmsteadsFootprintHoldsItsHouseAndBarnAndYard() {
        assertEquals(2 * StructureFit.YARD + StructureFit.HOUSE_ALONG + StructureFit.BETWEEN + StructureFit.BARN_ALONG,
                StructureKind.FARMSTEAD.along);
        assertEquals(2 * StructureFit.YARD + Math.max(StructureFit.HOUSE_DEEP, StructureFit.BARN_DEEP),
                StructureKind.FARMSTEAD.deep);
    }

    @Test
    void sandbagsMustFaceSomewhere() {
        assertThrows(IllegalArgumentException.class,
                () -> new Block("bags", List.of(new int[]{1, 1}), BlockKind.SANDBAG, null, false));
        assertEquals(1, new Block("wreck", List.of(new int[]{1, 1}), BlockKind.WRECK, null, false).cells().size());
    }
}
