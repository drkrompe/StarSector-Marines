package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.BuildingKind;

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.IntFunction;

/**
 * Buildings stood up in the natural drops' pinhole camera ({@code voids.md}, buildings in depth; owner: "a similar
 * trick for walls to get that partially 3d look"): a roof {@code h} metres up is its plan scaled about the view's
 * centre by {@code eye / (eye - h)}, and each wall turned to the camera is the quad between its foot on the ground and
 * its eave at the roof's scale. A wall turned away lies under its own roof, so only the faces toward the view's centre
 * are drawn; they are drawn before every roof, so a roof stands over the faces behind it. GL-free.
 *
 * <p><b>The faces hang from the roof as drawn.</b> A roof stops short of each outside cell by its eave
 * ({@link com.dillon.starsectormarines.battle.world.model.RoofCover#rects}), so a face is built on the outline of the
 * roof's own rectangles, never on its cells': its top is then the roof's edge at any view, and its foot the same line
 * on the ground ({@link #outline}).
 */
final class BuildingFaces {

    /** The eye's height over the ground, metres: the natural drops' camera. */
    static final float EYE_METERS = 120f;
    /**
     * Height classes, metres: a shed's or hut's low storey ({@link #SHED}), one, two and three storeys, a tower's
     * or tall works' ({@link #TALL}), then a destroyed building's rubble, flat ({@link #RAZED}). A broken first storey
     * left standing was tried: under this eye its walls stand a few pixels deep at play zoom, so a destroyed building
     * lies flat. UNTUNED.
     */
    static final float[] HEIGHTS = {3.2f, 4.5f, 8f, 11.5f, 15f, 0f};
    /** Height classes by name ({@link #HEIGHTS}). */
    static final int SHED = 0, ONE = 1, TWO = 2, THREE = 3, TALL = 4, RAZED = 5;
    /** How high a cut-away building's near walls still stand, metres: about knee height. UNTUNED. */
    static final float KNEE_METERS = .6f;
    /**
     * The share of a building's walls breached to rubble past which it is destroyed. A roof fallen in or burnt away
     * leaves its walls standing as a gutted shell (a creep's cave-in, a fire's), so only the walls going down razes it.
     * UNTUNED.
     */
    static final float DESTROYED_SHARE = .5f;

    /** How a town is built to live where it stands, as its walls show it: the renderer's view of its habitat. */
    enum Look { OPEN, SEALED, INSULATED, SHIELDED }

    /** What one cell's length of a wall shows at its foot. */
    enum Segment { BLIND, WINDOW, PANE, DOOR, LOADING, BREACH }

    /** Fields of one segment in {@link #segments}' result. */
    static final int SEG = 12;
    /** Steps a cell is cut into to trace a roof's outline: every eave depth is a tenth of a cell. */
    static final int RES = 10;

    private BuildingFaces() { }

    /**
     * A building's height class now ({@link #HEIGHTS}): what it was built as, until enough of its walls have been
     * breached ({@link #DESTROYED_SHARE}) that it drops flat with its rubble. The one place a building's height is
     * decided; an author's height for a building, when the map patch carries one, belongs here.
     *
     * @param breachedShare the share of its outer walls breached to rubble
     */
    static int heightClass(Built built, float breachedShare) {
        if (breachedShare >= DESTROYED_SHARE) return RAZED;
        return heightClass(built);
    }

    /**
     * What a building was built as, as far as its height goes: its kind, its floor's size, whether it is a sealed
     * town's block, whether it holds a tower's room, and a number in [0, 1) of its own that steps a street's
     * buildings up and down ({@link #hash} of its id).
     */
    record Built(BuildingKind kind, int floorCells, boolean sealed, boolean tower, float variation) { }

