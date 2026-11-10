package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.Cantonment;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;

/**
 * A cantonment's road in ({@code mapgen-nouns.md}, cantonment). A cantonment lays its own streets, square to one
 * avenue from its gate to its middle, and a road its growth threw across its square cuts its rows: the owner's notes
 * on the Raid site's garrison were that it read as messy. So every road inside its claim is taken up (a road another
 * place grew across it is welded back round it: {@link #block}), and one straight road is laid from the middle of its square out through the side nearest a road outside (the
 * side facing the middle of the map, where the rest of the site is, when two are as near), on until it meets that road.
 * That road is the avenue's line, and where it crosses the wall is the gate. Meeting the town's road is what keeps the
 * map's roads whole without a second road welded in across the cantonment.
 *
 * <p>Runs on the grown owner array before anything is painted or graphed, as pruning and the artery do.
 */
public final class CantonmentRoads {

    private CantonmentRoads() {}

    /** Half the road's width beside its centre cell: the avenue's five cells. */
    static final int HALF = 2;

    /**
     * Takes up every road inside {@code who}'s claim, its own growth's and any other place's across it, and lays its
     * road in.
     *
     * @param claim the claim, by precinct index; {@code owner} the grown roads, by the index of the place that grew
     *              them, {@link GrownTrunkPlan#UNOWNED} where none runs
     */
    public static void lay(int[][] claim, int[][] owner, int who, int width, int height) {
        boolean[][] mine = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] != who) continue;
                mine[x][y] = true;
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
        }
        // From the middle of the square the cantonment is laid on, which is the avenue's line.
        int[] square = Cantonment.square(mine);
        if (square == null) return;
        int cx = square[0] + square[2] / 2, cy = square[1] + square[2] / 2;
        // Out of the side nearest the town's road, the side facing the map's middle where two are as near.
        Side out = null;
        int bestSteps = Integer.MAX_VALUE;
        double bestFacing = Double.NEGATIVE_INFINITY;
        for (Side side : Side.values()) {
            int steps = toRoad(claim, owner, who, cx, cy, side, width, height);
            if (steps < 0) continue;
            double facing = side.dx * (width / 2.0 - cx) + side.dy * (height / 2.0 - cy);
            if (steps < bestSteps - REACH_TIE || Math.abs(steps - bestSteps) <= REACH_TIE && facing > bestFacing) {
                out = side;
                bestSteps = steps;
                bestFacing = facing;
            }
        }
        if (out == null) out = Side.facing(width / 2 - cx, height / 2 - cy);
        for (int k = 0; ; k++) {
            int x = cx + out.dx * k, y = cy + out.dy * k;
            if (x < 0 || y < 0 || x >= width || y >= height) return;
            if (claim[x][y] != who && owner[x][y] != GrownTrunkPlan.UNOWNED) return;   // met the town's road
            for (int w = -HALF; w <= HALF; w++) {
                int px = x + (out.dx == 0 ? w : 0), py = y + (out.dy == 0 ? w : 0);
                if (px < 0 || py < 0 || px >= width || py >= height) continue;
                if (owner[px][py] == GrownTrunkPlan.UNOWNED) owner[px][py] = who;
            }
        }
    }

    /** Cells within which two sides' roads count as equally near. UNTUNED. */
    private static final int REACH_TIE = 12;

    /**
     * Steps from {@code (cx, cy)} toward {@code side} to the first road outside the claim, or -1 where the ray leaves
     * the map first.
     */
    static int toRoad(int[][] claim, int[][] owner, int who, int cx, int cy, Side side, int width, int height) {
        for (int k = 0; ; k++) {
            int x = cx + side.dx * k, y = cy + side.dy * k;
            if (x < 0 || y < 0 || x >= width || y >= height) return -1;
            if (claim[x][y] != who && owner[x][y] != GrownTrunkPlan.UNOWNED) return k;
        }
    }

    /** A copy of {@code blocked} to add to, or an empty mask where it is {@code null}. */
    public static boolean[][] blocked(boolean[][] blocked, int width, int height) {
        boolean[][] out = new boolean[width][height];
        if (blocked != null) for (int x = 0; x < width; x++) out[x] = blocked[x].clone();
        return out;
    }

    /**
     * Marks {@code who}'s claim as ground a weld between road networks goes round, all but its own road in: a road
     * taken up from across it is joined back round it, not cut through its rows again.
     */
    public static void block(boolean[][] blocked, int[][] claim, int[][] owner, int who, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (claim[x][y] == who && owner[x][y] != who) blocked[x][y] = true;
            }
        }
    }
}
