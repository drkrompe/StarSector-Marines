package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.WallScars;

/**
 * A wall cell's kept hits ({@link WallScars}) laid on one of its faces: only the hits that landed on that face's side
 * of the cell, each where it struck along the face and as high as it struck. A round fired along the ground carries
 * no height, so it strikes at a marine's chest or a little above, by its point; a burst lower. GL-free.
 */
final class FaceScars {

    /** Floats per laid hit: its middle along the face (0 at the face's start, 1 at its end), metres up, size, mark. */
    static final int STRIDE = 4;

    private FaceScars() { }

    /**
     * Lays the hits {@code scars} keeps for {@code cell} on its face from {@code (x0, y0)} to {@code (x1, y1)} turned
     * to {@code (nx, ny)}, into {@code out}; returns how many. A hit is on this face where it struck on the face's
     * side of the cell's centre.
     */
    static int lay(WallScars scars, int cell, int gridW, float x0, float y0, float x1, float y1, float nx, float ny,
                   float[] out) {
        if (scars == null) return 0;
        float[] kept = scars.of(cell);
        int count = scars.count(cell);
        if (kept == null || count == 0) return 0;
        float cx = cell % gridW + .5f, cy = cell / gridW + .5f;
        float ex = x1 - x0, ey = y1 - y0, len2 = ex * ex + ey * ey;
        if (len2 < 1e-6f) return 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int o = i * WallScars.STRIDE;
            float px = kept[o], py = kept[o + 1];
            if ((px - cx) * nx + (py - cy) * ny < -.1f) continue;
            float t = ((px - x0) * ex + (py - y0) * ey) / len2;
            float size = kept[o + 3];
            int mark = (int) kept[o + 4];
            float z = kept[o + 2];
            if (z <= 0f) {
                float h = BuildingFaces.hash(Float.floatToIntBits(px) * 31 + Float.floatToIntBits(py), 90);
                z = mark == WallScars.Mark.BLAST.ordinal() ? .6f + .9f * h : .7f + 2.3f * h;
            }
            out[n * STRIDE] = Math.max(.04f, Math.min(.96f, t));
            out[n * STRIDE + 1] = z;
            out[n * STRIDE + 2] = size;
            out[n * STRIDE + 3] = mark;
            n++;
        }
        return n;
    }
}
