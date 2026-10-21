package com.dillon.starsectormarines.battle.sally;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BiPredicate;

/**
 * Where a walled place could have a sally door, read off the map as generated (see {@code garrisonables.md}, sally
 * doors). A place's enclosure is a rectangle (a building's footprint, a compound's bounds) with its holder's anchor
 * inside. On each face the survey looks for a span of {@link #WIDTH} cells of built wall, one or two cells thick, with
 * open floor inside the rectangle on one side and open ground on the other, and keeps the span nearest the face's
 * middle; it keeps that door only when the walk from the anchor out to the door's mouth by the existing ways is at least
 * {@link #MIN_SAVING_CELLS} longer than the walk to the door's inside cell and through it. A face that already has a way
 * out near where it is wanted gets no door, so doors go only where a place's own walls send its holder round.
 *
 * <p>Only a walled place gets doors: at least {@link #MIN_ENCLOSED_SHARE} of its rectangle's edge is built wall, so open
 * ground with somebody's wall at its edge (a landing zone beside a warehouse) gets none.
 *
 * <p>Only built wall is a door candidate: never natural rock, never a cell the caller excludes (a fort's works, which
 * carry their own authored ports), never a doorway. Two cells wide is wide enough for every chassis that fights on foot
 * (the widest, a Bulwark, is 1.2 cells across).
 */
public final class SallyDoorSurvey {

    /** Cells along the face a door spans. */
    public static final int WIDTH = 2;
    /** Thickest wall a door is cut through. */
    public static final int MAX_DEPTH = 2;
    /**
     * Share of a rectangle's edge that must be built wall (or a window or a doorway in it) for the rectangle to be a
     * walled place at all: below it the place is open ground (a landing zone, a field position) and any wall at its edge
     * is somebody else's. UNTUNED.
     */
    public static final float MIN_ENCLOSED_SHARE = 0.5f;
    /** A door is kept only where it saves its holder at least this walk, in cells. UNTUNED. */
    public static final float MIN_SAVING_CELLS = 20f;
    /** How far outside the rectangle the walk out is measured: past this, the way round is as good as none. */
    static final int SEARCH_MARGIN = 40;
    /** How far outside the rectangle the floor inside a door may stand. */
    static final int YARD_CELLS = 3;

    /** A door the survey found: its wall cells, the open cell inside it, its mouth, and the walk it saves. */
    public record Spec(int[][] cells, int insideX, int insideY, int mouthX, int mouthY, float saving) { }

    private SallyDoorSurvey() {}

