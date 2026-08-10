package com.dillon.starsectormarines.render2d;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_LINES;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;

/**
 * Untextured line-segment batcher — the line counterpart to {@link SolidQuadBatch}.
 * Callers queue per-segment endpoints + per-vertex color via {@link #append}, and a
 * single {@link #flush} emits them all as one {@code glDrawArrays(GL_LINES, …)}.
 * For in-loop line geometry (hitscan tracers, and later convoy-debug paths / the
 * zone grid) that needs to share painter ordering with the textured/solid batches
 * around it.
 *
 * <p><strong>Line width is per-flush GL state, not per-vertex.</strong> All segments
 * in one flush draw at {@link #setWidth} — the drain flushes and re-sets the width
 * whenever a {@code LINE} command's width changes, the same way it flips the active
 * sheet on a sheet change. {@code flush} restores width to {@code 1f} afterward
 * because {@code glLineWidth} lives in {@code GL_LINE_BIT}, which the
 * {@link GlStateBracket#textured2D()} bracket does not save.
 *
 * <p>Draws via client-side vertex arrays + {@code glDrawArrays}, not immediate-mode
 * {@code glBegin/glEnd} — see {@link QuadBatch#flush()} for the rationale (the
 * per-vertex {@code glBegin} loop is the dominant render-CPU cost we removed
 * elsewhere), and {@link ClientArray} for the packing contract those pointers must
 * honor. Brackets its client-array enables with {@code glPushClientAttrib} /
 * {@code glPopClientAttrib} and unbinds any host {@code GL_ARRAY_BUFFER} for the
 * draw (LWJGL throws on client-array pointers while a VBO is bound — see
 * {@code [[lwjgl_client_array_vbo_guard]]}).
 *
 * <p>NOT thread-safe. Plain {@code float[]} backing.
 */
public final class LineBatch {

    private static final int VERTS_PER_SEG = 2;
    /** Position floats per segment: 2 verts × (x, y). */
    private static final int POS_FLOATS_PER_SEG = VERTS_PER_SEG * 2;
    /** Color floats per segment: 2 verts × (r, g, b, a). */
    private static final int COL_FLOATS_PER_SEG = VERTS_PER_SEG * 4;

    /**
     * One packed array per vertex attribute rather than a single interleaved
     * array — required by the client-array contract in {@link ClientArray}.
     */
    private float[] posData;
    private float[] colData;
    private int segCount;
    private float width = 1f;

    private final ClientArray posArray = new ClientArray();
    private final ClientArray colArray = new ClientArray();

    public LineBatch(int initialSegCapacity) {
        int segs = Math.max(1, initialSegCapacity);
        this.posData = new float[segs * POS_FLOATS_PER_SEG];
        this.colData = new float[segs * COL_FLOATS_PER_SEG];
        this.segCount = 0;
    }

    public boolean isEmpty() { return segCount == 0; }

    /** The line width the next {@link #flush} will draw at. */
    public float width() { return width; }

    /** Set the line width for the queued + subsequently-appended segments. */
    public void setWidth(float width) { this.width = width; }

    /** Queue one line segment {@code (x0,y0)–(x1,y1)} with a uniform per-vertex color. */
    public void append(float x0, float y0, float x1, float y1,
                       float r, float g, float b, float a) {
        ensureCapacity(segCount + 1);
        int p = segCount * POS_FLOATS_PER_SEG;
        posData[p++] = x0; posData[p++] = y0;
        posData[p++] = x1; posData[p]   = y1;
        int c = segCount * COL_FLOATS_PER_SEG;
        for (int i = 0; i < VERTS_PER_SEG; i++) {
            colData[c++] = r; colData[c++] = g; colData[c++] = b; colData[c++] = a;
        }
        segCount++;
    }

    /**
     * Emit all queued segments as one {@code glDrawArrays(GL_LINES, …)} from
     * client-side vertex arrays. No-op if empty. Resets the queue. Disables
     * {@code GL_TEXTURE_2D} (lines are untextured) and sets the line width,
     * restoring it to {@code 1f} after.
     */
    public void flush() {
        if (segCount == 0) return;
        glDisable(GL_TEXTURE_2D);
        glLineWidth(width);

        int verts = segCount * VERTS_PER_SEG;

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
        glDrawArrays(GL_LINES, 0, verts);
        glPopClientAttrib();

        glLineWidth(1f);

        segCount = 0;
    }

    private void ensureCapacity(int neededSegs) {
        if (posData.length >= neededSegs * POS_FLOATS_PER_SEG) return;
        int newSegs = posData.length / POS_FLOATS_PER_SEG;
        while (newSegs < neededSegs) newSegs *= 2;
        posData = grow(posData, newSegs * POS_FLOATS_PER_SEG, segCount * POS_FLOATS_PER_SEG);
        colData = grow(colData, newSegs * COL_FLOATS_PER_SEG, segCount * COL_FLOATS_PER_SEG);
    }

    private static float[] grow(float[] src, int newLen, int used) {
        float[] grown = new float[newLen];
        System.arraycopy(src, 0, grown, 0, used);
        return grown;
    }
}
