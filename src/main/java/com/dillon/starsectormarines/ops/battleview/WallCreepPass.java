package com.dillon.starsectormarines.ops.battleview;

import java.util.Arrays;

/**
 * This frame's creep on stood-up walls ({@link CreepClimb}), queued as one custom command over the walls it lies on:
 * quads on screen, each corner carrying the map cell the creep is read at, drawn through the ground creep's own
 * program ({@link CreepShader}) with the layer's field, so a wall's creep is the ground's, pulse and all. The offline
 * drain shades the same corners through {@link CreepShading}. A quad's tint darkens a face turned from the light, its
 * alpha fades a wall cut away.
 */
final class WallCreepPass implements Runnable {

    /** Floats a quad: four corners of screen x, y and cell x, y, then its tint r, g, b, a. */
    static final int STRIDE = 20;

    private final CreepLayer layer;
    /** The ground pass's clock, palette, sun and scale this frame, for the standing walls. */
    final CreepPass params;
    private float[] quads = new float[STRIDE * 64];
    private int count;

    WallCreepPass(CreepLayer layer) {
        this.layer = layer;
        this.params = new CreepPass(layer);
        this.params.standing = true;
    }

    CreepLayer layer() { return layer; }

    void reset() { count = 0; }

    boolean isEmpty() { return count == 0; }

    int count() { return count; }

    /** The floats of quad {@code i}, at {@code i * STRIDE}. */
    float[] quads() { return quads; }

    /**
     * One quad: screen corners {@code s} and the cells they read {@code c}, both eight floats in the same corner order,
     * at tint {@code r, g, b} and alpha {@code a}.
     */
    void add(float[] s, float[] c, float r, float g, float b, float a) {
        if (a <= .004f) return;
        if (quads.length < (count + 1) * STRIDE) quads = Arrays.copyOf(quads, quads.length * 2);
        int o = count++ * STRIDE;
        for (int k = 0; k < 4; k++) {
            quads[o + k * 4] = s[k * 2];
            quads[o + k * 4 + 1] = s[k * 2 + 1];
            quads[o + k * 4 + 2] = c[k * 2];
            quads[o + k * 4 + 3] = c[k * 2 + 1];
        }
        quads[o + 16] = r; quads[o + 17] = g; quads[o + 18] = b; quads[o + 19] = a;
    }

    @Override
    public void run() {
        layer.drawWalls(this);
    }
}
