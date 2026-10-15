package com.dillon.starsectormarines.battle.world.gen.fortress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The anti-air aspect's authored geometry: a small hardened control room, its console against the back wall, the flak outside the door. */
class AirDefenceYardTest {

    private static final AirDefenceYard SOUTH = new AirDefenceYard(20, 20, 0, 1);
    private static final AirDefenceYard NORTH = new AirDefenceYard(20, 20, 0, -1);

    private static boolean inside(int[] floor, int x, int y) {
        return x >= floor[0] && x < floor[0] + floor[2] && y >= floor[1] && y < floor[1] + floor[3];
    }

    @Test
    void theConsoleStandsInsideTheRoomAgainstTheWallOppositeTheDoor() {
        for (AirDefenceYard yard : new AirDefenceYard[]{SOUTH, NORTH}) {
            int[] floor = yard.floor();
            int[] console = yard.consoleCentre();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    assertTrue(inside(floor, console[0] + dx, console[1] + dy), "the 3x3 console is on the floor");
                }
            }
            // Against the back wall: the console's far edge is the floor's far row.
            int back = yard.gapDy() > 0 ? floor[1] : floor[1] + floor[3] - 1;
            assertEquals(back, console[1] - yard.gapDy(), "the console's back row is the room's back row");
        }
    }

    @Test
    void theDoorIsInTheWallOnTheNearSideAndTheFlakStandsOutsideBesideIt() {
        for (AirDefenceYard yard : new AirDefenceYard[]{SOUTH, NORTH}) {
            int[] floor = yard.floor();
            int[] door = yard.gapCell();
            assertFalse(inside(floor, door[0], door[1]), "the door is in the ring, not on the floor");
            assertEquals(1, Math.abs(door[1] - (yard.gapDy() > 0 ? floor[1] + floor[3] - 1 : floor[1])), "one step outside the floor");
            for (int[] flak : yard.flakCells()) {
                assertFalse(inside(floor, flak[0], flak[1]), "the flak is outside the room");
                assertTrue(Math.abs(flak[0] - door[0]) >= 2, "and clear of the door's own line");
                assertEquals(door[1] + yard.gapDy(), flak[1], "one step past the door's row");
            }
        }
    }

    @Test
    void theRoomIsAnAuthoredCommandRoomWithItsCrewAtTheBanks() {
        FortRoom room = SOUTH.room();
        assertTrue(room.main(), "the control room is the aspect's main room");
        assertEquals(AirDefenceYard.CREW, room.standCells().size());
        for (int[] stand : room.standCells()) assertTrue(inside(SOUTH.floor(), stand[0], stand[1]), "the crew stands on the floor");
    }

    @Test
    void aDoorOnAnEastWestWallIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new AirDefenceYard(20, 20, 1, 0));
    }
}
