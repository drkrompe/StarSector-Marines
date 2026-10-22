package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Arrays;

/**
 * Where a unit in the way of fire goes (`fire-nouns.md`, Pathing). A unit needs
 * to move when it stands in a burning cell, or on fuel within {@link #THREAT_RADIUS}
 * of one: a front moves about as fast as a man walks, so waiting for it to arrive
 * is waiting to be overtaken. It goes to the nearest <em>safe</em> cell within {@link #SEARCH_RADIUS}:
 * walkable, out of and not beside the fire, and unable to burn (burnt out, or bare).
 * Ground just ahead of a front is not safe, because the front overtakes it. Steps
 * into burning cells count {@link #THROUGH_FIRE_STEP} times, so a thin front is
 * crossed into the burnt ground behind it and a deep one is outrun to bare ground.
 * Without a safe cell in reach it settles for the nearest clear one, and only a
 * unit already in the fire for the nearest cell merely not burning.
 *
 * <p>Nearest by that cost, eight ways, through walkable cells only; the caller
 * routes there, so a wall between the two is the route's to go round.
 */
public final class FireEscape {

    /** {@code -Dbattle.fire.stepOut=false} is the control: nobody steps out of fire or waits at its edge. */
    public static final String PROPERTY = "battle.fire.stepOut";
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    /** Chebyshev cells from a burning cell inside which a unit on fuel moves. */
    public static final int THREAT_RADIUS = 3;
    /** Chebyshev cells searched around the unit. */
    public static final int SEARCH_RADIUS = 16;
    /** What a step into a burning cell counts for against a step on clear ground. */
    public static final int THROUGH_FIRE_STEP = 5;
    private static final int SIDE = SEARCH_RADIUS * 2 + 1;
    /** Bucket ring wider than the dearest step. */
    private static final int BUCKETS = 8;

    private FireEscape() { }

    public static boolean enabled() { return ENABLED; }

    /** Whether a unit at a cell is in the fire's way: in a burning cell, or on fuel near one. */
    public static boolean needsToMove(FireField field, int x, int y) {
        if (field == null || field.burningCount() == 0) return false;
        if (field.isBurning(x, y)) return true;
        if (!field.canBurn(x, y)) return false;
        for (int dy = -THREAT_RADIUS; dy <= THREAT_RADIUS; dy++)
            for (int dx = -THREAT_RADIUS; dx <= THREAT_RADIUS; dx++)
                if (field.isBurning(x + dx, y + dy)) return true;
        return false;
    }

    /**
     * Whether a unit out of the fire's way should wait rather than take its
     * route's next step, into a burning cell: the route costing sends routes
     * round a fire where there is a way round, so one still into it has nowhere
     * else to go, and a fire passes. A unit in the fire's way walks on.
     */
    public static boolean waitsAtEdge(FireField field, int x, int y, int nextX, int nextY) {
        if (!ENABLED || field == null || field.burningCount() == 0 || !field.isBurning(nextX, nextY)) return false;
        return !needsToMove(field, x, y);
    }

    /** Whether a cell is somewhere to stay: out of and not beside the fire, and unable to burn. */
    public static boolean isSafe(FireField field, int x, int y) {
        return !FireEffects.refusedAsRefuge(field, x, y) && !field.canBurn(x, y);
    }

    /** The refuge's grid index, or -1 when the unit is not in the fire's way or nothing in reach will do. */
    public static int refuge(NavigationGrid grid, FireField field, int x, int y) {
        if (!needsToMove(field, x, y)) return -1;
        boolean inFire = field.isBurning(x, y);
        int[] cost = new int[SIDE * SIDE];
        Arrays.fill(cost, Integer.MAX_VALUE);
        int[][] buckets = new int[BUCKETS][SIDE * 4];
        int[] sizes = new int[BUCKETS];
        int start = local(x, y, x, y);
        cost[start] = 0;
        buckets[0][sizes[0]++] = start;
        int pending = 1, clear = -1, notBurning = -1;
        for (int at = 0; pending > 0; at++) {
            int slot = at % BUCKETS;
            while (sizes[slot] > 0) {
                int l = buckets[slot][--sizes[slot]];
                pending--;
                if (cost[l] != at) continue;
                int cx = x - SEARCH_RADIUS + l % SIDE, cy = y - SEARCH_RADIUS + l / SIDE;
                if (!field.isBurning(cx, cy)) {
                    if (isSafe(field, cx, cy)) return grid.index(cx, cy);
                    if (clear < 0 && !FireEffects.refusedAsRefuge(field, cx, cy)) clear = grid.index(cx, cy);
                    if (notBurning < 0 && l != start) notBurning = grid.index(cx, cy);
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cx + dx, ny = cy + dy;
                        if ((dx == 0 && dy == 0) || Math.abs(nx - x) > SEARCH_RADIUS || Math.abs(ny - y) > SEARCH_RADIUS) continue;
                        if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                        int n = local(x, y, nx, ny);
                        int c = at + (field.isBurning(nx, ny) ? THROUGH_FIRE_STEP : 1);
                        if (c >= cost[n]) continue;
                        cost[n] = c;
                        int s = c % BUCKETS;
                        if (sizes[s] == buckets[s].length) buckets[s] = Arrays.copyOf(buckets[s], sizes[s] * 2);
                        buckets[s][sizes[s]++] = n;
                        pending++;
                    }
                }
            }
        }
        if (clear >= 0) return clear;
        return inFire ? notBurning : -1;
    }

    private static int local(int ox, int oy, int x, int y) {
        return (y - oy + SEARCH_RADIUS) * SIDE + (x - ox + SEARCH_RADIUS);
    }
}
