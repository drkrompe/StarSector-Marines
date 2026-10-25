package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Lays a {@link Road} a {@link Deferred} resolves with the works: the way along
 * the streets already standing between its ends ({@code mission-layout.md}),
 * where a city's ground left no open country to carve one across. The way keeps
 * to the widest streets it can (a boulevard before a back street, a yard or a lot
 * only where no street joins), never enters a building nor ground the layout
 * holds, and is cleared: a prop standing in its carriageway is taken off. Reported
 * as a carved road is, its cells and its two ends.
 */
final class LayoutStreets {

    /** Cost of a step on road whose clearance is c: {@code 1 + NARROW / c²}, so a narrow street costs several wide ones. */
    static final float NARROW = 8f;
    /** Cost of a step off the road, on open ground. */
    static final float OFF_ROAD = 20f;
    /** Clearance a road cell is measured to at most; wider counts the same. */
    static final int WIDE = 4;
    /** Cells either side of the way a prop is cleared from. */
    static final int CLEAR_HALF = 1;

    private LayoutStreets() {}

    static Laid follow(GenContext ctx, LayoutGround ground, Road r, boolean[][] held) {
        PrecinctPlan plan = ground.plan();
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM), owner = ctx.get(BspKeys.PRECINCT_ROAD);
        if (plan == null || claim == null || owner == null) {
            return Laid.refused(r.name(), Laid.Kind.ROAD, r.required(), "no places to run between");
        }
        int[] a = LayoutRoads.endpoint(plan, claim, owner, r.from(), ctx.width, ctx.height);
        int[] b = LayoutRoads.endpoint(plan, claim, owner, r.to(), ctx.width, ctx.height);
        if (a == null || b == null) {
            return Laid.refused(r.name(), Laid.Kind.ROAD, r.required(),
                    "no place " + (a == null ? r.from().place() : r.to().place()));
        }
        int w = ctx.width, h = ctx.height;
        boolean[][] road = new boolean[w][h], open = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                road[x][y] = ctx.topology.getBuildingId(x, y) == 0 && !ctx.topology.isWall(x, y)
                        && (held == null || !held[x][y]) && ground.isRoad(x, y);
            }
        }
        int[] ends = {index(plan, r.from().place()), index(plan, r.to().place())};
        List<Doodad> props = clearable(ctx, road, claim, ends);
        // A prop on a street is cleared, so the way may run through it; off the street only open ground.
        boolean[][] cleared = cells(props, w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                open[x][y] = road[x][y] && (ground.walkable(x, y) || cleared[x][y])
                        || !road[x][y] && ground.walkable(x, y) && ctx.topology.getBuildingId(x, y) == 0
                        && (held == null || !held[x][y]);
            }
        }
        List<int[]> way = way(open, road, a, b);
        if (way == null) {
            return Laid.refused(r.name(), Laid.Kind.ROAD, r.required(), "no way along the streets between its ends");
        }
        int taken = clear(ctx, props, way);
        int onRoad = 0;
        for (int[] c : way) if (road[c[0]][c[1]]) onRoad++;
        return new Laid(r.name(), Laid.Kind.ROAD, Laid.Status.LAID,
                way.size() + " cells, " + Math.round(100f * onRoad / way.size()) + "% on road, " + taken + " props cleared",
                r.required(), way, List.of(a, b), List.of());
    }

    /**
     * The cheapest way from {@code a} to {@code b} over the {@code open} cells, four-connected, each step priced by
     * {@link #cost}; {@code null} where none joins them. The ends are snapped to the nearest open cell.
     */
    static List<int[]> way(boolean[][] open, boolean[][] road, int[] a, int[] b) {
        int w = open.length, h = open[0].length;
        int[] from = snap(open, a), to = snap(open, b);
        if (from == null || to == null) return null;
        int[][] clearance = clearance(road);
        float[] dist = new float[w * h];
        Arrays.fill(dist, Float.MAX_VALUE);
        int[] prev = new int[w * h];
        Arrays.fill(prev, -1);
        int start = from[0] * h + from[1], goal = to[0] * h + to[1];
        dist[start] = 0f;
        PriorityQueue<float[]> queue = new PriorityQueue<>((p, q) -> Float.compare(p[0], q[0]));
        queue.add(new float[]{0f, start});
        while (!queue.isEmpty()) {
            float[] top = queue.poll();
            int at = (int) top[1];
            if (top[0] > dist[at]) continue;
            if (at == goal) break;
            int x = at / h, y = at % h;
            for (int[] d : STEPS) {
                int nx = x + d[0], ny = y + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || !open[nx][ny]) continue;
                int next = nx * h + ny;
                float nd = dist[at] + cost(road[nx][ny], clearance[nx][ny]);
                if (nd >= dist[next]) continue;
                dist[next] = nd;
                prev[next] = at;
                queue.add(new float[]{nd, next});
            }
        }
        if (dist[goal] == Float.MAX_VALUE) return null;
        List<int[]> out = new ArrayList<>();
        for (int at = goal; at != -1; at = prev[at]) out.add(new int[]{at / h, at % h});
        Collections.reverse(out);
        return out;
    }

    /** A step onto a cell: cheap on a wide street, dearer on a narrow one, dear off the road. */
    static float cost(boolean road, int clearance) {
        if (!road) return OFF_ROAD;
        int c = Math.max(1, Math.min(WIDE, clearance));
        return 1f + NARROW / (c * c);
    }

    /**
     * For each road cell, the steps (eight-connected, to at most {@link #WIDE}) to the nearest cell that is not road:
     * one on a street's kerb row, more toward the middle of a wide one. Zero off the road.
     */
    static int[][] clearance(boolean[][] road) {
        int w = road.length, h = road[0].length;
        int[][] out = new int[w][h];
        ArrayDeque<int[]> open = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!road[x][y]) continue;
                out[x][y] = WIDE;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1 || edge(road, x, y)) {
                    out[x][y] = 1;
                    open.add(new int[]{x, y});
                }
            }
        }
        while (!open.isEmpty()) {
            int[] c = open.poll();
            int next = out[c[0]][c[1]] + 1;
            if (next > WIDE) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + dx, y = c[1] + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h || !road[x][y] || out[x][y] <= next) continue;
                    out[x][y] = next;
                    open.add(new int[]{x, y});
                }
            }
        }
        return out;
    }

    private static boolean edge(boolean[][] road, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) if (!road[x + dx][y + dy]) return true;
        }
        return false;
    }

    /** The nearest open cell to {@code p} within a few cells, or {@code null}. */
    private static int[] snap(boolean[][] open, int[] p) {
        int w = open.length, h = open[0].length;
        for (int r = 0; r <= SNAP; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = p[0] + dx, y = p[1] + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h && open[x][y]) return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static final int SNAP = 6;

    /**
     * The props a way along the streets may take off: every one with cover that stands wholly on road, but none on the
     * ground of the places at its ends (a base's or a redoubt's own works).
     */
    static List<Doodad> clearable(GenContext ctx, boolean[][] road, int[][] claim, int[] ends) {
        List<Doodad> out = new ArrayList<>();
        for (Doodad d : ctx.doodads) {
            if (d.cover == Doodad.COVER_NONE) continue;
            boolean onStreet = true;
            for (int dx = 0; dx < d.footprintCellsX && onStreet; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) {
                    int x = d.cellX + dx, y = d.cellY + dy;
                    if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height || !road[x][y]
                            || claim[x][y] != GrownTrunkPlan.UNOWNED && (claim[x][y] == ends[0] || claim[x][y] == ends[1])) {
                        onStreet = false;
                        break;
                    }
                }
            }
            if (onStreet) out.add(d);
        }
        return out;
    }

    private static boolean[][] cells(List<Doodad> props, int w, int h) {
        boolean[][] out = new boolean[w][h];
        for (Doodad d : props) {
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) out[d.cellX + dx][d.cellY + dy] = true;
            }
        }
        return out;
    }

    /** Takes off each of {@code props} within {@link #CLEAR_HALF} of the way, its ground opened again; returns how many. */
    static int clear(GenContext ctx, List<Doodad> props, List<int[]> way) {
        boolean[][] band = new boolean[ctx.width][ctx.height];
        for (int[] c : way) {
            for (int dx = -CLEAR_HALF; dx <= CLEAR_HALF; dx++) {
                for (int dy = -CLEAR_HALF; dy <= CLEAR_HALF; dy++) {
                    int x = c[0] + dx, y = c[1] + dy;
                    if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) band[x][y] = true;
                }
            }
        }
        List<Doodad> gone = new ArrayList<>();
        for (Doodad d : props) {
            boolean touches = false;
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) touches |= band[d.cellX + dx][d.cellY + dy];
            }
            if (!touches) continue;
            gone.add(d);
            for (int dx = 0; dx < d.footprintCellsX; dx++) {
                for (int dy = 0; dy < d.footprintCellsY; dy++) {
                    int x = d.cellX + dx, y = d.cellY + dy;
                    ctx.grid.setWalkable(x, y, true);
                    ctx.grid.setSeeThrough(x, y, true);
                    ctx.topology.setFixture(x, y, false);
                }
            }
        }
        ctx.doodads.removeAll(gone);
        return gone.size();
    }

    private static int index(PrecinctPlan plan, String place) {
        for (int i = 0; i < plan.precincts().size(); i++) if (plan.precincts().get(i).name().equals(place)) return i;
        return -1;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
}
