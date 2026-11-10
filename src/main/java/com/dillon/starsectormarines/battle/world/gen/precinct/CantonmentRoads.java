package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.Cantonment;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.layout.Side;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A cantonment's road in ({@code mapgen-nouns.md}, cantonment). A cantonment lays its own streets, square to one
 * avenue from its gate to its middle, and a road its growth threw across its square cuts its rows: the owner's notes
 * on the Raid site's garrison were that it read as messy. So every road inside its claim is taken up (a road another
 * place grew across it is welded back round it: {@link #block}), and one straight road is laid from the middle of its square out through the side nearest a road outside (the
 * side facing the middle of the map, where the rest of the site is, when two are as near), on until it meets that road.
 * That road is the avenue's line, and where it crosses the wall is the main gate. Meeting the town's road is what keeps
 * the map's roads whole without a second road welded in across the cantonment.
 *
 * <p>A base has more than one way in and out (owner: "they need multiple entrances and exits"): as many gates as its
 * market owes ({@link #gates}), the main gate and one in each further side that faces a road, nearest road first. A
 * further gate's road is narrower than the avenue and runs from the patrol lane inside the wall out across the glacis
 * until it meets the town's road, so it opens the wall without cutting the cantonment's rows, and every gate joins the
 * map's network. A side whose road would leave the map first has no gate: a gate onto nothing is not a way out.
 *
 * <p>Runs on the grown owner array before anything is painted or graphed, as pruning and the artery do.
 */
public final class CantonmentRoads {

    private CantonmentRoads() {}

    /** Half the road's width beside its centre cell: the avenue's five cells. */
    static final int HALF = 2;
    /** Half a further gate's road beside its centre cell: three cells, a vehicle's way. */
    static final int SIDE_HALF = 1;
    /** Cells of wall and patrol lane inside a cantonment's glacis, which a further gate's road comes in across. */
    static final int LANE = 3;

    /**
     * How many gates a cantonment of this program owes: two, three from a market of size five, four from size seven.
     * A garrison's barrack blocks are its market's size less one ({@link FortressProgram#cantonment}), so they stand
     * for it here. UNTUNED.
     */
    public static int gates(FortressProgram program) {
        int barracks = 0;
        for (FortressBuilding b : program.buildings()) if (b.purpose() == RoomPurpose.BARRACKS) barracks += b.count();
        return barracks >= 6 ? 4 : barracks >= 4 ? 3 : 2;
    }

    /**
     * The fortification {@code precinct}'s wall is stamped with: a cantonment's opens as many gates as it laid roads
     * in, whatever its grade's cap (the grade arms the gates rather than closing them); any other place's is its own.
     */
    public static Fortification wall(Precinct precinct) {
        Fortification f = precinct.fortification();
        if (f == null || !precinct.isProgrammed() || !precinct.program().cantonment()) return f;
        int owed = gates(precinct.program());
        return f.gates() >= owed ? f : f.withGates(owed);
    }

    /**
     * Takes up every road inside {@code who}'s claim, its own growth's and any other place's across it, and lays its
     * road in.
     *
     * @param claim the claim, by precinct index; {@code owner} the grown roads, by the index of the place that grew
     *              them, {@link GrownTrunkPlan#UNOWNED} where none runs
     */
    public static void lay(int[][] claim, int[][] owner, int who, int width, int height) {
        lay(claim, owner, who, width, height, 1, 0);
    }

    /**
     * Takes up every road inside {@code who}'s claim and lays its roads in: the avenue, and a road to each further
     * gate up to {@code gates} in all.
     *
     * @param glacis the open strip inside the claim's edge the wall stands behind
     */
    public static void lay(int[][] claim, int[][] owner, int who, int width, int height, int gates, int glacis) {
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
        // Further gates: the other sides with a road to meet, each from the middle of its side, nearest road first,
        // so a cap below the sides available keeps the shortest ways out.
        List<Side> further = new ArrayList<>();
        List<int[]> starts = new ArrayList<>();
        List<Integer> reach = new ArrayList<>();
        for (Side side : Side.values()) {
            if (side == out) continue;
            int[] start = sideStart(square, side, glacis);
            int steps = toRoad(claim, owner, who, start[0], start[1], side, width, height);
            if (steps < 0) continue;
            further.add(side);
            starts.add(start);
            reach.add(steps);
        }
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < further.size(); k++) order.add(k);
        order.sort(Comparator.comparingInt(reach::get));
        run(claim, owner, who, cx, cy, out, HALF, width, height);
        for (int k : order.subList(0, Math.min(order.size(), Math.max(0, gates - 1)))) {
            run(claim, owner, who, starts.get(k)[0], starts.get(k)[1], further.get(k), SIDE_HALF, width, height);
        }
    }

    /**
     * Where a further gate's road starts: the middle of the square's {@code side}, in from its edge across the glacis,
     * the wall and the patrol lane.
     */
    static int[] sideStart(int[] square, Side side, int glacis) {
        int l = square[0], b = square[1], s = square[2];
        int in = glacis + LANE;
        return switch (side) {
            case EAST -> new int[]{l + s - 1 - in, b + s / 2};
            case WEST -> new int[]{l + in, b + s / 2};
            case NORTH -> new int[]{l + s / 2, b + s - 1 - in};
            case SOUTH -> new int[]{l + s / 2, b + in};
        };
    }

    /** Lays a road {@code half} cells either side of its line from {@code (x0, y0)} toward {@code out} to the town's. */
    private static void run(int[][] claim, int[][] owner, int who, int x0, int y0, Side out, int half,
                            int width, int height) {
        for (int k = 0; ; k++) {
            int x = x0 + out.dx * k, y = y0 + out.dy * k;
            if (x < 0 || y < 0 || x >= width || y >= height) return;
            if (claim[x][y] != who && owner[x][y] != GrownTrunkPlan.UNOWNED) return;   // met the town's road
            for (int w = -half; w <= half; w++) {
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
