package com.dillon.starsectormarines.battle.warship;

/**
 * Where a multi-warhead missile's warheads fall: an even, slightly ragged spread
 * over a disc, so a sheaf of submunitions covers ground rather than clumping on
 * the aim point, and no two volleys are laid out alike.
 *
 * <p>A golden-angle spiral gives the even cover; each point is then jittered in
 * radius and angle, and the whole pattern is rotated, all from a stateless hash of
 * the round's seed so the layout is a pure function of it.
 */
public final class WarheadScatter {

    /** The spread's radius as a multiple of one warhead's blast radius times the root of their number. */
    public static final float SPREAD_FACTOR = 1.4f;

    private static final float GOLDEN_ANGLE = 2.3999632f;
    private static final int STREAM = 200;

    private WarheadScatter() {}

    /** The radius, in cells, of the ground {@code count} warheads of blast radius {@code blastCells} are spread over. */
    public static float spreadRadius(int count, float blastCells) {
        return SPREAD_FACTOR * blastCells * (float) Math.sqrt(Math.max(1, count));
    }

    /**
     * {@code count} points within {@code radius} of {@code (cx, cy)}, as {@code [x0, y0, x1, y1, ...]}.
     * The same seed always gives the same points.
     */
    public static float[] points(long seed, int count, float cx, float cy, float radius) {
        float[] out = new float[count * 2];
        float rotation = MissilePath.unit(seed, STREAM) * 2f * (float) Math.PI;
        for (int i = 0; i < count; i++) {
            float r = radius * (float) Math.sqrt((i + 0.5f) / count);
            r = Math.min(radius, Math.max(0f, r + (MissilePath.unit(seed, STREAM + 1 + 2 * i) - 0.5f) * radius / count));
            float a = rotation + i * GOLDEN_ANGLE + (MissilePath.unit(seed, STREAM + 2 + 2 * i) - 0.5f) * 0.6f;
            out[2 * i] = cx + (float) Math.cos(a) * r;
            out[2 * i + 1] = cy + (float) Math.sin(a) * r;
        }
        return out;
    }
}
