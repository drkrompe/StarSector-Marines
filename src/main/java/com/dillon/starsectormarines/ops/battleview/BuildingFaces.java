package com.dillon.starsectormarines.ops.battleview;

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
    /** Height classes, metres: one, two and three storeys. UNTUNED. */
    static final float[] HEIGHTS = {4.5f, 8f, 11.5f};
    /** Steps a cell is cut into to trace a roof's outline: every eave depth is a tenth of a cell. */
    static final int RES = 10;

    private BuildingFaces() { }

    /** A building's storey class ({@link #HEIGHTS}), from its floor's cells. UNTUNED. */
    static int heightClass(int floorCells) {
        if (floorCells <= 36) return 0;
        if (floorCells <= 150) return 1;
        return 2;
    }

    /** Storeys of windows a wall of this height carries. */
    static int storeys(float heightMeters) {
        return Math.max(1, Math.round(heightMeters / 3.8f));
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
