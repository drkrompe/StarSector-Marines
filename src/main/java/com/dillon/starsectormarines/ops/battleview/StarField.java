package com.dillon.starsectormarines.ops.battleview;

import java.util.Random;

/**
 * Where the stars behind a hull are, given how far the ship has come and where
 * the camera is looking.
 *
 * <p>Each star sits at a fixed place in one of a few depth layers. A nearer
 * layer travels further for the same movement, which is all that makes a flat
 * scatter of dots read as depth. A star leaving one edge comes back at the other,
 * so the field never runs out however long the page stays open.
 */
final class StarField {

    /** The fraction of the ship's movement each layer shows, farthest first. */
    private static final float[] DEPTHS = {0.25f, 0.55f, 1f};

    private final float[] x, y, size, brightness;
    private final int[] layer, art;

    StarField(long seed, int count, int artCount) {
        Random random = new Random(seed);
        x = new float[count];
        y = new float[count];
        size = new float[count];
        brightness = new float[count];
        layer = new int[count];
        art = new int[count];
        for (int i = 0; i < count; i++) {
            x[i] = random.nextFloat();
            y[i] = random.nextFloat();
            // Most stars are far: a sky of bright near ones reads as snow.
            float roll = random.nextFloat();
            layer[i] = roll < 0.6f ? 0 : roll < 0.88f ? 1 : 2;
            size[i] = (layer[i] + 1f) * (1f + random.nextFloat());
            brightness[i] = 0.35f + 0.65f * random.nextFloat() * DEPTHS[layer[i]] / DEPTHS[DEPTHS.length - 1];
            art[i] = random.nextInt(artCount);
        }
    }

    int count() {
        return x.length;
    }

    /** Across a field {@code width} wide, after {@code travel} along it. */
    float x(int star, float travel, float width) {
        return wrap(x[star] * width + DEPTHS[layer[star]] * travel, width);
    }

    /** @see #x */
    float y(int star, float travel, float height) {
        return wrap(y[star] * height + DEPTHS[layer[star]] * travel, height);
    }

    /** A unit size, larger for a nearer star. */
    float size(int star) {
        return size[star];
    }

    float brightness(int star) {
        return brightness[star];
    }

    int art(int star) {
        return art[star];
    }

    private static float wrap(float value, float extent) {
        float wrapped = value % extent;
        return wrapped < 0f ? wrapped + extent : wrapped;
    }
}
