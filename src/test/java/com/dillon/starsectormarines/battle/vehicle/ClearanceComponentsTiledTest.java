package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tiled labelling against the whole-map flood it replaced. The two must
 * partition the mask identically — not merely agree on sampled pairs — on
 * masks that cross tile seams every way a city can, and a catch-up that
 * relabels only the tiles a change touched must still land on the flood's
 * answer whether the change split a component or joined two.
 */
class ClearanceComponentsTiledTest {

    private static final int RADIUS = 1;

    /** Rectangular block of walkable floor [x0..x1] x [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
        }
    }

    /**
     * Asserts the tiled labels and the flood labels are the same partition:
     * a bijection between the two label sets over every cell on the mask, and
     * {@link ClearanceComponents#NONE} on exactly the same cells.
     */
    private static void assertSamePartition(NavigationGrid grid, VehicleClearance mask,
                                            ClearanceComponents tiled) {
        int[] flood = ClearanceComponents.floodOf(grid, mask);
        int w = mask.getWidth();
        int h = mask.getHeight();
        int floodComponents = Arrays.stream(flood).max().orElse(-1) + 1;
        int[] floodToTiled = new int[floodComponents];
        int[] tiledToFlood = new int[tiled.componentCount()];
        Arrays.fill(floodToTiled, ClearanceComponents.NONE);
        Arrays.fill(tiledToFlood, ClearanceComponents.NONE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int f = flood[y * w + x];
                int t = tiled.labelAt(x, y);
                if (f == ClearanceComponents.NONE || t == ClearanceComponents.NONE) {
                    assertEquals(f, t, "mask membership differs at (" + x + "," + y + ")");
                    continue;
                }
                if (floodToTiled[f] == ClearanceComponents.NONE) floodToTiled[f] = t;
                if (tiledToFlood[t] == ClearanceComponents.NONE) tiledToFlood[t] = f;
                assertEquals(floodToTiled[f], t, "flood component " + f
                        + " maps to two tiled labels at (" + x + "," + y + ")");
                assertEquals(tiledToFlood[t], f, "tiled component " + t
                        + " maps to two flood labels at (" + x + "," + y + ")");
            }
        }
        assertEquals(floodComponents, tiled.componentCount());
    }

    @Test
    void aRandomCityAgreesWithTheFloodAcrossEverySeam() {
        // 100x70 is four tiles by three with ragged edges, so seams fall
        // mid-map both ways and the last tiles are partial.
        NavigationGrid grid = new NavigationGrid(100, 70);
        Random random = new Random(7);
        for (int y = 0; y < 70; y++) {
            for (int x = 0; x < 100; x++) {
                if (random.nextFloat() < 0.72f) grid.setWalkableFloor(x, y);
            }
        }
        for (int i = 0; i < 300; i++) {
            grid.setSharedEdgePassable(random.nextInt(100), random.nextInt(70),
                    Direction.CARDINALS[random.nextInt(4)], false);
        }
        VehicleClearance mask = VehicleClearance.erode(grid, RADIUS);

        ClearanceComponents tiled = ClearanceComponents.of(grid, mask);

        assertEquals(12, tiled.tileCount());
        assertEquals(12, tiled.tilesRelabelled());
        assertTrue(tiled.boundaryAdjacencies() > 0);
        assertSamePartition(grid, mask, tiled);
    }

    @Test
    void randomChangesCaughtUpTileByTileStillMatchTheFlood() {
        NavigationGrid grid = new NavigationGrid(100, 70);
        Random random = new Random(11);
        for (int y = 0; y < 70; y++) {
            for (int x = 0; x < 100; x++) {
                if (random.nextFloat() < 0.8f) grid.setWalkableFloor(x, y);
            }
        }
        VehicleClearanceCache cache = new VehicleClearanceCache(RADIUS);
        cache.components(grid, grid.topologyRevision());

        for (int round = 0; round < 40; round++) {
            int changes = 1 + random.nextInt(3);
            for (int i = 0; i < changes; i++) {
                int x = random.nextInt(100);
                int y = random.nextInt(70);
                if (random.nextBoolean()) {
                    grid.setWalkable(x, y, !grid.isWalkable(x, y));
                } else {
                    Direction dir = Direction.CARDINALS[random.nextInt(4)];
                    grid.setSharedEdgePassable(x, y, dir, !grid.isEdgePassable(x, y, dir));
                }
            }
            ClearanceComponents tiled = cache.components(grid, grid.topologyRevision());
            // A change dilated by the chassis radius can straddle at most four
            // tiles, so that is the ceiling on what one change may relabel.
            assertTrue(tiled.tilesRelabelled() <= 4 * changes,
                    "changes " + changes + " relabelled " + tiled.tilesRelabelled());
            assertSamePartition(grid, VehicleClearance.erode(grid, RADIUS), tiled);
        }
        assertEquals(1, cache.componentBuilds());
        assertEquals(40, cache.componentCatchUps());
    }

    /**
     * A wreck lands on the one corridor between two halves of the map, with
     * the corridor crossing a tile seam: the split has to be found by the seam
     * union, since neither tile's local labels can see it on their own.
     */
    @Test
    void aWreckAcrossASeamSplitsOneComponentIntoTwo() {
        NavigationGrid grid = new NavigationGrid(70, 20);
        carve(grid, 1, 1, 28, 18);
        carve(grid, 29, 8, 40, 12); // corridor across the x=32 seam
        carve(grid, 41, 1, 68, 18);
        VehicleClearanceCache cache = new VehicleClearanceCache(RADIUS);
        ClearanceComponents before = cache.components(grid, grid.topologyRevision());
        assertEquals(1, before.componentCount());
        assertTrue(before.connected(10, 10, 60, 10));

        for (int y = 8; y <= 12; y++) grid.setWalkable(33, y, false);

        ClearanceComponents after = cache.components(grid, grid.topologyRevision());
        assertEquals(2, after.componentCount());
        assertFalse(after.connected(10, 10, 60, 10));
        assertTrue(after.connected(10, 10, 20, 15));
        assertSamePartition(grid, VehicleClearance.erode(grid, RADIUS), after);
        // The instance handed out before the wreck is untouched.
        assertTrue(before.connected(10, 10, 60, 10));
        assertEquals(1, cache.componentCatchUps());
    }

    @Test
    void aBreachAcrossASeamJoinsTwoComponentsIntoOne() {
        NavigationGrid grid = new NavigationGrid(70, 20);
        carve(grid, 1, 1, 30, 18);
        carve(grid, 34, 1, 68, 18);
        VehicleClearanceCache cache = new VehicleClearanceCache(RADIUS);
        ClearanceComponents before = cache.components(grid, grid.topologyRevision());
        assertEquals(2, before.componentCount());
        assertFalse(before.connected(10, 10, 60, 10));

        carve(grid, 31, 6, 33, 12); // breach the wall that straddles the seam

        ClearanceComponents after = cache.components(grid, grid.topologyRevision());
        assertEquals(1, after.componentCount());
        assertTrue(after.connected(10, 10, 60, 10));
        assertSamePartition(grid, VehicleClearance.erode(grid, RADIUS), after);
        assertTrue(after.tilesRelabelled() <= 2,
                "a breach on one seam relabels the tiles either side of it and no others");
    }

    @Test
    void fallingBehindTheLogRebuildsEveryTile() {
        NavigationGrid grid = new NavigationGrid(64, 64);
        carve(grid, 1, 1, 62, 62);
        VehicleClearanceCache cache = new VehicleClearanceCache(0);
        cache.components(grid, grid.topologyRevision());

        int capacity = grid.changeLogCapacity();
        for (int i = 0; i <= capacity; i++) {
            grid.setWalkable(5, 5, !grid.isWalkable(5, 5));
        }

        ClearanceComponents after = cache.components(grid, grid.topologyRevision());
        assertEquals(after.tileCount(), after.tilesRelabelled());
        assertEquals(2, cache.componentBuilds());
        assertEquals(0, cache.componentCatchUps());
        assertSamePartition(grid, VehicleClearance.erode(grid, 0), after);
    }
}
