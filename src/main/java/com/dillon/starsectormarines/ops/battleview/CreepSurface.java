package com.dillon.starsectormarines.ops.battleview;

/**
 * The creep's surface, made once in code and repeating every {@link #CELLS}
 * map cells: the GL pass uploads it (mipmapped) and {@link CreepShading}
 * samples the same bytes through the same mip chain, so the game and a review
 * frame read one picture.
 *
 * <ul>
 *   <li><b>R</b> plate height: chitin scales, their cell lattice warped so no
 *       seam runs straight, each one rounded down into its seam and lifted
 *       along a slowly turning grain so neighbours overlap like grown scales
 *       rather than sitting like cobbles;</li>
 *   <li><b>G</b> plate tone, one value per scale;</li>
 *   <li><b>B</b> the vein network: thin ridged lines about one to two cells
 *       apart, which is what reads at play zoom without turning to noise;</li>
 *   <li><b>A</b> blisters: the rolled-up beads along the creep's lip.</li>
 * </ul>
 *
 * Every lattice is periodic in the square, so the texture tiles without a seam.
 */
final class CreepSurface {

    /** Texels per side. */
    static final int SIZE = 384;
    /** Map cells one repeat covers: 64 texels a cell. */
    static final float CELLS = 6f;
    /** Plates across one repeat (two a cell), blisters across one repeat. */
    static final int PLATES = 12, BLISTERS = 16;

    private static final long SEED = 0x5CA1_E5EEL;
    private static byte[][] mips;

    private CreepSurface() { }

    /** Level-0 RGBA bytes, row-major; shared and never written after they are made. */
    static synchronized byte[] rgba() {
        load();
        return mips[0];
    }

