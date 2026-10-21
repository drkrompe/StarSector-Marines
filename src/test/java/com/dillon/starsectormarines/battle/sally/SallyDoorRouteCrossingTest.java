package com.dillon.starsectormarines.battle.sally;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A shut door drops every route that crosses it, and a straight leg between two waypoints can cross a door with neither
 * waypoint in it; and a door never shuts on a body overlapping its cells. Both are the geometry asked here.
 */
class SallyDoorRouteCrossingTest {

    private static final SallyDoor DOOR = new SallyDoor(new int[][]{{10, 3}, {10, 4}}, 9, 3, 11, 3, null, 0);

    @Test
    void theDoorsBoxIsItsCells() {
        assertArrayEquals(new float[]{10f, 3f, 11f, 5f}, SallyDoors.box(DOOR));
    }

    @Test
    void aBodyOverlappingTheDoorIsInIt() {
        float[] box = SallyDoors.box(DOOR);
        assertEquals(0f, SallyDoors.distanceToBox(box, 10.5f, 4f));
        assertEquals(0.3f, SallyDoors.distanceToBox(box, 9.7f, 4f), 1e-5f, "a body whose radius reaches past 0.3 is in it");
        assertEquals(5f, SallyDoors.distanceToBox(box, 10.5f, 10f), 1e-5f);
    }

    @Test
    void aLegAcrossTheDoorCrossesItWithNeitherEndInIt() {
        float[] box = SallyDoors.box(DOOR);
        assertTrue(SallyDoors.segmentNearBox(box, 4.5f, 4f, 16.5f, 4f, 0.5f), "straight through");
        assertTrue(SallyDoors.segmentNearBox(box, 4.5f, 0.5f, 16.5f, 7.5f, 0.5f), "slantwise through");
        assertFalse(SallyDoors.segmentNearBox(box, 4.5f, 8.5f, 16.5f, 8.5f, 0.5f), "past it, below");
        assertFalse(SallyDoors.segmentNearBox(box, 2f, 4f, 8f, 4f, 0.5f), "short of it");
    }
}
