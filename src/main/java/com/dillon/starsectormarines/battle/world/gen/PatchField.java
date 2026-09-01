package com.dillon.starsectormarines.battle.world.gen;

/**
 * A deterministic, spatially coherent value in {@code [0,1)} per cell — the
 * thing an independent per-cell {@code rng.nextFloat()} is not.
 *
 * <p>Ground drawn one cell at a time from a weighted pool comes out with the
 * authored proportions and none of the shape: 15% dirt over a meadow arrives as
 * isolated speckles rather than as a few patches of bare earth, and reads as
 * static rather than as ground. What is missing is correlation between
 * neighbours, and that is all this supplies.
 *
 * <p><b>The marginal distribution is uniform, exactly, and that is the whole
 * design constraint.</b> Substituted for the roll, this field decides the mix
 * as well as its arrangement, so a field that is merely smooth changes what the
 * ground is made of while claiming to change only where. Interpolating between
 * hashed lattice corners — the obvious construction — is smooth and is not
 * uniform: averaging independent values concentrates them toward the middle and
 * starves the ends of the range, which are exactly where a pool's rare grounds
 * live. Measured, it took a 5%-weighted sand down to 0.9% while every number
 * about patchiness looked right.
 *
 * <p>So the value is never averaged. One point is scattered per lattice square
 * at a hashed offset, each carrying one hashed value, and a cell takes the
 * value of the nearest point. Assignment depends on position alone and never on
 * the value, so every cell's value is one untouched uniform draw; what makes
 * neighbours agree is that they usually resolve to the same point. Patches come
 * out as the cells of a jittered Voronoi diagram — irregular, unlike the square
 * blocks a lattice alone would give.
 *
 * <p>Sampling is a pure function of (seed, x, y): it consumes no
 * {@link java.util.Random}, so two fillers reading the same field agree without
 * being ordered relative to each other, and a region samples the same whichever
 * cell is visited first.
 */
public final class PatchField {

    /** Lattice pitch in cells; one scattered point per square of this size. */
    private final float featureCells;
    private final long seed;

    /**
     * @param seed         keys the whole field; two fields with the same seed
     *                     and feature size are the same field
     * @param featureCells lattice pitch in cells, and roughly the width of a
     *                     patch. Must be positive.
     */
    public PatchField(long seed, float featureCells) {
        if (featureCells <= 0) {
            throw new IllegalArgumentException("feature size must be positive: " + featureCells);
        }
        this.seed = seed;
        this.featureCells = featureCells;
    }

    /**
     * The value of the scattered point nearest this cell — one uniform draw in
     * {@code [0,1)}, shared with the cell's neighbours in the same patch.
     */
    public float sample(int x, int y) {
        float fx = x / featureCells;
        float fy = y / featureCells;
        int cx = (int) Math.floor(fx);
        int cy = (int) Math.floor(fy);
        float bestDistSq = Float.MAX_VALUE;
        int bestX = cx;
        int bestY = cy;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int lx = cx + dx;
                int ly = cy + dy;
                // Jitter is drawn from a different mix of the same coordinates
                // than the value is, so a patch's position says nothing about
                // what is in it.
                float px = lx + hash(lx, ly, 0x51_7C_C1_B7L);
                float py = ly + hash(lx, ly, 0x27_22_0A_95L);
                float ddx = fx - px;
                float ddy = fy - py;
                float distSq = ddx * ddx + ddy * ddy;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestX = lx;
                    bestY = ly;
                }
            }
        }
        return hash(bestX, bestY, 0x9E_37_79_B9L);
    }

    /**
     * One uniform value in {@code [0,1)} for a lattice square and a purpose.
     * Mixing is splitmix64's finalizer — cheap, and it avalanches, which the
     * raw multiply-add of two small coordinates does not.
     */
    private float hash(int lx, int ly, long salt) {
        long h = seed + salt
                + lx * 0x9E3779B97F4A7C15L
                + ly * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53f;
    }
}
