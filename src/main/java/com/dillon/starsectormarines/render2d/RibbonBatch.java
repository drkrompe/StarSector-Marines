package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.ops.battleview.BattleCamera;

import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Solid-color ribbon batcher for {@link ContrailTrail} samples. Walks each
 * trail's samples in oldest→newest order, computes a per-vertex tangent
 * from neighboring samples, expands to two side points along the
 * perpendicular at the age-interpolated half-width, and emits one quad per
 * segment with all four vertex colors lerped between the style's
 * {@code start*} and {@code end*} fields by age.
 *
 * <p>Per-vertex tangent (averaged neighbor delta at interior samples, end
 * delta at the two endpoints) keeps interior joints continuous — no V-shaped
 * creases at curves — so a missile arc reads as a smooth ribbon rather than
 * a chain of misaligned segments.
 *
 * <p>One batch instance is bound to one blend mode at draw time; callers
 * pass the trails into {@link #append} then call {@link #flush}. Pair with
 * {@link GlStateBracket#textured2D()} for {@code additive=false} (smoke)
 * and {@link GlStateBracket#additiveBlend()} for {@code additive=true}
 * (engine plume); the batch disables {@code GL_TEXTURE_2D} during flush
 * since contrail quads are solid color.
 *
 * <p>NOT thread-safe. Plain {@code float[]} backing.
 */
public final class RibbonBatch {

    /** 4 verts per quad, 6 floats per vert: (x, y, r, g, b, a). */
    private static final int FLOATS_PER_QUAD = 4 * 6;

    private float[] data;
    private int quadCount;

    public RibbonBatch(int initialQuadCapacity) {
        this.data = new float[Math.max(1, initialQuadCapacity) * FLOATS_PER_QUAD];
        this.quadCount = 0;
    }

    public boolean isEmpty() { return quadCount == 0; }

    /**
     * Queue one trail's segments. No-op for trails with fewer than 2
     * samples. The {@code alphaMult} (e.g. dialog fade-in) is folded
     * into every vertex's alpha channel here so the batch flush stays a
     * single solid {@code GL_QUADS} block with no per-flush color state.
     */
    public void append(ContrailTrail trail, BattleCamera camera, float alphaMult) {
        int n = trail.size();
        if (n < 2) return;
        ContrailStyle style = trail.style;
        float invDur = 1f / Math.max(0.0001f, style.durationSec);
        float cellPx = camera.cellPxSize();

        ensureCapacity(quadCount + (n - 1));

        // Cache per-vertex side points so a sample shared by two adjacent
        // segments uses the same (x, y) on both — that's what keeps interior
        // joints continuous. Two parallel arrays: leftX/leftY/rightX/rightY
        // indexed by sample index 0..n-1.
        float[] lx = new float[n];
        float[] ly = new float[n];
        float[] rx = new float[n];
        float[] ry = new float[n];
        for (int i = 0; i < n; i++) {
            ContrailTrail.Sample s = trail.get(i);
            // Tangent: averaged neighbor delta at interior, end delta at endpoints.
            float tx, ty;
            if (i == 0) {
                ContrailTrail.Sample b = trail.get(1);
                tx = b.x - s.x; ty = b.y - s.y;
            } else if (i == n - 1) {
                ContrailTrail.Sample a = trail.get(n - 2);
                tx = s.x - a.x; ty = s.y - a.y;
            } else {
                ContrailTrail.Sample a = trail.get(i - 1);
                ContrailTrail.Sample b = trail.get(i + 1);
                tx = b.x - a.x; ty = b.y - a.y;
            }
            float len = (float) Math.sqrt(tx * tx + ty * ty);
            if (len < 1e-5f) { tx = 1f; ty = 0f; }
            else             { tx /= len; ty /= len; }
            // Perpendicular: (-ty, tx) — rotated +90° from the tangent.
            float perpX = -ty;
            float perpY =  tx;

            float ageT = Math.min(1f, s.age * invDur);
            float halfWidthCells = lerp(style.startWidthCells, style.endWidthCells, ageT);
            float halfWidthPx = halfWidthCells * cellPx;

            // Convert sample's world (cell-space) position to screen pixels,
            // then offset along perp by half-width. We resolve the screen-
            // space center once and add the perpendicular in pixels so the
            // width is camera-zoom-aware via cellPxSize().
            float cx = camera.cellToScreenX(s.x);
            float cy = camera.cellToScreenY(s.y);
            lx[i] = cx - perpX * halfWidthPx;
            ly[i] = cy - perpY * halfWidthPx;
            rx[i] = cx + perpX * halfWidthPx;
            ry[i] = cy + perpY * halfWidthPx;
        }

        // Emit one quad per segment (i, i+1). Color/alpha are per-vertex,
        // lerped by each endpoint's age — that gives the leading edge full
        // start-color and the trailing edge full end-color even mid-flight.
        for (int i = 0; i < n - 1; i++) {
            ContrailTrail.Sample a = trail.get(i);
            ContrailTrail.Sample b = trail.get(i + 1);
            float aAge = Math.min(1f, a.age * invDur);
            float bAge = Math.min(1f, b.age * invDur);

            float aR = lerp(style.startR, style.endR, aAge);
            float aG = lerp(style.startG, style.endG, aAge);
            float aB = lerp(style.startB, style.endB, aAge);
            float aA = lerp(style.startA, style.endA, aAge) * alphaMult;

            float bR = lerp(style.startR, style.endR, bAge);
            float bG = lerp(style.startG, style.endG, bAge);
            float bB = lerp(style.startB, style.endB, bAge);
            float bA = lerp(style.startA, style.endA, bAge) * alphaMult;

            int o = quadCount * FLOATS_PER_QUAD;
            // Winding: a-left, a-right, b-right, b-left (CCW for GL_QUADS).
            data[o++] = lx[i];     data[o++] = ly[i];     data[o++] = aR; data[o++] = aG; data[o++] = aB; data[o++] = aA;
            data[o++] = rx[i];     data[o++] = ry[i];     data[o++] = aR; data[o++] = aG; data[o++] = aB; data[o++] = aA;
            data[o++] = rx[i + 1]; data[o++] = ry[i + 1]; data[o++] = bR; data[o++] = bG; data[o++] = bB; data[o++] = bA;
            data[o++] = lx[i + 1]; data[o++] = ly[i + 1]; data[o++] = bR; data[o++] = bG; data[o++] = bB; data[o]   = bA;
            quadCount++;
        }
    }

    /**
     * Emit all queued segments as one {@code glBegin(GL_QUADS)} block.
     * No-op if empty. Resets the queue. Disables {@code GL_TEXTURE_2D}
     * before drawing so a stale binding doesn't bleed through; the next
     * textured flush in the same bracket re-enables it. Caller chooses
     * blend mode by wrapping the flush in the appropriate
     * {@link GlStateBracket}.
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

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
