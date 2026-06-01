package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15.glBindBuffer;

/**
 * Per-sheet textured-quad batcher. One instance is bound to one
 * {@link SpriteAPI} sheet at construction; callers stuff sub-rects + dst
 * rects + per-quad color via {@link #append}, and a single {@link #flush}
 * binds the texture once and emits all queued quads in one
 * {@code glBegin(GL_QUADS) … glEnd()} block.
 *
 * <p>Replaces the per-cell {@code sheet.renderAtCenter(…)} pattern, which
 * does one bind + state churn per tile and dominates frame time on
 * 100×100 maps. Multiple flushes on the same instance are fine — the
 * vertex buffer resets after each flush, so the same batch can be reused
 * across multiple passes within a frame (e.g., one urban-sheet flush for
 * the floor pass and another for the wall pass to preserve painter order
 * around the decal/vehicle layers that draw between them).
 *
 * <p>{@link #flush} draws via <strong>client-side vertex arrays +
 * {@code glDrawArrays}</strong>, not immediate-mode {@code glBegin/glEnd}.
 * Profiling (3 captures, 2026-05/06) put the old per-vertex {@code glBegin}
 * loop at ~78% of render CPU / ~29% of total mod CPU in a ~400-unit battle —
 * dominated by the 12 LWJGL JNI crossings per quad
 * ({@code glColor4f}+{@code glTexCoord2f}+{@code glVertex2f} × 4 verts), not the
 * float-packing in {@link #append} (which barely registered). The array path
 * collapses that to a fixed handful of calls per flush regardless of quad
 * count: one bulk copy of the interleaved {@code data[]} into a cached
 * <em>direct</em> {@link FloatBuffer}, three strided pointer binds, one
 * {@code glDrawArrays(GL_QUADS, …)}.
 *
 * <p>This is <em>not</em> a VBO — no buffer object, no {@code glBufferData},
 * none of the streaming sync-stall management that comes with it. The data is
 * GPU-trivial (simple ortho quads, fill-rate-cheap), so the only bottleneck was
 * CPU-side submission, which the client-array path fully removes; a VBO's
 * GPU-residency win would be marginal here and not worth the binding/sync
 * complexity. The one cost is client-state hygiene — Starsector hands UI hooks a
 * polluted GL state — so each flush brackets its array enables with
 * {@code glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT)} / {@code glPopClientAttrib}
 * (2 calls), leaking no enable or pointer binding into the host's state.
 *
 * <p>UV mapping follows the same convention {@link com.dillon.starsectormarines.ui.BitmapFont}
 * uses: {@code u = (srcPx / sheetPx) * sheet.getTextureWidth()}, where
 * {@code getTextureWidth()} returns the normalized max-U for the image
 * within the (possibly POT-padded) GL texture. V is flipped because
 * source pixel-Y is top-down but GL texture-V is bottom-up.
 *
 * <p>NOT thread-safe. NOT a managed resource — backing array is plain
 * {@code float[]} so no close/free needed.
 */
public final class QuadBatch {

    /** 4 verts per quad, 8 floats per vert: (x, y, u, v, r, g, b, a). */
    private static final int FLOATS_PER_QUAD = 4 * 8;

    private final SpriteAPI sheet;
    private final int sheetPxW;
    private final int sheetPxH;

    private float[] data;
    private int quadCount;

    /** Cached direct buffer the flush copies {@link #data} into for {@code glDrawArrays}. */
    private FloatBuffer vbuf;

    public QuadBatch(SpriteAPI sheet, int sheetPxW, int sheetPxH, int initialQuadCapacity) {
        this.sheet = sheet;
        this.sheetPxW = sheetPxW;
        this.sheetPxH = sheetPxH;
        this.data = new float[Math.max(1, initialQuadCapacity) * FLOATS_PER_QUAD];
        this.quadCount = 0;
    }

    public int quadCount() { return quadCount; }
    public boolean isEmpty() { return quadCount == 0; }

    /**
     * Queue one rotated textured quad. Same as {@link #append} but rotates
     * the destination rect by {@code rotationDeg} (CCW) around its center
     * before writing the four corners. UVs are NOT rotated — the sub-rect
     * still samples axis-aligned from the sheet; only the four
     * destination-space corners are rotated.
     *
     * <p>Used by callers that need per-quad rotation (decals, projectile
     * sprites). For axis-aligned quads, prefer {@link #append} — it skips
     * the trig and is a hair cheaper at queue time.
     */
    public void appendRotated(int srcX, int srcY, int srcW, int srcH,
                              float dstCx, float dstCy, float dstW, float dstH,
                              float rotationDeg,
                              float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);

