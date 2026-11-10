package com.dillon.starsectormarines.ops.battleview;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Buildings stood up in the natural drops' pinhole camera ({@code voids.md}, buildings in depth; owner: "a similar
 * trick for walls to get that partially 3d look"): a roof {@code h} metres up is its plan scaled about the view's
 * centre by {@code eye / (eye - h)}, and each wall turned to the camera is the quad between its foot on the ground and
 * its eave at the roof's scale. A wall turned away lies under its own roof, so only the faces toward the view's centre
 * are drawn; they are drawn before every roof, so a roof stands over the faces behind it. GL-free.
 */
final class BuildingFaces {

    /**
     * Stands buildings up with wall faces and lifted roofs. A temporary A/B switch, off by default: remove it, and this
     * path or the flat roofs with it, once the owner decides keep, keep for tall buildings, or drop
     * ({@code voids.md}, buildings in depth; picks question voids-building-faces).
     */
    static final String SWITCH = "battle.render.buildingFaces";

    /** The eye's height over the ground, metres: the natural drops' camera. */
    static final float EYE_METERS = 120f;

    private BuildingFaces() { }

    static boolean on() {
        return Boolean.getBoolean(SWITCH);
    }

    /** A building's eave height, metres, from the cells its roof covers: one, two or three storeys. UNTUNED. */
    static float heightMeters(int roofCells) {
        if (roofCells <= 48) return 4.5f;
        if (roofCells <= 180) return 8f;
        return 11.5f;
    }

    /** Storeys of windows a wall of this height carries. */
    static int storeys(float heightMeters) {
        return Math.max(1, Math.round(heightMeters / 3.8f));
    }

    /** How much a plan {@code h} metres up is scaled about the view's centre. */
    static float lift(float heightMeters) {
        return EYE_METERS / (EYE_METERS - heightMeters);
    }

    /**
     * The outline of a building's roof cells: each cell side with a roof cell on one side and none on the other, as
     * {@code x0, y0, x1, y1, nx, ny} in cells, its outward normal last.
     *
     * @param cells the roof's cells, {@code x + y * gridW}
     */
    static float[] edges(int[] cells, int gridW) {
        IntOpenHashSet in = new IntOpenHashSet(cells.length * 2);
        for (int at : cells) in.add(at);
        float[] out = new float[cells.length * 4 * 6];
        int n = 0;
        for (int at : cells) {
            int x = at % gridW, y = at / gridW;
            if (x == 0 || !in.contains(at - 1)) n = put(out, n, x, y + 1, x, y, -1, 0);
            if (x == gridW - 1 || !in.contains(at + 1)) n = put(out, n, x + 1, y, x + 1, y + 1, 1, 0);
            if (!in.contains(at - gridW)) n = put(out, n, x, y, x + 1, y, 0, -1);
            if (!in.contains(at + gridW)) n = put(out, n, x + 1, y + 1, x, y + 1, 0, 1);
        }
        float[] edges = new float[n];
        System.arraycopy(out, 0, edges, 0, n);
        return edges;
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
