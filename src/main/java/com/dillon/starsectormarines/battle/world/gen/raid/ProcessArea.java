package com.dillon.starsectormarines.battle.world.gen.raid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A refinery's process area on one side of its spine road, planned as a refinery's plot plan is: the units in a row
 * of identical blocks along the pipe rack that runs beside the road, each block the same from the rack outward (its
 * column and stripper by the rack, its fired heater behind them), with fire lanes between the blocks and round the
 * far edge, wide enough for a vehicle and a squad. Pure: cells in, cells out; {@link RefineryGround} lays it.
 *
 * <p>In a block's own frame {@code u} runs from the spine edge outward and {@code v} along the road:
 * <pre>
 *   u: 0..RACK-1 rack corridor | column, stripper | heater | ... | far lane
 *   v: lane | block (BLOCK_V rows) | lane | block | ... | lane
 * </pre>
 */
public final class ProcessArea {

    /** The corridor along the spine edge the rack stands over: walkable under it. */
    static final int RACK = 3;
    /** A fire lane's width: a vehicle and a squad abreast. UNTUNED. */
    static final int LANE = 5;
    /** The column's and the stripper's discs, and the fired heater's box. */
    static final int COLUMN = 6, STRIPPER = 4, HEATER_U = 8, HEATER_V = 6;
    /** A block's extent from the corridor outward, and along the road. */
    static final int BLOCK_U = 17, BLOCK_V = 11;

    private ProcessArea() { }

    /** A disc {@code size} cells across from {@code (x, y)}, or a box {@code w} by {@code h}. */
    public record Box(int x, int y, int w, int h) {
        boolean contains(int cx, int cy) { return cx >= x && cy >= y && cx < x + w && cy < y + h; }
    }

    /**
     * One unit: its column and stripper discs, its heater's box, the pad under them all, and the cell where a charge
     * on the unit is set (beside its heater, toward the lane).
     */
    public record Block(Box column, Box stripper, Box heater, Box pad, int chargeX, int chargeY) { }

    /** The plan: its blocks, the rack corridor beside the spine, and its lanes. */
    public record Plan(int x0, int y0, int x1, int y1, List<Block> blocks, Box rack, List<Box> lanes) {

        /** Whether a cell is solid: a disc or a heater, the only things the plan stands on its ground. */
        public boolean blocked(int x, int y) {
            for (Block b : blocks) {
                if (disc(b.column(), x, y) || disc(b.stripper(), x, y) || b.heater().contains(x, y)) return true;
            }
            return false;
        }

        /**
         * Whether every cell open in the plan is reached from the spine edge through open cells: every charge, every
         * lane, nothing walled in.
         */
        public boolean allReached(boolean spineLeft) {
            int w = x1 - x0 + 1, h = y1 - y0 + 1;
            boolean[] seen = new boolean[w * h];
            ArrayDeque<int[]> open = new ArrayDeque<>();
            int sx = spineLeft ? x0 : x1;
            for (int y = y0; y <= y1; y++) {
                if (!blocked(sx, y)) {
                    seen[(y - y0) * w + sx - x0] = true;
                    open.add(new int[]{sx, y});
                }
            }
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!open.isEmpty()) {
                int[] c = open.poll();
                for (int[] s : steps) {
                    int x = c[0] + s[0], y = c[1] + s[1];
                    if (x < x0 || y < y0 || x > x1 || y > y1 || seen[(y - y0) * w + x - x0] || blocked(x, y)) continue;
                    seen[(y - y0) * w + x - x0] = true;
                    open.add(new int[]{x, y});
                }
            }
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
                if (!blocked(x, y) && !seen[(y - y0) * w + x - x0]) return false;
            }
            return true;
        }
    }

    static boolean disc(Box b, int x, int y) {
        if (!b.contains(x, y)) return false;
        float r = b.w() / 2f, dx = x + .5f - (b.x() + r), dy = y + .5f - (b.y() + r);
        return dx * dx + dy * dy <= r * r + .25f;
    }

    /** The width a process area needs across, spine edge to far edge. */
    public static int width() { return RACK + BLOCK_U + LANE; }

    /**
     * The process area in the box {@code (x0, y0)}..{@code (x1, y1)}, its spine (the road and rack) along its left
     * edge or its right, as many blocks as fit along it up to {@code most}. Null when no block fits.
     */
    public static Plan plan(int x0, int y0, int x1, int y1, boolean spineLeft, int most) {
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        if (w < width() || most < 1) return null;
        int n = Math.min(most, (h - LANE) / (BLOCK_V + LANE));
        if (n < 1) return null;
        // The blocks centred along the spine, a lane before the first and after each.
        int used = LANE + n * (BLOCK_V + LANE);
        int top = y0 + (h - used) / 2;
        List<Block> blocks = new ArrayList<>();
        List<Box> lanes = new ArrayList<>();
        for (int k = 0; k <= n; k++) lanes.add(new Box(x0, top + k * (BLOCK_V + LANE), w, LANE));
        // The far lane down the outer edge, the full length.
        lanes.add(spineLeft ? new Box(x1 - LANE + 1, y0, LANE, h) : new Box(x0, y0, LANE, h));
        for (int k = 0; k < n; k++) {
            int v0 = top + LANE + k * (BLOCK_V + LANE);
            Box column = box(spineLeft, x0, x1, RACK, v0, COLUMN, COLUMN);
            Box stripper = box(spineLeft, x0, x1, RACK + 1, v0 + COLUMN + 1, STRIPPER, STRIPPER);
            Box heater = box(spineLeft, x0, x1, RACK + COLUMN + 2, v0 + 2, HEATER_U, HEATER_V);
            Box pad = box(spineLeft, x0, x1, RACK, v0, BLOCK_U, BLOCK_V);
            int cu = RACK + COLUMN + 2 + HEATER_U / 2;
            int cx = spineLeft ? x0 + cu : x1 - cu;
            blocks.add(new Block(column, stripper, heater, pad, cx, v0 + 2 + HEATER_V + 1));
        }
        Box rack = spineLeft ? new Box(x0, y0, RACK, h) : new Box(x1 - RACK + 1, y0, RACK, h);
        return new Plan(x0, y0, x1, y1, blocks, rack, lanes);
    }

    /** A box {@code bu} by {@code bv} at {@code u} out from the spine edge and row {@code v}, in map cells. */
    private static Box box(boolean spineLeft, int x0, int x1, int u, int v, int bu, int bv) {
        return new Box(spineLeft ? x0 + u : x1 - u - bu + 1, v, bu, bv);
    }
}
