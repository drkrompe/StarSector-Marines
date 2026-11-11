package com.dillon.starsectormarines.ops.battleview;

/**
 * Where hive creep is read on a stood-up wall ({@code voids.md}, buildings in depth; {@code colony-breach-nouns.md},
 * Creep). The creep on a wall is the ground creep's own, drawn by its program from its field ({@link WallCreepPass}):
 * the creep claims the wall cells beside the ground it holds and fills them slowly, and on a wall cell the field
 * already grows veins across it and then a crust in from the side the creep came from. A wall's top reads its own
 * cells, so the crust over the top is the ground's crust at the ground's scale; a face reads a thin band in from the
 * wall's outer side as it rises ({@link #FACE_DEPTH}), so the crust fills a face up from its foot as it first takes the
 * outer edge, and the face is overgrown before the crust has come far over the top. The band runs up the face
 * stretched, so the crust's lobes and plates hang down it in tongues. GL-free.
 */
final class CreepClimb {

    /** How far in from a wall's outer side a face's top reads, cells: a face's height is this much of the wall. UNTUNED. */
    static final float FACE_DEPTH = .3f;
    /**
     * How far in from a wall's outer side its top reads, cells: the wall cell and no further. Past it lies the floor,
     * whose creep is on the ground inside, not at the wall's height.
     */
    static final float TOP_DEPTH = 1f;
    /** In from the outer side a face's foot reads, cells: just inside the wall cell, never the ground beside it. */
    static final float SKIN = .03f;

    private CreepClimb() { }

    /**
     * The cells the part of a face {@code t0..t1} along and {@code z0..z1} up (shares of its height) reads, into
     * {@code out} as four corners: foot at {@code t0}, foot at {@code t1}, top at {@code t1}, top at {@code t0}. The
     * face stands on the wall's outer side from {@code (ax, ay)} to {@code (bx, by)}, cells, looking out along
     * {@code (nx, ny)}.
     */
    static void faceCells(float ax, float ay, float bx, float by, float nx, float ny, float t0, float t1, float z0,
                          float z1, float[] out) {
        float d0 = SKIN + FACE_DEPTH * z0, d1 = SKIN + FACE_DEPTH * z1;
        float x0 = ax + (bx - ax) * t0, y0 = ay + (by - ay) * t0, x1 = ax + (bx - ax) * t1, y1 = ay + (by - ay) * t1;
        out[0] = x0 - nx * d0; out[1] = y0 - ny * d0;
        out[2] = x1 - nx * d0; out[3] = y1 - ny * d0;
        out[4] = x1 - nx * d1; out[5] = y1 - ny * d1;
        out[6] = x0 - nx * d1; out[7] = y0 - ny * d1;
    }

    /**
     * A wall's top in from its outer side ({@code a} to {@code b}, outward {@code n}) by {@code depth} cells, into
     * {@code out} as four corners: the outer side's two, then the inner's back. The top reads the cells it lies over.
     */
    static void topCells(float ax, float ay, float bx, float by, float nx, float ny, float depth, float[] out) {
        out[0] = ax; out[1] = ay;
        out[2] = bx; out[3] = by;
        out[4] = bx - nx * depth; out[5] = by - ny * depth;
        out[6] = ax - nx * depth; out[7] = ay - ny * depth;
    }
}
