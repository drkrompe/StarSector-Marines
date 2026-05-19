package com.dillon.starsectormarines.render2d;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Untextured-quad counterpart to {@link QuadBatch}. Emits solid-colored
 * quads with no texture binding — for in-loop solid fills (interior-wall
 * fallback, crosswalk stripes) that need to share painter ordering with
 * the textured passes around them.
 *
 * <p>Without this batch, mixing immediate-mode {@code fillRect} calls
 * with deferred textured batches would draw the solid fills BEFORE the
 * later textured flush, breaking layer order (e.g. crosswalk stripes
 * would disappear behind the road tile).
 *
 * <p>Designed to interleave with {@link QuadBatch} under one
 * {@link GlStateBracket#textured2D()} bracket — flush() locally
 * disables {@code GL_TEXTURE_2D}; the next {@link QuadBatch#flush()}
 * re-enables it. Bracket pop restores prior state.
 *
 * <p>NOT thread-safe. Plain {@code float[]} backing.
 */
public final class SolidQuadBatch {

    /** 4 verts per quad, 6 floats per vert: (x, y, r, g, b, a). */
    private static final int FLOATS_PER_QUAD = 4 * 6;

    private float[] data;
    private int quadCount;

    public SolidQuadBatch(int initialQuadCapacity) {
        this.data = new float[Math.max(1, initialQuadCapacity) * FLOATS_PER_QUAD];
        this.quadCount = 0;
    }

    public int quadCount() { return quadCount; }
    public boolean isEmpty() { return quadCount == 0; }

    /**
     * Queue one solid-color rect. Corners are screen-space, axis-aligned;
     * pass any two opposing corners (the call site decides which axis is
     * inverted).
     */
    public void appendRect(float x0, float y0, float x1, float y1,
                           float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);
        int o = quadCount * FLOATS_PER_QUAD;
        data[o++] = x0; data[o++] = y0; data[o++] = r; data[o++] = g; data[o++] = b; data[o++] = a;
        data[o++] = x1; data[o++] = y0; data[o++] = r; data[o++] = g; data[o++] = b; data[o++] = a;
        data[o++] = x1; data[o++] = y1; data[o++] = r; data[o++] = g; data[o++] = b; data[o++] = a;
        data[o++] = x0; data[o++] = y1; data[o++] = r; data[o++] = g; data[o++] = b; data[o]   = a;
        quadCount++;
    }

    /**
     * Emit all queued quads as one {@code glBegin(GL_QUADS)} block.
     * No-op if empty. Resets the queue. Disables {@code GL_TEXTURE_2D}
     * before drawing so a stale texture binding doesn't bleed through
     * — paired flushes with {@link QuadBatch} handle re-enabling.
     */
    public void flush() {
        if (quadCount == 0) return;
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_QUADS);
        int verts = quadCount * 4;
        for (int v = 0; v < verts; v++) {
            int o = v * 6;
            glColor4f(data[o + 2], data[o + 3], data[o + 4], data[o + 5]);
            glVertex2f(data[o], data[o + 1]);
        }
        glEnd();
        quadCount = 0;
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
