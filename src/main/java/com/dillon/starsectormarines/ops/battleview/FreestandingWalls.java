package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * Walls that are no building's, stood up in the same pinhole camera as the buildings ({@link BuildingFaces}): a sealed
 * town's perimeter, an installation's, a Cordon's blast walls, a compound's masonry, a garden's olive wall, an authored
 * map's walls. Each connected run of one kind's wall cells stands at its kind's height; its faces turned to the view's
 * centre run from their foot on the ground to its cap, the run's cells lifted about the view's centre. GL-free.
 *
 * <p>Fences, sandbags, jersey barriers and windbreaks are doodads, not wall cells, and lie flat.
 */
final class FreestandingWalls {

    /**
     * What a freestanding wall was built as, from its surface, with its height in metres and its plain tint. UNTUNED.
     * {@link #NONE} is a wall not stood up: a ship's bulkhead, or a wall out over the void.
     */
    enum Kind {
        SEALED_PERIMETER(7f, .48f, .50f, .53f),
        INSTALLATION(5f, .42f, .43f, .44f),
        BLAST(3.5f, .62f, .62f, .60f),
        COMPOUND(2.4f, .62f, .55f, .47f),
        GARDEN(1.6f, .46f, .50f, .37f),
        UNKNOWN(2.4f, .56f, .54f, .50f),
        NONE(0f, 0f, 0f, 0f);

        final float meters, r, g, b;

        Kind(float meters, float r, float g, float b) {
            this.meters = meters; this.r = r; this.g = g; this.b = b;
        }
    }

    /**
     * A run of fewer cells than this is a ruin's broken stub (a wasteland lot's, a delve's), which stands no higher
     * than a compound wall whatever it was built of: a ruin's ceramic is not a sealed town's perimeter. UNTUNED.
     */
    static final int STUB_CELLS = 6;

    private static final Kind[] KINDS = Kind.values();

    private FreestandingWalls() { }

    /**
     * A freestanding wall's kind, from its surface id (a {@code @look} suffix kept) and whether it stands on a fort's
     * lot ({@code worksStyle} non-zero).
     *
     * <ul>
     *   <li>{@code colony.ceramic-wall}, and the shielded town's brutalist light concrete: a sealed town's perimeter
     *       ({@code OwnedMapRecipe.perimeter}).</li>
     *   <li>{@code installation.dark-concrete-wall}: an installation's perimeter ({@code InstallationWalls}).</li>
     *   <li>{@code installation.light-concrete-wall}, and any wall on a fort's lot: a blast wall
     *       ({@code CordonBuilders.BLAST}, a fort's works).</li>
     *   <li>{@code colony.masonry-wall}, {@code urban.wall}: a compound's wall.</li>
     *   <li>{@code colony.olive-wall}: a garden's or yard's wall.</li>
     *   <li>{@code ship.*}: a bulkhead, not stood up.</li>
     *   <li>None, or anything else: an authored wall ({@code OwnedMapRecipe.walls}).</li>
     * </ul>
     */
    static Kind kind(String surfaceId, int worksStyle) {
        if (surfaceId != null) {
            if (surfaceId.startsWith("ship")) return Kind.NONE;
            if (surfaceId.startsWith("installation.light-concrete-wall") && surfaceId.contains("@brutalist"))
                return Kind.SEALED_PERIMETER;
            if (surfaceId.startsWith("installation.dark-concrete-wall")) return Kind.INSTALLATION;
        }
        if (worksStyle != 0) return Kind.BLAST;
        if (surfaceId == null) return Kind.UNKNOWN;
        if (surfaceId.startsWith("colony.ceramic-wall")) return Kind.SEALED_PERIMETER;
        if (surfaceId.startsWith("installation.light-concrete-wall")) return Kind.BLAST;
        if (surfaceId.startsWith("colony.masonry-wall") || surfaceId.startsWith("urban.wall")) return Kind.COMPOUND;
        if (surfaceId.startsWith("colony.olive-wall")) return Kind.GARDEN;
        return Kind.UNKNOWN;
    }

    /**
     * A sealed town's perimeter rings a lot, so a run of its material shorter than this is a pod's or a yard's wall
     * in the same shell (a sealed town's stalls and kiosks), standing as a compound wall. UNTUNED.
     */
    static final int PERIMETER_CELLS = 48;

    /**
     * How high a run of {@code cells} wall cells of {@code kind} stands, metres: its kind's; a ruin's stub, or a
     * sealed town's shell too short to be its perimeter, no higher than a compound wall.
     */
    static float height(Kind kind, int cells) {
        boolean small = cells < STUB_CELLS || kind == Kind.SEALED_PERIMETER && cells < PERIMETER_CELLS;
        if (small) return Math.min(kind.meters, Kind.COMPOUND.meters);
        return kind.meters;
    }