    /**
     * A building's height class from what it was built as. A small footprint is a shed, a hut, a pump house or a
     * pillbox, one low storey. Homes stand one to three storeys, mostly two; shops and stores one or two, a big
     * one three; works and warehouses one tall bay, the biggest a tall works or a silo; civic halls and clinics two
     * or three; forts' blocks one to three by size. A sealed town's blocks stack a storey higher, and a tower's room
     * makes a tower. UNTUNED.
     */
    static int heightClass(Built b) {
        if (b.tower()) return TALL;
        int n = b.floorCells();
        float v = b.variation();
        int at;
        if (n <= 12) return SHED;
        switch (b.kind()) {
            case INDUSTRIAL:
                at = n <= 40 ? (v < .5f ? SHED : ONE) : n <= 200 ? TWO : v < .35f ? TALL : THREE;
                break;
            case RESIDENTIAL:
                at = n <= 30 ? ONE : v < .25f ? ONE : v > .7f && n >= 60 ? THREE : TWO;
                break;
            case COMMERCIAL:
                at = n <= 24 ? ONE : v > .75f && n >= 80 ? THREE : v < .3f ? ONE : TWO;
                break;
            case FORTIFIED:
                at = n <= 40 ? ONE : n >= 200 ? THREE : TWO;
                break;
            default:
                at = n <= 24 ? ONE : v > .5f && n >= 120 ? THREE : TWO;
        }
        return b.sealed() ? Math.min(TALL, at + 1) : at;
    }

    /** The habitat a building's walls show: its wall's surface, and a frozen world's ground for the insulated town. */
    static Look look(String wallSurface, boolean frozen) {
        if (wallSurface != null) {
            if (wallSurface.startsWith("colony.ceramic-wall")) return Look.SEALED;
            if (wallSurface.startsWith("installation.light-concrete-wall")) return Look.SHIELDED;
        }
        return frozen ? Look.INSULATED : Look.OPEN;
    }

    /**
     * What a wall's segment shows at its foot, from the cell under it.
     *
     * @param standing   whether a wall, window or doorway still stands there; false where it has been breached
     * @param door       whether the cell is an outside doorway
     * @param window     whether the cell holds a window: a slit pane in its wall, or a window on its outer edge
     * @param pane       whether the window is a slit pane
     * @param industrial whether the building is works or a warehouse, whose doors are loading doors
     */
    static Segment segment(boolean standing, boolean door, boolean window, boolean pane, boolean industrial) {
        if (!standing) return Segment.BREACH;
        if (door) return industrial ? Segment.LOADING : Segment.DOOR;
        if (window) return pane ? Segment.PANE : Segment.WINDOW;
        return Segment.BLIND;
    }

    /**
     * What a storey of a wall's cell shows, from what stands at its foot ({@link #segment}). The ground storey is the
     * feature itself. An upper storey has a window only over a real window, stacked the way the ground one is, on a
     * building whose upper storeys have windows ({@link #upperWindows}); over a door or a blind wall it is blind. So
     * every pane on a face stands over an opening that sight and fire really pass.
     */
    static Segment storeyFeature(Segment ground, int storey, boolean upperWindows) {
        if (storey == 0) return ground;
        return upperWindows && (ground == Segment.WINDOW || ground == Segment.PANE) ? ground : Segment.BLIND;
    }

    /**
     * How far gone a window is, from its frame's structure: 0 whole, 1 shot out. A window whose frame is gone
     * ({@code maxStructure} 0 means none stands) is shot out.
     */
    static float windowHurt(int structure, int maxStructure) {
        if (maxStructure <= 0 || structure <= 0) return 1f;
        return Math.max(0f, Math.min(1f, 1f - structure / (float) maxStructure));
    }

    /** Whether a building's upper storeys have windows: homes, shops, offices and clinics, never works or a shield. */
    static boolean upperWindows(BuildingKind kind, Look look) {
        if (look == Look.SHIELDED) return false;
        switch (kind) {
            case RESIDENTIAL: case COMMERCIAL: case CIVIC: case MEDICAL: case UNKNOWN: case OTHER: return true;
            default: return false;
        }
    }

