package com.dillon.starsectormarines.combathybrid;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleSprites;
import com.dillon.starsectormarines.ops.battleview.RenderContext;
import com.dillon.starsectormarines.ops.battleview.RenderLayer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;

import java.util.EnumSet;

/**
 * S3b — draws the <b>real ground scene</b> (terrain + building structures) under the
 * vanilla ships, on {@link CombatEngineLayers#BELOW_SHIPS_LAYER}. Replaces the S0b/S2
 * placeholder grid plate ({@code CanvasBackdropRenderer}) with the mod's actual tile
 * renderer.
 *
 * <p><b>The render-target seam.</b> The ground renderer ({@link BattleRenderer}) is a
 * collect→drain pipeline: each pass turns sim cells into coordinates via a
 * {@link BattleCamera} and emits draw commands; the drain replays them into whatever
 * GL projection is active. The <em>only</em> thing binding it to the standalone screen
 * view is the camera's transform — and a {@code BattleCamera} is a generic cell→affine
 * map, parameterized by viewport + cell size. Configured with a <b>world-unit
 * viewport</b> centered on the origin ({@link #worldCamera()}), it emits
 * {@code (cell − grid/2)·worldUnitsPerCell} — exactly the combat world coords the
 * proxies use ({@link GroundSimBridge}). So retargeting the whole renderer to the
 * combat layer is just "configure the camera with a world viewport"; no fork of the
 * pass code.
 *
 * <p>This host runs only the projection-agnostic terrain + structure layers
 * ({@link RenderLayer#GROUND}, {@link RenderLayer#DOODADS}, {@link RenderLayer#ROOFS})
 * via {@link BattleRenderer#renderWorld(RenderContext, EnumSet)}. The FBO-backed
 * accumulators (decals, lighting) and the screen-coupled overlays (fog, highlights,
 * units, FX) are left out — units are already shown by the proxy markers, and the
 * accumulators blit in screen space (S3b scope: terrain + structures only).
 *
 * <p>Pan/zoom come free: the combat free-cam ({@code SpectatorCanvasPlugin}) moves the
 * combat world projection, which moves where our world-coord geometry lands — so the
 * backdrop camera stays a static cell→world map.
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public class GroundSceneBackdrop implements CombatLayeredRenderingPlugin {

    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);

    /** Terrain + building structure only — the projection-agnostic subset. */
    private static final EnumSet<RenderLayer> SCENE_LAYERS =
            EnumSet.of(RenderLayer.GROUND, RenderLayer.DOODADS, RenderLayer.ROOFS);

    private final BattleSimulation sim;
    private final int gridW;
    private final int gridH;
    private final float worldUnitsPerCell;
    private final float renderRadius;

    private BattleSprites sprites;
    private BattleRenderer renderer;
    private BattleCamera worldCamera;
    private boolean ready;
    private boolean expired;

    public GroundSceneBackdrop(BattleSimulation sim, int gridW, int gridH, float worldUnitsPerCell) {
        this.sim = sim;
        this.gridW = gridW;
        this.gridH = gridH;
        this.worldUnitsPerCell = worldUnitsPerCell;
        this.renderRadius = (float) Math.hypot(gridW, gridH) * worldUnitsPerCell * 0.5f + 200f;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (sim == null) return;
        if (!ready) initOnGlThread();          // sprite loads need the GL thread → first render()
        if (renderer == null) return;          // sheets unavailable; nothing to draw

        RenderContext rc = new RenderContext(
                sim, worldCamera, /*layout*/ null, /*alphaMult*/ 1f, /*realDt*/ 0f,
                /*debugZonesVisible*/ false, /*highlights*/ null, /*selection*/ null);
        renderer.renderWorld(rc, SCENE_LAYERS);
    }

    /** One-time setup on the GL thread: load terrain/structure sheets, build batches, configure the camera. */
    private void initOnGlThread() {
        ready = true;
        sprites = new BattleSprites();
        sprites.ensureTileSheet();
        sprites.ensureRoadSheet();
        sprites.ensureFloorsSheet();
        sprites.ensureNatureSheet();
        sprites.ensureWaterSheet();
        sprites.ensureUrbanTile3Sheet();

        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();

        worldCamera = worldCamera();
    }

    /**
     * A {@link BattleCamera} configured as a cell→world transform: a world-unit
     * viewport centered on the origin, with {@code worldUnitsPerCell} as the cell
     * size. {@code cellToScreenX(c) = vpX + vpW/2 + (c − panCellX)·cellPx}; with
     * {@code vpX = −gridW·u/2}, {@code vpW = gridW·u}, default pan = grid center,
     * zoom 1, this is {@code (c − gridW/2)·u} — origin-centered world coords, with
     * the cull range + clamp invariants intact (it's the same affine map, just over
     * a world-unit viewport instead of a screen-pixel one).
     */
    private BattleCamera worldCamera() {
        BattleCamera cam = new BattleCamera(gridW, gridH);
        cam.setViewport(-gridW * worldUnitsPerCell * 0.5f, -gridH * worldUnitsPerCell * 0.5f,
                gridW * worldUnitsPerCell, gridH * worldUnitsPerCell, worldUnitsPerCell);
        return cam;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public float getRenderRadius() {
        return renderRadius;
    }

    @Override public void init(CombatEntityAPI entity) {}
    @Override public void cleanup() {}
    @Override public boolean isExpired() { return expired; }
    public void expire() { expired = true; }
    @Override public void advance(float amount) {}
}
