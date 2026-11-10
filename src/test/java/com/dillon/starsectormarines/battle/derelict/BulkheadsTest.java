package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkheadsTest {

    private static final RuinsDelve.Area HALL = new RuinsDelve.Area(1, 1, 8, 4);

    /**
     * A hall (x 1..8, y 1..4) and a room (x 1..8, y 6..9) either side of a one-cell wall at y 5. {@code round}
     * joins them: 0 not at all, 1 by a short column at x 9, 2 by the long way round the east end.
     */
    private static Bulkheads.Ground ground(int round) {
        return new Bulkheads.Ground() {
            public int width() { return 30; }

            public int height() { return 12; }

            public boolean floor(int x, int y) {
                if (x >= 1 && x <= 8 && ((y >= 1 && y <= 4) || (y >= 6 && y <= 9))) return true;
                if (round == 1) return x == 9 && y >= 1 && y <= 9;
                if (round == 2) return (y == 1 && x >= 9 && x <= 28) || (x == 28 && y >= 1 && y <= 9)
                        || (y == 9 && x >= 9 && x <= 28);
                return false;
            }

            public boolean wall(int x, int y) {
                return x >= 0 && y >= 0 && x < 30 && y < 12 && !floor(x, y);
            }
        };
    }

    @Test
    void aBulkheadIsCutThroughTheWallToFloorThatIsALongWalkRound() {
        List<Bulkheads.Bulkhead> laid = Bulkheads.lay(ground(2), List.of(HALL), List.of());
        assertEquals(1, laid.size());
        Bulkheads.Bulkhead b = laid.get(0);
        assertEquals(Bulkheads.WIDTH, b.cells().length, "two cells through a one-cell wall");
        for (int[] c : b.cells()) assertEquals(5, c[1], "in the wall between");
        assertTrue(b.door().alongX());
        assertEquals(RuinsDelve.DoorStance.ANSWERS, b.door().stance());
        assertTrue(HALL.contains(b.likeX(), b.likeY()), "the opened cells take the hall's floor");
    }

    @Test
    void noBulkheadWhereTheWalkRoundIsShortOrWhereItWouldOpenIntoSealedFloor() {
        assertTrue(Bulkheads.lay(ground(1), List.of(HALL), List.of()).isEmpty(), "the way round is already short");
        assertTrue(Bulkheads.lay(ground(0), List.of(HALL), List.of()).isEmpty(), "the room is sealed");
    }

    @Test
    void noBulkheadBesideALeafDoor() {
        RuinsDelve.LeafDoor door = new RuinsDelve.LeafDoor(new RuinsDelve.Area(1, 5, 8, 5), true, false,
                RuinsDelve.DoorStance.DEAD);
        assertTrue(Bulkheads.lay(ground(2), List.of(HALL), List.of(door)).isEmpty());
    }
}
