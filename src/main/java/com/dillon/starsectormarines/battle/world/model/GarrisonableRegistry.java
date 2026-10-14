package com.dillon.starsectormarines.battle.world.model;

import java.util.Arrays;

/**
 * The battle's <b>garrisonables</b>: the places a squad can be sent to man, derived from one
 * {@link FightingPositionIndex} and nothing else. Today a garrisonable is a fighting-position run, and its id is
 * the run's id, so the registry is a read-through of the index plus what a commander needs to ask about a place
 * cheaply: where it is, how big a circle covers it, which fort it fronts, and which influence blocks it covers.
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

    /** How far (in blocks) from a covered block a contact still wakes the garrisonable. UNTUNED. */
    public static final int REACH_BLOCKS = 3;
    /** Smallest and largest radius of the defend circle placed on a garrisonable, in cells. */
    public static final int MIN_RADIUS_CELLS = 4;
    public static final int MAX_RADIUS_CELLS = 10;
    /** How far (Chebyshev, cells) from a fort's centre a garrisonable may stand and still be that fort's belt. */
    public static final int FORT_REACH_CELLS = 40;

    private static final int[] NONE = new int[0];

    private final FightingPositionIndex source;
    private final int blockSize;
    private final int blockWidth;
    private final int blockHeight;
    private final int[] centreX;
    private final int[] centreY;
    private final int[] radius;
    private final int[] fortId;
    private final int[][] covered;
    /** Garrisonables within reach of each block (index {@code by * blockWidth + bx}), ascending; {@code null} where none. */
    private final int[][] nearBlock;

    private GarrisonableRegistry(FightingPositionIndex source, int blockSize, int blockWidth, int blockHeight,
                                 int[] centreX, int[] centreY, int[] radius, int[] fortId, int[][] covered,
                                 int[][] nearBlock) {
        this.source = source;
        this.blockSize = blockSize;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.centreX = centreX;
        this.centreY = centreY;
        this.radius = radius;
        this.fortId = fortId;
        this.covered = covered;
        this.nearBlock = nearBlock;
    }

    /** A registry with no garrisonables, for a battle with no prepared ground. */
    public static GarrisonableRegistry empty(FightingPositionIndex source, int blockSize) {
        return new GarrisonableRegistry(source, Math.max(1, blockSize), 0, 0, NONE, NONE, NONE, NONE,
                new int[0][], new int[0][]);
    }

    /**
     * Derives the registry from the index.
     *
     * @param worldWidth  map width in cells
     * @param worldHeight map height in cells
     * @param blockSize   cells per influence block side
     * @param forts       fort centres as packed {@code fortId, x, y} triples; a garrisonable is attributed to the
     *                    nearest centre within {@link #FORT_REACH_CELLS}
     */
    public static GarrisonableRegistry build(FightingPositionIndex index, int worldWidth, int worldHeight,
                                             int blockSize, int[] forts) {
        int runs = index.runCount();
        if (runs == 0) return empty(index, blockSize);
        int bw = (worldWidth + blockSize - 1) / blockSize;
        int bh = (worldHeight + blockSize - 1) / blockSize;
        int[] cx = new int[runs];
        int[] cy = new int[runs];
        int[] rad = new int[runs];
        int[] fort = new int[runs];
        int[][] cov = new int[runs][];
        int[][] near = new int[bw * bh][];
        int[] coveredStamp = new int[bw * bh];
        int[] nearStamp = new int[bw * bh];
        Arrays.fill(coveredStamp, -1);
        Arrays.fill(nearStamp, -1);
        int[] scratch = new int[bw * bh];
        for (int run = 0; run < runs; run++) {
            int n = index.runSize(run);
            long sx = 0;
            long sy = 0;
            for (int i = 0; i < n; i++) {
                sx += index.runCellX(run, i);
                sy += index.runCellY(run, i);
            }
            double mx = (double) sx / n;
            double my = (double) sy / n;
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double dx = index.runCellX(run, i) - mx;
                double dy = index.runCellY(run, i) - my;
                double d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            cx[run] = index.runCellX(run, best);
            cy[run] = index.runCellY(run, best);
            int far = 0;
            int coveredCount = 0;
            for (int i = 0; i < n; i++) {
                int x = index.runCellX(run, i);
                int y = index.runCellY(run, i);
                far = Math.max(far, (int) Math.ceil(Math.hypot(x - cx[run], y - cy[run])));
                int b = Math.min(bh - 1, y / blockSize) * bw + Math.min(bw - 1, x / blockSize);
                if (coveredStamp[b] != run) {
                    coveredStamp[b] = run;
                    scratch[coveredCount++] = b;
                }
            }
            rad[run] = Math.max(MIN_RADIUS_CELLS, Math.min(MAX_RADIUS_CELLS, far));
            int[] blocks = Arrays.copyOf(scratch, coveredCount);
            Arrays.sort(blocks);
            cov[run] = blocks;
            for (int b : blocks) {
                int bx0 = b % bw;
                int by0 = b / bw;
                for (int by = Math.max(0, by0 - REACH_BLOCKS); by <= Math.min(bh - 1, by0 + REACH_BLOCKS); by++) {
                    for (int bx = Math.max(0, bx0 - REACH_BLOCKS); bx <= Math.min(bw - 1, bx0 + REACH_BLOCKS); bx++) {
                        int nb = by * bw + bx;
                        if (nearStamp[nb] == run) continue;
                        nearStamp[nb] = run;
                        int[] list = near[nb];
                        near[nb] = list == null ? new int[]{run} : append(list, run);
                    }
                }
            }
            fort[run] = fortFor(forts, cx[run], cy[run]);
        }
        return new GarrisonableRegistry(index, blockSize, bw, bh, cx, cy, rad, fort, cov, near);
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

    /** Whether this registry was derived from exactly this index instance. */
    public boolean isBuiltFrom(FightingPositionIndex index) {
        return source == index;
    }

    public FightingPositionIndex source() {
        return source;
    }

    public int count() {
        return centreX.length;
    }

    /** The garrisonable containing this cell, or -1. */
    public int idAt(int x, int y) {
        return source.runIdAt(x, y);
    }

    public int centreX(int id) { return centreX[id]; }
    public int centreY(int id) { return centreY[id]; }
    /** Radius, in cells, of the defend circle that covers the garrisonable's useful ground. */
    public int radius(int id) { return radius[id]; }
    /** Cells of ground the garrisonable offers. */
    public int size(int id) { return source.runSize(id); }
    /** The fort this garrisonable fronts, or -1. */
    public int fortId(int id) { return fortId[id]; }

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