    /**
     * The doors one enclosure could have, at most one a face, north, south, west, east.
     *
     * @param left the rectangle's cells, inclusive
     * @param anchorX the cell the holder stands to from, inside the rectangle
     * @param excluded cells no door may be cut through
     */
    public static List<Spec> derive(NavigationGrid grid, CellTopology topology, int left, int top, int right, int bottom,
                                    int anchorX, int anchorY, BiPredicate<Integer, Integer> excluded) {
        List<Spec> out = new ArrayList<>();
        if (anchorX < left || anchorX > right || anchorY < top || anchorY > bottom) return out;
        // A node's anchor is its identity, not a cell anyone is guaranteed to stand on: start from the open cell nearest it.
        int[] start = nearestOpen(grid, anchorX, anchorY, left, top, right, bottom);
        if (start == null) return out;
        anchorX = start[0];
        anchorY = start[1];
        if (right - left + 1 < WIDTH + 2 || bottom - top + 1 < WIDTH + 2) return out;
        if (enclosedShare(grid, topology, left, top, right, bottom) < MIN_ENCLOSED_SHARE) return out;
        int bx0 = Math.max(0, left - SEARCH_MARGIN), by0 = Math.max(0, top - SEARCH_MARGIN);
        int bx1 = Math.min(grid.getWidth() - 1, right + SEARCH_MARGIN), by1 = Math.min(grid.getHeight() - 1, bottom + SEARCH_MARGIN);
        float[] walk = walk(grid, anchorX, anchorY, bx0, by0, bx1, by1);
        int bw = bx1 - bx0 + 1;
        // {outX, outY, alongX, alongY, edge start x, edge start y, face length}
        int[][] faces = {
                {0, -1, 1, 0, left, top, right - left + 1},
                {0, 1, 1, 0, left, bottom, right - left + 1},
                {-1, 0, 0, 1, left, top, bottom - top + 1},
                {1, 0, 0, 1, right, top, bottom - top + 1}};
        for (int[] f : faces) {
            int ox = f[0], oy = f[1], ax = f[2], ay = f[3], length = f[6];
            Spec best = null;
            int bestOff = Integer.MAX_VALUE;
            for (int p = 1; p + WIDTH <= length - 1; p++) {
                int[] columns = new int[WIDTH * 2];
                boolean ok = true;
                for (int s = 0; s < WIDTH && ok; s++) {
                    int ex = f[4] + ax * (p + s), ey = f[5] + ay * (p + s);
                    int[] pattern = pattern(grid, topology, ex, ey, ox, oy, excluded);
                    if (pattern == null) ok = false;
                    else {
                        columns[s * 2] = pattern[0];
                        columns[s * 2 + 1] = pattern[1];
                    }
                }
                // Both columns the same: the same inside depth and the same wall thickness, a clean rectangular door.
                if (!ok || columns[0] != columns[2] || columns[1] != columns[3]) continue;
                int s0 = columns[0], depth = columns[1];
                int ex = f[4] + ax * p, ey = f[5] + ay * p;
                int inX = ex + ox * s0, inY = ey + oy * s0;
                int mouthX = ex + ox * (s0 + depth + 1), mouthY = ey + oy * (s0 + depth + 1);
                float through = Float.POSITIVE_INFINITY;
                float round = Float.POSITIVE_INFINITY;
                for (int s = 0; s < WIDTH; s++) {
                    int ix = inX + ax * s, iy = inY + ay * s, mx = mouthX + ax * s, my = mouthY + ay * s;
                    if (ix < bx0 || ix > bx1 || iy < by0 || iy > by1) continue;
                    through = Math.min(through, walk[(iy - by0) * bw + ix - bx0] + depth + 1);
                    if (mx >= bx0 && mx <= bx1 && my >= by0 && my <= by1) round = Math.min(round, walk[(my - by0) * bw + mx - bx0]);
                }
                if (!Float.isFinite(through)) continue;
                float saving = Float.isFinite(round) ? round - through : SEARCH_MARGIN * 4f;
                if (saving < MIN_SAVING_CELLS) continue;
                int off = Math.abs(2 * p + WIDTH - length);
                if (off >= bestOff) continue;
                int[][] cells = new int[WIDTH * depth][];
                for (int s = 0; s < WIDTH; s++) {
                    for (int k = 0; k < depth; k++) {
                        cells[s * depth + k] = new int[]{inX + ax * s + ox * (k + 1), inY + ay * s + oy * (k + 1)};
                    }
                }
                bestOff = off;
                best = new Spec(cells, inX, inY, mouthX, mouthY, saving);
            }
            if (best != null) out.add(best);
        }
        return out;
    }

    /**
     * Along the outward step from an edge cell, the pattern nearest the edge of open floor at step {@code s0}, then one
     * to {@link #MAX_DEPTH} cells of built wall, then open ground beyond the edge: {s0, depth}, or null. The floor may
     * stand up to {@link #YARD_CELLS} outside the rectangle, because a place's own enclosure (a checkpoint's yard wall,
     * a compound's fence line) often stands a step or two beyond the footprint it is measured by.
     */
    private static int[] pattern(NavigationGrid grid, CellTopology topology, int ex, int ey, int ox, int oy,
                                 BiPredicate<Integer, Integer> excluded) {
        for (int step = 0; step <= 2 * Math.max(MAX_DEPTH + 1, YARD_CELLS); step++) {
            int s0 = (step % 2 == 0) ? -step / 2 : (step + 1) / 2;
            if (s0 < -MAX_DEPTH - 1 || s0 > YARD_CELLS) continue;
            int ix = ex + ox * s0, iy = ey + oy * s0;
            if (!open(grid, ix, iy)) continue;
            int depth = 0;
            while (depth < MAX_DEPTH && wall(grid, topology, ix + ox * (depth + 1), iy + oy * (depth + 1), excluded)) depth++;
            if (depth == 0) continue;
            int mx = ix + ox * (depth + 1), my = iy + oy * (depth + 1);
            if (s0 + depth + 1 < 1 || !open(grid, mx, my)) continue;
            return new int[]{s0, depth};
        }
        return null;
    }

