package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground that makes a military site read as a base ({@code raid-mission.md}, Site templates): the parade square
 * on the garrison's widest open yard, paved and marked out, its saluting base and flag mast; and a watchtower in each
 * corner of the motor pool. Every piece is a prop of the military-base sheet and stands only where that sheet is
 * installed; with it missing the ground is only paved.
 */
final class MilitaryGround {

    /** The paving the parade square is laid in. */
    static final String PARADE_PAVING = "pavement.asphalt.worn";
    /** The smallest open yard a parade square is marked on, cells. UNTUNED. */
    static final int PARADE_MIN_ALONG = 12, PARADE_MIN_DEEP = 8;
    /** The largest parade square, cells; a wider yard keeps the rest as it is. UNTUNED. */
    static final int PARADE_MAX_ALONG = 24, PARADE_MAX_DEEP = 14;
    /** Cells the square keeps from the yard's edge. */
    static final int PARADE_MARGIN = 1;
    /** Cells a rank row stands inside the square's long sides. UNTUNED. */
    static final int RANK_IN = 3;
    /** A watchtower's side, and the farthest it moves in from its corner to find open ground, cells. */
    static final int TOWER_SIDE = 3, TOWER_SEARCH = 8;

    private MilitaryGround() {}

    /**
     * The parade square on the widest open yard of place {@code index}: the largest rectangle of open, unbuilt,
     * unroaded ground in its claim, cut to {@link #PARADE_MAX_ALONG} by {@link #PARADE_MAX_DEEP} about its middle,
     * paved, lined round, marked across, with a saluting base and a flag mast at the middle of its south side.
     * Returns the square {x0, y0, x1, y1}, or null where no yard is big enough.
     */
    static int[] paradeSquare(GenContext c, int index) {
        boolean[][] open = openYard(c, index);
        if (open == null) return null;
        int[] r = largestRectangle(open);
        if (r == null) return null;
        int x0 = r[0] + PARADE_MARGIN, y0 = r[1] + PARADE_MARGIN, x1 = r[2] - PARADE_MARGIN, y1 = r[3] - PARADE_MARGIN;
        boolean wide = x1 - x0 >= y1 - y0;
        int maxW = wide ? PARADE_MAX_ALONG : PARADE_MAX_DEEP, maxH = wide ? PARADE_MAX_DEEP : PARADE_MAX_ALONG;
        if (x1 - x0 + 1 > maxW) { int cut = (x1 - x0 + 1 - maxW) / 2; x0 += cut; x1 = x0 + maxW - 1; }
        if (y1 - y0 + 1 > maxH) { int cut = (y1 - y0 + 1 - maxH) / 2; y0 += cut; y1 = y0 + maxH - 1; }
        int along = Math.max(x1 - x0, y1 - y0) + 1, deep = Math.min(x1 - x0, y1 - y0) + 1;
        if (along < PARADE_MIN_ALONG || deep < PARADE_MIN_DEEP) return null;
        int paving = c.topology.surfaceIndex(PARADE_PAVING);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, paving);
                c.topology.setNatureOverlayIndex(x, y, -1);
            }
        }
        markOut(c, x0, y0, x1, y1);
        return new int[]{x0, y0, x1, y1};
    }

    /** The open yard of place {@code index}: its unbuilt, unroaded outdoor ground; null on a map with no claims. */
    private static boolean[][] openYard(GenContext c, int index) {
        int[][] claim = c.get(BspKeys.PRECINCT_CLAIM);
        if (claim == null) return null;
        boolean[][] open = new boolean[c.width][c.height];
        LayoutGround ground = new LayoutGround(c);
        int[][] roads = c.get(BspKeys.PRECINCT_ROAD);
        for (int x = 0; x < c.width; x++) {
            for (int y = 0; y < c.height; y++) {
                open[x][y] = claim[x][y] == index && yard(c, x, y) && !ground.isWay(x, y)
                        && (roads == null || roads[x][y] == GrownTrunkPlan.UNOWNED);
            }
        }
        return open;
    }

    /** What a drill yard beside the parade square is put to, in the order the next largest yards take them. */
    enum YardUse { ARMOUR_PARK, FIRING_RANGE, SUPPLY_DUMP }

    /** The smallest yard a use is laid on, cells either way. UNTUNED. */
    static final int YARD_MIN = 8;
    /** Cells a yard's dressing keeps from its edge, and the ground kept clear round one laid. */
    static final int YARD_MARGIN = 2, YARD_APART = 3;

    /**
     * The garrison's other drill yards put to use: after the parade square ({@code square}, kept clear round), the
     * next largest open rectangles of place {@code index}, one to each {@link YardUse} in turn while one at least
     * {@link #YARD_MIN} cells either way is left. Returns the yards dressed, each {x0, y0, x1, y1}.
     */
    static List<int[]> drillYards(GenContext c, int index, int[] square) {
        List<int[]> out = new ArrayList<>();
        boolean[][] open = openYard(c, index);
        if (open == null) return out;
        if (square != null) clear(open, square, YARD_APART);
        for (YardUse use : YardUse.values()) {
            int[] r = largestRectangle(open);
            if (r == null) break;
            int x0 = r[0] + YARD_MARGIN, y0 = r[1] + YARD_MARGIN, x1 = r[2] - YARD_MARGIN, y1 = r[3] - YARD_MARGIN;
            if (x1 - x0 + 1 < YARD_MIN || y1 - y0 + 1 < YARD_MIN) break;
            switch (use) {
                case ARMOUR_PARK -> armourPark(c, x0, y0, x1, y1);
                case FIRING_RANGE -> firingRange(c, x0, y0, x1, y1);
                case SUPPLY_DUMP -> supplyDump(c, x0, y0, x1, y1);
            }
            int[] yard = {x0, y0, x1, y1};
            out.add(yard);
            clear(open, r, YARD_APART);
        }
        return out;
    }

    /** Takes {@code r} and {@code apart} cells round it out of {@code open}. */
    private static void clear(boolean[][] open, int[] r, int apart) {
        for (int x = Math.max(0, r[0] - apart); x <= Math.min(open.length - 1, r[2] + apart); x++) {
            for (int y = Math.max(0, r[1] - apart); y <= Math.min(open[0].length - 1, r[3] + apart); y++) {
                open[x][y] = false;
            }
        }
    }

    /** Ranks of armour across the yard: tanks and carriers a rank apiece, a lane between ranks. */
    private static void armourPark(GenContext c, int x0, int y0, int x1, int y1) {
        String[] kinds = {"doodad.parked-tank", "doodad.parked-apc-army"};
        int rank = 0;
        for (int y = y0; y + 3 <= y1; y += 6, rank++) {
            for (int x = x0; x + 1 <= x1; x += 3) WorksProps.place(c, x, y, kinds[rank % 2], 1);
        }
    }

    /**
     * A firing range down the yard's length: a sandbagged firing line across its near end, lanes divided by jersey
     * barriers, and a sandbag backstop across its far end.
     */
    private static void firingRange(GenContext c, int x0, int y0, int x1, int y1) {
        boolean lengthwise = x1 - x0 >= y1 - y0;
        if (lengthwise) {
            for (int y = y0; y <= y1; y++) {
                WorksProps.place(c, x0, y, "doodad.sandbag-straight-e", 0);
                WorksProps.place(c, x1, y, "doodad.sandbag-straight-w", 0);
            }
            for (int y = y0 + 3; y <= y1 - 3; y += 3) {
                for (int x = x0 + 3; x + 1 <= x1 - 3; x += 3) WorksProps.place(c, x, y, "doodad.jersey-barrier", 0);
            }
        } else {
            for (int x = x0; x <= x1; x++) {
                WorksProps.place(c, x, y0, "doodad.sandbag-straight-n", 0);
                WorksProps.place(c, x, y1, "doodad.sandbag-straight-s", 0);
            }
            for (int x = x0 + 3; x <= x1 - 3; x += 3) {
                for (int y = y0 + 3; y + 1 <= y1 - 3; y += 3) WorksProps.place(c, x, y, "doodad.jersey-barrier", 1);
            }
        }
    }

    /** A supply dump: rows of pallets and crates with a walk between rows, fuel drums at each row's end. */
    private static void supplyDump(GenContext c, int x0, int y0, int x1, int y1) {
        int row = 0;
        for (int y = y0; y <= y1; y += 3, row++) {
            WorksProps.place(c, x0, y, "doodad.industrial-drum-cluster", 0);
            String stack = row % 2 == 0 ? "doodad.industrial-pallet-stack" : "doodad.industrial-crate-stack";
            for (int x = x0 + 2; x <= x1; x += 2) WorksProps.place(c, x, y, stack, 0);
        }
    }

    /**
     * The square's border line (corner pieces, which turn anticlockwise from the north-west, and four-long runs end to
     * end between them), a front and a rear rank row across it, the ring at its middle, and the
     * saluting base with the flag mast beside it at the middle of its south side.
     */
    private static void markOut(GenContext c, int x0, int y0, int x1, int y1) {
        WorksProps.decal(c, x0, y1 - 1, "doodad.parade-corner", 0);
        WorksProps.decal(c, x1 - 1, y1 - 1, "doodad.parade-corner", 3);
        WorksProps.decal(c, x1 - 1, y0, "doodad.parade-corner", 2);
        WorksProps.decal(c, x0, y0, "doodad.parade-corner", 1);
        // Runs four long end to end between the corners, the last laid flush against the far corner, so a side whose
        // length is no multiple of four is still lined to its end (the overlap paints the same line twice).
        for (int x : runs(x0 + 2, x1 - 2)) {
            WorksProps.decal(c, x, y0, "doodad.parade-line", 0);
            WorksProps.decal(c, x, y1, "doodad.parade-line", 0);
        }
        for (int y : runs(y0 + 2, y1 - 2)) {
            WorksProps.decal(c, x0, y, "doodad.parade-line", 1);
            WorksProps.decal(c, x1, y, "doodad.parade-line", 1);
        }
        int midX = (x0 + x1) / 2, midY = (y0 + y1) / 2;
        // The ranks: a front and a rear rank {@link #RANK_IN} cells inside the long sides, across the square.
        for (int y : new int[]{y0 + RANK_IN, y1 - RANK_IN}) {
            for (int x : runs(x0 + RANK_IN, x1 - RANK_IN)) WorksProps.decal(c, x, y, "doodad.parade-ranks", 0);
        }
        WorksProps.decal(c, midX, midY, "doodad.parade-centre", 0);
        WorksProps.place(c, midX - 1, y0 - 3, "doodad.saluting-base", 0);
        WorksProps.place(c, midX + 3, y0 - 2, "doodad.flag-mast", 0);
    }

    /** The starts of four-long runs covering {@code from..to} inclusive: end to end, the last flush with {@code to}. */
    static int[] runs(int from, int to) {
        int length = to - from + 1;
        if (length < 4) return new int[0];
        int n = (length + 3) / 4;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = Math.min(from + 4 * i, to - 3);
        return out;
    }

    /**
     * A watchtower in each corner of the bounds {x0, y0, x1, y1} of a walled place, just inside its wall, moved in
     * along the diagonal up to {@link #TOWER_SEARCH} cells to find open ground. Returns how many stood.
     */
    static int watchtowers(GenContext c, int[] bounds) {
        int stood = 0;
        int[][] corners = {{bounds[0] + 1, bounds[1] + 1, 1, 1}, {bounds[2] - TOWER_SIDE, bounds[1] + 1, -1, 1},
                {bounds[2] - TOWER_SIDE, bounds[3] - TOWER_SIDE, -1, -1}, {bounds[0] + 1, bounds[3] - TOWER_SIDE, 1, -1}};
        for (int[] k : corners) {
            for (int step = 0; step <= TOWER_SEARCH; step++) {
                if (WorksProps.place(c, k[0] + k[2] * step, k[1] + k[3] * step, "doodad.watchtower", 0)) {
                    stood++;
                    break;
                }
            }
        }
        return stood;
    }

    /** Whether a cell is open yard: walkable outdoor ground, no wall, fixture, building or street on it. */
    private static boolean yard(GenContext c, int x, int y) {
        GroundKind kind = c.topology.getGroundKind(x, y);
        return c.grid.isWalkable(x, y) && !c.topology.isWall(x, y) && !c.topology.isFixture(x, y)
                && c.topology.getBuildingId(x, y) == 0 && !c.topology.hasBuildingKindHint(x, y)
                && kind != GroundKind.INDOOR && kind != GroundKind.STREET;
    }

    /**
     * The largest-area rectangle of true cells, as inclusive {x0, y0, x1, y1}, or null for none: row by row, the
     * runs of true cells above each column as a histogram, and its largest rectangle by a stack.
     */
    static int[] largestRectangle(boolean[][] open) {
        int w = open.length, h = w == 0 ? 0 : open[0].length;
        int[] heights = new int[w];
        int bestArea = 0;
        int[] best = null;
        int[] stack = new int[w + 1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) heights[x] = open[x][y] ? heights[x] + 1 : 0;
            int top = 0;
            for (int x = 0; x <= w; x++) {
                int height = x == w ? 0 : heights[x];
                while (top > 0 && heights[stack[top - 1]] >= height) {
                    int hh = heights[stack[--top]];
                    int left = top == 0 ? 0 : stack[top - 1] + 1;
                    int area = hh * (x - left);
                    if (hh > 0 && area > bestArea) {
                        bestArea = area;
                        best = new int[]{left, y - hh + 1, x - 1, y};
                    }
                }
                stack[top++] = x;
            }
        }
        return best;
    }
}