    /**
     * Bilinear, repeating sample at a map-cell position from the mip level whose texels best match
     * {@code texelsPerPixel}, as GL's mipmapped filter picks one; channels in 0..1 into {@code out[0..3]}.
     */
    static void sample(float cellX, float cellY, float texelsPerPixel, float[] out) {
        byte[][] chain;
        synchronized (CreepSurface.class) {
            load();
            chain = mips;
        }
        int level = 0;
        float t = texelsPerPixel;
        while (t > 1.5f && level < chain.length - 1) {
            t *= 0.5f;
            level++;
        }
        int s = Math.max(1, SIZE >> level);
        byte[] m = chain[level];
        float u = cellX / CELLS * s - 0.5f, v = cellY / CELLS * s - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int ax = Math.floorMod(x0, s), bx = Math.floorMod(x0 + 1, s);
        int ay = Math.floorMod(y0, s), by = Math.floorMod(y0 + 1, s);
        for (int c = 0; c < 4; c++) {
            float a = m[(ay * s + ax) * 4 + c] & 0xFF, b = m[(ay * s + bx) * 4 + c] & 0xFF;
            float d = m[(by * s + ax) * 4 + c] & 0xFF, e = m[(by * s + bx) * 4 + c] & 0xFF;
            out[c] = ((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy) / 255f;
        }
    }

    private static void load() {
        if (mips != null) return;
        byte[] level0 = build();
        int levels = 1;
        for (int s = SIZE; s > 1; s >>= 1) levels++;
        byte[][] chain = new byte[levels][];
        chain[0] = level0;
        for (int l = 1, s = SIZE >> 1; l < levels; l++, s >>= 1) {
            byte[] prev = chain[l - 1], next = new byte[s * s * 4];
            int ps = s * 2;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    for (int c = 0; c < 4; c++) {
                        int sum = (prev[((2 * y) * ps + 2 * x) * 4 + c] & 0xFF) + (prev[((2 * y) * ps + 2 * x + 1) * 4 + c] & 0xFF)
                                + (prev[((2 * y + 1) * ps + 2 * x) * 4 + c] & 0xFF) + (prev[((2 * y + 1) * ps + 2 * x + 1) * 4 + c] & 0xFF);
                        next[(y * s + x) * 4 + c] = (byte) ((sum + 2) / 4);
                    }
                }
            }
            chain[l] = next;
        }
        mips = chain;
    }

    private static byte[] build() {
        byte[] out = new byte[SIZE * SIZE * 4];
        double[] cell = new double[6];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = (x + 0.5) / SIZE, v = (y + 0.5) / SIZE;
                // Warp in repeat units: about half a cell, varying over a cell and a half.
                double wx = (periodic(u, v, 4, 1) - 0.5) * 0.09 + (periodic(u, v, 9, 2) - 0.5) * 0.035;
                double wy = (periodic(u, v, 4, 3) - 0.5) * 0.09 + (periodic(u, v, 9, 4) - 0.5) * 0.035;
                worley(u + wx, v + wy, PLATES, 5, cell);
                double edge = cell[1] - cell[0];
                double pillow = smooth(0.0, 0.22, edge);
                // The grain the scales overlap along: a direction that turns slowly across the repeat.
                double grain = periodic(u, v, 3, 6) * Math.PI * 2;
                double along = (Math.cos(grain) * cell[2] + Math.sin(grain) * cell[3]) / 0.6;
                double lift = 0.5 + 0.5 * Math.max(-1, Math.min(1, along));
                double plate = pillow * lift;
                double tone = hash((int) cell[4], (int) cell[5], 7);
                double ridgeA = 1 - Math.abs(2 * fbm(u, v, 3, 8) - 1);
                double ridgeB = 1 - Math.abs(2 * fbm(u, v, 5, 9) - 1);
                double vein = Math.max(smooth(0.955, 0.988, ridgeA), 0.8 * smooth(0.965, 0.99, ridgeB));
                worley(u, v, BLISTERS, 10, cell);
                double blister = smooth(0.62, 0.05, cell[0]) * (0.75 + 0.5 * hash((int) cell[4], (int) cell[5], 11));
                int i = (y * SIZE + x) * 4;
                out[i] = (byte) quantise(plate);
                out[i + 1] = (byte) quantise(tone);
                out[i + 2] = (byte) quantise(vein);
                out[i + 3] = (byte) quantise(blister * 0.8);
            }
        }
        return out;
    }

    /**
     * Jittered-grid cellular noise over a square of {@code period} cells: {@code out[0]} the nearest site's
     * distance and {@code out[1]} the second's (in lattice cells), {@code out[2..3]} the offset to the nearest site, {@code out[4..5]} its wrapped lattice cell.
     */
    private static void worley(double u, double v, int period, int salt, double[] out) {
        double px = u * period, py = v * period;
        int gx = (int) Math.floor(px), gy = (int) Math.floor(py);
        double f1 = 9, f2 = 9, ox = 0, oy = 0;
        int sxId = 0, syId = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = gx + dx, cy = gy + dy;
                int wx = Math.floorMod(cx, period), wy = Math.floorMod(cy, period);
                double sx = cx + 0.1 + 0.8 * hash(wx, wy, salt), sy = cy + 0.1 + 0.8 * hash(wx, wy, salt + 100);
                double ddx = sx - px, ddy = sy - py;
                double d = Math.sqrt(ddx * ddx + ddy * ddy);
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                    ox = ddx;
                    oy = ddy;
                    sxId = wx;
                    syId = wy;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
        out[0] = f1;
        out[1] = f2;
        out[2] = ox;
        out[3] = oy;
        out[4] = sxId;
        out[5] = syId;
    }

    private static double fbm(double u, double v, int period, int salt) {
        return 0.55 * periodic(u, v, period, salt) + 0.3 * periodic(u, v, period * 2, salt + 1)
                + 0.15 * periodic(u, v, period * 4, salt + 2);
    }

    /** Smooth value noise with {@code period} lattice cells across the square; {@code u, v} in repeats. */
    private static double periodic(double u, double v, int period, int salt) {
        double pu = u * period, pv = v * period;
        int gx = (int) Math.floor(pu), gy = (int) Math.floor(pv);
        double fx = pu - gx, fy = pv - gy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int x0 = Math.floorMod(gx, period), x1 = Math.floorMod(gx + 1, period);
        int y0 = Math.floorMod(gy, period), y1 = Math.floorMod(gy + 1, period);
        double a = hash(x0, y0, salt), b = hash(x1, y0, salt), c = hash(x0, y1, salt), d = hash(x1, y1, salt);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static double hash(int x, int y, int salt) {
        long z = SEED ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static int quantise(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
