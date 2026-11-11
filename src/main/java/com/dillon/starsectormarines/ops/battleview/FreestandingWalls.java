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
        /** A sealed town's envelope ({@code SealedTown.ENVELOPE_WALL}): its body and its buttresses. */
        ENVELOPE(8f, .58f, .59f, .58f),
        /** The plinth along the envelope's outer course ({@code SealedTown.ENVELOPE_PLINTH}). */
        ENVELOPE_PLINTH(3.5f, .42f, .43f, .44f),
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
     *   <li>{@code habitat.envelope-wall}: a sealed town's envelope ({@code PrecinctWardStage.sealTowns}), its
     *       {@code @plinth} the lower course along its outer face.</li>
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
            Part part = part(surfaceId);
            if (part != null) return part == Part.PLINTH ? Kind.ENVELOPE_PLINTH : Kind.ENVELOPE;
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

    /** Which part of a sealed town's envelope a wall cell is, drawn each in its own way ({@link FreestandingWallFaces}). */
    enum Part { BODY, BUTTRESS, PLINTH }

    /** The envelope's surface ids ({@code SealedTown}): its body, and its outer course's {@code @look}s. */
    static final String ENVELOPE = "habitat.envelope-wall";

    /** The part of the envelope a surface is, or null where it is no envelope's. */
    static Part part(String surfaceId) {
        if (surfaceId == null || !surfaceId.startsWith(ENVELOPE)) return null;
        if (surfaceId.endsWith("@plinth")) return Part.PLINTH;
        if (surfaceId.endsWith("@buttress")) return Part.BUTTRESS;
        return Part.BODY;
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
    record Run(Kind kind, float meters, int minX, int minY, int maxX, int maxY, int[] cells, float[] segments,
               float[] strokes, float[] edges, float[] joints) { }

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
            int mark = -k;
            float[] strokes = strokes(cells, gridW, gridH, at -> kind[at] == mark);
            runs.add(new Run(of, height(of, count), minX, minY, maxX, maxY, cells, outline(cells, gridW, kind, mark),
                    strokes, edges(strokes, gridW), joints(strokes)));
        }
        runs.sort((a, b) -> Float.compare(a.meters(), b.meters()));
        return runs;
    }

    /** Floats per stroke: its first and last cells' centres, then whether each end is free (1) or joins (0). */
    static final int STROKE = 6;
    /** The directions a stroke runs: east, north, north-east, north-west. */
    private static final int[] SDX = {1, 0, 1, -1}, SDY = {0, 1, 1, 1};

    /**
     * A run's cells laid out as straight strokes, so a wall stands as the line it was built along rather than as a
     * staircase of cells: every chain of two or more cells in a line east, north or diagonally is a stroke from its
     * first cell's centre to its last's, and a cell in no chain is a stroke of its own (a post). A diagonal step only
     * counts where no cell beside it already joins the two orthogonally. Each end is free where no other cell of the
     * run touches its cell from outside the stroke: a diagonal's free end is cut square across it, a joined end stops
     * at its cell's centre inside the straight wall it meets, or on a pier (a post) where it meets only other
     * diagonals. {@link #STROKE} floats each.
     */
    static float[] strokes(int[] cells, int gridW, int gridH, IntPredicate inRun) {
        float[] out = new float[STROKE * 8];
        int n = 0;
        boolean[] covered = new boolean[cells.length];
        for (int dir = 0; dir < 4; dir++) {
            int dx = SDX[dir], dy = SDY[dir];
            for (int i = 0; i < cells.length; i++) {
                int x = cells[i] % gridW, y = cells[i] / gridW;
                if (step(x - dx, y - dy, dx, dy, gridW, gridH, inRun)) continue;
                int len = 0;
                while (step(x + len * dx, y + len * dy, dx, dy, gridW, gridH, inRun)) len++;
                if (len == 0) continue;
                for (int k = 0; k <= len; k++) {
                    int at = Arrays.binarySearch(cells, x + k * dx + (y + k * dy) * gridW);
                    if (at >= 0) covered[at] = true;
                }
                if (n + 3 * STROKE > out.length) out = Arrays.copyOf(out, out.length * 2 + 3 * STROKE);
                n = stroke(out, n, x, y, x + len * dx, y + len * dy, dx, dy, gridW, gridH, inRun);
                if (dx != 0 && dy != 0) {
                    // A diagonal's end that stands free is cut square across it ({@link #corners}); one that runs
                    // into a straight wall stops inside that wall's end cell; one that meets only other diagonals
                    // stands on a square pier, so the two meet on the grid.
                    for (int end = 0; end < 2; end++) {
                        int ex = x + end * len * dx, ey = y + end * len * dy;
                        if (out[n - 2 + end] == 0f && !straight(ex, ey, gridW, gridH, inRun)
                                && !pier(out, n, ex, ey)) {
                            n = stroke(out, n, ex, ey, ex, ey, 0, 0, gridW, gridH, inRun);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < cells.length; i++) {
            if (covered[i]) continue;
            int x = cells[i] % gridW, y = cells[i] / gridW;
            if (n + STROKE > out.length) out = Arrays.copyOf(out, out.length * 2);
            n = stroke(out, n, x, y, x, y, 0, 0, gridW, gridH, inRun);
        }
        return Arrays.copyOf(out, n);
    }

    /** Whether a pier already stands on cell {@code (x, y)} among the first {@code n} floats of strokes. */
    private static boolean pier(float[] out, int n, int x, int y) {
        for (int o = 0; o < n; o += STROKE) {
            if (out[o] == x + .5f && out[o + 1] == y + .5f && out[o + 2] == x + .5f && out[o + 3] == y + .5f) return true;
        }
        return false;
    }

    /** Whether a cell of the run has a neighbour across a side, so a straight stroke stands on it. */
    private static boolean straight(int x, int y, int gridW, int gridH, IntPredicate inRun) {
        for (int s = 0; s < 4; s++) if (in(x + DX[s], y + DY[s], gridW, gridH, inRun)) return true;
        return false;
    }

    /** Whether the run steps on from {@code (x, y)} by {@code (dx, dy)}: both cells in it, a diagonal unbridged. */
    private static boolean step(int x, int y, int dx, int dy, int gridW, int gridH, IntPredicate inRun) {
        if (!in(x, y, gridW, gridH, inRun) || !in(x + dx, y + dy, gridW, gridH, inRun)) return false;
        return dx == 0 || dy == 0
                || !in(x + dx, y, gridW, gridH, inRun) && !in(x, y + dy, gridW, gridH, inRun);
    }

    private static boolean in(int x, int y, int gridW, int gridH, IntPredicate inRun) {
        return x >= 0 && y >= 0 && x < gridW && y < gridH && inRun.test(x + y * gridW);
    }

    private static int stroke(float[] out, int n, int x0, int y0, int x1, int y1, int dx, int dy, int gridW,
                              int gridH, IntPredicate inRun) {
        out[n] = x0 + .5f;
        out[n + 1] = y0 + .5f;
        out[n + 2] = x1 + .5f;
        out[n + 3] = y1 + .5f;
        out[n + 4] = free(x0, y0, dx, dy, gridW, gridH, inRun) ? 1f : 0f;
        out[n + 5] = free(x1, y1, -dx, -dy, gridW, gridH, inRun) ? 1f : 0f;
        return n + STROKE;
    }

    /**
     * Whether a stroke's end cell {@code (x, y)} stands free: no other cell of the run touches it except the stroke's
     * own next cell, along {@code (bx, by)}. A post ({@code (0, 0)}) is free only if alone.
     */
    private static boolean free(int x, int y, int bx, int by, int gridW, int gridH, IntPredicate inRun) {
        for (int s = 0; s < 8; s++) {
            if (RX[s] == bx && RY[s] == by && (bx != 0 || by != 0)) continue;
            if (in(x + RX[s], y + RY[s], gridW, gridH, inRun)) return false;
        }
        return true;
    }

    /** A wall's half-thickness, in cells: a diagonal stands as thick as a straight wall. */
    static final float HALF = .5f;

    /**
     * A stroke's footprint from {@code (x0, y0)} to {@code (x1, y1)}, {@code half} either side of its line, a straight
     * one reaching a half cell past each end and a diagonal stopping at its end cells' centres, as four corners into
     * {@code out}: start and end on one side, then end and start on the other. A post (no length) is a square.
     */
    static void corners(float x0, float y0, float x1, float y1, float half, float[] out) {
        corners(x0, y0, x1, y1, false, false, half, out);
    }

    /**
     * How far past its end cell's centre a diagonal's free end reaches: as far as it can while its square cut's two
     * corners stay on the cell's far sides, so the end finishes inside its cell.
     */
    static final float FREE_REACH = .5f * (float) Math.sqrt(2) - HALF;

    /** {@link #corners} of stroke {@code o} of {@code strokes}, a diagonal's free ends reaching {@link #FREE_REACH}. */
    static void corners(float[] strokes, int o, float half, float[] out) {
        corners(strokes[o], strokes[o + 1], strokes[o + 2], strokes[o + 3], strokes[o + 4] != 0f,
                strokes[o + 5] != 0f, half, out);
    }

    /**
     * A stroke's footprint as {@link #corners(float, float, float, float, float, float[])}, a diagonal's free start
     * or end reaching {@link #FREE_REACH} past its cell's centre, cut square across.
     */
    static void corners(float x0, float y0, float x1, float y1, boolean startFree, boolean endFree, float half,
                        float[] out) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            dx = 1f;
            dy = 0f;
        } else {
            dx /= len;
            dy /= len;
        }
        float nx = dy, ny = -dx;
        // A straight wall reaches its end cells' edges; a diagonal stops at their centres, inside their piers.
        boolean diagonal = dx != 0f && dy != 0f;
        float from = diagonal ? (startFree ? FREE_REACH : 0f) : .5f, to = diagonal ? (endFree ? FREE_REACH : 0f) : .5f;
        float sx = x0 - dx * from, sy = y0 - dy * from, ex = x1 + dx * to, ey = y1 + dy * to;
        out[0] = sx + nx * half; out[1] = sy + ny * half;
        out[2] = ex + nx * half; out[3] = ey + ny * half;
        out[4] = ex - nx * half; out[5] = ey - ny * half;
        out[6] = sx - nx * half; out[7] = sy - ny * half;
    }

    /** Floats per outline edge: its ends in cells, its outward normal, and the wall cell it stands on. */
    static final int EDGE = 7;
    /** How far either side of an edge its open ground and its wall are probed, cells. */
    private static final float PROBE = 1e-3f;

    /**
     * The outline of a run's strokes taken together, so where strokes meet (a diagonal into a straight wall, a pier,
     * a jog) the run stands as one wall: every side of every stroke's footprint ({@link #corners}), cut where another
     * footprint crosses it, keeping only the pieces with open ground outside them. A piece two footprints share is
     * kept once, for the first. {@link #EDGE} floats each, the normal pointing out to the open ground.
     */
    static float[] edges(float[] strokes, int gridW) {
        int count = strokes.length / STROKE;
        float[][] quads = new float[count][8];
        for (int i = 0; i < count; i++) {
            int o = i * STROKE;
            corners(strokes, o, HALF, quads[i]);
        }
        float[] out = new float[EDGE * 8];
        int n = 0;
        float[] cuts = new float[16];
        for (int i = 0; i < count; i++) {
            float[] q = quads[i];
            float cx = (q[0] + q[2] + q[4] + q[6]) / 4f, cy = (q[1] + q[3] + q[5] + q[7]) / 4f;
            for (int side = 0; side < 4; side++) {
                int a = side * 2, b = (a + 2) % 8;
                float ax = q[a], ay = q[a + 1], bx = q[b], by = q[b + 1];
                float ex = bx - ax, ey = by - ay, len = (float) Math.hypot(ex, ey);
                if (len < 1e-4f) continue;
                float nx = ey / len, ny = -ex / len;
                if (nx * ((ax + bx) / 2f - cx) + ny * ((ay + by) / 2f - cy) < 0f) {
                    nx = -nx;
                    ny = -ny;
                }
                int cutCount = 0;
                cuts[cutCount++] = 0f;
                cuts[cutCount++] = 1f;
                for (int j = 0; j < count; j++) {
                    if (j == i) continue;
                    float[] r = quads[j];
                    for (int k = 0; k < 8; k += 2) {
                        float px = r[k], py = r[k + 1], qx = r[(k + 2) % 8], qy = r[(k + 3) % 8];
                        if (cutCount + 2 > cuts.length) cuts = Arrays.copyOf(cuts, cuts.length * 2);
                        // Where the other side crosses this one, and where its corner lies on it.
                        float fx = qx - px, fy = qy - py, den = ex * fy - ey * fx;
                        if (Math.abs(den) > 1e-9f) {
                            float t = ((px - ax) * fy - (py - ay) * fx) / den;
                            float u = ((px - ax) * ey - (py - ay) * ex) / den;
                            if (t > 0f && t < 1f && u >= -1e-5f && u <= 1f + 1e-5f) cuts[cutCount++] = t;
                        }
                        float off = ((px - ax) * ey - (py - ay) * ex) / len;
                        float t = ((px - ax) * ex + (py - ay) * ey) / (len * len);
                        if (Math.abs(off) < 1e-4f && t > 0f && t < 1f) cuts[cutCount++] = t;
                    }
                }
                Arrays.sort(cuts, 0, cutCount);
                int open = -1;
                for (int c = 0; c + 1 < cutCount; c++) {
                    float t0 = cuts[c], t1 = cuts[c + 1];
                    if (t1 - t0 < 1e-4f) continue;
                    float mx = ax + ex * (t0 + t1) / 2f, my = ay + ey * (t0 + t1) / 2f;
                    boolean keep = true;
                    for (int j = 0; j < count && keep; j++) {
                        if (j == i) continue;
                        if (contains(quads[j], mx + nx * PROBE, my + ny * PROBE)) keep = false;
                        else if (j < i && contains(quads[j], mx - nx * PROBE, my - ny * PROBE)) keep = false;
                    }
                    if (!keep) {
                        open = -1;
                        continue;
                    }
                    float x0 = ax + ex * t0, y0 = ay + ey * t0, x1 = ax + ex * t1, y1 = ay + ey * t1;
                    if (open >= 0) {
                        // Runs on from the piece before it: one piece.
                        out[open + 2] = x1;
                        out[open + 3] = y1;
                        continue;
                    }
                    if (n + EDGE > out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[n] = x0; out[n + 1] = y0; out[n + 2] = x1; out[n + 3] = y1;
                    out[n + 4] = nx; out[n + 5] = ny;
                    out[n + 6] = (int) Math.floor(mx - nx * .25f) + (int) Math.floor(my - ny * .25f) * gridW;
                    open = n;
                    n += EDGE;
                }
            }
        }
        for (int e = 0; e < n; e += EDGE) {
            // A piece's wall cell, from its middle once merged.
            float mx = (out[e] + out[e + 2]) / 2f, my = (out[e + 1] + out[e + 3]) / 2f;
            out[e + 6] = (int) Math.floor(mx - out[e + 4] * .25f) + (int) Math.floor(my - out[e + 5] * .25f) * gridW;
        }
        return Arrays.copyOf(out, n);
    }

    /** A joint across a cap's stroke, as a quad's four corners: its width, cells. */
    static final float JOINT_WIDE = .05f;

    /**
     * The joints across a run's cap: one across each stroke at every second cell, as quads of eight floats, but none
     * where another stroke's footprint covers it, so a joint never crosses where strokes meet.
     */
    static float[] joints(float[] strokes) {
        int count = strokes.length / STROKE;
        float[][] quads = new float[count][8];
        for (int i = 0; i < count; i++) {
            int o = i * STROKE;
            corners(strokes, o, HALF, quads[i]);
        }
        float[] out = new float[64];
        int n = 0;
        for (int i = 0; i < count; i++) {
            int o = i * STROKE;
            float x0 = strokes[o], y0 = strokes[o + 1], x1 = strokes[o + 2], y1 = strokes[o + 3];
            int cells = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))) + 1);
            float[] q = quads[i];
            float len = (float) Math.hypot(q[2] - q[0], q[3] - q[1]);
            if (len < 1e-4f) continue;
            float w = JOINT_WIDE / len / 2f;
            for (int k = 2; k < cells; k += 2) {
                float t = k / (float) cells;
                float mx = (q[0] + (q[2] - q[0]) * t + q[6] + (q[4] - q[6]) * t) / 2f;
                float my = (q[1] + (q[3] - q[1]) * t + q[7] + (q[5] - q[7]) * t) / 2f;
                boolean covered = false;
                for (int j = 0; j < count && !covered; j++) covered = j != i && contains(quads[j], mx, my);
                if (covered) continue;
                if (n + 8 > out.length) out = Arrays.copyOf(out, out.length * 2);
                float t0 = t - w, t1 = t + w;
                out[n] = q[0] + (q[2] - q[0]) * t0; out[n + 1] = q[1] + (q[3] - q[1]) * t0;
                out[n + 2] = q[0] + (q[2] - q[0]) * t1; out[n + 3] = q[1] + (q[3] - q[1]) * t1;
                out[n + 4] = q[6] + (q[4] - q[6]) * t1; out[n + 5] = q[7] + (q[5] - q[7]) * t1;
                out[n + 6] = q[6] + (q[4] - q[6]) * t0; out[n + 7] = q[7] + (q[5] - q[7]) * t0;
                n += 8;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Whether {@code (x, y)} lies strictly inside a convex quad of four corners in order. */
    static boolean contains(float[] q, float x, float y) {
        int sign = 0;
        for (int k = 0; k < 8; k += 2) {
            float ax = q[k], ay = q[k + 1], bx = q[(k + 2) % 8], by = q[(k + 3) % 8];
            float cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
            if (Math.abs(cross) < 1e-7f) return false;
            int s = cross > 0f ? 1 : -1;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return true;
    }

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    /** A run's reach: across a side or a corner, as a diagonal wall's cells touch. */
    private static final int[] RX = {1, 0, -1, 0, 1, 1, -1, -1}, RY = {0, 1, 0, -1, 1, -1, 1, -1};

    /**
     * The outline of a run's cells as full cells: every side of a cell whose neighbour across it is no cell of the
     * run, as one segment of {@link BuildingFaces#SEG} floats ({@code x0, y0, x1, y1, nx, ny}, the cell, a spare 0,
     * and its foot, the same line).
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
                // A full cell's side: its foot is the same line.
                out[n + 8] = x0; out[n + 9] = y0; out[n + 10] = x1; out[n + 11] = y1;
                n += BuildingFaces.SEG;
            }
        }
        return Arrays.copyOf(out, n);
    }
}
