package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand-built trenches on a 100x40 field with 8-cell blocks: the registry's mapping, not its use. */
class GarrisonableRegistryTest {

    private static final int W = 100;
    private static final int H = 40;
    private static final int BLOCK = 8;

    private final List<Doodad> props = new ArrayList<>();

    private void trench(int fromX, int toX, int y) {
        for (int x = fromX; x <= toX; x++) {
            DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                    1, 1, null, 0, null, FightingPositionRole.TRENCH);
            props.add(new Doodad(x, y, def));
        }
    }

    private FightingPositionIndex index() {
        return FightingPositionIndex.build(W, H, props, (x, y) -> true);
    }

    @Test
    void aTrenchIsOneGarrisonableWithItsCentreRadiusAndBlocks() {
        trench(10, 25, 12);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(1, registry.count());
        assertEquals(16, registry.size(0));
        assertEquals(17, registry.centreX(0), "the run cell nearest its centroid (17.5): the lower on a tie");
        assertEquals(12, registry.centreY(0));
        assertEquals(GarrisonableRegistry.MAX_RADIUS_CELLS - 2, registry.radius(0),
                "a circle that reaches the far end, 8 cells away, inside the cap");
        // x 10..25 is blocks 1..3 on row 1 (y 12 / 8); block row width is ceil(100/8) = 13
        assertArrayEquals(new int[]{13 + 1, 13 + 2, 13 + 3}, registry.coveredBlocks(0));
        assertEquals(-1, registry.fortId(0));
        assertEquals(0, registry.idAt(20, 12));
        assertEquals(-1, registry.idAt(20, 13));
    }

    @Test
    void aShortRunGetsTheMinimumRadiusAndALongOneIsCapped() {
        trench(10, 21, 12);
        trench(10, 70, 30);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0], false);

        assertEquals(2, registry.count());
        assertEquals(GarrisonableRegistry.MIN_RADIUS_CELLS + 2, registry.radius(0));
        assertEquals(GarrisonableRegistry.MAX_RADIUS_CELLS, registry.radius(1));
    }

    @Test
    void theBlockLookupWakesOnlyGarrisonablesWithinReach() {
        trench(10, 25, 4);
        trench(70, 85, 36);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);
        assertEquals(2, registry.count());

        assertArrayEquals(new int[]{0}, registry.near(2, 0), "a block holding the first run");
        assertArrayEquals(new int[]{0}, registry.near(3 + GarrisonableRegistry.REACH_BLOCKS, 0),
                "and one within reach of its last covered block");
        assertEquals(0, registry.near(7, 0).length, "beyond reach of every run wakes nothing");
        assertEquals(0, registry.near(2, 4).length, "four block rows from the first run, and the second is further along");
        assertArrayEquals(new int[]{1}, registry.near(10, 4));
        assertEquals(0, registry.near(-1, 0).length, "off the lattice is empty, not an error");
        assertEquals(0, registry.near(0, 40).length);
    }

    @Test
    void reachCanWakeSeveralGarrisonablesInAscendingOrder() {
        trench(10, 25, 12);
        trench(30, 45, 12);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertArrayEquals(new int[]{0, 1}, registry.near(3, 1));
    }

    @Test
    void aGarrisonableIsAttributedToTheNearestFortInReachElseNone() {
        trench(10, 25, 12);
        trench(70, 85, 30);
        int[] forts = {4, 20, 20, 7, 30, 20};
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, forts);

        assertEquals(4, registry.fortId(0), "nearer fort 4 over fort 7");
        assertEquals(-1, registry.fortId(1), "more than the belt reach from any fort");
    }

    @Test
    void theRegistryIsBuiltFromExactlyOneIndexInstance() {
        trench(10, 25, 12);
        FightingPositionIndex first = index();
        GarrisonableRegistry registry = GarrisonableRegistry.build(first, W, H, BLOCK, new int[0]);

        assertTrue(registry.isBuiltFrom(first));
        assertSame(first, registry.source());
        assertFalse(registry.isBuiltFrom(index()), "an equal index rebuilt is a new instance and asks for a new registry");
    }

    @Test
    void aLongRunIsCutIntoEvenConnectedStretchesThatChainAlongIt() {
        trench(10, 73, 12);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(4, registry.count(), "64 cells along: four stretches of sixteen");
        for (int g = 0; g < 4; g++) {
            assertEquals(16, registry.size(g));
            assertEquals(0, registry.runOf(g), "every stretch is part of the one run");
            assertTrue(registry.radius(g) <= 9, "a stretch's circle reaches both its ends");
        }
        int[] order = {registry.idAt(10, 12), registry.idAt(26, 12), registry.idAt(42, 12), registry.idAt(58, 12)};
        assertEquals(order[0], registry.idAt(25, 12));
        assertEquals(order[3], registry.idAt(73, 12));
        assertArrayEquals(new int[]{order[1]}, registry.neighbours(order[0]));
        int[] middle = registry.neighbours(order[1]);
        assertEquals(2, middle.length);
        assertTrue(contains(middle, order[0]) && contains(middle, order[2]));
        for (int g = 0; g < 4; g++) {
            for (int i = 0; i < registry.size(g); i++) {
                assertEquals(g, registry.idAt(registry.cellX(g, i), registry.cellY(g, i)));
            }
        }
    }

    @Test
    void aRunRoundACornerIsCutAlongItsLengthNotAcrossTheMap() {
        trench(10, 33, 4);      // 24 cells east...
        for (int y = 5; y <= 28; y++) cell(33, y);   // ...then 24 south from the corner
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(3, registry.count(), "48 cells along: three stretches");
        assertTrue(registry.idAt(10, 4) != registry.idAt(33, 28), "the two ends are different stretches");
        int corner = registry.idAt(33, 4);
        assertEquals(corner, registry.idAt(33, 12), "the middle stretch takes the corner, round it");
        assertEquals(corner, registry.idAt(27, 4));
        assertTrue(registry.idAt(25, 4) != corner && registry.idAt(33, 13) != corner);
    }

    @Test
    void aRingRoundTheWorksIsNeverOneStretchOnTwoSides() {
        // A square ring 30 on a side: x 10..39, y 5..34 (116 cells).
        trench(10, 39, 5);
        trench(10, 39, 34);
        for (int y = 6; y <= 33; y++) {
            cell(10, y);
            cell(39, y);
        }
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertTrue(registry.count() >= 6, "a ring 116 cells round is cut into several stretches");
        int total = 0;
        for (int g = 0; g < registry.count(); g++) {
            total += registry.size(g);
            assertTrue(registry.size(g) >= GarrisonableRegistry.MIN_STRETCH_CELLS);
            assertTrue(connected(registry, g), "stretch " + g + " is one connected piece");
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int i = 0; i < registry.size(g); i++) {
                minX = Math.min(minX, registry.cellX(g, i));
                maxX = Math.max(maxX, registry.cellX(g, i));
                minY = Math.min(minY, registry.cellY(g, i));
                maxY = Math.max(maxY, registry.cellY(g, i));
            }
            assertTrue(maxX - minX < 29 || maxY - minY < 29, "stretch " + g + " does not span the ring");
            assertTrue(registry.neighbours(g).length == 2, "every stretch of a ring has a neighbour either side");
        }
        assertEquals(116, total, "the stretches partition the run");
    }

    @Test
    void withStretchesOffEveryRunIsOneGarrisonableWithTheRunsId() {
        trench(10, 73, 12);
        trench(10, 25, 30);
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0], false);

        assertEquals(2, registry.count());
        assertFalse(registry.stretched());
        assertEquals(64, registry.size(0));
        assertEquals(0, registry.runOf(0));
        assertEquals(1, registry.runOf(1));
        assertEquals(0, registry.neighbours(0).length);
        assertEquals(0, registry.idAt(50, 12));
        assertEquals(1, registry.idAt(20, 30));
    }

    private void cell(int x, int y) {
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        props.add(new Doodad(x, y, def));
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) if (v == value) return true;
        return false;
    }

    private static boolean connected(GarrisonableRegistry registry, int g) {
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < registry.size(g); i++) cells.add(((long) registry.cellX(g, i) << 32) | registry.cellY(g, i));
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new long[]{registry.cellX(g, 0), registry.cellY(g, 0)});
        seen.add(((long) registry.cellX(g, 0) << 32) | registry.cellY(g, 0));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            long[] c = queue.poll();
            for (int[] d : steps) {
                long key = ((c[0] + d[0]) << 32) | (c[1] + d[1]);
                if (cells.contains(key) && seen.add(key)) queue.add(new long[]{c[0] + d[0], c[1] + d[1]});
            }
        }
        return seen.size() == cells.size();
    }

    @Test
    void aMapWithNoPreparedGroundHasNoGarrisonables() {
        GarrisonableRegistry registry = GarrisonableRegistry.build(index(), W, H, BLOCK, new int[0]);

        assertEquals(0, registry.count());
        assertEquals(0, registry.near(3, 1).length);
    }
}
