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
                        if (parent[next] >= 0) continue;
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

    private static boolean open(NavigationGrid grid, int x, int y, Direction d) {
        return grid.isEdgePassable(x, y, d) && grid.isEdgePassable(x + d.dx, y + d.dy, d.opposite());
    }
}
