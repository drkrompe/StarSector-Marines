package com.dillon.starsectormarines.battle.world.gen.elevation;

import com.dillon.starsectormarines.battle.world.model.NaturalVoid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * An open-cut mine's pit ({@code voids.md}, the open pit): a terraced void cut in a box a plan reserves beside a
 * mine's head works. GL-free; the landscape pass asks it once per box and stamps what it returns.
 *
 * <p>The pit takes only the box's open ground ({@code free}): the caller's reservations already keep roads,
 * buildings, doorways, fixtures, props, the landing and the routes between required places out of it, so the pit
 * never covers any of them. Its outline is the free ground inside a rounded box, opened by
 * {@link #OPEN_RADIUS} so no neck or sliver of pit is narrower than a pit can be terraced, and only its largest
 * piece is cut. Then it is proved: every walkable cell left outside the pit that was connected before stays
 * connected to the same ground. A pocket the pit would strand is cut into the pit when it lies wholly in the box on
 * open ground (an island of rock left in the cut); any other is refused, and the map keeps no pit.
 */
public final class OpenPit {

    /**
     * The name of the ground site a plan's layout holds for an open pit; a Raid mining site names its pit so
     * ({@code RaidSiteTemplate}).
     */
    public static final String SITE = "pit";
    /** Cells the outline is opened by: no part of the pit is narrower than twice this plus one. UNTUNED. */
    public static final int OPEN_RADIUS = 6;
    /** Fewest cells worth cutting as a pit. UNTUNED. */
    public static final int MIN_CELLS = 220;
    /** How far the rounded outline swells and dips, as a share of the box's half-size. */
    static final float WANDER = .10f;
    /** The rounded box's exponent: 2 an ellipse, higher squarer. */
    static final float ROUNDNESS = 3.2f;

    /**
     * The benches going down, shallowest first: each a working bench 7 m below the last, cut 2 cells back from the
     * face above, to a floor 42 m down: the owner's pick (raid-mine-pit-depth).
     */
    public static final List<NaturalVoid.Step> STEPS = List.of(
            new NaturalVoid.Step(7f, .45f),
            new NaturalVoid.Step(14f, 2.45f),
            new NaturalVoid.Step(21f, 4.45f),
            new NaturalVoid.Step(28f, 6.45f),
            new NaturalVoid.Step(35f, 8.45f),
            new NaturalVoid.Step(42f, 10.45f));
    /** The camera the natural styles are drawn from (`voids.md`, Gorge river). */
    public static final float EYE_METERS = 120f;
    public static final String WALL_TEXTURE = "graphics/battle/voids/pit-bench-wall.png";
    public static final float WALL_TILE_METERS = 12f;

    private OpenPit() { }

    /** The open pit's look: its benches, the camera, its blasted face, and its conveyors. */
    public static NaturalVoid look(int seed, List<NaturalVoid.Belt> belts) {
        return new NaturalVoid(NaturalVoid.Style.OPEN_PIT, STEPS, EYE_METERS, WALL_TEXTURE, WALL_TILE_METERS, seed,
                belts);
    }

    /**
     * The pit's cells in the inclusive box {@code x0..x1, y0..y1}, row-major over a {@code w} by {@code h} map, or
     * {@code null} when the box keeps too little open ground or the cut would strand ground it may not.
     *
     * @param free     cells the pit may take: open natural ground nothing is reserved on
     * @param walkable cells anything stands on now
     */
    public static boolean[] plan(int w, int h, int x0, int y0, int x1, int y1, IntPredicate free,
                                 IntPredicate walkable, int seed) {
        x0 = Math.max(1, x0); y0 = Math.max(1, y0);
        x1 = Math.min(w - 2, x1); y1 = Math.min(h - 2, y1);
        if (x1 - x0 < 2 * OPEN_RADIUS || y1 - y0 < 2 * OPEN_RADIUS) return null;
        // The outline is rounded within the open ground the site has, not the site itself, which may run to the
        // map's edge or under a reservation along one side.
        int fx0 = x1, fy0 = y1, fx1 = x0, fy1 = y0;
        boolean[] open = new boolean[w * h];
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            int i = y * w + x;
            if (!free.test(i) || !walkable.test(i)) continue;
            open[i] = true;
            fx0 = Math.min(fx0, x); fx1 = Math.max(fx1, x); fy0 = Math.min(fy0, y); fy1 = Math.max(fy1, y);
        }
        if (fx1 - fx0 < 2 * OPEN_RADIUS || fy1 - fy0 < 2 * OPEN_RADIUS) return null;
        boolean[] cut = new boolean[w * h];
        float cx = (fx0 + fx1) / 2f, cy = (fy0 + fy1) / 2f, hx = (fx1 - fx0) / 2f + .5f, hy = (fy1 - fy0) / 2f + .5f;
        for (int y = fy0; y <= fy1; y++) for (int x = fx0; x <= fx1; x++) {
            int i = y * w + x;
            if (!open[i]) continue;
            float ax = Math.abs(x - cx) / hx, ay = Math.abs(y - cy) / hy;
            float angle = (float) Math.atan2(y - cy, x - cx);
            float swell = 1f - WANDER * (.5f + .5f * (float) Math.sin(3 * angle + seed % 7)
                    * (float) Math.cos(2 * angle + seed % 5));
            cut[i] = Math.pow(ax, ROUNDNESS) + Math.pow(ay, ROUNDNESS) <= Math.pow(swell, ROUNDNESS);
        }
        cut = and(grow(shrink(cut, w, h, OPEN_RADIUS), w, h, OPEN_RADIUS), cut);
        cut = largest(cut, w, h);
        if (count(cut) < MIN_CELLS) return null;
        return proved(w, h, cut, x0, y0, x1, y1, free, walkable);
    }

    /**
     * The cut with every pocket it would strand inside the box taken in, or {@code null} when it strands ground it
     * may not take: any piece of a connected stretch of walkable ground, other than the piece holding most of it,
     * that is not wholly open ground inside the box.
     */
    static boolean[] proved(int w, int h, boolean[] cut, int x0, int y0, int x1, int y1, IntPredicate free,
                            IntPredicate walkable) {
        int[] before = label(w, h, i -> walkable.test(i));
        boolean[] pit = cut.clone();
        int[] after = label(w, h, i -> walkable.test(i) && !pit[i]);
        int pieces = 0;
        for (int v : after) pieces = Math.max(pieces, v);
        // For each stretch of ground before the cut, the piece of it after the cut that holds most of its cells.
        int[] size = new int[pieces + 1];
        for (int v : after) if (v > 0) size[v]++;
        int stretches = 0;
        for (int v : before) stretches = Math.max(stretches, v);
        int[] main = new int[stretches + 1];
        for (int i = 0; i < after.length; i++) {
            if (after[i] == 0) continue;
            int s = before[i];
            if (main[s] == 0 || size[after[i]] > size[main[s]]) main[s] = after[i];
        }
        boolean[] stranded = new boolean[pieces + 1], takeable = new boolean[pieces + 1];
        Arrays.fill(takeable, true);
        for (int i = 0; i < after.length; i++) {
            int p = after[i];
            if (p == 0 || p == main[before[i]]) continue;
            stranded[p] = true;
            int x = i % w, y = i / w;
            if (x < x0 || x > x1 || y < y0 || y > y1 || !free.test(i)) takeable[p] = false;
        }
        for (int p = 1; p <= pieces; p++) if (stranded[p] && !takeable[p]) return null;
        for (int i = 0; i < after.length; i++) if (after[i] > 0 && stranded[after[i]]) pit[i] = true;
        return pit;
    }

    /**
     * Where a conveyor runs from the pit to a works at {@code (tx, ty)}: from a point on the floor (the pit cell
     * deepest from its edge nearest the works' side) along the straight line toward the works, rising at the last
     * pit cell on that line and ending at {@code (tx, ty)}.
     */
    public static NaturalVoid.Belt belt(int w, int h, boolean[] pit, float tx, float ty) {
        int[] depth = chamfer(w, h, pit);
        int best = -1;
        float bestScore = -Float.MAX_VALUE;
        for (int i = 0; i < pit.length; i++) {
            if (!pit[i]) continue;
            float dx = i % w - tx, dy = i / w - ty;
            // Deep in the pit first, then near the works.
            float score = Math.min(depth[i], 12) * 100f - (float) Math.sqrt(dx * dx + dy * dy);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best < 0) return null;
        float fx = best % w + .5f, fy = best / w + .5f;
        float rx = fx, ry = fy;
        float len = (float) Math.hypot(tx - fx, ty - fy);
        for (float t = 0f; t <= len; t += .25f) {
            float x = fx + (tx - fx) * t / len, y = fy + (ty - fy) * t / len;
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            if (cx < 0 || cy < 0 || cx >= w || cy >= h || !pit[cy * w + cx]) break;
            rx = x; ry = y;
        }
        return new NaturalVoid.Belt(fx, fy, rx, ry, tx, ty);
    }

    // ----- grids -----

    static int count(boolean[] m) {
        int n = 0;
        for (boolean b : m) if (b) n++;
        return n;
    }

    private static boolean[] and(boolean[] a, boolean[] b) {
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] && b[i];
        return out;
    }

    /** Cells whose whole disc of radius {@code r} is set. */
    static boolean[] shrink(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!m[y * w + x]) continue;
            boolean all = true;
            for (int dy = -r; dy <= r && all; dy++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || !m[ny * w + nx]) { all = false; break; }
            }
            out[y * w + x] = all;
        }
        return out;
    }

    /** Cells within a disc of radius {@code r} of a set one. */
    static boolean[] grow(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!m[y * w + x]) continue;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) out[ny * w + nx] = true;
            }
        }
        return out;
    }

    /** The largest four-connected piece. */
    static boolean[] largest(boolean[] m, int w, int h) {
        int[] label = label(w, h, i -> m[i]);
        int pieces = 0;
        for (int v : label) pieces = Math.max(pieces, v);
        int[] size = new int[pieces + 1];
        for (int v : label) if (v > 0) size[v]++;
        int best = 0;
        for (int p = 1; p <= pieces; p++) if (size[p] > size[best]) best = p;
        boolean[] out = new boolean[m.length];
        for (int i = 0; i < m.length; i++) out[i] = best > 0 && label[i] == best;
        return out;
    }

    /** Four-connected pieces of the set cells, numbered from 1; 0 where unset. */
    static int[] label(int w, int h, IntPredicate set) {
        int[] label = new int[w * h];
        int next = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int s = 0; s < label.length; s++) {
            if (label[s] != 0 || !set.test(s)) continue;
            label[s] = ++next;
            queue.add(s);
            while (!queue.isEmpty()) {
                int at = queue.poll(), x = at % w, y = at / w;
                int[] around = {x > 0 ? at - 1 : -1, x < w - 1 ? at + 1 : -1, y > 0 ? at - w : -1,
                        y < h - 1 ? at + w : -1};
                for (int n : around) {
                    if (n < 0 || label[n] != 0 || !set.test(n)) continue;
                    label[n] = next;
                    queue.add(n);
                }
            }
        }
        return label;
    }

    /** Cells in from the nearest unset cell, four-connected; 0 off the set. */
    static int[] chamfer(int w, int h, boolean[] m) {
        int[] d = new int[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < d.length; i++) {
            d[i] = m[i] ? Integer.MAX_VALUE : 0;
            if (!m[i]) queue.add(i);
        }
        List<Integer> edge = new ArrayList<>();
        for (int i = 0; i < d.length; i++) {
            int x = i % w, y = i / w;
            if (m[i] && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) { d[i] = 1; edge.add(i); }
        }
        queue.addAll(edge);
        while (!queue.isEmpty()) {
            int at = queue.poll(), x = at % w, y = at / w;
            int[] around = {x > 0 ? at - 1 : -1, x < w - 1 ? at + 1 : -1, y > 0 ? at - w : -1, y < h - 1 ? at + w : -1};
            for (int n : around) {
                if (n < 0 || d[n] <= d[at] + 1) continue;
                d[n] = d[at] + 1;
                queue.add(n);
            }
        }
        return d;
    }
}
