package com.dillon.starsectormarines.render2d;

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
 * via {@code GL_QUADS} is orientation-agnostic) plus a flat RGBA. Corner order
 * is the GL vertex order.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class PolyMesh {

    /** 8 position floats (4 corners) + 4 color floats per quad. */
    private static final int FLOATS_PER_QUAD = 12;

    private float[] data;
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
        quadCount++;
    }

    /** Package-private vertex readback (corner 0..3, x-coord) — for same-package tests. */
    float vertX(int quad, int corner) { return data[quad * FLOATS_PER_QUAD + corner * 2]; }

    /** Package-private vertex readback (corner 0..3, y-coord) — for same-package tests. */
    float vertY(int quad, int corner) { return data[quad * FLOATS_PER_QUAD + corner * 2 + 1]; }

    /** Replay every queued quad into {@code batch} (the drain's shared solid-fill batch). */
    void appendTo(SolidQuadBatch batch) {
        for (int i = 0; i < quadCount; i++) {
            int o = i * FLOATS_PER_QUAD;
            batch.appendQuad(data[o], data[o + 1], data[o + 2], data[o + 3],
                    data[o + 4], data[o + 5], data[o + 6], data[o + 7],
                    data[o + 8], data[o + 9], data[o + 10], data[o + 11]);
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
    }
}
