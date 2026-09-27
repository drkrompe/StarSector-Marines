package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;

import java.util.Arrays;

/** Local emergency geometry, independent of map-sized pathfinder scratch and components. */
public final class LocalGrenadeEscape {
    static final int RADIUS = 12;
    static final int MAX_EXPANSIONS = 256;
    static final int MAX_PATH_POINTS = 128;
    private static final int SIDE = RADIUS * 2 + 1;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private LocalGrenadeEscape() {}

    /** Member-owned witness; replacing even an identical path revokes ownership. */
    public record Retained(Projectile hazard, NavigationGrid grid, long revision,
                           boolean cardinalOnly, int[] path) {
        boolean usable(Projectile current, NavigationGrid currentGrid, int[] currentPath,
                       int pathIndex, int fromX, int fromY) {
            if (hazard != current || grid != currentGrid || revision != grid.topologyRevision()
                    || cardinalOnly != GridPathfinder.USE_CARDINAL_NAVIGATION
                    || path != currentPath || pathIndex < 0
                    || pathIndex >= Paths.cellCount(path)) return false;
            return grid.canTraverseCellStep(fromX, fromY,
                    Paths.cellX(path, pathIndex), Paths.cellY(path, pathIndex));
        }
    }

    record Result(int[] path, int expanded) {}

    /**
     * Visits at most 128 points of the actual upcoming polyline, clipped at
     * distance the mover could travel before detonation. Duplicate vertices
     * cannot turn a finite travel horizon into an unbounded scan.
     */
    static boolean pathThreatened(float fromX, float fromY, int[] path, int first,
                                  float travel, float hazardX, float hazardY, float radius) {
        if (!(travel > 0f)) return false;
        float radiusSq = radius * radius;
        int end = Math.min(Paths.cellCount(path), Math.max(0, first) + MAX_PATH_POINTS);
        for (int i = Math.max(0, first); i < end && travel > 0f; i++) {
            float toX = Paths.cellX(path, i) + 0.5f;
            float toY = Paths.cellY(path, i) + 0.5f;
            float dx = toX - fromX;
            float dy = toY - fromY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length == 0f) continue;
            float fraction = Math.min(1f, travel / length);
            dx *= fraction;
            dy *= fraction;
            float projection = Math.max(0f, Math.min(1f,
                    ((hazardX - fromX) * dx + (hazardY - fromY) * dy)
                            / (dx * dx + dy * dy)));
            float hx = fromX + projection * dx - hazardX;
            float hy = fromY + projection * dy - hazardY;
            if (hx * hx + hy * hy <= radiusSq) return true;
            travel -= length;
            fromX = toX;
            fromY = toY;
        }
        return false;
    }

    /**
     * One breadth-first search, complete safe paths only. Fixed 625-cell reset,
     * at most 256 expansions and 2048 neighbor checks, regardless of map size.
     * Safety is geometric, not an occupancy reservation or a fuse-time promise.
     */
    static Result find(NavigationGrid grid, int fromX, int fromY,
                       float hazardX, float hazardY, float safeRadius, boolean cardinalOnly) {
        if (!grid.inBounds(fromX, fromY) || !grid.isWalkable(fromX, fromY)) {
            return new Result(GridPathfinder.EMPTY_PATH, 0);
        }
        Scratch scratch = SCRATCH.get();
        Arrays.fill(scratch.parent, -1);
        int start = RADIUS * SIDE + RADIUS;
        scratch.parent[start] = start;
        scratch.queue[0] = start;
        int head = 0;
        int tail = 1;
        int expanded = 0;
        Direction[] directions = cardinalOnly ? Direction.CARDINALS : Direction.ALL;
        while (head < tail && expanded < MAX_EXPANSIONS) {
            int cell = scratch.queue[head++];
            expanded++;
            int lx = cell % SIDE;
            int ly = cell / SIDE;
            int x = fromX + lx - RADIUS;
            int y = fromY + ly - RADIUS;
            float dx = x + 0.5f - hazardX;
            float dy = y + 0.5f - hazardY;
            if (cell != start && dx * dx + dy * dy > safeRadius * safeRadius) {
                int length = 1;
                for (int p = cell; p != start; p = scratch.parent[p]) length++;
                int[] path = new int[length * 2];
                for (int p = cell, i = length - 1; i >= 0; i--, p = scratch.parent[p]) {
                    path[i * 2] = fromX + p % SIDE - RADIUS;
                    path[i * 2 + 1] = fromY + p / SIDE - RADIUS;
                }
                return new Result(path, expanded);
            }
            for (Direction direction : directions) {
                int nx = lx + direction.dx;
                int ny = ly + direction.dy;
                if (nx < 0 || ny < 0 || nx >= SIDE || ny >= SIDE) continue;
                int next = ny * SIDE + nx;
                if (scratch.parent[next] != -1
                        || !grid.canTraverseCellStep(x, y, direction)) continue;
                scratch.parent[next] = cell;
                scratch.queue[tail++] = next;
            }
        }
        return new Result(GridPathfinder.EMPTY_PATH, expanded);
    }

    private static final class Scratch {
        final int[] parent = new int[SIDE * SIDE];
        final int[] queue = new int[SIDE * SIDE];
    }
}
