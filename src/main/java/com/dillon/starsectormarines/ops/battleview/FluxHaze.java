package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Flux made visible on a body, as a starship shows it: a purple haze over the vents that thickens as flux builds, with
 * wisps drifting aft off them, and a bright lavender plume while the body is overloaded or venting. One look for a
 * ground hull's gun ({@code convoy-nouns.md}) and a mech's reservoir ({@code mechs-nouns.md}), so the two read as the
 * same thing. Drawn from simulation time alone, so a review frame shows it as the screen does.
 */
final class FluxHaze {

    /** The haze at full flux and the vent plume: purple, then lavender. */
    static final float[] HAZE = {0.58f, 0.30f, 1.00f};
    static final float[] VENT = {0.82f, 0.68f, 1.00f};
    /** Wisps rising off the vents; each rides its own phase of the drift. */
    static final int WISPS = 4;
    /** Below this share of capacity nothing is drawn. */
    static final float THRESHOLD = 0.02f;

    private FluxHaze() {}

    /** A mech's vents sit this many hull widths behind the waist, across the torso's back. */
    static final float MECH_VENT_AFT_HULLS = 0.30f;
    /** Half the spread of a mech's vents across its back, in hull widths. */
    static final float MECH_VENT_HALF_SPAN_HULLS = 0.26f;
    /** The hull width, in cells, at which a mech's glows draw at a ground hull's size. */
    static final float MECH_REFERENCE_HULL_CELLS = 1.4f;

    /** How strongly the haze draws at {@code level} of capacity: rising with its square, and full while venting. */
    static float strength(float level, boolean venting) {
        if (!venting && level <= THRESHOLD) return 0f;
        float l = Math.max(0f, Math.min(1f, level));
        return venting ? 0.55f + 0.45f * l : l * l;
    }

    /**
     * Draws the haze over vents centred at {@code (ventX, ventY)} cells on a body facing {@code facingDeg} (hull-local
     * +Y is the nose, so the wisps drift toward -Y), spread {@code halfSpanCells} either side. {@code scale} sizes the
     * glows against a ground hull's (1).
     */
    static void collect(DrawList out, RenderLayer layer, SpriteAPI glow, BattleCamera cam, float cellPx,
                        float ventX, float ventY, float facingDeg, float halfSpanCells, float scale,
                        float level, boolean venting, float timeSeconds, float alphaMult) {
        float strength = strength(level, venting);
        if (strength <= 0f || glow == null) return;
        float[] colour = venting ? VENT : HAZE;
        float t = timeSeconds;
        float rad = (float) Math.toRadians(facingDeg);
        float fwdX = -(float) Math.sin(rad);
        float fwdY = (float) Math.cos(rad);
        float sideX = (float) Math.cos(rad);
        float sideY = (float) Math.sin(rad);
        float span = halfSpanCells;
        for (int i = -1; i <= 1; i++) {
            float flicker = 0.85f + 0.15f * (float) Math.sin(t * 9f + i * 2.1f);
            float size = (venting ? 1.5f : 1.05f) * cellPx * scale;
            out.addAdditiveSprite(layer, glow,
                    cam.cellToScreenX(ventX + sideX * span * 0.7f * i), cam.cellToScreenY(ventY + sideY * span * 0.7f * i),
                    size, size, 0f, colour[0], colour[1], colour[2], 0.5f * strength * flicker * alphaMult);
        }
        float drift = venting ? 1.6f : 0.55f;
        for (int w = 0; w < WISPS; w++) {
            float phase = (t * drift + w / (float) WISPS) % 1f;
            float lateral = span * (float) Math.sin(w * 2.4f + t * 0.7f) * 0.8f;
            float aft = scale * ((venting ? 0.4f : 0.2f) + phase * (venting ? 1.8f : 0.9f));
            float wx = ventX - fwdX * aft + sideX * lateral;
            float wy = ventY - fwdY * aft + sideY * lateral;
            float size = cellPx * scale * (0.7f + phase * (venting ? 1.6f : 0.9f));
            float fade = (1f - phase) * Math.min(1f, phase * 4f);
            out.addAdditiveSprite(layer, glow, cam.cellToScreenX(wx), cam.cellToScreenY(wy),
                    size, size, 0f, colour[0], colour[1], colour[2], 0.4f * strength * fade * alphaMult);
        }
    }

    /**
     * A mech's reservoir ({@link MechFlux}): the haze over the vents across the torso's back, which turn with the
     * torso, and the lavender plume while it vents. {@code (waistX, waistY)} is the torso's pivot in cells.
     */
    static void collectMech(DrawList out, SpriteAPI glow, BattleCamera cam, float cellPx,
                            float waistX, float waistY, float torsoFacingDeg, float hullWidthCells,
                            MechFlux flux, float timeSeconds, float alphaMult) {
        if (flux == null || flux.capacity() <= 0f) return;
        float level = Math.min(1f, flux.value() / flux.capacity());
        if (strength(level, flux.venting()) <= 0f) return;
        float rad = (float) Math.toRadians(torsoFacingDeg);
        float aft = MECH_VENT_AFT_HULLS * hullWidthCells;
        collect(out, RenderLayer.UNITS, glow, cam, cellPx,
                waistX + (float) Math.sin(rad) * aft, waistY - (float) Math.cos(rad) * aft, torsoFacingDeg,
                MECH_VENT_HALF_SPAN_HULLS * hullWidthCells, hullWidthCells / MECH_REFERENCE_HULL_CELLS,
                level, flux.venting(), timeSeconds, alphaMult);
    }
}
