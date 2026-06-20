package com.dillon.starsectormarines.render2d;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

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
 * elsewhere). Brackets its client-array enables with {@code glPushClientAttrib} /
 * {@code glPopClientAttrib} and unbinds any host {@code GL_ARRAY_BUFFER} for the
 * draw (LWJGL throws on client-array pointers while a VBO is bound — see
 * {@code [[lwjgl_client_array_vbo_guard]]}).
 *
 * <p>NOT thread-safe. Plain {@code float[]} backing.
 */
public final class LineBatch {

    /** 2 verts per segment, 6 floats per vert: (x, y, r, g, b, a). */
    private static final int FLOATS_PER_SEG = 2 * 6;

    private float[] data;
    private int segCount;
    private float width = 1f;

    /** Cached direct buffer the flush copies {@link #data} into for {@code glDrawArrays}. */
    private FloatBuffer vbuf;

    public LineBatch(int initialSegCapacity) {
        this.data = new float[Math.max(1, initialSegCapacity) * FLOATS_PER_SEG];
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
        int o = segCount * FLOATS_PER_SEG;
        data[o++] = x0; data[o++] = y0; data[o++] = r; data[o++] = g; data[o++] = b; data[o++] = a;
        data[o++] = x1; data[o++] = y1; data[o++] = r; data[o++] = g; data[o++] = b; data[o]   = a;
        segCount++;
    }

    /**
     * Emit all queued segments as one {@code glDrawArrays(GL_LINES, …)} from a
     * client-side interleaved vertex array. No-op if empty. Resets the queue.
     * Disables {@code GL_TEXTURE_2D} (lines are untextured) and sets the line
     * width, restoring it to {@code 1f} after.
     */
    public void flush() {
        if (segCount == 0) return;
        glDisable(GL_TEXTURE_2D);
        glLineWidth(width);

        int verts = segCount * 2;
        int floats = verts * 6;
        FloatBuffer buf = ensureBuffer(floats);
        buf.clear();
        buf.put(data, 0, floats);
        buf.flip();

        // See QuadBatch.flush: unbind any host-bound VBO so LWJGL's
        // ensureArrayVBOdisabled check doesn't throw on the client-array pointers.
        // Left at 0 (no readback) so a glGet* doesn't stall async-renderer bridges.
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Interleaved layout: (x, y, r, g, b, a), stride = 6 floats. No texcoords.
        int strideBytes = 6 * 4;
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        buf.position(0); glVertexPointer(2, strideBytes, buf);
        buf.position(2); glColorPointer(4, strideBytes, buf);
        glDrawArrays(GL_LINES, 0, verts);
        glPopClientAttrib();

        glLineWidth(1f);

        segCount = 0;
    }

    /** Lazily (re)allocate the direct buffer to hold at least {@code floats} elements. */
    private FloatBuffer ensureBuffer(int floats) {
        if (vbuf == null || vbuf.capacity() < floats) {
            vbuf = BufferUtils.createFloatBuffer(data.length);
        }
        return vbuf;
    }

    private void ensureCapacity(int neededSegs) {
        int neededFloats = neededSegs * FLOATS_PER_SEG;
        if (data.length >= neededFloats) return;
        int newLen = data.length;
        while (newLen < neededFloats) newLen *= 2;
        float[] grown = new float[newLen];
        System.arraycopy(data, 0, grown, 0, segCount * FLOATS_PER_SEG);
        data = grown;
    }
}
