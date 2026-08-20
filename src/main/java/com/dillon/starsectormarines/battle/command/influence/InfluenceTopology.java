package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Fine-connectivity component graph whose nodes retain their tactical block. */
final class InfluenceTopology {

    private final NavigationGrid grid;
    private final int blockSize;
    private final int blockWidth;
    private final int blockHeight;
    private final int[] fineComponent;
    private final List<Integer> componentBlocks = new ArrayList<>();
    private List<int[]> neighbors;

    InfluenceTopology(NavigationGrid grid, int blockSize) {
        this.grid = grid;
        this.blockSize = blockSize;
        this.blockWidth = (grid.getWidth() + blockSize - 1) / blockSize;
        this.blockHeight = (grid.getHeight() + blockSize - 1) / blockSize;
        this.fineComponent = new int[grid.getWidth() * grid.getHeight()];
        Arrays.fill(fineComponent, -1);
        buildComponents();
        buildCrossBlockEdges();
    }

    int blockWidth() { return blockWidth; }
    int blockHeight() { return blockHeight; }
    int blockCount() { return blockWidth * blockHeight; }
    int componentCount() { return componentBlocks.size(); }
    int blockForComponent(int component) { return componentBlocks.get(component); }
    int[] neighbors(int component) { return neighbors.get(component); }

    int[] componentsForCell(int cellX, int cellY) {
        if (grid.inBounds(cellX, cellY)) {
            int exact = fineComponent[grid.index(cellX, cellY)];
            if (exact >= 0) return new int[] { exact };
        }

        int bestDistance = Integer.MAX_VALUE;
        Set<Integer> closest = new LinkedHashSet<>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int component = fineComponent[grid.index(x, y)];
                if (component < 0) continue;
                int dx = x - cellX;
                int dy = y - cellY;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    closest.clear();
                }
                if (distance == bestDistance) closest.add(component);
            }
        }
        return closest.stream().mapToInt(Integer::intValue).toArray();
    }

    private void buildComponents() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int blockY = 0; blockY < blockHeight; blockY++) {
            for (int blockX = 0; blockX < blockWidth; blockX++) {
                int minX = blockX * blockSize;
                int minY = blockY * blockSize;
                int maxX = Math.min(grid.getWidth(), minX + blockSize);
                int maxY = Math.min(grid.getHeight(), minY + blockSize);
                int block = blockY * blockWidth + blockX;
                for (int y = minY; y < maxY; y++) {
                    for (int x = minX; x < maxX; x++) {
                        int fine = grid.index(x, y);
                        if (!grid.isWalkableAt(fine) || fineComponent[fine] >= 0) continue;
                        int component = componentBlocks.size();
                        componentBlocks.add(block);
                        fineComponent[fine] = component;
                        queue.add(fine);
                        while (!queue.isEmpty()) {
                            int current = queue.removeFirst();
                            int currentX = current % grid.getWidth();
                            int currentY = current / grid.getWidth();
                            for (Direction direction : Direction.CARDINALS) {
                                int nextX = currentX + direction.dx;
                                int nextY = currentY + direction.dy;
                                if (nextX < minX || nextX >= maxX
                                        || nextY < minY || nextY >= maxY) continue;
                                int next = grid.index(nextX, nextY);
                                if (fineComponent[next] >= 0
                                        || !canStep(currentX, currentY, nextX, nextY, direction)) continue;
                                fineComponent[next] = component;
                                queue.addLast(next);
                            }
                        }
                    }
                }
            }
        }
    }

    private void buildCrossBlockEdges() {
        List<Set<Integer>> edgeSets = new ArrayList<>(componentCount());
        for (int i = 0; i < componentCount(); i++) edgeSets.add(new LinkedHashSet<>());
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                connectAcrossBoundary(x, y, x + 1, y, Direction.E, edgeSets);
                connectAcrossBoundary(x, y, x, y + 1, Direction.N, edgeSets);
            }
        }
        neighbors = new ArrayList<>(componentCount());
        for (Set<Integer> edges : edgeSets) {
            neighbors.add(edges.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private void connectAcrossBoundary(int x, int y, int nextX, int nextY,
                                       Direction direction, List<Set<Integer>> edgeSets) {
        if (!grid.inBounds(nextX, nextY)) return;
        int first = fineComponent[grid.index(x, y)];
        int second = fineComponent[grid.index(nextX, nextY)];
        if (first < 0 || second < 0 || first == second) return;
        if (componentBlocks.get(first).equals(componentBlocks.get(second))) return;
        if (!canStep(x, y, nextX, nextY, direction)) return;
        edgeSets.get(first).add(second);
        edgeSets.get(second).add(first);
    }

    private boolean canStep(int x, int y, int nextX, int nextY, Direction direction) {
        if (!grid.inBounds(x, y) || !grid.inBounds(nextX, nextY)) return false;
        if (!grid.isWalkable(x, y) || !grid.isWalkable(nextX, nextY)) return false;
        return grid.isEdgePassable(x, y, direction)
                && grid.isEdgePassable(nextX, nextY, direction.opposite());
    }
}
