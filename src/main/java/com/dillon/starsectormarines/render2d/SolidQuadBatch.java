package com.dillon.starsectormarines.render2d;

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
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;

/**
 * Untextured-quad counterpart to {@link QuadBatch}. Emits solid-colored
 * quads with no texture binding — for in-loop solid fills (interior-wall
 * fallback, crosswalk stripes) that need to share painter ordering with
 * the textured passes around them.
 *
 * <p>Without this batch, mixing immediate-mode {@code fillRect} calls
 * with deferred textured batches would draw the solid fills BEFORE the
 * later textured flush, breaking layer order (e.g., crosswalk stripes
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

    private static final int VERTS_PER_QUAD = 4;
    /** Position floats per quad: 4 verts × (x, y). */
    private static final int POS_FLOATS_PER_QUAD = VERTS_PER_QUAD * 2;
    /** Color floats per quad: 4 verts × (r, g, b, a). */
    private static final int COL_FLOATS_PER_QUAD = VERTS_PER_QUAD * 4;

    /**
     * One packed array per vertex attribute rather than a single interleaved
     * array — required by the client-array contract in {@link ClientArray}.
     */
    private float[] posData;
    private float[] colData;
    private int quadCount;

    private final ClientArray posArray = new ClientArray();
    private final ClientArray colArray = new ClientArray();

    public SolidQuadBatch(int initialQuadCapacity) {
        int quads = Math.max(1, initialQuadCapacity);
        this.posData = new float[quads * POS_FLOATS_PER_QUAD];
        this.colData = new float[quads * COL_FLOATS_PER_QUAD];
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
        appendQuad(x0, y0, x1, y0, x1, y1, x0, y1, r, g, b, a);
    }

    /**
     * Queue one solid-color quad with four arbitrary screen-space corners
     * (CCW or CW; {@code GL_QUADS} doesn't care for a flat fill). The general
     * primitive {@link #appendRect} is the axis-aligned special case of — used
     * by {@code POLY} command geometry (annulus / arc fans, where each segment
     * is a non-axis-aligned trapezoid). Corner order is the GL vertex order:
     * {@code (0,1)→(2,3)→(4,5)→(6,7)}.
     */
    public void appendQuad(float x0, float y0, float x1, float y1,
                           float x2, float y2, float x3, float y3,
                           float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);
        int p = quadCount * POS_FLOATS_PER_QUAD;
        posData[p++] = x0; posData[p++] = y0;
        posData[p++] = x1; posData[p++] = y1;
        posData[p++] = x2; posData[p++] = y2;
        posData[p++] = x3; posData[p]   = y3;
        writeQuadColor(r, g, b, a);
        quadCount++;
    }

    /** Writes {@code (r,g,b,a)} to all four verts of the quad at {@link #quadCount}. */
    private void writeQuadColor(float r, float g, float b, float a) {
        int c = quadCount * COL_FLOATS_PER_QUAD;
        for (int i = 0; i < VERTS_PER_QUAD; i++) {
            colData[c++] = r; colData[c++] = g; colData[c++] = b; colData[c++] = a;
        }
    }

    /**
     * Emit all queued quads as one {@code glDrawArrays(GL_QUADS, …)} from
     * client-side vertex arrays (see {@link QuadBatch#flush()} for the
     * rationale — the per-vertex {@code glBegin} loop was the dominant render-CPU
     * cost). No-op if empty. Resets the queue. Disables {@code GL_TEXTURE_2D}
     * before drawing so a stale texture binding doesn't bleed through — paired
     * flushes with {@link QuadBatch} handle re-enabling. Brackets its client-array
     * enables with {@code glPushClientAttrib}/{@code glPopClientAttrib}.
     */
    public void flush() {
        if (quadCount == 0) return;
        glDisable(GL_TEXTURE_2D);

        int verts = quadCount * VERTS_PER_QUAD;

        // See QuadBatch.flush: unbind any host-bound VBO so LWJGL's
        // ensureArrayVBOdisabled check doesn't throw on the client-array pointers.
        // Left at 0 (no readback) so a glGet* doesn't stall async-renderer bridges.
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Stride 0 + position 0 + exact capacity, one buffer per attribute — see ClientArray.
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(2, 0, posArray.upload(posData, verts * 2));
        glColorPointer(4, 0, colArray.upload(colData, verts * 4));
        glDrawArrays(GL_QUADS, 0, verts);
        glPopClientAttrib();

        quadCount = 0;
    }

    private void ensureCapacity(int neededQuads) {
        if (posData.length >= neededQuads * POS_FLOATS_PER_QUAD) return;
        int newQuads = posData.length / POS_FLOATS_PER_QUAD;
        while (newQuads < neededQuads) newQuads *= 2;
        posData = grow(posData, newQuads * POS_FLOATS_PER_QUAD, quadCount * POS_FLOATS_PER_QUAD);
        colData = grow(colData, newQuads * COL_FLOATS_PER_QUAD, quadCount * COL_FLOATS_PER_QUAD);
    }

    private static float[] grow(float[] src, int newLen, int used) {
        float[] grown = new float[newLen];
        System.arraycopy(src, 0, grown, 0, used);
        return grown;
    }
}