    /**
     * A segment's spare slot from {@link #segments}: it starts a run of at least {@link #CORNER_RUN} cells at an
     * outside corner of the building.
     */
    static final int CORNER_START = 1;
    /** A segment's spare slot from {@link #segments}: it ends such a run at an outside corner. */
    static final int CORNER_END = 2;
    /** A run shorter than this is an eave's jog at a doorway, not a building's corner. */
    static final float CORNER_RUN = 1.5f;

    /**
     * The outline's runs cut at every cell boundary, each piece a segment of {@link #SEG} floats:
     * {@code x0, y0, x1, y1, nx, ny}, then the cell under its middle (just inside the outline) as {@code x + y * gridW},
     * a spare slot holding its outside corners ({@link #CORNER_START}, {@link #CORNER_END}), which the caller may build
     * on, and last its foot {@code fx0, fy0, fx1, fy1}.
     *
     * <p><b>A face's foot stands on its wall's outer side; its top hangs from the roof.</b> The roof covers only the
     * inner part of a wall cell (its eave, {@link com.dillon.starsectormarines.battle.world.model.RoofCover#rects}), so
     * a foot on the outline would leave the rest of the wall cell's flat art showing as a strip along the ground. Each
     * point of the outline is pushed out along the normals of the edges meeting there to the next cell boundary: a run
     * reaches its wall's outer face, an outside corner reaches the wall's corner, an inside corner draws back to it,
     * and the short reveal where an eave steps in at a doorway closes up at the foot.
     */
    static float[] segments(float[] outline, int gridW) {
        float[] out = new float[outline.length / 6 * SEG * 4];
        // Each vertex's normal across the other axis: a run's end is pushed along the edge that turns there.
        HashMap<Long, Float> turnAt = new HashMap<>();
        for (int e = 0; e < outline.length; e += 6) {
            boolean alongX = outline[e + 1] == outline[e + 3];
            float across = alongX ? outline[e + 5] : outline[e + 4];
            turnAt.put(vertex(outline[e], outline[e + 1], alongX), across);
            turnAt.put(vertex(outline[e + 2], outline[e + 3], alongX), across);
        }
        int n = 0;
        for (int e = 0; e < outline.length; e += 6) {
            float x0 = outline[e], y0 = outline[e + 1], x1 = outline[e + 2], y1 = outline[e + 3];
            float nx = outline[e + 4], ny = outline[e + 5];
            boolean alongX = y0 == y1;
            float a = alongX ? x0 : y0, b = alongX ? x1 : y1;
            float lo = Math.min(a, b), hi = Math.max(a, b);
            // The run's ends as its foot stands: out past an outside corner, back from an inside one.
            float loTurn = turnAt.getOrDefault(alongX ? vertex(lo, y0, false) : vertex(x0, lo, true), 0f);
            float hiTurn = turnAt.getOrDefault(alongX ? vertex(hi, y0, false) : vertex(x0, hi, true), 0f);
            float footLo = push(lo, loTurn), footHi = push(hi, hiTurn);
            float footAcross = push(alongX ? y0 : x0, alongX ? ny : nx);
            float at = lo;
            while (at < hi - 1e-4f) {
                float next = Math.min(hi, (float) Math.floor(at + 1e-4f) + 1f);
                float mid = (at + next) / 2f;
                float mx = alongX ? mid : x0, my = alongX ? y0 : mid;
                int cx = (int) Math.floor(mx - nx * .2f), cy = (int) Math.floor(my - ny * .2f);
                if (n + SEG > out.length) out = Arrays.copyOf(out, out.length * 2 + SEG);
                if (alongX) { out[n] = at; out[n + 1] = y0; out[n + 2] = next; out[n + 3] = y0; }
                else { out[n] = x0; out[n + 1] = at; out[n + 2] = x0; out[n + 3] = next; }
                int corners = hi - lo < CORNER_RUN ? 0
                        : (at == lo && loTurn < 0f ? CORNER_START : 0) | (next == hi && hiTurn > 0f ? CORNER_END : 0);
                out[n + 4] = nx; out[n + 5] = ny; out[n + 6] = cx + cy * gridW; out[n + 7] = corners;
                float f0 = at == lo ? footLo : at, f1 = next == hi ? footHi : next;
                if (alongX) { out[n + 8] = f0; out[n + 9] = footAcross; out[n + 10] = f1; out[n + 11] = footAcross; }
                else { out[n + 8] = footAcross; out[n + 9] = f0; out[n + 10] = footAcross; out[n + 11] = f1; }
                n += SEG;
                at = next;
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** A coordinate pushed to the next cell boundary along a normal's sign; a boundary already stays put. */
    static float push(float v, float normal) {
        if (normal > 0f) return (float) Math.ceil(v - 1e-3f);
        if (normal < 0f) return (float) Math.floor(v + 1e-3f);
        return v;
    }

    /** A vertex's key, by the axis of the edge it belongs to, in tenth-cell steps. */
    private static long vertex(float x, float y, boolean ofEdgeAlongX) {
        long qx = Math.round(x * RES), qy = Math.round(y * RES);
        return (qx << 32 ^ qy & 0xFFFFFFFFL) << 1 | (ofEdgeAlongX ? 1L : 0L);
    }

    /** A stable number in [0, 1) for a cell and a salt: where a window is blown, where a hole is torn. */
    static float hash(int cell, int salt) {
        long h = cell * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }


    /**
     * How much of a wall turned to the camera still stands, as a share of its height, while its building is cut
     * away to show the rooms inside: all of it with the roof on, down to about knee height ({@link #KNEE_METERS}) with
     * the roof gone, following the roof's own fade so it never pops.
     *
     * @param cut how far the building is cut away, 0 with its roof on and 1 with it gone
     */
    static float standing(float heightMeters, float cut) {
        float knee = Math.min(1f, KNEE_METERS / heightMeters);
        float c = Math.max(0f, Math.min(1f, cut));
        return 1f - (1f - knee) * c;
    }

    /** Storeys of windows a wall of this height carries. */
    static int storeys(float heightMeters) {
        return Math.max(1, (int) (heightMeters / 3.6f));
    }

    /** Pixels a cell must span, or a storey stand, on screen for a face's trim, windows and doors to be drawn. */
    static final float DETAIL_CELL_PX = 10f, DETAIL_STOREY_PX = 7f;

    /**
     * Whether a face is big enough on screen to be dressed (trim, storey lines, windows, doors) rather than shown as
     * its shaded material: its cells wide enough, or its storeys tall enough. Zoomed out, the faces far from the
     * view's centre stand tall while their cells are narrow, and a tall face bare of storeys reads as a blank slab.
     */
    static boolean detailed(float cellPx, float storeyPx) {
        return cellPx >= DETAIL_CELL_PX || storeyPx >= DETAIL_STOREY_PX;
    }

    /** How much a plan {@code h} metres up is scaled about the view's centre. */
    static float lift(float heightMeters) {
        return EYE_METERS / (EYE_METERS - heightMeters);
    }

    /** Where a point {@code p} of a plan lifted by {@code lift} stands, the view's centre at {@code c}. */
    static float lifted(float p, float c, float lift) {
        return c + (p - c) * lift;
    }

    /**
     * Where a face shows the point {@code z} of the way up it (a share of its height), along one axis: between its
     * foot {@code foot} on the ground and its eave {@code eave} lifted by {@code lift} about the view's centre
     * {@code c}. The face is painted so ({@code FacePainter}), and whatever hangs on it is fixed to it so.
     */
    static float onFace(float foot, float eave, float c, float lift, float z) {
        return foot + (lifted(eave, c, lift) - foot) * z;
    }

    /**
     * The ground point a plan lifted by {@code lift} shows at {@code p}: what of a building {@code h} metres up is
     * drawn over a ground point, so whether a unit standing there is hidden behind it.
     */
    static float unlifted(float p, float c, float lift) {
        return c + (p - c) / lift;
    }

    /**
     * The outline of what a roof covers: each straight run of its edge, as {@code x0, y0, x1, y1, nx, ny} in cells,
     * its outward normal last.
     *
     * @param cells   the roof's cells, {@code x + y * gridW}
     * @param rectsOf each cell's covered rectangles, {@code {x0, y0, x1, y1}} in cell units from its south-west corner
     */
    static float[] outline(int[] cells, int gridW, IntFunction<float[][]> rectsOf) {
        if (cells.length == 0) return new float[0];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int at : cells) {
            int x = at % gridW, y = at / gridW;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int w = (maxX - minX + 1) * RES, h = (maxY - minY + 1) * RES;
        boolean[] in = new boolean[w * h];
        for (int at : cells) {
            int ox = (at % gridW - minX) * RES, oy = (at / gridW - minY) * RES;
            for (float[] r : rectsOf.apply(at)) {
                int i0 = Math.round(r[0] * RES), i1 = Math.round(r[2] * RES);
                int j0 = Math.round(r[1] * RES), j1 = Math.round(r[3] * RES);
                for (int j = j0; j < j1; j++) for (int i = i0; i < i1; i++) in[(oy + j) * w + ox + i] = true;
            }
        }
        float[] out = new float[64];
        int n = 0;
        // Edges across rows: between sub-cell row j-1 and j.
        for (int j = 0; j <= h; j++) {
            int run = -1, dir = 0;
            for (int i = 0; i <= w; i++) {
                int d = 0;
                if (i < w) {
                    boolean below = j > 0 && in[(j - 1) * w + i], above = j < h && in[j * w + i];
                    d = below == above ? 0 : below ? 1 : -1;
                }
                if (d != dir) {
                    if (dir != 0) n = put(out = grow(out, n), n, minX + run / (float) RES, minY + j / (float) RES,
                            minX + i / (float) RES, minY + j / (float) RES, 0, dir);
                    run = i;
                    dir = d;
                }
            }
        }
        // Edges across columns: between sub-cell column i-1 and i.
        for (int i = 0; i <= w; i++) {
            int run = -1, dir = 0;
            for (int j = 0; j <= h; j++) {
                int d = 0;
                if (j < h) {
                    boolean left = i > 0 && in[j * w + i - 1], right = i < w && in[j * w + i];
                    d = left == right ? 0 : left ? 1 : -1;
                }
                if (d != dir) {
                    if (dir != 0) n = put(out = grow(out, n), n, minX + i / (float) RES, minY + run / (float) RES,
                            minX + i / (float) RES, minY + j / (float) RES, dir, 0);
                    run = j;
                    dir = d;
                }
            }
        }
        float[] edges = new float[n];
        System.arraycopy(out, 0, edges, 0, n);
        return edges;
    }

    private static float[] grow(float[] out, int n) {
        if (n + 6 <= out.length) return out;
        float[] bigger = new float[out.length * 2];
        System.arraycopy(out, 0, bigger, 0, n);
        return bigger;
    }

    private static int put(float[] out, int n, float x0, float y0, float x1, float y1, float nx, float ny) {
        out[n++] = x0; out[n++] = y0; out[n++] = x1; out[n++] = y1; out[n++] = nx; out[n++] = ny;
        return n;
    }

    /** Whether a wall at {@code (mx, my)} with outward normal {@code (nx, ny)} is turned to an eye over {@code (cx, cy)}. */
    static boolean facing(float nx, float ny, float mx, float my, float cx, float cy) {
        return nx * (cx - mx) + ny * (cy - my) > 0f;
    }

    /** A face's light: the sun from the north-west of the map, so the faces turned that way are the brightest. */
    static float shade(float nx, float ny) {
        return .72f + .2f * (-.6f * nx + .8f * ny);
    }
}
