package com.dillon.starsectormarines.render2d;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glVertexPointer;

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

    /** Cached direct buffer the flush copies {@link #data} into for {@code glDrawArrays}. */
    private FloatBuffer vbuf;

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
     * Emit all queued quads as one {@code glDrawArrays(GL_QUADS, …)} from a
     * client-side interleaved vertex array (see {@link QuadBatch#flush()} for the
     * rationale — the per-vertex {@code glBegin} loop was the dominant render-CPU
     * cost). No-op if empty. Resets the queue. Disables {@code GL_TEXTURE_2D}
     * before drawing so a stale texture binding doesn't bleed through — paired
     * flushes with {@link QuadBatch} handle re-enabling. Brackets its client-array
     * enables with {@code glPushClientAttrib}/{@code glPopClientAttrib}.
     */
    public void flush() {
        if (quadCount == 0) return;
        glDisable(GL_TEXTURE_2D);

        int verts = quadCount * 4;
        int floats = verts * 6;
        FloatBuffer buf = ensureBuffer(floats);
        buf.clear();
        buf.put(data, 0, floats);
        buf.flip();

        // Interleaved layout: (x, y, r, g, b, a), stride = 6 floats. No texcoords.
        int strideBytes = 6 * 4;
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        buf.position(0); glVertexPointer(2, strideBytes, buf);
        buf.position(2); glColorPointer(4, strideBytes, buf);
        glDrawArrays(GL_QUADS, 0, verts);
        glPopClientAttrib();

        quadCount = 0;
    }

    /** Lazily (re)allocate the direct buffer to hold at least {@code floats} elements. */
    private FloatBuffer ensureBuffer(int floats) {
        if (vbuf == null || vbuf.capacity() < floats) {
            vbuf = BufferUtils.createFloatBuffer(data.length);
        }
        return vbuf;
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
