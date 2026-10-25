package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/** Generation-only access proofs. Cardinal floods honor both sides of shared edges. */
public final class LandscapeAccess {
    private LandscapeAccess() {}

    /** Retain a five-cell-wide tree of existing routes between required places in each component. */
    public static void reserveRoutes(NavigationGrid grid, List<int[]> anchors, boolean[] reserved) {
        reserveRoutes(grid, anchors, reserved, null);
    }

    /**
     * As above, with ground the routes go round ({@code avoid}, or null for none): sealed ground, crossed only at
     * the gaps between its bands. An anchor reached only across it keeps no route there; the ridges raised on it
     * prove their own access.
     */
    public static void reserveRoutes(NavigationGrid grid, List<int[]> anchors, boolean[] reserved, boolean[] avoid) {
        int w = grid.getWidth(), h = grid.getHeight();
        int[] parent = new int[w * h], queue = new int[w * h];
        Arrays.fill(parent, -1);
        boolean[] traced = new boolean[parent.length];
        for (int[] anchor : anchors) {
            int x = anchor[0], y = anchor[1];
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
            int start = y * w + x;
            if (parent[start] < 0) {
                int head = 0, tail = 0; queue[tail++] = start; parent[start] = start;
                while (head < tail) {
                    int at = queue[head++], ax = at % w, ay = at / w;
                    for (Direction d : Direction.CARDINALS) {
                        int nx = ax + d.dx, ny = ay + d.dy;
                        if (!grid.inBounds(nx, ny) || !grid.isWalkable(nx, ny) || !open(grid, ax, ay, d)) continue;
                        int next = ny * w + nx;
                        if (parent[next] >= 0 || (avoid != null && avoid[next])) continue;
                        parent[next] = at; queue[tail++] = next;
                    }
                }
            }
            for (int at = start; !traced[at]; at = parent[at]) {
                traced[at] = true;
                for (int yy = Math.max(0, at / w - 2); yy <= Math.min(h - 1, at / w + 2); yy++)
                    for (int xx = Math.max(0, at % w - 2); xx <= Math.min(w - 1, at % w + 2); xx++)
                        reserved[yy * w + xx] = true;
            }
        }
    }

    /**
     * A sufficient local no-islands proof: every remaining cell in a box surrounding
     * the proposed obstacle must still connect inside that box. External routes can
     * therefore replace any lost crossing by this local detour. Conservative refusal
     * is intentional; trees are optional and never justify a whole-map path repair.
     */
    public static boolean canClose(NavigationGrid grid, boolean[] earlier,
                                   int left, int bottom, int right, int top,
                                   BiPredicate<Integer, Integer> closes) {
        int w = grid.getWidth();
        int l = Math.max(0, left - 2), b = Math.max(0, bottom - 2);
        int r = Math.min(w - 1, right + 2), t = Math.min(grid.getHeight() - 1, top + 2);
        int bw = r - l + 1, bh = t - b + 1, total = 0, start = -1;
        boolean[] usable = new boolean[bw * bh];
        for (int y = b; y <= t; y++) for (int x = l; x <= r; x++) {
            int at = (y - b) * bw + x - l;
            if (grid.isWalkable(x, y) && !earlier[y * w + x] && !closes.test(x, y)) {
                usable[at] = true; total++; start = at;
            }
        }
        if (start < 0) return false;
        int[] queue = new int[usable.length]; int head = 0, tail = 0;
        queue[tail++] = start; usable[start] = false;
        while (head < tail) {
            int at = queue[head++], x = l + at % bw, y = b + at / bw;
            for (Direction d : Direction.CARDINALS) {
                int nx = x + d.dx, ny = y + d.dy;
                if (nx < l || nx > r || ny < b || ny > t || !open(grid, x, y, d)) continue;
                int next = (ny - b) * bw + nx - l;
                if (!usable[next]) continue;
                usable[next] = false; queue[tail++] = next;
            }
        }
        return tail == total;
    }

    /**
     * The same local proof for a hull {@code radius} cells either side of its centre line
     * ({@link com.dillon.starsectormarines.battle.vehicle.VehicleClearance}): any two cells the hull could stand on
     * and drive between inside the box before the obstacle must still be joined after it. Cells that lose their
     * footing to the obstacle may go; a pocket that was already apart from its neighbours stays apart. The box
     * reaches {@code radius + 2} past the obstacle, so a route's first and last cells inside it never lose footing
     * and any route through it is replaced by a local detour.
     */
    public static boolean keepsClearance(NavigationGrid grid, boolean[] earlier,
                                         int left, int bottom, int right, int top,
                                         BiPredicate<Integer, Integer> closes, int radius) {
        int w = grid.getWidth(), m = radius + 2;
        int l = Math.max(0, left - m), b = Math.max(0, bottom - m);
        int r = Math.min(w - 1, right + m), t = Math.min(grid.getHeight() - 1, top + m);
        int bw = r - l + 1, bh = t - b + 1;
        BiPredicate<Integer, Integer> before = (x, y) -> !earlier[y * w + x];
        int[] was = label(grid, l, b, bw, bh, radius, before);
        int[] now = label(grid, l, b, bw, bh, radius, (x, y) -> before.test(x, y) && !closes.test(x, y));
        // Each earlier component's surviving cells must all carry one new label.
        int[] joined = new int[bw * bh + 1];
        for (int i = 0; i < was.length; i++) {
            if (was[i] == 0 || now[i] == 0) continue;
            if (joined[was[i]] == 0) joined[was[i]] = now[i];
            else if (joined[was[i]] != now[i]) return false;
        }
        return true;
    }

    /** 4-connected labels, inside the box, of the cells a hull of {@code radius} stands on over ground {@code open} keeps. */
    private static int[] label(NavigationGrid grid, int l, int b, int bw, int bh, int radius,
                               BiPredicate<Integer, Integer> open) {
        boolean[] fits = new boolean[bw * bh];
        for (int y = b; y < b + bh; y++) for (int x = l; x < l + bw; x++)
            fits[(y - b) * bw + x - l] = fits(grid, x, y, radius, open);
        int[] label = new int[bw * bh], queue = new int[bw * bh];
        int next = 0;
        for (int i = 0; i < fits.length; i++) {
            if (!fits[i] || label[i] != 0) continue;
            int head = 0, tail = 0; queue[tail++] = i; label[i] = ++next;
            while (head < tail) {
                int at = queue[head++], x = at % bw, y = at / bw;
                for (Direction d : Direction.CARDINALS) {
                    int nx = x + d.dx, ny = y + d.dy, n = ny * bw + nx;
                    if (nx < 0 || ny < 0 || nx >= bw || ny >= bh || !fits[n] || label[n] != 0) continue;
                    label[n] = next; queue[tail++] = n;
                }
            }
        }
        return label;
    }

    /** Every cell of the hull's block walkable and kept by {@code open}, and every edge inside it open. */
    private static boolean fits(NavigationGrid grid, int cx, int cy, int r, BiPredicate<Integer, Integer> open) {
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++)
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !open.test(x, y)) return false;
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++) {
            if (x < cx + r && !open(grid, x, y, Direction.E)) return false;
            if (y < cy + r && !open(grid, x, y, Direction.N)) return false;
        }
        return true;
    }

    private static boolean open(NavigationGrid grid, int x, int y, Direction d) {
        return grid.isEdgePassable(x, y, d) && grid.isEdgePassable(x + d.dx, y + d.dy, d.opposite());
    }
}
