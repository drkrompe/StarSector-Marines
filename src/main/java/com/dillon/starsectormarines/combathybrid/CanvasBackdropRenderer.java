package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.render2d.SolidQuadBatch;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;

import java.util.EnumSet;

/**
 * S0b probe — <b>verified fact 10: render below ships.</b>
 *
 * <p>A {@link CombatLayeredRenderingPlugin} that draws a flat backdrop plate (a
 * dark fill + grid lines) on {@link CombatEngineLayers#BELOW_SHIPS_LAYER}, sized to
 * the sim grid at {@link S0BattleProbe#WORLD_UNITS_PER_CELL} world units per cell
 * and centered on the origin. Stand-in for the real ground-battle tile plate — its
 * only job here is to prove that mod geometry renders <em>under</em> the vanilla
 * ships and their FX, and to make the chosen cell→world scale visible at speed.
 *
 * <p>Renders in world coordinates (the engine has the world transform active when
 * it calls {@link #render}); {@link SolidQuadBatch} emits into whatever projection
 * is current, so the same batch type serves this and the screen-space overlay.
 */
@DebugOnly
public class CanvasBackdropRenderer implements CombatLayeredRenderingPlugin {

    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);

    /** Grid line every N cells. */
    private static final int LINE_EVERY_CELLS = 16;
    private static final float LINE_HALF_THICKNESS = 8f; // world units

    private final float halfWidth;
    private final float halfHeight;
    private final float step;

    private boolean expired;

    public CanvasBackdropRenderer(int gridCellsW, int gridCellsH, float worldUnitsPerCell) {
        this.halfWidth = gridCellsW * worldUnitsPerCell * 0.5f;
        this.halfHeight = gridCellsH * worldUnitsPerCell * 0.5f;
        this.step = LINE_EVERY_CELLS * worldUnitsPerCell;
    }

    @Override
    public void init(CombatEntityAPI entity) {
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void expire() {
        expired = true;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public float getRenderRadius() {
        // Cover the whole plate so the engine never frustum-culls it.
        return (float) Math.hypot(halfWidth, halfHeight) + 100f;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        SolidQuadBatch batch = new SolidQuadBatch(64);

        // Base plate — dark so vanilla ships read clearly on top of it.
        batch.appendRect(-halfWidth, -halfHeight, halfWidth, halfHeight,
                0.06f, 0.07f, 0.09f, 1f);

        // Grid lines (vertical then horizontal) in a faint blue.
        float r = 0.20f, g = 0.32f, b = 0.42f, a = 0.55f;
        for (float x = -halfWidth; x <= halfWidth + 0.5f; x += step) {
            batch.appendRect(x - LINE_HALF_THICKNESS, -halfHeight,
                    x + LINE_HALF_THICKNESS, halfHeight, r, g, b, a);
        }
        for (float y = -halfHeight; y <= halfHeight + 0.5f; y += step) {
            batch.appendRect(-halfWidth, y - LINE_HALF_THICKNESS,
                    halfWidth, y + LINE_HALF_THICKNESS, r, g, b, a);
        }

        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            batch.flush();
        }
    }
}
