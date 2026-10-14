package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt.Cell;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The belt's authored geometry: where it stands, what it leaves open, and that it is not a rectangle. */
class FortBeltTest {

    @Test
    void theBeltStandsOffTheWorksAcrossAGlacis() {
        for (Cell cell : FortBelt.cells()) {
            assertFalse(BatteryFort.isCleared(cell.x(), cell.y()), "belt cell on the works or glacis at " + cell.x() + "," + cell.y());
        }
    }

    @Test
    void theBeltIsOpenInFrontOfEachGate() {
        for (Cell cell : FortBelt.cells()) {
            boolean mainLane = cell.x() < BatteryFort.OX && Math.abs(cell.y() - (BatteryFort.OY + 17)) <= 3;
            boolean posternLane = cell.y() < BatteryFort.OY && Math.abs(cell.x() - (BatteryFort.OX + 23)) <= 3;
            // Only the nests' own pits may sit near a lane; the trench, bags and fence leave it open.
            if (cell.role() == Role.TRENCH || cell.role() == Role.FENCE) {
                assertFalse(mainLane || posternLane, cell.role() + " crosses a gate lane at " + cell.x() + "," + cell.y());
            }
        }
    }

    @Test
    void theBeltHasNestsAndAllThreeKindsOfWork() {
        assertEquals(4, FortBelt.nests().size());
        Set<Role> roles = new HashSet<>();
        for (Cell cell : FortBelt.cells()) roles.add(cell.role());
        assertTrue(roles.containsAll(Set.of(Role.TRENCH, Role.BAG, Role.FENCE, Role.EARTH, Role.NEST)));
    }

    @Test
    void theTraceIsIrregularNotARectangle() {
        // A rectangle's trench would have one x per vertical side; count how many distinct columns the western run uses.
        Set<Integer> columns = new HashSet<>();
        for (Cell cell : FortBelt.cells()) {
            if (cell.role() == Role.TRENCH && cell.x() < BatteryFort.OX - 4) columns.add(cell.x());
        }
        assertTrue(columns.size() >= 4, "the western side kinks through " + columns.size() + " columns");
    }

    @Test
    void theFenceIsBrokenIntoRunsSoThereAreFunnelsThroughIt() {
        Set<Long> fence = new HashSet<>();
        for (Cell cell : FortBelt.cells()) if (cell.role() == Role.FENCE) fence.add((long) cell.x() << 32 | cell.y());
        assertFalse(fence.isEmpty());
        Set<Long> seen = new HashSet<>();
        int runs = 0;
        for (long start : fence) {
            if (!seen.add(start)) continue;
            runs++;
            ArrayDeque<Long> open = new ArrayDeque<>(java.util.List.of(start));
            while (!open.isEmpty()) {
                long at = open.poll();
                int x = (int) (at >> 32), y = (int) at;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long next = (long) (x + d[0]) << 32 | ((y + d[1]) & 0xffffffffL);
                    if (fence.contains(next) && seen.add(next)) open.add(next);
                }
            }
        }
        assertTrue(runs >= 4, "the fence line is " + runs + " separate runs with gaps between them");
    }
    @Test
    void everyCellIsInsideTheFootprintAndNoneIsDrawnTwice() {
        Set<Long> seen = new HashSet<>();
        for (Cell cell : FortBelt.cells()) {
            assertTrue(cell.x() >= 0 && cell.y() >= 0 && cell.x() < BatteryFort.WIDTH && cell.y() < BatteryFort.HEIGHT);
            assertTrue(seen.add((long) cell.x() << 32 | cell.y()), "cell drawn twice at " + cell.x() + "," + cell.y());
        }
    }
}
