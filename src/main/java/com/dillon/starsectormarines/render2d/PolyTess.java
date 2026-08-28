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
     * Append a band of annulus centred on {@code centerDegrees} and spanning
     * {@code sweepDegrees} of arc, as {@code segments} trapezoid quads.
     *
     * <p>Angles are ordinary mathematical degrees in the caller's screen frame
     * ({@code 0} along {@code +x}, increasing toward {@code +y}); the caller
     * converts from whatever facing convention it holds. Distinct from
     * {@link #appendArc}, which sweeps a <em>fraction</em> of a full circle from
     * a fixed twelve o'clock start: this one is told where to point and how wide
     * to be, which is what a drawn arc of protection needs — its width is a
     * property of the thing being drawn rather than a progress readout.
     *
     * <p>{@code segments} is the count across this sweep, so a wide arc and a
     * narrow one drawn with the same value stay equally smooth. No-op for a
     * non-positive sweep.
     */
    public static void appendSector(PolyMesh mesh, float cx, float cy,
                                    float innerR, float outerR,
                                    float centerDegrees, float sweepDegrees, int segments,
                                    float r, float g, float b, float a) {
        if (!(sweepDegrees > 0f) || segments < 1 || !(outerR > innerR)) return;
        double start = Math.toRadians(centerDegrees - sweepDegrees * 0.5f);
        double step = Math.toRadians(sweepDegrees) / segments;
        float prevC = (float) Math.cos(start);
        float prevS = (float) Math.sin(start);
        for (int i = 1; i <= segments; i++) {
            double angle = start + step * i;
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
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
     * Append one radial spoke at {@code degrees} — the same mathematical-degree
     * frame {@link #appendSector} uses — {@code widthPx} across, running from
     * {@code innerR} to {@code outerR}. Used to mark exactly where a drawn arc
     * stops, which on a screen that only covers one facing is the single most
     * load-bearing thing in the picture.
     */
    public static void appendRadialSpoke(PolyMesh mesh, float cx, float cy,
                                         float innerR, float outerR,
                                         float degrees, float widthPx,
                                         float r, float g, float b, float a) {
        if (!(outerR > innerR) || !(widthPx > 0f)) return;
        double angle = Math.toRadians(degrees);
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);
        float halfX = -dirY * widthPx * 0.5f;
        float halfY = dirX * widthPx * 0.5f;
        mesh.appendQuad(
                cx + dirX * innerR - halfX, cy + dirY * innerR - halfY,
                cx + dirX * innerR + halfX, cy + dirY * innerR + halfY,
                cx + dirX * outerR + halfX, cy + dirY * outerR + halfY,
                cx + dirX * outerR - halfX, cy + dirY * outerR - halfY,
                r, g, b, a);
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
