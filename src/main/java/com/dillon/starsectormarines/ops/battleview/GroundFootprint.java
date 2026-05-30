package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.TileManifest;

import java.awt.Color;

/**
 * Emits the single-cell ground pad painted under a map turret or drone hub — a
 * solid {@code ROAD_FILL} quad that visually seats the structure on the road
 * surface. Layer-agnostic emit helper (the caller picks the layer + supplies the
 * cell's screen-space origin), the footprint analog of {@link HpBarDecor}.
 *
 * <p>The pad color is intrinsic to the footprint and identical for every
 * footprint-drawer, so it lives here rather than per-{@link RenderAppearance}
 * descriptor (see the appearance table's scope note). Drawn as a
 * {@code SOLID_RECT} so a run of footprints coalesces into one
 * {@code SolidQuadBatch} flush in the drain.
 */
public final class GroundFootprint {

    /** Turret/hub pad fill — the shared road color (matches the {@code GroundRenderSystem} road fill). */
    public static final Color ROAD_FILL = new Color(TileManifest.ROAD_FILL_RGB);

    private GroundFootprint() {}

    /**
     * Emits one cell-sized pad whose lower-left corner is the cell's screen-space
     * origin {@code (x0, y0)} (i.e. {@code cellToScreen} of the integer cell, not
     * the cell center).
     */
    public static void emit(DrawList out, RenderLayer layer, float x0, float y0, float cellPx, float alpha) {
        out.addSolidRect(layer, x0, y0, x0 + cellPx, y0 + cellPx,
                ROAD_FILL.getRed() / 255f, ROAD_FILL.getGreen() / 255f, ROAD_FILL.getBlue() / 255f, alpha);
    }
}
