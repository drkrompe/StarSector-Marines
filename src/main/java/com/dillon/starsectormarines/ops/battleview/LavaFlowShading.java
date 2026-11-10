package com.dillon.starsectormarines.ops.battleview;

/**
 * A volcanic world's older flows ({@code voids.md}, Lava flows), shaded: the CPU model of {@link LavaFlowShader},
 * line for line, which the offline drain draws review frames through.
 *
 * <p>A flow's root runs on into the melt it broke from ({@link #rooted}), so its margin meets the river's lip as the
 * flow's own curve, not along the line where the flow cells stop; the crust fades out across the melt's edge (the
 * field's third channel) and the river's lip lies over the join.
 *
 * <p>A flow's margin is where its smoothed cover ({@link LavaFlowLayer}: each cell's share of flow under a binomial
 * blur, sampled linearly between cell centres) crosses {@link #EDGE}, pushed out and in by two scales of
 * {@link CreepNoise}: broad lobes and a fine fray. So the edge runs as a curve through the cells, never along their
 * sides. Inside it the lava rift's crust image lies dark and still, {@link #TILE_CELLS} cells to a repeat; where the
 * flow is still hot the same image is added again in the melt's light, breathing.
 */
final class LavaFlowShading {

    private LavaFlowShading() { }

    /** Cells one repeat of the crust image spans. */
    static final float TILE_CELLS = 10f;
    /** The cooled crust's tint and cover. */
    static final float[] TINT = {.30f, .26f, .25f};
    static final float ALPHA = .94f;
    /** How strongly a hot flow's cracks still glow, and the light they glow in. */
    static final float GLOW = .85f;
    static final float[] GLOW_TINT = {LavaVoidLook.GLOW_R, LavaVoidLook.GLOW_G * .7f, LavaVoidLook.GLOW_B * .5f};
    /** The cover the margin stands at, and half the width it fades in over. */
    static final float EDGE = .5f, FEATHER = .04f;
    /** How far the noise pushes the margin: the broad lobes and the fine fray. */
    static final float LOBE = .32f, FRAY = .07f;
    /** The noise's scale for each, in repeats of {@link CreepNoise#CELLS} per cell. */
    static final float LOBE_SCALE = 1.1f, FRAY_SCALE = 3.3f;
    /** How deep and how fast the glow breathes. */
    static final float BREATH = .22f, BREATH_RATE = .7f;

    /** The smoothed flow: channel 0 the cover, 1 the heat, 2 the melt (a drop's cells), as bytes. */
    interface Field {
        int width();
        int height();
        int texel(int x, int y, int c);
    }

    /** Working space, so a frame shades without allocating. */
    static final class Scratch {
        final float[] lobe = new float[4], fray = new float[4];
    }

    /**
     * The flow at the map-cell point {@code (cellX, cellY)}: {@code out[0]} the crust's cover, {@code out[1]} the
     * glow's strength, {@code out[2..3]} where in one repeat of the crust image it samples, 0 to 1. False where no
     * flow is near.
     */
    static boolean shade(Field field, float cellX, float cellY, float time, Scratch s, float[] out) {
        float cover = sample(field, cellX, cellY, 0);
        float onMelt = smoothstep(MELT_FADE_FROM, MELT_FADE_TO, sample(field, cellX, cellY, 2));
        if (cover <= .01f || onMelt >= 1f) return false;
        CreepNoise.sample(cellX * LOBE_SCALE, cellY * LOBE_SCALE, s.lobe);
        CreepNoise.sample(cellX * FRAY_SCALE + 3.7f, cellY * FRAY_SCALE + 1.3f, s.fray);
        float m = cover + (s.lobe[0] - .5f) * 2f * LOBE + (s.fray[1] - .5f) * 2f * FRAY;
        float k = smoothstep(EDGE - FEATHER, EDGE + FEATHER, m) * (1f - onMelt);
        if (k <= 0f) return false;
        float heat = sample(field, cellX, cellY, 1);
        float breath = 1f - BREATH * .5f * (1f + (float) Math.sin(time * BREATH_RATE * 6.2832f + s.lobe[2] * 6.2832f));
        out[0] = k;
        out[1] = GLOW * heat * heat * breath * k * smoothstep(EDGE, .9f, m);
        out[2] = fract(cellX / TILE_CELLS);
        out[3] = fract(cellY / TILE_CELLS);
        return true;
    }

    /** A linear, edge-clamped sample of channel {@code c} at a map-cell point, as the GL texture unit reads it. */
    static float sample(Field f, float cellX, float cellY, int c) {
        float gx = cellX - .5f, gy = cellY - .5f;
        int x0 = (int) Math.floor(gx), y0 = (int) Math.floor(gy);
        float tx = gx - x0, ty = gy - y0;
        float a = at(f, x0, y0, c), b = at(f, x0 + 1, y0, c), d = at(f, x0, y0 + 1, c), e = at(f, x0 + 1, y0 + 1, c);
        return ((a + (b - a) * tx) * (1f - ty) + (d + (e - d) * tx) * ty) / 255f;
    }

    private static int at(Field f, int x, int y, int c) {
        return f.texel(Math.max(0, Math.min(f.width() - 1, x)), Math.max(0, Math.min(f.height() - 1, y)), c);
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    private static float fract(float v) { return v - (float) Math.floor(v); }

    /** Where the crust fades out over the melt: the share of melt about a point it starts and ends at. */
    static final float MELT_FADE_FROM = .2f, MELT_FADE_TO = .6f;
    /** How far into the melt a flow's root runs on, in cells, for its margin's sake. */
    static final int ROOT_REACH = 2;

    /**
     * The flows ({@code heat} above zero) with the melt ({@code melt}) within {@link #ROOT_REACH} of any of them
     * counted as flow too: what the margin is drawn from, so a flow's root runs on into the river it broke from.
     */
    static float[] rooted(float[] heat, boolean[] melt, int w, int h) {
        float[] out = heat.clone();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (!melt[i] || out[i] > 0f) continue;
            boolean near = false;
            for (int dy = -ROOT_REACH; dy <= ROOT_REACH && !near; dy++) {
                for (int dx = -ROOT_REACH; dx <= ROOT_REACH && !near; dx++) {
                    int nx = x + dx, ny = y + dy;
                    near = nx >= 0 && ny >= 0 && nx < w && ny < h && heat[ny * w + nx] > 0f;
                }
            }
            if (near) out[i] = Float.MIN_VALUE;
        }
        return out;
    }

    /**
     * Each cell's share of flow ({@code heat} above zero) under a five-wide binomial blur, as a byte: what the
     * margin is drawn from. Zero beyond two cells of any flow.
     */
    static byte[] cover(float[] heat, int w, int h) {
        float[] row = new float[w * h];
        int[] k = {1, 4, 6, 4, 1};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float sum = 0f;
            for (int d = -2; d <= 2; d++) {
                int nx = x + d;
                if (nx >= 0 && nx < w && heat[y * w + nx] > 0f) sum += k[d + 2];
            }
            row[y * w + x] = sum / 16f;
        }
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float sum = 0f;
            for (int d = -2; d <= 2; d++) {
                int ny = y + d;
                if (ny >= 0 && ny < h) sum += k[d + 2] * row[ny * w + x];
            }
            out[y * w + x] = (byte) Math.round(Math.min(1f, sum / 16f) * 255f);
        }
        return out;
    }
}
