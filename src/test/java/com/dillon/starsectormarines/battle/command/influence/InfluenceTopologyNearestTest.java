package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class InfluenceTopologyNearestTest {

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
