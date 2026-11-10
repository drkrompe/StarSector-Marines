package com.dillon.starsectormarines.render2d;

import java.util.Arrays;

/**
 * A small, reusable buffer of solid-colored quads — the geometry payload a
 * {@code POLY} {@link DrawCommand} carries from collect to drain. Built fresh
 * each frame by a producer (e.g. a marker renderer tessellating a ring/arc into
 * a fan of trapezoids), then replayed into the shared {@link SolidQuadBatch} at
 * drain time.
 *
 * <p><strong>Why a carrier, not inline command fields.</strong> A pooled
 * {@link DrawCommand} has room for a fixed handful of floats — fine for a rect
 * or a line, but an annulus is 48 trapezoids. So {@code POLY} follows the
 * {@code RIBBON} precedent: the command holds a reference to a variable-length
 * geometry object the producer owns and rebuilds in place. Marker passes emit a
 * <em>handful</em> of these per frame (not the 38k-tile GROUND hot path), so a
 * per-frame rebuild is cheap; the producer reuses one instance across frames via
 * {@link #reset()} so steady-state allocation is zero once the buffer reaches its
 * high-water mark.
 *
 * <p>Each quad is four arbitrary screen-space corners (CW or CCW — a flat fill
 * via {@code GL_QUADS} is orientation-agnostic) plus a flat RGBA, or a colour at
 * each corner blended across it ({@link #appendShadedQuad}). Corner order is the
 * GL vertex order.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class PolyMesh {

    /** 8 position floats (4 corners) + 4 color floats per quad. */
    private static final int FLOATS_PER_QUAD = 12;

    /** Colour floats per shaded quad: 4 corners x (r, g, b). */
    private static final int CORNER_FLOATS = 12;

    private float[] data;
    /** Per quad, its corners' colours when shaded; allocated on the first shaded quad. */
    private float[] corners;
    /** Per quad, whether it is shaded corner by corner; null until the first shaded quad. */
    private boolean[] shaded;
    private int quadCount;

    public PolyMesh(int initialQuadCapacity) {
        this.data = new float[Math.max(1, initialQuadCapacity) * FLOATS_PER_QUAD];
        this.quadCount = 0;
    }

    public int quadCount() { return quadCount; }
    public boolean isEmpty() { return quadCount == 0; }

    /** Drop all queued quads, retaining the backing array. Call once per frame before rebuilding. */
    public void reset() { quadCount = 0; }

    /**
     * Queue one quad with four arbitrary corners (GL vertex order
     * {@code (0,1)→(2,3)→(4,5)→(6,7)}) and a flat RGBA.
     */
    public void appendQuad(float x0, float y0, float x1, float y1,
                           float x2, float y2, float x3, float y3,
                           float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);
        int o = quadCount * FLOATS_PER_QUAD;
        data[o++] = x0; data[o++] = y0;
        data[o++] = x1; data[o++] = y1;
        data[o++] = x2; data[o++] = y2;
        data[o++] = x3; data[o++] = y3;
        data[o++] = r; data[o++] = g; data[o++] = b; data[o] = a;
        if (shaded != null) shaded[quadCount] = false;
        quadCount++;
    }

    /**
     * Queue one quad with four arbitrary corners, as {@link #appendQuad}, coloured corner by corner and blended
     * across it: {@code rgb} holds twelve floats from {@code offset}, each corner's {@code (r, g, b)} in vertex
     * order; {@code a} stands for all four. Its flat colour, for drains that draw one colour a quad, is the corners'
     * mean.
     */
    public void appendShadedQuad(float x0, float y0, float x1, float y1,
                                 float x2, float y2, float x3, float y3,
                                 float[] rgb, int offset, float a) {
        float r = (rgb[offset] + rgb[offset + 3] + rgb[offset + 6] + rgb[offset + 9]) * .25f;
        float g = (rgb[offset + 1] + rgb[offset + 4] + rgb[offset + 7] + rgb[offset + 10]) * .25f;
        float b = (rgb[offset + 2] + rgb[offset + 5] + rgb[offset + 8] + rgb[offset + 11]) * .25f;
        appendQuad(x0, y0, x1, y1, x2, y2, x3, y3, r, g, b, a);
        int q = quadCount - 1;
        if (shaded == null) {
            shaded = new boolean[data.length / FLOATS_PER_QUAD];
            corners = new float[shaded.length * CORNER_FLOATS];
        }
        shaded[q] = true;
        System.arraycopy(rgb, offset, corners, q * CORNER_FLOATS, CORNER_FLOATS);
    }

    /** Whether quad {@code quad} is coloured corner by corner. */
    public boolean isShaded(int quad) { return shaded != null && shaded[quad]; }

    /** Corner {@code corner}'s colour channel {@code channel} (0 red, 1 green, 2 blue): its flat colour unless shaded. */
    public float cornerColour(int quad, int corner, int channel) {
        if (isShaded(quad)) return corners[quad * CORNER_FLOATS + corner * 3 + channel];
        return data[quad * FLOATS_PER_QUAD + 8 + channel];
    }

    /** Package-private vertex readback (corner 0..3, x-coord) — for same-package tests. */
    float vertX(int quad, int corner) { return data[quad * FLOATS_PER_QUAD + corner * 2]; }

    /** Package-private vertex readback (corner 0..3, y-coord) — for same-package tests. */
    float vertY(int quad, int corner) { return data[quad * FLOATS_PER_QUAD + corner * 2 + 1]; }

    /** Read-only vertex X for non-GL drains. */
    public float vertexX(int quad, int corner) { return vertX(quad, corner); }

    /** Read-only vertex Y for non-GL drains. */
    public float vertexY(int quad, int corner) { return vertY(quad, corner); }

    public float red(int quad) { return data[quad * FLOATS_PER_QUAD + 8]; }
    public float green(int quad) { return data[quad * FLOATS_PER_QUAD + 9]; }
    public float blue(int quad) { return data[quad * FLOATS_PER_QUAD + 10]; }
    public float alpha(int quad) { return data[quad * FLOATS_PER_QUAD + 11]; }

    /** Replay every queued quad into {@code batch} (the drain's shared solid-fill batch). */
    void appendTo(SolidQuadBatch batch) {
        for (int i = 0; i < quadCount; i++) {
            int o = i * FLOATS_PER_QUAD;
            batch.appendQuad(data[o], data[o + 1], data[o + 2], data[o + 3],
                    data[o + 4], data[o + 5], data[o + 6], data[o + 7],
                    data[o + 8], data[o + 9], data[o + 10], data[o + 11]);
            if (isShaded(i)) batch.setLastQuadColours(corners, i * CORNER_FLOATS);
        }
    }

    private void ensureCapacity(int neededQuads) {
        int neededFloats = neededQuads * FLOATS_PER_QUAD;
        if (data.length >= neededFloats) return;
        int newLen = data.length;
        while (newLen < neededFloats) newLen *= 2;
        float[] grown = new float[newLen];
        System.arraycopy(data, 0, grown, 0, quadCount * FLOATS_PER_QUAD);
        data = grown;
        if (shaded != null) {
            shaded = Arrays.copyOf(shaded, newLen / FLOATS_PER_QUAD);
            corners = Arrays.copyOf(corners, shaded.length * CORNER_FLOATS);
        }
    }
}
