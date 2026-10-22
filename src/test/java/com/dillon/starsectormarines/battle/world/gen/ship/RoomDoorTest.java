package com.dillon.starsectormarines.battle.world.gen.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomDoorTest {
    @Test void aCalledDoorOpensAtItsRateAndClosesWhenNobodyIsNear() {
        var door = new RoomDoor(true, 4f, 6f, 9.5f, false);
        door.advance(RoomDoor.TRAVEL_SECONDS / 2f, true);
        assertEquals(0.5f, door.openness(), 1e-5f);
        door.advance(RoomDoor.TRAVEL_SECONDS, true);
        assertEquals(1f, door.leafTravel());
        door.advance(RoomDoor.TRAVEL_SECONDS / 4f, false);
        assertEquals(0.75f, door.openness(), 1e-5f);
    }

    @Test void aLockedDoorNeverOpens() {
        var door = new RoomDoor(false, 2f, 3f, 7.5f, true);
        door.advance(10f, true);
        assertEquals(0f, door.openness());
    }

    @Test void geometryFollowsTheDoorwayCells() {
        var door = new RoomDoor(true, 4f, 6f, 9.5f, false);
        assertEquals(5f, door.centerX());
        assertEquals(9.5f, door.centerY());
        assertTrue(door.calledBy(5f, 8.5f), "In the doorway's line, a cell inside");
        assertTrue(door.calledBy(4.1f, 10.9f), "In line, approaching from the corridor");
        assertFalse(door.calledBy(3f, 8.5f), "Beside the door, behind the bulkhead");
        assertFalse(door.calledBy(5f, 12f), "Too far down the corridor");
        var upright = new RoomDoor(false, 2f, 3f, 7.5f, false);
        assertEquals(7.5f, upright.centerX());
        assertEquals(2.5f, upright.centerY());
    }
}
