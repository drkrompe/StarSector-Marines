package com.dillon.starsectormarines.ops.battleview;

/**
 * The tileable noise the creep is drawn from, made once in code: the GL pass
 * uploads it as a texture and {@link CreepShading} samples the same bytes, so
 * the shader and its CPU model read one picture.
 *
 * <p>Four channels over a square that repeats every {@link #CELLS} map cells:
 * R a soft multi-octave cloud (the ragged edge and the dead tissue's mottling),
 * G a ridged network whose crests are the veins, B coarse blobs (fleshier
 * mounds against thinner film), A fine grain (wet specks). Every lattice is
 * periodic in the square, so the texture tiles without a seam.
 */
final class CreepNoise {

    /** Texels per side. */
    static final int SIZE = 128;
    /** Map cells one repeat of the texture covers. */
    static final float CELLS = 10f;

    private static final long SEED = 0xB100_3C4EL;
    private static byte[] rgba;

    private CreepNoise() { }

    /** RGBA bytes, row-major from texel row 0; shared and never written after it is made. */
    static synchronized byte[] rgba() {
        if (rgba == null) rgba = build();
        return rgba;
    }

    /** Bilinear, repeating sample at a map-cell position; channels in 0..1 into {@code out[0..3]}. */
    static void sample(float cellX, float cellY, float[] out) {
        byte[] t = rgba();
        float u = cellX / CELLS * SIZE - 0.5f, v = cellY / CELLS * SIZE - 0.5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        int ax = Math.floorMod(x0, SIZE), bx = Math.floorMod(x0 + 1, SIZE);
        int ay = Math.floorMod(y0, SIZE), by = Math.floorMod(y0 + 1, SIZE);
        for (int c = 0; c < 4; c++) {
            float a = t[(ay * SIZE + ax) * 4 + c] & 0xFF, b = t[(ay * SIZE + bx) * 4 + c] & 0xFF;
            float d = t[(by * SIZE + ax) * 4 + c] & 0xFF, e = t[(by * SIZE + bx) * 4 + c] & 0xFF;
            out[c] = ((a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy) / 255f;
        }
    }

    private static byte[] build() {
        byte[] out = new byte[SIZE * SIZE * 4];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double cloud = 0.5 * periodic(x, y, 4, 1) + 0.3 * periodic(x, y, 8, 2) + 0.2 * periodic(x, y, 16, 3);
                double ridgeA = 1 - Math.abs(2 * periodic(x, y, 6, 4) - 1);
                double ridgeB = 1 - Math.abs(2 * periodic(x, y, 13, 5) - 1);
                double veins = Math.max(ridgeA, 0.92 * ridgeB);
                double blobs = periodic(x, y, 5, 6);
                double grain = hash(x, y, 7);
                int i = (y * SIZE + x) * 4;
                out[i] = (byte) quantise(cloud);
                out[i + 1] = (byte) quantise(veins);
                out[i + 2] = (byte) quantise(blobs);
                out[i + 3] = (byte) quantise(grain);
            }
        }
        return out;
    }

    /** Smooth value noise with {@code period} lattice cells across the square. */
    private static double periodic(int x, int y, int period, int salt) {
        double u = x * (double) period / SIZE, v = y * (double) period / SIZE;
        int gx = (int) Math.floor(u), gy = (int) Math.floor(v);
        double fx = u - gx, fy = v - gy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(gx % period, gy % period, salt), b = hash((gx + 1) % period, gy % period, salt);
        double c = hash(gx % period, (gy + 1) % period, salt), d = hash((gx + 1) % period, (gy + 1) % period, salt);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static double hash(int x, int y, int salt) {
        long z = SEED ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    private static int quantise(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
