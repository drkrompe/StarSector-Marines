package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.elevation.ElevationLayout;

import java.util.stream.IntStream;

/**
 * Where raised ground's rock shows, at {@link #TEXELS_PER_CELL} texels a cell:
 * {@link CliffShading#faceCover} baked once per layout, one byte a texel, zero
 * away from every face and ramp. The creep reads it to leave the faces it
 * would otherwise paint over bare ({@link CreepLayer}), so a ridge, a brook's
 * bank or a ramp's cut still reads inside a bloom.
 */
final class CliffFaceMask {

    /** Texels per cell; finer than the field, because a face is a fraction of a cell to two cells wide. */
    static final int TEXELS_PER_CELL = 4;

    private final int width, height;
    private final byte[] texels;

    private CliffFaceMask(int width, int height, byte[] texels) {
        this.width = width;
        this.height = height;
        this.texels = texels;
    }

    static CliffFaceMask of(ElevationLayout layout) {
        int cellsW = layout.width(), cellsH = layout.height();
        int w = cellsW * TEXELS_PER_CELL, h = cellsH * TEXELS_PER_CELL;
        boolean[] near = CliffShading.reach(layout, 1);
        CliffShading shading = new CliffShading(layout, new SunLight(), TEXELS_PER_CELL);
        byte[] out = new byte[w * h];
        IntStream.range(0, h).parallel().forEach(ty -> {
            int cellY = ty / TEXELS_PER_CELL;
            float wy = (ty + .5f) / TEXELS_PER_CELL;
            float[] scratch = new float[4];
            for (int tx = 0; tx < w; tx++) {
                if (!near[cellY * cellsW + tx / TEXELS_PER_CELL]) continue;
                float f = shading.faceCover((tx + .5f) / TEXELS_PER_CELL, wy, scratch);
                out[ty * w + tx] = (byte) Math.round(Math.max(0f, Math.min(1f, f)) * 255f);
            }
        });
        return new CliffFaceMask(w, h, out);
    }

    int width() { return width; }
    int height() { return height; }
    byte[] texels() { return texels; }

    /** Face cover at a point (cells), bilinear between texel centres as GL's linear filter reads it; 0..1. */
    float sample(float cellX, float cellY) {
        float u = cellX * TEXELS_PER_CELL - .5f, v = cellY * TEXELS_PER_CELL - .5f;
        int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
        float fx = u - x0, fy = v - y0;
        float a = at(x0, y0), b = at(x0 + 1, y0), c = at(x0, y0 + 1), d = at(x0 + 1, y0 + 1);
        return ((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy) / 255f;
    }

    private int at(int x, int y) {
        x = x < 0 ? 0 : Math.min(width - 1, x);
        y = y < 0 ? 0 : Math.min(height - 1, y);
        return texels[y * width + x] & 0xFF;
    }
}
