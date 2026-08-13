package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Seam for a per-texel height contribution to combine with the per-cell
 * MACRO height {@link GroundHeightPass} computes.
 *
 * <p>{@link #NONE} is the macro-only fallback (S2's original ship state).
 * {@link GroundMicroHeightSampler} is the real implementation, wired in by
 * {@link GroundParallaxPipeline}: it multiplies/biases the macro value with
 * S1's derived {@code <sheet>_height.png} sheets (sampled at the same source
 * rect the color pass resolves for that cell — see
 * {@code roadmap/surface-relief/overview.md}'s "Macro × micro height"
 * section), falling back to {@code macroHeight} unchanged for any cell it
 * can't resolve a derived sheet for (walls, sliced-sheet kinds, a missing
 * bake) — never crashes, never guesses.
 */
interface HeightSource {

    /** No-op micro contribution — returns {@code macroHeight} unchanged. */
    HeightSource NONE = (macroHeight, topology, gridX, gridY) -> macroHeight;

    /**
     * Combines a cell's macro height with this source's per-texel contribution
     * for cell {@code (gridX, gridY)}. Result is clamped to {@code [0,1]} by
     * the caller. {@code topology} lets an implementation resolve the same
     * ground-kind/wall-mask dispatch {@link GroundRenderSystem} uses to pick
     * the color tile, so the micro sample can come from the matching source
     * rect on that tile's derived height sheet.
     */
    float combine(float macroHeight, CellTopology topology, int gridX, int gridY);
}
