package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InfluenceTopologyNearestTest {

    @Test
    void sourceGridChangesDoNotAlterCapturedComponentsEdgesOrPropagation() {
        ReadGuardGrid grid = new ReadGuardGrid(8, 4);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkable(3, 1, false);
        InfluenceTopology topology = new InfluenceTopology(grid, 4);
        int[] left = topology.componentsForCell(1, 1).clone();
        int[] right = topology.componentsForCell(6, 1).clone();
        int[] wallNearest = topology.componentsForCell(3, 1).clone();
        int[] offMapNearest = topology.componentsForCell(8, 1).clone();
        assertEquals(2, topology.componentCount());
        assertArrayEquals(right, topology.neighbors(left[0]));
        List<InfluenceSource> sources = List.of(new InfluenceSource(1, 1, 1f));
        float[] propagated = InfluenceFieldBuilder.propagate(topology, sources);

        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkable(x, y, false);
            }
        }
        assertEquals(0, new InfluenceTopology(grid, 4).componentCount(),
                "a fresh topology must observe the mutation");
        grid.rejectReads = true;
        assertArrayEquals(left, topology.componentsForCell(1, 1));
        assertArrayEquals(right, topology.componentsForCell(6, 1));
        assertArrayEquals(wallNearest, topology.componentsForCell(3, 1));
        assertArrayEquals(offMapNearest, topology.componentsForCell(8, 1));
        assertArrayEquals(right, topology.neighbors(left[0]));
        assertArrayEquals(left, topology.neighbors(right[0]));
        assertArrayEquals(propagated, InfluenceFieldBuilder.propagate(topology, sources));
    }

    /** Once captured, even dimension/index queries must not reach the live grid. */
    private static final class ReadGuardGrid extends NavigationGrid {
        private boolean rejectReads;

        private ReadGuardGrid(int width, int height) {
            super(width, height);
        }

        @Override public int getWidth() {
            requireReadable();
            return super.getWidth();
        }

        @Override public int getHeight() {
            requireReadable();
            return super.getHeight();
        }

        @Override public boolean inBounds(int x, int y) {
            requireReadable();
            return super.inBounds(x, y);
        }

        @Override public int index(int x, int y) {
            requireReadable();
            return super.index(x, y);
        }

        private void requireReadable() {
            if (rejectReads) throw new AssertionError("captured topology read its source grid");
        }
    }

    @Test
    void nearestComponentsAgreeWithFullMapScanIncludingTies() {
        for (int seed = 0; seed < 20; seed++) {
            NavigationGrid grid = new NavigationGrid(12, 10);
            Random random = new Random(seed);
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    if (random.nextInt(4) != 0) grid.setWalkableFloor(x, y);
                }
            }
            InfluenceTopology topology = new InfluenceTopology(grid, 4);
            for (int y = -1; y <= grid.getHeight(); y++) {
                for (int x = -1; x <= grid.getWidth(); x++) {
                    assertArrayEquals(fullScan(topology, grid, x, y),
                            topology.componentsForCell(x, y),
                            "seed=" + seed + " cell=" + x + "," + y);
                }
            }
        }
    }

    private static int[] fullScan(InfluenceTopology topology,
                                  NavigationGrid grid, int cellX, int cellY) {
        if (grid.isWalkable(cellX, cellY)) {
            return topology.componentsForCell(cellX, cellY);
        }
        int bestDistance = Integer.MAX_VALUE;
        List<Integer> nearest = new ArrayList<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                int dx = x - cellX;
                int dy = y - cellY;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest.clear();
                }
                if (distance == bestDistance) {
                    nearest.add(topology.componentsForCell(x, y)[0]);
                }
            }
        }
        Set<Integer> distinct = new LinkedHashSet<>(nearest);
        int[] result = new int[distinct.size()];
        int i = 0;
        for (int component : distinct) result[i++] = component;
        return result;
    }
}
