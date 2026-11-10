package com.dillon.starsectormarines.battle.world.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * A volcanic world's older lava flows ({@code voids.md}, Lava flows), GL-free: tongues of cooled crust on the plain,
 * each a lobe that spread out from where the melt broke its bank, wandering, widening toward a rounded toe and now
 * and then splitting. A flow is walkable ground, only its look changes; it takes only cells {@code free} allows, and
 * stops where it meets anything else, as a flow ponds against what stands in its way.
 */
public final class LavaFlows {

    /** The fewest cells along a bank between two breakouts. UNTUNED. */
    static final int SPACING = 26;
    /** The most breakouts off one channel. UNTUNED. */
    static final int MAX_BREAKOUTS = 6;
    /** A lobe's length, cells. UNTUNED. */
    static final int MIN_LENGTH = 10, MAX_LENGTH = 24;
    /** A lobe's half width at its root and its widest. UNTUNED. */
    static final float ROOT_HALF = 1.9f, TOE_HALF = 3.8f;
    /** How far a lobe's heading wanders each cell, radians. UNTUNED. */
    static final float WANDER = .22f;
    /** The chance a lobe splits halfway down. UNTUNED. */
    static final float SPLIT = .45f;

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};

    private LavaFlows() { }

    /**
     * The older flows off a lava channel ({@code lava}, its cells): lobes from breakouts along its banks. A flow
     * takes ground {@code free} allows, and within {@link #ROOT_REACH} of the melt also its lip, where {@code lip}
     * allows (natural ground the drop keeps clear, never a crossing), so it is seen to break from the melt itself.
     */
    public static boolean[] offChannel(int w, int h, boolean[] lava, IntPredicate free, IntPredicate lip, int seed) {
        boolean[] near = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!lava[y * w + x]) continue;
            for (int dy = -ROOT_REACH; dy <= ROOT_REACH; dy++) for (int dx = -ROOT_REACH; dx <= ROOT_REACH; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) near[ny * w + nx] = true;
            }
        }
        IntPredicate may = i -> !lava[i] && (free.test(i) || near[i] && lip.test(i));
        return breakouts(w, h, lava, may, seed);
    }

    private static boolean[] breakouts(int w, int h, boolean[] lava, IntPredicate free, int seed) {
        Random rnd = new Random(seed);
        List<int[]> bank = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (lava[i] || !free.test(i)) continue;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && lava[ny * w + nx]) { bank.add(new int[]{x, y}); break; }
            }
        }
        boolean[] out = new boolean[w * h];
        List<int[]> anchors = new ArrayList<>();
        for (int k = bank.size() - 1; k > 0; k--) {
            int j = rnd.nextInt(k + 1);
            int[] t = bank.get(k); bank.set(k, bank.get(j)); bank.set(j, t);
        }
        for (int[] b : bank) {
            if (anchors.size() >= MAX_BREAKOUTS) break;
            boolean near = false;
            for (int[] a : anchors) {
                int dx = a[0] - b[0], dy = a[1] - b[1];
                if (dx * dx + dy * dy < SPACING * SPACING) { near = true; break; }
            }
            if (near) continue;
            float[] away = away(w, h, lava, b[0], b[1]);
            if (away == null) continue;
            anchors.add(b);
            float heading = (float) Math.atan2(away[1], away[0]);
            int length = MIN_LENGTH + rnd.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
            lobe(w, h, out, lava, free, b[0] + .5f, b[1] + .5f, heading, length, 1f, rnd, true);
        }
        return out;
    }

    /** Older flows with no channel: long lobes come in off one edge ({@code side}, 0 east, 1 north, 2 west, 3 south). */
    public static boolean[] fromEdge(int w, int h, int side, IntPredicate free, int seed) {
        Random rnd = new Random(seed);
        boolean[] out = new boolean[w * h];
        boolean[] none = new boolean[w * h];
        int along = side % 2 == 0 ? h : w, deep = side % 2 == 0 ? w : h;
        int count = 3 + rnd.nextInt(2);
        for (int k = 0; k < count; k++) {
            float a = along * (k + .5f + (rnd.nextFloat() - .5f) * .5f) / count;
            float x, y, heading;
            switch (side) {
                case 0 -> { x = w - .5f; y = a; heading = (float) Math.PI; }
                case 1 -> { x = a; y = h - .5f; heading = (float) (-Math.PI / 2); }
                case 2 -> { x = .5f; y = a; heading = 0f; }
                default -> { x = a; y = .5f; heading = (float) (Math.PI / 2); }
            }
            int length = (int) (deep * (.35f + rnd.nextFloat() * .3f));
            lobe(w, h, out, none, free, x, y, heading, length, 1.5f, rnd, true);
        }
        return out;
    }

    /** Cells along a flow over which its crust cools from the melt's heat to cold. UNTUNED. */
    static final float HEAT_REACH = 9f;
    /** Cells from the melt within which a flow is still at the melt's heat: the drop's lip lies between. */
    static final int ROOT_REACH = 3;

    /**
     * How hot each flow cell's crust still is: 1 where the flow meets the melt ({@code lava}) or comes in off the
     * map's edge, falling to a little above 0 {@link #HEAT_REACH} cells down the flow; 0 off the flows.
     */
    public static float[] heat(int w, int h, boolean[] flows, boolean[] lava) {
        int[] dist = new int[w * h];
        Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0, tail = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!flows[i]) continue;
            boolean root = x == 0 || y == 0 || x == w - 1 || y == h - 1;
            for (int dy = -ROOT_REACH; dy <= ROOT_REACH && !root; dy++) {
                for (int dx = -ROOT_REACH; dx <= ROOT_REACH && !root; dx++) {
                    int nx = x + dx, ny = y + dy;
                    root = nx >= 0 && ny >= 0 && nx < w && ny < h && lava[ny * w + nx];
                }
            }
            if (root) { dist[i] = 0; queue[tail++] = i; }
        }
        while (head < tail) {
            int i = queue[head++], x = i % w, y = i / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int n = ny * w + nx;
                if (!flows[n] || dist[n] >= 0) continue;
                dist[n] = dist[i] + 1;
                queue[tail++] = n;
            }
        }
        float[] out = new float[w * h];
        for (int i = 0; i < out.length; i++) {
            if (!flows[i]) continue;
            out[i] = dist[i] < 0 ? .02f : Math.max(.02f, 1f - dist[i] / HEAT_REACH);
        }
        return out;
    }

    /** The way out of the channel from a bank cell: away from the lava within three cells; null where it is boxed in. */
    static float[] away(int w, int h, boolean[] lava, int x, int y) {
        float sx = 0, sy = 0;
        for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h || !lava[ny * w + nx]) continue;
            sx -= dx;
            sy -= dy;
        }
        float len = (float) Math.sqrt(sx * sx + sy * sy);
        return len < 1e-3f ? null : new float[]{sx / len, sy / len};
    }

    private static void lobe(int w, int h, boolean[] out, boolean[] lava, IntPredicate free, float x, float y,
                             float heading, int length, float scale, Random rnd, boolean mayFork) {
        int fork = mayFork && rnd.nextFloat() < SPLIT ? length / 2 : -1;
        boolean entered = false;
        for (int t = 0; t < length; t++) {
            float grow = Math.min(1f, (t + 1f) / (length * .7f));
            float half = (ROOT_HALF + (TOE_HALF - ROOT_HALF) * grow) * scale;
            if (t == length - 1) half += .6f * scale;
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            if (cx < 0 || cy < 0 || cx >= w || cy >= h) return;
            int ci = cy * w + cx;
            boolean open = lava[ci] || free.test(ci);
            if (!open && entered) return;
            entered |= open;
            if (entered) disc(w, h, out, lava, free, x, y, half);
            if (t == fork) {
                float turn = (rnd.nextBoolean() ? 1 : -1) * (.55f + rnd.nextFloat() * .3f);
                lobe(w, h, out, lava, free, x, y, heading + turn, (int) ((length - t) * .8f), scale * .8f, rnd, false);
                heading -= turn * .4f;
            }
            heading += (rnd.nextFloat() * 2f - 1f) * WANDER;
            x += (float) Math.cos(heading);
            y += (float) Math.sin(heading);
        }
    }

    private static void disc(int w, int h, boolean[] out, boolean[] lava, IntPredicate free, float x, float y,
                             float r) {
        int r0 = (int) Math.ceil(r);
        for (int dy = -r0; dy <= r0; dy++) for (int dx = -r0; dx <= r0; dx++) {
            int nx = (int) Math.floor(x) + dx, ny = (int) Math.floor(y) + dy;
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
            float ex = nx + .5f - x, ey = ny + .5f - y;
            if (ex * ex + ey * ey > r * r) continue;
            int i = ny * w + nx;
            if (!lava[i] && free.test(i)) out[i] = true;
        }
    }
}