        float texU = sheet.getTextureWidth();
        float texV = sheet.getTextureHeight();
        float u0 = ((float) srcX           / sheetPxW) * texU;
        float u1 = ((float) (srcX + srcW)  / sheetPxW) * texU;
        float v0 = texV - ((float) srcY           / sheetPxH) * texV;
        float v1 = texV - ((float) (srcY + srcH)  / sheetPxH) * texV;

        float halfW = dstW * 0.5f;
        float halfH = dstH * 0.5f;
        double rad = Math.toRadians(rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        // Local corners before rotation: (±halfW, ±halfH). Rotate by (cos, sin)
        // around origin, then translate to (dstCx, dstCy).
        float blX = dstCx + (-halfW) * cos - (-halfH) * sin;
        float blY = dstCy + (-halfW) * sin + (-halfH) * cos;
        float brX = dstCx + ( halfW) * cos - (-halfH) * sin;
        float brY = dstCy + ( halfW) * sin + (-halfH) * cos;
        float trX = dstCx + ( halfW) * cos - ( halfH) * sin;
        float trY = dstCy + ( halfW) * sin + ( halfH) * cos;
        float tlX = dstCx + (-halfW) * cos - ( halfH) * sin;
        float tlY = dstCy + (-halfW) * sin + ( halfH) * cos;

        int o = quadCount * FLOATS_PER_QUAD;
        // BL — ↔ (u0, v1)
        data[o++] = blX; data[o++] = blY; data[o++] = u0; data[o++] = v1;
        data[o++] = r;   data[o++] = g;   data[o++] = b;  data[o++] = a;
        // BR — ↔ (u1, v1)
        data[o++] = brX; data[o++] = brY; data[o++] = u1; data[o++] = v1;
        data[o++] = r;   data[o++] = g;   data[o++] = b;  data[o++] = a;
        // TR — ↔ (u1, v0)
        data[o++] = trX; data[o++] = trY; data[o++] = u1; data[o++] = v0;
        data[o++] = r;   data[o++] = g;   data[o++] = b;  data[o++] = a;
        // TL — ↔ (u0, v0)
        data[o++] = tlX; data[o++] = tlY; data[o++] = u0; data[o++] = v0;
        data[o++] = r;   data[o++] = g;   data[o++] = b;  data[o] = a;

        quadCount++;
    }

    /**
     * Queue one textured quad. Source rect is in sheet-image-pixel space
     * (top-down). Destination is a center + size in screen-space units.
     * Per-vertex color/alpha is applied to all four corners.
     */
    public void append(int srcX, int srcY, int srcW, int srcH,
                       float dstCx, float dstCy, float dstW, float dstH,
                       float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);

        float texU = sheet.getTextureWidth();
        float texV = sheet.getTextureHeight();
        float u0 = ((float) srcX           / sheetPxW) * texU;
        float u1 = ((float) (srcX + srcW)  / sheetPxW) * texU;
        // Flip Y: srcY=0 is the top of the image, but GL's V=texV is the top.
        float v0 = texV - ((float) srcY           / sheetPxH) * texV;
        float v1 = texV - ((float) (srcY + srcH)  / sheetPxH) * texV;

        float halfW = dstW * 0.5f;
        float halfH = dstH * 0.5f;
        float x0 = dstCx - halfW;
        float x1 = dstCx + halfW;
        float y0 = dstCy - halfH;
        float y1 = dstCy + halfH;