    /**
     * One connected run of a kind's freestanding wall cells: its height, its bounds in cells, its cells
     * ({@code x + y * gridW}) and the outline of their union cut by cell ({@link BuildingFaces#segments}' layout,
     * the spare slot unused).
     */
    record Run(Kind kind, float meters, int minX, int minY, int maxX, int maxY, int[] cells, float[] segments) { }

    /**
     * Every run of freestanding wall on a {@code gridW} by {@code gridH} map, lowest first, so a taller wall drawn
     * later stands over a lower one beside it. Cells join a run across a side or a corner (a diagonal wall is one
     * run); its outline is still drawn on the cells' sides.
     *
     * @param freestanding whether a cell ({@code x + y * gridW}) is a wall no building owns
     * @param kindOf       a freestanding cell's kind; {@link Kind#NONE} leaves it flat
     */
    static List<Run> runs(int gridW, int gridH, IntPredicate freestanding, IntFunction<Kind> kindOf) {
        int n = gridW * gridH;
        byte[] kind = new byte[n];
        for (int at = 0; at < n; at++) {
            kind[at] = (byte) (freestanding.test(at) ? kindOf.apply(at).ordinal() + 1 : 0);
            if (kind[at] == Kind.NONE.ordinal() + 1) kind[at] = 0;
        }
        List<Run> runs = new ArrayList<>();
        int[] stack = new int[64];
        for (int seed = 0; seed < n; seed++) {
            int k = kind[seed];
            if (k <= 0) continue;
            int[] cells = new int[16];
            int count = 0, top = 0;
            stack[top++] = seed;
            kind[seed] = (byte) -k;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            while (top > 0) {
                int at = stack[--top];
                if (count == cells.length) cells = Arrays.copyOf(cells, count * 2);
                cells[count++] = at;
                int x = at % gridW, y = at / gridW;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int s = 0; s < 8; s++) {
                    int nx = x + RX[s], ny = y + RY[s];
                    if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue;
                    int next = nx + ny * gridW;
                    if (kind[next] != k) continue;
                    kind[next] = (byte) -k;
                    if (top == stack.length) stack = Arrays.copyOf(stack, top * 2);
                    stack[top++] = next;
                }
            }
            cells = Arrays.copyOf(cells, count);
            Arrays.sort(cells);
            Kind of = KINDS[k - 1];
            runs.add(new Run(of, height(of, count), minX, minY, maxX, maxY, cells, outline(cells, gridW, kind, -k)));
        }
        runs.sort((a, b) -> Float.compare(a.meters(), b.meters()));
        return runs;
    }

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    /** A run's reach: across a side or a corner, as a diagonal wall's cells touch. */
    private static final int[] RX = {1, 0, -1, 0, 1, 1, -1, -1}, RY = {0, 1, 0, -1, 1, -1, 1, -1};

    /**
     * The outline of a run's cells as full cells: every side of a cell whose neighbour across it is no cell of the
     * run, as one segment of {@link BuildingFaces#SEG} floats ({@code x0, y0, x1, y1, nx, ny}, the cell, a spare 0).
     */
    private static float[] outline(int[] cells, int gridW, byte[] mark, int k) {
        float[] out = new float[cells.length * 2 * BuildingFaces.SEG];
        int n = 0;
        for (int at : cells) {
            int x = at % gridW, y = at / gridW;
            for (int s = 0; s < 4; s++) {
                int nx = x + DX[s], ny = y + DY[s];
                boolean inside = nx >= 0 && ny >= 0 && nx < gridW && ny < mark.length / gridW
                        && mark[nx + ny * gridW] == k;
                if (inside) continue;
                if (n + BuildingFaces.SEG > out.length) out = Arrays.copyOf(out, out.length * 2);
                // The side between the cell and its neighbour, its normal toward the neighbour.
                float x0, y0, x1, y1;
                switch (s) {
                    case 0: x0 = x + 1; y0 = y; x1 = x + 1; y1 = y + 1; break;
                    case 1: x0 = x; y0 = y + 1; x1 = x + 1; y1 = y + 1; break;
                    case 2: x0 = x; y0 = y; x1 = x; y1 = y + 1; break;
                    default: x0 = x; y0 = y; x1 = x + 1; y1 = y; break;
                }
                out[n] = x0; out[n + 1] = y0; out[n + 2] = x1; out[n + 3] = y1;
                out[n + 4] = DX[s]; out[n + 5] = DY[s]; out[n + 6] = at; out[n + 7] = 0f;
                n += BuildingFaces.SEG;
            }
        }
        return Arrays.copyOf(out, n);
    }
}
