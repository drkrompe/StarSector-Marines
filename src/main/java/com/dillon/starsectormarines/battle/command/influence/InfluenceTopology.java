package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private int[][] singletonComponents;
    private List<int[]> neighbors;

    InfluenceTopology(NavigationGrid grid, int blockSize) {
        this.grid = grid;
        this.blockSize = blockSize;
        this.blockWidth = (grid.getWidth() + blockSize - 1) / blockSize;
        this.blockHeight = (grid.getHeight() + blockSize - 1) / blockSize;
        this.fineComponent = new int[grid.getWidth() * grid.getHeight()];
        Arrays.fill(fineComponent, -1);
        buildComponents();
        singletonComponents = new int[componentCount()][];
        for (int component = 0; component < componentCount(); component++) {
            singletonComponents[component] = new int[] { component };
        }
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
            if (exact >= 0) return singletonComponents[exact];
        }

        // Believed contacts can stand on a cell that has since become a wall.
        // The nearest live component is usually one or two cells away. Search
        // outward and stop only once every unseen cell is strictly farther;
        // equal-distance ties can occur on a later square ring.
        int bestDistance = Integer.MAX_VALUE;
        List<Integer> nearestCells = new ArrayList<>();
        if (grid.inBounds(cellX, cellY)) {
            int maxRadius = Math.max(
                    Math.max(cellX, grid.getWidth() - 1 - cellX),
                    Math.max(cellY, grid.getHeight() - 1 - cellY));
            for (int radius = 1; radius <= maxRadius; radius++) {
                int minY = Math.max(0, cellY - radius);
                int maxY = Math.min(grid.getHeight() - 1, cellY + radius);
                int minX = Math.max(0, cellX - radius);
                int maxX = Math.min(grid.getWidth() - 1, cellX + radius);
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        int dx = x - cellX;
                        int dy = y - cellY;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                        int index = grid.index(x, y);
                        if (fineComponent[index] < 0) continue;
                        int distance = dx * dx + dy * dy;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            nearestCells.clear();
                        }
                        if (distance == bestDistance) nearestCells.add(index);
                    }
                }
                long nearestUnseen = (long) (radius + 1) * (radius + 1);
                if (bestDistance < nearestUnseen) break;
            }
        } else {
            // Off-map beliefs are rare. Preserve the original all-map answer
            // without walking potentially enormous empty rings outside it.
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) {
                    int index = grid.index(x, y);
                    if (fineComponent[index] < 0) continue;
                    int dx = x - cellX;
                    int dy = y - cellY;
                    int distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearestCells.clear();
                    }
                    if (distance == bestDistance) nearestCells.add(index);
                }
            }
        }
        // Rings encounter ties in a different order than a row-major full-map
        // scan. Restore that order so field accumulation remains byte stable.
        Collections.sort(nearestCells);
        Set<Integer> closest = new LinkedHashSet<>();
        for (int index : nearestCells) closest.add(fineComponent[index]);
        int[] result = new int[closest.size()];
        int i = 0;
        for (int component : closest) result[i++] = component;
        return result;
    }

    private void buildComponents() {
        // A flood never leaves its tactical block. Reuse a primitive queue so
        // a full-map rebuild does not box every walkable cell into an Integer.
        int[] queue = new int[blockSize * blockSize];
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
                        int head = 0;
                        int tail = 0;
                        queue[tail++] = fine;
                        while (head < tail) {
                            int current = queue[head++];
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
                                queue[tail++] = next;
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
