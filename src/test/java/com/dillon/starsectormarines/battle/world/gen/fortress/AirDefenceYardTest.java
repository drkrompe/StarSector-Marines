package com.dillon.starsectormarines.battle.world.gen.fortress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The yard's authored geometry: the aspect's control point, with a walkway that runs the length of the yard. */
class AirDefenceYardTest {

    private static final AirDefenceYard YARD = new AirDefenceYard(20, 20, 0, 1);

    private static Set<Long> footprint(int[] centre, int half) {
        Set<Long> cells = new HashSet<>();
        for (int x = centre[0] - half; x <= centre[0] + half; x++) {
            for (int y = centre[1] - half; y <= centre[1] + half; y++) cells.add(((long) x << 32) | (y & 0xFFFFFFFFL));
        }
        return cells;
    }

    @Test
    void everythingStandsInsideTheYardAndNothingOverlaps() {
        int[] floor = YARD.floor();
        List<Set<Long>> bodies = new ArrayList<>();
        bodies.add(footprint(YARD.consoleCentre(), 1));
        for (int[] flak : YARD.flakCells()) bodies.add(footprint(flak, 0));
        Set<Long> seen = new HashSet<>();
        for (Set<Long> body : bodies) {
            for (long cell : body) {
                int x = (int) (cell >> 32), y = (int) cell;
                assertTrue(x >= floor[0] && x < floor[0] + floor[2] && y >= floor[1] && y < floor[1] + floor[3],
                        "cell " + x + "," + y + " is inside the yard");
                assertTrue(seen.add(cell), "cell " + x + "," + y + " belongs to one body only");
            }
        }
    }

    @Test
    void aWalkwayRunsTheWholeLengthOfTheYardSoTheGateOpensOnFloor() {
        int[] floor = YARD.floor();
        Set<Long> occupied = new HashSet<>();
        occupied.addAll(footprint(YARD.consoleCentre(), 1));
        for (int[] flak : YARD.flakCells()) occupied.addAll(footprint(flak, 0));
        for (int y = floor[1]; y < floor[1] + floor[3]; y++) {
            long cell = ((long) YARD.centreX() << 32) | (y & 0xFFFFFFFFL);
            assertFalse(occupied.contains(cell), "the middle column is open at row " + y);
        }
    }
}
