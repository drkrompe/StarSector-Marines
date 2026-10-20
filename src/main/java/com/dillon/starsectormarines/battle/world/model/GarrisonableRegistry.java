package com.dillon.starsectormarines.battle.world.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * The battle's <b>garrisonables</b>: the places a squad can be sent to man, derived from one
 * {@link FightingPositionIndex} and nothing else. A garrisonable is a <b>stretch</b> of a fighting-position run: a run
 * a squad's frontage long or shorter is one stretch, and a longer one (a fort's belt, wrapping the corners of its
 * works) is cut along its length into several, so that pressure, manning, release and breach are decided where the
 * attack is rather than wherever any part of a long run happens to be near it. The registry carries what a commander
 * needs to ask about a place cheaply: where it is, how big a circle covers it, which run it is part of and which
 * stretches of that run it touches, which fort it fronts, and which influence blocks it covers.
 *
 * <p><b>Cutting.</b> Deterministic, from the index alone. Each run is measured along itself: a breadth-first walk
 * inside the run from one of its ends (the cell farthest from its first cell) gives every cell its distance along the
 * run. That length is cut into equal stretches about {@link #STRETCH_CELLS} long. Each stretch is grown, connected,
 * from the unassigned cell nearest the end, over unassigned cells short of its far limit, so a ring round the works or
 * a branching trench is never one stretch on two sides of the fort. A remnant smaller than {@link #MIN_STRETCH_CELLS}
 * joins the neighbour it shares most border with. With stretches switched off ({@link #STRETCHES_PROPERTY}) every run
 * is one stretch and its id is the run's id, which is the model before stretches exactly.
 *
 * <p>Immutable. It is built once for an index instance and the owner rebuilds it only when the index instance
 * changes (the index hands out a fresh instance whenever a prepared-ground prop is destroyed), never per tick.
 *
 * <p>The block lookup is what keeps a commander's cost proportional to the fight rather than to the number of
 * places: {@link #near} answers, for one influence block, which garrisonables lie within {@link #REACH_BLOCKS}
 * blocks of it, so a believed contact in a block wakes only the places it could be pressing. Blocks here are the
 * influence field's blocks ({@code blockSize} cells), so a pressure read is one array lookup per covered block.
 *
 * <p>See {@code garrisonables.md}.
 */
public final class GarrisonableRegistry {

    /** {@code false} makes every run one garrisonable: the control for stretches. */
    public static final String STRETCHES_PROPERTY = "battle.garrison.stretches";

    /** Read once at class load and settable for evidence; volatile because a scene flips it between loops. */
    private static volatile boolean stretchesEnabled =
            Boolean.parseBoolean(System.getProperty(STRETCHES_PROPERTY, "true"));

    /** How far (in blocks) from a covered block a contact still wakes the garrisonable. UNTUNED. */
    public static final int REACH_BLOCKS = 3;
    /** Smallest and largest radius of the defend circle placed on a garrisonable, in cells. */
    public static final int MIN_RADIUS_CELLS = 4;
    public static final int MAX_RADIUS_CELLS = 10;
    /** How far (Chebyshev, cells) from a fort's centre a garrisonable may stand and still be that fort's belt. */
    public static final int FORT_REACH_CELLS = 40;
    /**
     * Length along its run of one stretch, in cells: about a squad's frontage, and short enough that the circle a
     * stretch is manned through ({@link #MAX_RADIUS_CELLS}) reaches both of its ends. UNTUNED.
     */
    public static final int STRETCH_CELLS = 16;
    /** A stretch smaller than this is a remnant and joins its neighbour: fewer cells than a fire team wants. */
    public static final int MIN_STRETCH_CELLS = 6;

    private static final int[] NONE = new int[0];
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private final FightingPositionIndex source;
    private final boolean stretched;
    private final int worldWidth;
    private final int blockSize;
    private final int blockWidth;
    private final int blockHeight;
    private final int[] centreX;
    private final int[] centreY;
    private final int[] radius;
    private final int[] fortId;
    private final int[] runOf;
    private final int[] size;
    private final int[][] covered;
    private final int[][] neighbours;
    /** Each garrisonable's cells as {@code y * worldWidth + x}, in its run's order. */
    private final int[][] cells;
    /** Garrisonable per cell ({@code y * worldWidth + x}), or -1; {@code null} when stretches are runs. */
    private final int[] byCell;
    /** Garrisonables within reach of each block (index {@code by * blockWidth + bx}), ascending; {@code null} where none. */
    private final int[][] nearBlock;

    private GarrisonableRegistry(FightingPositionIndex source, boolean stretched, int worldWidth, int blockSize,
                                 int blockWidth, int blockHeight, int[] centreX, int[] centreY, int[] radius,
                                 int[] fortId, int[] runOf, int[] size, int[][] covered, int[][] neighbours,
                                 int[][] cells, int[] byCell, int[][] nearBlock) {
        this.source = source;
        this.stretched = stretched;
        this.worldWidth = worldWidth;
        this.blockSize = blockSize;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.centreX = centreX;
        this.centreY = centreY;
        this.radius = radius;
        this.fortId = fortId;
        this.runOf = runOf;
        this.size = size;
        this.covered = covered;
        this.neighbours = neighbours;
        this.cells = cells;
        this.byCell = byCell;
        this.nearBlock = nearBlock;
    }

    /** Whether runs are cut into stretches; read by builders and by callers whose pricing follows the model. */
    public static boolean stretchesEnabled() {
        return stretchesEnabled;
    }

    /** Evidence seam: switches stretches on or off for the rest of this JVM. Restore the previous value in a {@code finally}. */
    public static void setStretchesForEvidence(boolean enabled) {
        stretchesEnabled = enabled;
    }

    /** A registry with no garrisonables, for a battle with no prepared ground. */
    public static GarrisonableRegistry empty(FightingPositionIndex source, int blockSize) {
        return empty(source, blockSize, stretchesEnabled);
    }

    private static GarrisonableRegistry empty(FightingPositionIndex source, int blockSize, boolean stretched) {
        return new GarrisonableRegistry(source, stretched, 0, Math.max(1, blockSize), 0, 0, NONE, NONE, NONE,
                NONE, NONE, NONE, new int[0][], new int[0][], new int[0][], null, new int[0][]);
    }

    /** {@link #build(FightingPositionIndex, int, int, int, int[], boolean)} with stretches as the switch says. */
    public static GarrisonableRegistry build(FightingPositionIndex index, int worldWidth, int worldHeight,
                                             int blockSize, int[] forts) {
        return build(index, worldWidth, worldHeight, blockSize, forts, stretchesEnabled);
    }

    /**
     * Derives the registry from the index.
     *
     * @param worldWidth  map width in cells
     * @param worldHeight map height in cells
     * @param blockSize   cells per influence block side
     * @param forts       fort centres as packed {@code fortId, x, y} triples; a garrisonable is attributed to the
     *                    nearest centre within {@link #FORT_REACH_CELLS}
     * @param stretched   whether long runs are cut into stretches; {@code false} makes every run one garrisonable
     */
    public static GarrisonableRegistry build(FightingPositionIndex index, int worldWidth, int worldHeight,
                                             int blockSize, int[] forts, boolean stretched) {
        int runs = index.runCount();
        if (runs == 0) return empty(index, blockSize, stretched);
        int bw = (worldWidth + blockSize - 1) / blockSize;
        int bh = (worldHeight + blockSize - 1) / blockSize;

        // Each stretch's cells, in the run's own order, and its run; neighbours by stretch id.
        List<int[]> stretchCells = new ArrayList<>();
        List<Integer> stretchRun = new ArrayList<>();
        List<TreeSet<Integer>> touching = new ArrayList<>();
        int[] byCell = null;
        if (stretched) {
            byCell = new int[worldWidth * worldHeight];
            Arrays.fill(byCell, -1);
            int[] local = new int[worldWidth * worldHeight];
            Arrays.fill(local, -1);
            for (int run = 0; run < runs; run++) {
                int[] label = cut(index, run, worldWidth, worldHeight, local);
                int first = stretchCells.size();
                int pieces = 0;
                for (int l : label) pieces = Math.max(pieces, l + 1);
                int[] counts = new int[pieces];
                for (int l : label) counts[l]++;
                int[][] cells = new int[pieces][];
                for (int p = 0; p < pieces; p++) cells[p] = new int[counts[p]];
                int[] fill = new int[pieces];
                int n = index.runSize(run);
                for (int i = 0; i < n; i++) {
                    int cell = index.runCellY(run, i) * worldWidth + index.runCellX(run, i);
                    cells[label[i]][fill[label[i]]++] = cell;
                    byCell[cell] = first + label[i];
                }
                for (int p = 0; p < pieces; p++) {
                    stretchCells.add(cells[p]);
                    stretchRun.add(run);
                    touching.add(new TreeSet<>());
                }
            }
            for (int g = 0; g < stretchCells.size(); g++) {
                for (int cell : stretchCells.get(g)) {
                    int x = cell % worldWidth;
                    int y = cell / worldWidth;
                    for (int k = 0; k < 4; k++) {
                        int nx = x + DX[k];
                        int ny = y + DY[k];
                        if (nx < 0 || ny < 0 || nx >= worldWidth || ny >= worldHeight) continue;
                        int other = byCell[ny * worldWidth + nx];
                        if (other >= 0 && other != g) touching.get(g).add(other);
                    }
                }
            }
        } else {
            for (int run = 0; run < runs; run++) {
                int n = index.runSize(run);
                int[] cells = new int[n];
                for (int i = 0; i < n; i++) cells[i] = index.runCellY(run, i) * worldWidth + index.runCellX(run, i);
                stretchCells.add(cells);
                stretchRun.add(run);
                touching.add(new TreeSet<>());
            }
        }

        int count = stretchCells.size();
        int[] cx = new int[count];
        int[] cy = new int[count];
        int[] rad = new int[count];
        int[] fort = new int[count];
        int[] runOf = new int[count];
        int[] sizes = new int[count];
        int[][] cov = new int[count][];
        int[][] adjacent = new int[count][];
        int[][] near = new int[bw * bh][];
        int[] coveredStamp = new int[bw * bh];
        int[] nearStamp = new int[bw * bh];
        Arrays.fill(coveredStamp, -1);
        Arrays.fill(nearStamp, -1);
        int[] scratch = new int[bw * bh];
        for (int g = 0; g < count; g++) {
            int[] cells = stretchCells.get(g);
            int n = cells.length;
            runOf[g] = stretchRun.get(g);
            sizes[g] = n;
            adjacent[g] = touching.get(g).stream().mapToInt(Integer::intValue).toArray();
            long sx = 0;
            long sy = 0;
            for (int cell : cells) {
                sx += cell % worldWidth;
                sy += cell / worldWidth;
            }
            double mx = (double) sx / n;
            double my = (double) sy / n;
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double dx = cells[i] % worldWidth - mx;
                double dy = cells[i] / worldWidth - my;
                double d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            cx[g] = cells[best] % worldWidth;
            cy[g] = cells[best] / worldWidth;
            int far = 0;
            int coveredCount = 0;
            for (int cell : cells) {
                int x = cell % worldWidth;
                int y = cell / worldWidth;
                far = Math.max(far, (int) Math.ceil(Math.hypot(x - cx[g], y - cy[g])));
                int b = Math.min(bh - 1, y / blockSize) * bw + Math.min(bw - 1, x / blockSize);
                if (coveredStamp[b] != g) {
                    coveredStamp[b] = g;
                    scratch[coveredCount++] = b;
                }
            }
            rad[g] = Math.max(MIN_RADIUS_CELLS, Math.min(MAX_RADIUS_CELLS, far));
            int[] blocks = Arrays.copyOf(scratch, coveredCount);
            Arrays.sort(blocks);
            cov[g] = blocks;
            for (int b : blocks) {
                int bx0 = b % bw;
                int by0 = b / bw;
                for (int by = Math.max(0, by0 - REACH_BLOCKS); by <= Math.min(bh - 1, by0 + REACH_BLOCKS); by++) {
                    for (int bx = Math.max(0, bx0 - REACH_BLOCKS); bx <= Math.min(bw - 1, bx0 + REACH_BLOCKS); bx++) {
                        int nb = by * bw + bx;
                        if (nearStamp[nb] == g) continue;
                        nearStamp[nb] = g;
                        int[] list = near[nb];
                        near[nb] = list == null ? new int[]{g} : append(list, g);
                    }
                }
            }
            fort[g] = fortFor(forts, cx[g], cy[g]);
        }
        return new GarrisonableRegistry(index, stretched, worldWidth, blockSize, bw, bh, cx, cy, rad, fort, runOf,
                sizes, cov, adjacent, stretchCells.toArray(new int[0][]), byCell, near);
    }

    /**
     * Labels each cell of {@code run} (in the run's own order) with its stretch, {@code 0..k-1}, numbered from the end
     * the run is measured from. {@code local} is a world-sized scratch array of {@code -1}s, returned as it was found.
     */
    static int[] cut(FightingPositionIndex index, int run, int worldWidth, int worldHeight, int[] local) {
        int n = index.runSize(run);
        int[] cells = new int[n];
        for (int i = 0; i < n; i++) {
            cells[i] = index.runCellY(run, i) * worldWidth + index.runCellX(run, i);
            local[cells[i]] = i;
        }
        try {
            int[] along = new int[n];
            int end = farthest(cells, 0, worldWidth, worldHeight, local, along);
            farthest(cells, end, worldWidth, worldHeight, local, along);
            int length = 0;
            for (int d : along) length = Math.max(length, d + 1);
            int pieces = Math.max(1, (length + STRETCH_CELLS / 2) / STRETCH_CELLS);
            int[] label = new int[n];
            if (pieces == 1) return label;
            int span = (length + pieces - 1) / pieces;
            Arrays.fill(label, -1);
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> along[a] != along[b] ? Integer.compare(along[a], along[b])
                    : Integer.compare(cells[a], cells[b]));
            int[] queue = new int[n];
            int stretches = 0;
            for (int seed : order) {
                if (label[seed] >= 0) continue;
                int limit = along[seed] + span;
                int head = 0;
                int tail = 0;
                queue[tail++] = seed;
                label[seed] = stretches;
                while (head < tail) {
                    int i = queue[head++];
                    int x = cells[i] % worldWidth;
                    int y = cells[i] / worldWidth;
                    for (int k = 0; k < 4; k++) {
                        int j = localAt(x + DX[k], y + DY[k], worldWidth, worldHeight, local);
                        if (j < 0 || label[j] >= 0 || along[j] >= limit) continue;
                        label[j] = stretches;
                        queue[tail++] = j;
                    }
                }
                stretches++;
            }
            return mergeRemnants(cells, label, stretches, worldWidth, worldHeight, local);
        } finally {
            for (int cell : cells) local[cell] = -1;
        }
    }

    /** Breadth-first distances inside the run from cell {@code start} into {@code along}; returns the farthest cell. */
    private static int farthest(int[] cells, int start, int worldWidth, int worldHeight, int[] local, int[] along) {
        Arrays.fill(along, -1);
        int[] queue = new int[cells.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        along[start] = 0;
        int far = start;
        while (head < tail) {
            int i = queue[head++];
            if (along[i] > along[far] || (along[i] == along[far] && cells[i] < cells[far])) far = i;
            int x = cells[i] % worldWidth;
            int y = cells[i] / worldWidth;
            for (int k = 0; k < 4; k++) {
                int j = localAt(x + DX[k], y + DY[k], worldWidth, worldHeight, local);
                if (j < 0 || along[j] >= 0) continue;
                along[j] = along[i] + 1;
                queue[tail++] = j;
            }
        }
        return far;
    }

    private static int localAt(int x, int y, int worldWidth, int worldHeight, int[] local) {
        if (x < 0 || y < 0 || x >= worldWidth || y >= worldHeight) return -1;
        return local[y * worldWidth + x];
    }

    /**
     * Folds every stretch smaller than {@link #MIN_STRETCH_CELLS} into the neighbour it shares the most cell edges
     * with (ties to the lower label), smallest first, then numbers what is left densely, still in order along the run.
     */
    private static int[] mergeRemnants(int[] cells, int[] label, int stretches, int worldWidth, int worldHeight,
                                       int[] local) {
        int[] sizes = new int[stretches];
        for (int l : label) sizes[l]++;
        int alive = stretches;
        while (alive > 1) {
            int small = -1;
            for (int s = 0; s < stretches; s++) {
                if (sizes[s] > 0 && sizes[s] < MIN_STRETCH_CELLS && (small < 0 || sizes[s] < sizes[small])) small = s;
            }
            if (small < 0) break;
            int[] shared = new int[stretches];
            for (int i = 0; i < cells.length; i++) {
                if (label[i] != small) continue;
                int x = cells[i] % worldWidth;
                int y = cells[i] / worldWidth;
                for (int k = 0; k < 4; k++) {
                    int j = localAt(x + DX[k], y + DY[k], worldWidth, worldHeight, local);
                    if (j >= 0 && label[j] != small) shared[label[j]]++;
                }
            }
            int into = -1;
            for (int s = 0; s < stretches; s++) if (shared[s] > 0 && (into < 0 || shared[s] > shared[into])) into = s;
            if (into < 0) break;
            for (int i = 0; i < label.length; i++) if (label[i] == small) label[i] = into;
            sizes[into] += sizes[small];
            sizes[small] = 0;
            alive--;
        }
        int[] dense = new int[stretches];
        int next = 0;
        // Labels were issued in order along the run, so the surviving labels ascending are that order.
        for (int s = 0; s < stretches; s++) if (sizes[s] > 0) dense[s] = next++;
        int[] out = new int[label.length];
        for (int i = 0; i < label.length; i++) out[i] = dense[label[i]];
        return out;
    }

    private static int[] append(int[] list, int value) {
        int[] out = Arrays.copyOf(list, list.length + 1);
        out[list.length] = value;
        return out;
    }

    private static int fortFor(int[] forts, int x, int y) {
        if (forts == null) return -1;
        int best = -1;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i + 2 < forts.length; i += 3) {
            int d = Math.max(Math.abs(forts[i + 1] - x), Math.abs(forts[i + 2] - y));
            if (d > FORT_REACH_CELLS) continue;
            if (d < bestD || (d == bestD && forts[i] < best)) {
                bestD = d;
                best = forts[i];
            }
        }
        return best;
    }

    /** Whether this registry was derived from exactly this index instance, under the stretch setting now in force. */
    public boolean isBuiltFrom(FightingPositionIndex index) {
        return source == index && stretched == stretchesEnabled;
    }

    /** Whether long runs were cut into stretches; {@code false} means every garrisonable is a whole run. */
    public boolean stretched() {
        return stretched;
    }

    public FightingPositionIndex source() {
        return source;
    }

    public int count() {
        return centreX.length;
    }

    /** The garrisonable (stretch) containing this cell, or -1. */
    public int idAt(int x, int y) {
        if (byCell == null) return source.runIdAt(x, y);
        if (x < 0 || y < 0 || x >= worldWidth || y * worldWidth + x >= byCell.length) return -1;
        return byCell[y * worldWidth + x];
    }

    public int centreX(int id) { return centreX[id]; }
    public int centreY(int id) { return centreY[id]; }
    /** Radius, in cells, of the defend circle that covers the garrisonable's useful ground. */
    public int radius(int id) { return radius[id]; }
    /** Cells of ground the garrisonable offers. */
    public int size(int id) { return size[id]; }
    /** The fort this garrisonable fronts, or -1. */
    public int fortId(int id) { return fortId[id]; }
    /** The fighting-position run this stretch is part of: the id an order's claims are scoped to. */
    public int runOf(int id) { return runOf[id]; }

    /** The {@code i}th of the garrisonable's {@link #size} cells, in its run's order. */
    public int cellX(int id, int i) { return cells[id][i] % worldWidth; }
    public int cellY(int id, int i) { return cells[id][i] / worldWidth; }

    /** The stretches of the same run that touch this one, ascending. Shared; do not modify. */
    public int[] neighbours(int id) { return neighbours[id]; }

    /** The influence blocks the garrisonable's cells fall in, ascending, as {@code by * blockWidth + bx}. Shared; do not modify. */
    public int[] coveredBlocks(int id) { return covered[id]; }

    /** Garrisonables within {@link #REACH_BLOCKS} of block {@code (bx, by)}, ascending. Shared; do not modify. */
    public int[] near(int bx, int by) {
        if (bx < 0 || by < 0 || bx >= blockWidth || by >= blockHeight) return NONE;
        int[] list = nearBlock[by * blockWidth + bx];
        return list == null ? NONE : list;
    }

    public int blockSize() { return blockSize; }
    public int blockWidth() { return blockWidth; }
    public int blockHeight() { return blockHeight; }
}