    /**
     * How walled the rectangle is: the share of its edge cells along which a built wall, window or doorway stands within
     * one cell inside the edge or {@link #YARD_CELLS} outside it. A walled place reads at least {@link #MIN_ENCLOSED_SHARE}.
     */
    public static float enclosedShare(NavigationGrid grid, CellTopology topology, int left, int top, int right, int bottom) {
        int edge = 0, walled = 0;
        int[][] faces = {{0, -1, 1, 0, left, top, right - left + 1}, {0, 1, 1, 0, left, bottom, right - left + 1},
                {-1, 0, 0, 1, left, top, bottom - top + 1}, {1, 0, 0, 1, right, top, bottom - top + 1}};
        for (int[] f : faces) {
            for (int p = 0; p < f[6]; p++) {
                edge++;
                for (int s = -1; s <= YARD_CELLS; s++) {
                    int x = f[4] + f[2] * p + f[0] * s, y = f[5] + f[3] * p + f[1] * s;
                    if (!grid.inBounds(x, y)) continue;
                    if (grid.isDoorway(x, y) || (!grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)
                            && (topology.isWall(x, y) || topology.isWindow(x, y)))) {
                        walled++;
                        break;
                    }
                }
            }
        }
        return edge == 0 ? 0f : walled / (float) edge;
    }

    /** The open cell inside the rectangle nearest (x, y), by rings, or null. */
    static int[] nearestOpen(NavigationGrid grid, int x, int y, int left, int top, int right, int bottom) {
        int reach = Math.max(right - left, bottom - top);
        for (int r = 0; r <= reach; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (cx < left || cx > right || cy < top || cy > bottom) continue;
                    if (open(grid, cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    private static boolean open(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }

    private static boolean wall(NavigationGrid grid, CellTopology topology, int x, int y,
                                BiPredicate<Integer, Integer> excluded) {
        if (!grid.inBounds(x, y) || grid.isWalkable(x, y) || grid.isDoorway(x, y)) return false;
        if (grid.hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) return false;
        if (!topology.isWall(x, y) && !topology.isWindow(x, y)) return false;
        return !excluded.test(x, y);
    }

    /** Rifleman walk from the anchor by the grid's own step rule, inside the box only. */
    static float[] walk(NavigationGrid grid, int sx, int sy, int bx0, int by0, int bx1, int by1) {
        int bw = bx1 - bx0 + 1;
        float[] dist = new float[bw * (by1 - by0 + 1)];
        Arrays.fill(dist, Float.POSITIVE_INFINITY);
        dist[(sy - by0) * bw + sx - bx0] = 0;
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        open.add(new float[]{0, sx, sy});
        while (!open.isEmpty()) {
            float[] n = open.poll();
            int x = (int) n[1], y = (int) n[2];
            if (n[0] > dist[(y - by0) * bw + x - bx0]) continue;
            for (Direction d : Direction.ALL) {
                int nx = x + d.dx, ny = y + d.dy;
                if (nx < bx0 || nx > bx1 || ny < by0 || ny > by1 || !grid.canTraverseCellStep(x, y, d)) continue;
                float nd = n[0] + (d.isDiagonal() ? 1.4142f : 1f);
                int idx = (ny - by0) * bw + nx - bx0;
                if (nd < dist[idx]) {
                    dist[idx] = nd;
                    open.add(new float[]{nd, nx, ny});
                }
            }
        }
        return dist;
    }
}
