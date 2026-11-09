package com.dillon.starsectormarines.battle.drone;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Each member's own route to a flock's anchor, over the cells a low flier may cross: one distance field integrated
 * outward from the anchor's cell, so every member descends it by its own way round. A wall between two members and
 * the anchor sends each down the side its own position favours, which is what splits a flock round a building;
 * cohesion closes it again on the far side ({@link FlockSteering}).
 *
 * <p>The field covers a square of {@link #radius} cells round the anchor, never the whole map, so its cost is the
 * flock's reach and not the battle's size. Steps are 10 straight and 14 diagonal, and a diagonal step needs both
 * cells beside it open, so no route cuts a wall's corner. Immutable once built.
 */
public final class FlockField {

    /** Whether a low flier may be in cell {@code (x, y)}: open ground, not a wall and not under a roof. */
    @FunctionalInterface
    public interface Open {
        boolean open(int x, int y);
    }

    /** Distance of a cell with no route to the anchor, or outside the field. */
    public static final int UNREACHABLE = Integer.MAX_VALUE;
    static final int STRAIGHT = 10;
    static final int DIAGONAL = 14;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

    private final int originX, originY, size, radius;
    private final int sourceX, sourceY;
    private final int[] dist;

    private FlockField(int sourceX, int sourceY, int radius, int[] dist) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.radius = radius;
        this.originX = sourceX - radius;
        this.originY = sourceY - radius;
        this.size = 2 * radius + 1;
        this.dist = dist;
    }

    /**
     * Integrates the field from cell {@code (sx, sy)} over the open cells within {@code radius} of it. A closed source
     * cell (the anchor over a roof) has no routes at all.
     */
    public static FlockField build(int sx, int sy, int radius, Open open) {
        int size = 2 * radius + 1;
        int[] dist = new int[size * size];
        Arrays.fill(dist, UNREACHABLE);
        FlockField field = new FlockField(sx, sy, radius, dist);
        if (!open.open(sx, sy)) return field;
        int ox = sx - radius, oy = sy - radius;
        boolean[] openAt = new boolean[size * size];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) openAt[j * size + i] = open.open(ox + i, oy + j);
        }
        PriorityQueue<long[]> frontier = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        int start = radius * size + radius;
        dist[start] = 0;
        frontier.add(new long[]{0L, start});
        while (!frontier.isEmpty()) {
            long[] head = frontier.poll();
            int idx = (int) head[1];
            if (head[0] > dist[idx]) continue;
            int i = idx % size, j = idx / size;
            for (int k = 0; k < 8; k++) {
                int ni = i + DX[k], nj = j + DY[k];
                if (ni < 0 || nj < 0 || ni >= size || nj >= size) continue;
                int n = nj * size + ni;
                if (!openAt[n]) continue;
                boolean diagonal = k >= 4;
                if (diagonal && (!openAt[j * size + ni] || !openAt[nj * size + i])) continue;
                int d = dist[idx] + (diagonal ? DIAGONAL : STRAIGHT);
                if (d < dist[n]) {
                    dist[n] = d;
                    frontier.add(new long[]{d, n});
                }
            }
        }
        return field;
    }

    public int sourceX() { return sourceX; }

    public int sourceY() { return sourceY; }

    public int radius() { return radius; }

    /** Route distance from cell {@code (x, y)} to the anchor, in tenths of a cell, or {@link #UNREACHABLE}. */
    public int distance(int x, int y) {
        int i = x - originX, j = y - originY;
        if (i < 0 || j < 0 || i >= size || j >= size) return UNREACHABLE;
        return dist[j * size + i];
    }

    /**
     * The way to the anchor from point {@code (x, y)}, as {@code {dx, dy, cells}}: a unit direction toward the centre
     * of the neighbouring cell nearest the anchor, and the route distance left. In the anchor's own cell the
     * direction is straight at {@code (ax, ay)}. {@code null} when the point has no route.
     */
    public float[] way(float x, float y, float ax, float ay) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        int here = distance(cx, cy);
        if (here == UNREACHABLE) return null;
        if (here == 0) return toward(x, y, ax, ay, 0f);
        int best = here, bx = cx, by = cy;
        for (int k = 0; k < 8; k++) {
            int nx = cx + DX[k], ny = cy + DY[k];
            int d = distance(nx, ny);
            if (d >= best) continue;
            if (k >= 4 && (distance(nx, cy) == UNREACHABLE || distance(cx, ny) == UNREACHABLE)) continue;
            best = d;
            bx = nx;
            by = ny;
        }
        if (bx == cx && by == cy) return toward(x, y, ax, ay, here / 10f);
        return toward(x, y, bx + .5f, by + .5f, here / 10f);
    }

    private static float[] toward(float x, float y, float tx, float ty, float cells) {
        float dx = tx - x, dy = ty - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return new float[]{0f, 0f, cells};
        return new float[]{dx / len, dy / len, Math.max(cells, len)};
    }
}
