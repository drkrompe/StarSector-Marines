package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryRowTest {

    @Test
    void everyPoseLaysTheWholeFootprintOnceAndOpensBothGatesOutward() {
        for (int depth : FactoryRow.DEPTHS) for (int length : FactoryRow.LENGTHS) {
            for (int turn = 0; turn < 4; turn++) {
                FactoryRow.Pose pose = new FactoryRow.Pose(10, 20, turn, length, depth);
                Set<Long> cells = new HashSet<>();
                for (int y = 0; y < depth; y++) {
                    for (int x = 0; x < length; x++) {
                        int[] c = pose.cell(x, y);
                        assertTrue(c[0] >= 10 && c[0] < 10 + pose.width() && c[1] >= 20 && c[1] < 20 + pose.height(),
                                length + "/" + turn + " put " + x + "," + y + " outside its footprint");
                        cells.add(key(c));
                    }
                }
                assertEquals(length * depth, cells.size(), length + "/" + turn + " laid a cell twice");
                assertFalse(cells.contains(key(pose.cell(-1, FactoryRow.CENTRE))), length + "/" + turn + " gate A inward");
                assertFalse(cells.contains(key(pose.cell(length, FactoryRow.CENTRE))), length + "/" + turn + " gate B inward");
            }
        }
    }

    @Test
    void theLaneRunsClearFromGateToGateWithTheCoverOffIt() {
        // A fight can come down the lane from either end: nothing stands in it, and both ends are open gates.
        for (int length : FactoryRow.LENGTHS) {
            for (int y = FactoryRow.HALL; y < FactoryRow.POCKET; y++) {
                assertTrue(FactoryRow.gate(length, 0, y) && FactoryRow.gate(length, length - 1, y));
                for (int x = 0; x < length; x++) {
                    assertNull(FactoryRow.pocket(length, x, y), "prop in the lane at " + x + "," + y);
                    assertFalse(FactoryRow.vehicle(length, x, y), "vehicle in the lane at " + x + "," + y);
                    assertFalse(FactoryRow.wall(length, x, y), "wall in the lane at " + x + "," + y);
                }
            }
            for (int y = FactoryRow.POCKET; y < FactoryRow.DEPTH; y++) {
                for (int x = 0; x < length; x++) {
                    assertFalse(FactoryRow.gate(length, x, y), "gate beside the pockets at " + x + "," + y);
                    boolean stood = FactoryRow.pocket(length, x, y) != null || FactoryRow.vehicle(length, x, y);
                    assertFalse(stood && FactoryRow.wall(length, x, y), "cover in the wall at " + x + "," + y);
                    // Mirrored end to end, so the lane reads the same from either gate.
                    assertEquals(FactoryRow.pocket(length, x, y), FactoryRow.pocket(length, length - 1 - x, y));
                }
            }
        }
    }

    @Test
    void theShallowRowClosesItsFarWallWithMachineryAndKeepsTheLaneClear() {
        int depth = FactoryRow.SHALLOW;
        for (int length : FactoryRow.LENGTHS) {
            for (int y = FactoryRow.HALL; y < FactoryRow.POCKET; y++) {
                for (int x = 0; x < length; x++) {
                    assertFalse(FactoryRow.wall(length, depth, x, y), "wall in the lane at " + x + "," + y);
                    assertNull(FactoryRow.machinery(length, depth, x, y), "machinery in the lane at " + x + "," + y);
                }
            }
            int machines = 0;
            for (int x = 0; x < length; x++) {
                String machine = FactoryRow.machinery(length, depth, x, depth - 1);
                // Every far-wall cell is wall or machinery, never both and never open.
                assertTrue(FactoryRow.wall(length, depth, x, depth - 1) != (machine != null), "far wall open at " + x);
                assertEquals(machine, FactoryRow.machinery(length, depth, length - 1 - x, depth - 1));
                if (machine != null) machines++;
            }
            assertTrue(machines >= 2, "no machinery at length " + length);
            assertTrue(FactoryRow.wall(length, depth, 0, depth - 1) && FactoryRow.wall(length, depth, length - 1, depth - 1));
        }
        // The full row keeps its machinery in the pockets, not in its wall.
        for (int x = 0; x < 20; x++) assertNull(FactoryRow.machinery(20, FactoryRow.DEPTH, x, FactoryRow.DEPTH - 1));
    }

    @Test
    void aLotHoldsARowWhereItIsAsDeepAsTheShallowRow() {
        int shortest = FactoryRow.LENGTHS[FactoryRow.LENGTHS.length - 1];
        assertTrue(FactoryRow.fits(shortest, FactoryRow.SHALLOW));
        assertTrue(FactoryRow.fits(FactoryRow.SHALLOW, shortest));
        assertFalse(FactoryRow.fits(shortest, FactoryRow.SHALLOW - 1));
        assertFalse(FactoryRow.fits(shortest - 1, FactoryRow.DEPTH));
    }

    @Test
    void theWorkshopsFillTheLengthSharingTheirPartyWalls() {
        for (int length : FactoryRow.LENGTHS) {
            int[][] halls = FactoryRow.halls(length);
            assertTrue(halls.length == 2 || halls.length == 3);
            assertEquals(0, halls[0][0]);
            for (int i = 0; i < halls.length; i++) {
                assertTrue(halls[i][1] - halls[i][0] + 1 >= 6, "a workshop too narrow to hold a room at length " + length);
                if (i > 0) assertEquals(halls[i - 1][1], halls[i][0], "workshops not sharing a wall at length " + length);
            }
            assertEquals(length - 1, halls[halls.length - 1][1]);
            if (halls.length == 3) assertEquals(halls[0][1] - halls[0][0], halls[2][1] - halls[2][0]);
        }
    }

    private static long key(int[] c) {
        return ((long) c[0] << 32) | (c[1] & 0xffffffffL);
    }
}