        int o = quadCount * FLOATS_PER_QUAD;
        // BL — (x0, y0) ↔ (u0, v1)
        data[o++] = x0; data[o++] = y0; data[o++] = u0; data[o++] = v1;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // BR — (x1, y0) ↔ (u1, v1)
        data[o++] = x1; data[o++] = y0; data[o++] = u1; data[o++] = v1;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // TR — (x1, y1) ↔ (u1, v0)
        data[o++] = x1; data[o++] = y1; data[o++] = u1; data[o++] = v0;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // TL — (x0, y1) ↔ (u0, v0)
        data[o++] = x0; data[o++] = y1; data[o++] = u0; data[o++] = v0;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o] = a;

        quadCount++;
    }

    /**
     * Queue one textured quad with the source sub-rect mirrored vertically
     * (top↔bottom). Identical to {@link #append} but the four destination corners
     * sample the swapped V coordinates — the engine equivalent of the old
     * negative-{@code setTexHeight} flip used for the SOUTH-weapon-up infantry
     * pose. Axis-aligned only (the mirror has no rotated caller).
     */
    public void appendFlippedV(int srcX, int srcY, int srcW, int srcH,
                               float dstCx, float dstCy, float dstW, float dstH,
                               float r, float g, float b, float a) {
        ensureCapacity(quadCount + 1);

        float texU = sheet.getTextureWidth();
        float texV = sheet.getTextureHeight();
        float u0 = ((float) srcX           / sheetPxW) * texU;
        float u1 = ((float) (srcX + srcW)  / sheetPxW) * texU;
        float v0 = texV - ((float) srcY           / sheetPxH) * texV;
        float v1 = texV - ((float) (srcY + srcH)  / sheetPxH) * texV;

        float halfW = dstW * 0.5f;
        float halfH = dstH * 0.5f;
        float x0 = dstCx - halfW;
        float x1 = dstCx + halfW;
        float y0 = dstCy - halfH;
        float y1 = dstCy + halfH;

        int o = quadCount * FLOATS_PER_QUAD;
        // V swapped vs append(): bottom corners take v0, top corners take v1.
        // BL — (x0, y0) ↔ (u0, v0)
        data[o++] = x0; data[o++] = y0; data[o++] = u0; data[o++] = v0;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // BR — (x1, y0) ↔ (u1, v0)
        data[o++] = x1; data[o++] = y0; data[o++] = u1; data[o++] = v0;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // TR — (x1, y1) ↔ (u1, v1)
        data[o++] = x1; data[o++] = y1; data[o++] = u1; data[o++] = v1;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o++] = a;
        // TL — (x0, y1) ↔ (u0, v1)
        data[o++] = x0; data[o++] = y1; data[o++] = u0; data[o++] = v1;
        data[o++] = r;  data[o++] = g;  data[o++] = b;  data[o] = a;

        quadCount++;
    }

    /**
     * Emit all queued quads as one {@code glDrawArrays(GL_QUADS, …)} from a
     * client-side interleaved vertex array. No-op if empty. Resets the queue.
     * Caller owns the surrounding <em>server</em> GL state — see
     * {@link GlStateBracket#textured2D()}; this method owns the <em>client</em>
     * array state, restoring it via {@code glPopClientAttrib}.
     *
     * <p>Re-enables {@code GL_TEXTURE_2D} defensively in case a solid-color
     * draw (e.g. {@code fillCell} during the wall pass) toggled it off
     * between {@link #append} calls.
     */
    public void flush() {
        if (quadCount == 0) return;
        glEnable(GL_TEXTURE_2D);
        sheet.bindTexture();

        int verts = quadCount * 4;
        int floats = verts * 8;
        FloatBuffer buf = ensureBuffer(floats);
        buf.clear();
        buf.put(data, 0, floats);
        buf.flip();

        // LWJGL's gl*Pointer(FloatBuffer) overloads throw OpenGLException if a VBO
        // is bound to GL_ARRAY_BUFFER (its ensureArrayVBOdisabled check, on by
        // default). Starsector hands us a polluted GL state and a co-loaded mod may
        // leave one bound — unbind for our client-array draw, restore after. (The
        // client-attrib bracket does NOT cover the array-buffer binding.)
        int prevArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        if (prevArrayBuffer != 0) glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Interleaved layout: (x, y, u, v, r, g, b, a), stride = 8 floats.
        int strideBytes = 8 * 4;
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        buf.position(0); glVertexPointer(2, strideBytes, buf);
        buf.position(2); glTexCoordPointer(2, strideBytes, buf);
        buf.position(4); glColorPointer(4, strideBytes, buf);
        glDrawArrays(GL_QUADS, 0, verts);
        glPopClientAttrib();

        if (prevArrayBuffer != 0) glBindBuffer(GL_ARRAY_BUFFER, prevArrayBuffer);

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
