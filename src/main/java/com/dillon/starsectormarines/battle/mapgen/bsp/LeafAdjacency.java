package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cardinal-adjacency graph over BSP {@link BlockLeaf}s. Two leaves are
 * adjacent if you can step from one's outer edge to the other's outer edge
 * across {@link #MAX_ROAD_SCAN_DEPTH} or fewer road cells — i.e., they're
 * separated by a normal BSP road frame, not a trunk.
 *
 * <p>The scan depth is chosen so trunks act as <em>barriers</em>: a width-5
 * secondary trunk is exactly one cell wider than the scan reaches, so leaves
 * on opposite sides of a trunk register as non-adjacent. Compounds built off
 * this graph therefore can't span a major arterial, which matches the
 * intent — a "military base compound" is one block of buildings, not "all
 * the buildings on both sides of Main Street".
 *
 * <p>Adjacency is symmetric and reflexively excluded (a leaf is never
 * adjacent to itself). The result is an {@link IdentityHashMap} so callers
 * can use {@link BlockLeaf} references directly as keys without worrying
 * about value-based equals/hashCode (there isn't one).
 */
public final class LeafAdjacency {

    /**
     * Max road-strip depth to scan from a leaf's outer edge before giving up
     * on finding a neighbor. BSP frames are 3-4 cells (max 4 in trunk mode),
     * so 5 catches every legitimate frame while still falling short of the
     * 5-cell secondary trunk by one cell. The 7-cell primary trunk is well
     * out of range.
     */
    private static final int MAX_ROAD_SCAN_DEPTH = 5;

    private LeafAdjacency() {}

    /**
     * Compute the adjacency map. Each leaf's value list contains every leaf
     * separated from it by ≤ {@link #MAX_ROAD_SCAN_DEPTH} road cells in any
     * of the four cardinal directions. Order of neighbors is not stable
     * across runs — callers that care should sort.
     */
    public static Map<BlockLeaf, List<BlockLeaf>> compute(List<BlockLeaf> leaves, int width, int height) {
        // Cell→leaf lookup. Cells inside no leaf (road, perimeter, trunk)
        // remain null and are skipped during scans.
        BlockLeaf[][] leafAt = new BlockLeaf[width][height];
        for (BlockLeaf leaf : leaves) {
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        leafAt[x][y] = leaf;
                    }
                }
            }
        }

        Map<BlockLeaf, Set<BlockLeaf>> sets = new IdentityHashMap<>(leaves.size() * 2);
        for (BlockLeaf leaf : leaves) sets.put(leaf, new HashSet<>(4));

        for (BlockLeaf leaf : leaves) {
            Set<BlockLeaf> neighbors = sets.get(leaf);
            scanEdge(leafAt, leaf, neighbors, width, height, 0, -1); // up
            scanEdge(leafAt, leaf, neighbors, width, height, 0,  1); // down
            scanEdge(leafAt, leaf, neighbors, width, height, -1, 0); // left
            scanEdge(leafAt, leaf, neighbors, width, height, 1,  0); // right
        }

        // Symmetrize (each edge scan only finds the "outward" neighbor; the
        // reverse is implied but we set it explicitly for caller convenience).
        for (Map.Entry<BlockLeaf, Set<BlockLeaf>> e : sets.entrySet()) {
            for (BlockLeaf other : e.getValue()) {
                sets.get(other).add(e.getKey());
            }
        }

        Map<BlockLeaf, List<BlockLeaf>> out = new IdentityHashMap<>(sets.size() * 2);
        for (Map.Entry<BlockLeaf, Set<BlockLeaf>> e : sets.entrySet()) {
            out.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return out;
    }

    /**
     * Walk the edge of {@code leaf} in direction {@code (dx, dy)} (must be
     * one cardinal step). For each cell along the edge, march outward
     * stepping by {@code (dx, dy)} up to {@link #MAX_ROAD_SCAN_DEPTH} cells;
     * first non-null leaf hit becomes a neighbor. Stops at the first hit per
     * column/row so each side contributes at most one neighbor per edge cell.
     */
    private static void scanEdge(BlockLeaf[][] leafAt, BlockLeaf leaf, Set<BlockLeaf> neighbors,
                                 int width, int height, int dx, int dy) {
        int startX, endX, startY, endY;
        if (dy != 0) {
            startX = leaf.left;
            endX   = leaf.right;
            startY = endY = (dy < 0 ? leaf.top - 1 : leaf.bottom + 1);
        } else {
            startY = leaf.top;
            endY   = leaf.bottom;
            startX = endX = (dx < 0 ? leaf.left - 1 : leaf.right + 1);
        }
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int cx = x;
                int cy = y;
                for (int step = 0; step < MAX_ROAD_SCAN_DEPTH; step++) {
                    if (cx < 0 || cx >= width || cy < 0 || cy >= height) break;
                    BlockLeaf other = leafAt[cx][cy];
                    if (other != null && other != leaf) {
                        neighbors.add(other);
                        break;
                    }
                    cx += dx;
                    cy += dy;
                }
            }
        }
    }
}
