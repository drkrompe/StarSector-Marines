package com.dillon.starsectormarines.render2d;

/**
 * Pure tessellation helpers that expand filled radial shapes (rings, progress
 * arcs) into a fan of screen-space quads in a {@link PolyMesh}. The geometry
 * mechanism behind the {@code POLY} {@link DrawCommand} — knows nothing about
 * markers, factions, or objectives; callers pass center, radii, and color.
 *
 * <p>Consolidates the {@code drawAnnulus} / {@code drawProgressArc} immediate-mode
 * loops that were duplicated across the charge-site and compound marker passes
 * (each was its own private {@code GL_QUADS} fan). Same construction, now
 * emitting deferred command geometry instead of immediate GL.
 */
public final class PolyTess {

    private PolyTess() {}

    /**
     * Append a filled annulus (ring band) centered at {@code (cx, cy)} between
     * {@code innerR} and {@code outerR}, as {@code segments} trapezoid quads
     * around the full circle.
     */
    public static void appendAnnulus(PolyMesh mesh, float cx, float cy,
                                     float innerR, float outerR, int segments,
                                     float r, float g, float b, float a) {
        float prevC = 1f, prevS = 0f;
        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            float ang = t * (float) (Math.PI * 2.0);
            float c = (float) Math.cos(ang);
            float s = (float) Math.sin(ang);
            mesh.appendQuad(
                    cx + prevC * innerR, cy + prevS * innerR,
                    cx + prevC * outerR, cy + prevS * outerR,
                    cx + c * outerR,     cy + s * outerR,
                    cx + c * innerR,     cy + s * innerR,
                    r, g, b, a);
            prevC = c;
            prevS = s;
        }
    }

    /**
     * Append a clockwise-filling progress arc (partial annulus) starting at the
     * 12 o'clock position. {@code progress} in {@code [0,1]} controls the swept
     * fraction; {@code segments} is the full-circle segment count (the arc fills
     * {@code ceil(segments * progress)} of them). No-op for non-positive progress.
     */
    public static void appendArc(PolyMesh mesh, float cx, float cy,
                                 float innerR, float outerR,
                                 float progress, int segments,
                                 float r, float g, float b, float a) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) return;
        int filled = (int) Math.ceil(segments * progress);
        for (int i = 0; i < filled; i++) {
            float t1 = (float) i / segments;
            float t2 = Math.min(progress, (float) (i + 1) / segments);
            float a1 = (float) (Math.PI / 2.0) - t1 * (float) (Math.PI * 2.0);
            float a2 = (float) (Math.PI / 2.0) - t2 * (float) (Math.PI * 2.0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            mesh.appendQuad(
                    cx + c1 * innerR, cy + s1 * innerR,
                    cx + c1 * outerR, cy + s1 * outerR,
                    cx + c2 * outerR, cy + s2 * outerR,
                    cx + c2 * innerR, cy + s2 * innerR,
                    r, g, b, a);
        }
    }
}
