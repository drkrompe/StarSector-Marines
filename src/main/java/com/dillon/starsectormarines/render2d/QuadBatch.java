package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

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
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
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
 * count: one bulk copy per attribute array into a cached direct buffer, three
 * pointer binds, one {@code glDrawArrays(GL_QUADS, …)}. Position, texcoord and
 * color live in separate packed arrays rather than one interleaved array — see
 * {@link ClientArray} for why that packing is mandatory rather than a
 * preference.
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
 * <p>NOT thread-safe. NOT a managed resource — backing arrays are plain
 * {@code float[]} so no close/free needed.
 */
public final class QuadBatch {

    private static final int VERTS_PER_QUAD = 4;
    /** Position floats per quad: 4 verts × (x, y). */
    private static final int POS_FLOATS_PER_QUAD = VERTS_PER_QUAD * 2;
    /** Texcoord floats per quad: 4 verts × (u, v). */
    private static final int UV_FLOATS_PER_QUAD = VERTS_PER_QUAD * 2;
    /** Color floats per quad: 4 verts × (r, g, b, a). */
    private static final int COL_FLOATS_PER_QUAD = VERTS_PER_QUAD * 4;

    private final SpriteAPI sheet;
    private final int sheetPxW;
    private final int sheetPxH;

    /**
     * One packed array per vertex attribute rather than a single interleaved
     * array — required by the client-array contract in {@link ClientArray}.
     */
    private float[] posData;
    private float[] uvData;
    private float[] colData;
    private int quadCount;

    private final ClientArray posArray = new ClientArray();
    private final ClientArray uvArray = new ClientArray();
    private final ClientArray colArray = new ClientArray();

    public QuadBatch(SpriteAPI sheet, int sheetPxW, int sheetPxH, int initialQuadCapacity) {
        this.sheet = sheet;
        this.sheetPxW = sheetPxW;
        this.sheetPxH = sheetPxH;
        int quads = Math.max(1, initialQuadCapacity);
        this.posData = new float[quads * POS_FLOATS_PER_QUAD];
        this.uvData = new float[quads * UV_FLOATS_PER_QUAD];
        this.colData = new float[quads * COL_FLOATS_PER_QUAD];
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

        int p = quadCount * POS_FLOATS_PER_QUAD;
        posData[p++] = blX; posData[p++] = blY;
        posData[p++] = brX; posData[p++] = brY;
        posData[p++] = trX; posData[p++] = trY;
        posData[p++] = tlX; posData[p]   = tlY;

        // BL ↔ (u0, v1), BR ↔ (u1, v1), TR ↔ (u1, v0), TL ↔ (u0, v0)
        writeQuadUv(u0, v1, u1, v1, u1, v0, u0, v0);
        writeQuadColor(r, g, b, a);

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

        writeAxisAlignedQuad(dstCx, dstCy, dstW, dstH);
        // BL ↔ (u0, v1), BR ↔ (u1, v1), TR ↔ (u1, v0), TL ↔ (u0, v0)
        writeQuadUv(u0, v1, u1, v1, u1, v0, u0, v0);
        writeQuadColor(r, g, b, a);

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

        writeAxisAlignedQuad(dstCx, dstCy, dstW, dstH);
        // V swapped vs append(): bottom corners take v0, top corners take v1.
        writeQuadUv(u0, v0, u1, v0, u1, v1, u0, v1);
        writeQuadColor(r, g, b, a);

        quadCount++;
    }

    /** Writes the BL/BR/TR/TL corners of an axis-aligned dst rect at {@link #quadCount}. */
    private void writeAxisAlignedQuad(float dstCx, float dstCy, float dstW, float dstH) {
        float halfW = dstW * 0.5f;
        float halfH = dstH * 0.5f;
        float x0 = dstCx - halfW;
        float x1 = dstCx + halfW;
        float y0 = dstCy - halfH;
        float y1 = dstCy + halfH;

        int p = quadCount * POS_FLOATS_PER_QUAD;
        posData[p++] = x0; posData[p++] = y0;
        posData[p++] = x1; posData[p++] = y0;
        posData[p++] = x1; posData[p++] = y1;
        posData[p++] = x0; posData[p]   = y1;
    }

    /** Writes the four per-corner UV pairs of the quad at {@link #quadCount}, in vertex order. */
    private void writeQuadUv(float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3) {
        int t = quadCount * UV_FLOATS_PER_QUAD;
        uvData[t++] = u0; uvData[t++] = v0;
        uvData[t++] = u1; uvData[t++] = v1;
        uvData[t++] = u2; uvData[t++] = v2;
        uvData[t++] = u3; uvData[t]   = v3;
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
     * client-side vertex arrays. No-op if empty. Resets the queue.
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

        int verts = quadCount * VERTS_PER_QUAD;

        // LWJGL's gl*Pointer(FloatBuffer) overloads throw OpenGLException if a VBO
        // is bound to GL_ARRAY_BUFFER (its ensureArrayVBOdisabled check, on by
        // default). Starsector hands us a polluted GL state and a co-loaded mod may
        // leave one bound — unbind for our client-array draw. We leave it at 0 (the
        // fixed-function default the UI pass runs under) rather than reading the
        // prior binding back and restoring it: a glGet* here forces a synchronous
        // round-trip that stalls async-renderer bridge mods (e.g. genir's). The
        // client-attrib bracket does NOT cover the array-buffer binding.
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Stride 0 + position 0 + exact capacity, one buffer per attribute — see ClientArray.
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(2, 0, posArray.upload(posData, verts * 2));
        glTexCoordPointer(2, 0, uvArray.upload(uvData, verts * 2));
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
        uvData = grow(uvData, newQuads * UV_FLOATS_PER_QUAD, quadCount * UV_FLOATS_PER_QUAD);
        colData = grow(colData, newQuads * COL_FLOATS_PER_QUAD, quadCount * COL_FLOATS_PER_QUAD);
    }

    private static float[] grow(float[] src, int newLen, int used) {
        float[] grown = new float[newLen];
        System.arraycopy(src, 0, grown, 0, used);
        return grown;
    }
}
