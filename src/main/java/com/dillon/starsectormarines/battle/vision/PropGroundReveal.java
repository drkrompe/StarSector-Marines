package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation-only: how much of the ground under a large solid prop the player is shown.
 *
 * <p>A prop's footprint is opaque, so the shadowcast reveals the faces turned toward the player
 * and never the cells behind them. The sprite is drawn over the fog through its own alpha, which
 * leaves the part of the footprint the art does not cover (corners, gaps between engine bells)
 * black, as though the ground there had never been lit. Nothing is wrong with sight; the art
 * simply does not fill its footprint.
 *
 * <p>This shows the ground a short way in from any footprint cell the player already sees, as it
 * would look if the prop were not in the way of that near strip. It is not line of sight: the far
 * side of a hull stays dark until something sees it, and nothing outside the footprint changes.
 * Gates (unit visibility, building roofs, sight) keep reading the real reveal bitmap.
 */
final class PropGroundReveal {
    /** Cells in from a revealed footprint cell that are shown, 8-connected through the footprint. */
    static final int DEPTH = 4;

    /** Footprints worth the pass; smaller props are either fully covered by art or fully revealed. */
    static final int MIN_SIDE_CELLS = 4;

    private final List<int[]> footprints = new ArrayList<>();
    private int[] queue = new int[0];
    private byte[] depth = new byte[0];

    static boolean qualifies(int footprintCellsX, int footprintCellsY) {
        return Math.min(footprintCellsX, footprintCellsY) >= MIN_SIDE_CELLS;
    }

    void register(int cellX, int cellY, int cellsX, int cellsY) {
        footprints.add(new int[]{cellX, cellY, cellsX, cellsY});
    }

    boolean isEmpty() {
        return footprints.isEmpty();
    }

    /**
     * Rewrites {@code shown} for every registered footprint from the real reveal and records each
     * flip in {@code log}. Cells outside the footprints are not touched.
     */
    void refresh(NavigationGrid grid, boolean[] revealed, boolean[] shown, RevealChangeLog log) {
        for (int[] f : footprints) refreshOne(grid, revealed, shown, log, f[0], f[1], f[2], f[3]);
    }

    private void refreshOne(NavigationGrid grid, boolean[] revealed, boolean[] shown,
                            RevealChangeLog log, int x0, int y0, int w, int h) {
        int gw = grid.getWidth();
        int gh = grid.getHeight();
        int minX = Math.max(0, x0), minY = Math.max(0, y0);
        int maxX = Math.min(gw - 1, x0 + w - 1), maxY = Math.min(gh - 1, y0 + h - 1);
        if (maxX < minX || maxY < minY) return;
        int rw = maxX - minX + 1, rh = maxY - minY + 1;
        int n = rw * rh;
        if (queue.length < n) {
            queue = new int[n];
            depth = new byte[n];
        }
        int head = 0, tail = 0;
        for (int ly = 0; ly < rh; ly++) for (int lx = 0; lx < rw; lx++) {
            int local = ly * rw + lx;
            int idx = (minY + ly) * gw + minX + lx;
            if (grid.blocksStructuralLineOfSightAt(idx) && revealed[idx]) {
                depth[local] = 0;
                queue[tail++] = local;
            } else {
                depth[local] = -1;
            }
        }
        while (head < tail) {
            int local = queue[head++];
            int d = depth[local];
            if (d >= DEPTH) continue;
            int lx = local % rw, ly = local / rw;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = lx + dx, ny = ly + dy;
                if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue;
                int next = ny * rw + nx;
                if (depth[next] != -1) continue;
                if (!grid.blocksStructuralLineOfSightAt((minY + ny) * gw + minX + nx)) continue;
                depth[next] = (byte) (d + 1);
                queue[tail++] = next;
            }
        }
        for (int ly = 0; ly < rh; ly++) for (int lx = 0; lx < rw; lx++) {
            int idx = (minY + ly) * gw + minX + lx;
            boolean want = revealed[idx] || depth[ly * rw + lx] >= 0;
            if (shown[idx] != want) {
                shown[idx] = want;
                log.note(idx);
            }
        }
    }
}
