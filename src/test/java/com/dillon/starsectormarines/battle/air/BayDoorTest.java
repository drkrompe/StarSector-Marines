package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BayDoorTest {
    /** A door on a bay's east wall at x = 20, opening east. */
    private final BayAperture east = new BayAperture(3, 20.5f, 10.5f, 1, 0, 8);

    @Test void aDepartingBoatHoldsTheDoorUntilItIsClearOutside() {
        var door = new BayDoor(east);
        assertTrue(door.calledBy(east, false, 12f, 10.5f), "Still at its berth inboard");
        assertTrue(door.calledBy(east, false, 26f, 10.5f), "Just outside the hull");
        assertFalse(door.calledBy(east, false, 30f, 10.5f), "Clear of the ship");
    }

    @Test void aReturningBoatOpensItOnApproachAndLetsItCloseOnceInside() {
        var door = new BayDoor(east);
        assertFalse(door.calledBy(east, true, 40f, 10.5f), "Still far out");
        assertTrue(door.calledBy(east, true, 30f, 10.5f), "On approach");
        assertTrue(door.calledBy(east, true, 19f, 10.5f), "Through the opening");
        assertFalse(door.calledBy(east, true, 15f, 10.5f), "Inside, rolling to its berth");
    }

    @Test void onlyBoatsUsingThisOpeningCallIt() {
        var door = new BayDoor(east);
        assertFalse(door.calledBy(new BayAperture(4, 20.5f, 30.5f, 1, 0, 8), false, 20f, 10.5f));
        assertFalse(door.calledBy(null, false, 20f, 10.5f));
    }

    @Test void theLeavesTravelAtAFixedRateAndReverseFromWhereTheyStand() {
        var door = new BayDoor(east);
        door.advance(BayDoor.TRAVEL_SECONDS / 2f, true);
        assertEquals(0.5f, door.openness(), 1e-5f);
        door.advance(BayDoor.TRAVEL_SECONDS / 4f, false);
        assertEquals(0.25f, door.openness(), 1e-5f);
        door.advance(BayDoor.TRAVEL_SECONDS * 3f, true);
        assertEquals(1f, door.openness());
        assertEquals(1f, door.leafTravel());
        door.advance(BayDoor.TRAVEL_SECONDS * 3f, false);
        assertEquals(0f, door.leafTravel());
    }
}
