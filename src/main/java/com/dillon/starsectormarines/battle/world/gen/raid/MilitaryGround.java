package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.layout.LayoutGround;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

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
    /** Cells between two marching marks across the square. UNTUNED. */
    static final int MARK_STEP = 5;
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

    /**
     * The square's border line (corner pieces, which turn clockwise from the north-west, and four-long runs end to
     * end between them), a dashed rank row every {@link #MARK_STEP} cells across it, the ring at its middle, and the
     * saluting base with the flag mast beside it at the middle of its south side.
     */
    private static void markOut(GenContext c, int x0, int y0, int x1, int y1) {
        WorksProps.decal(c, x0, y1 - 1, "doodad.parade-corner", 0);
        WorksProps.decal(c, x1 - 1, y1 - 1, "doodad.parade-corner", 1);
        WorksProps.decal(c, x1 - 1, y0, "doodad.parade-corner", 2);
        WorksProps.decal(c, x0, y0, "doodad.parade-corner", 3);
        for (int x = x0 + 2; x + 3 <= x1 - 2; x += 4) {
            WorksProps.decal(c, x, y0, "doodad.parade-line", 0);
            WorksProps.decal(c, x, y1, "doodad.parade-line", 0);
        }
        for (int y = y0 + 2; y + 3 <= y1 - 2; y += 4) {
            WorksProps.decal(c, x0, y, "doodad.parade-line", 1);
            WorksProps.decal(c, x1, y, "doodad.parade-line", 1);
        }
        int midX = (x0 + x1) / 2, midY = (y0 + y1) / 2;
        for (int y = y0 + MARK_STEP; y <= y1 - MARK_STEP; y += MARK_STEP) {
            if (Math.abs(y - midY) <= 1) continue;
            for (int x = x0 + 3; x + 3 <= x1 - 3; x += 5) WorksProps.decal(c, x, y, "doodad.parade-ranks", 0);
        }
        WorksProps.decal(c, midX, midY, "doodad.parade-centre", 0);
        WorksProps.place(c, midX - 1, y0 - 3, "doodad.saluting-base", 0);
        WorksProps.place(c, midX + 3, y0 - 2, "doodad.flag-mast", 0);
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
