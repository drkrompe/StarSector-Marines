package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — point overlays (rocks, plants,
 * debris) painted above ground/decals/vehicles and below units. Each doodad is a
 * full {@code TILE_SIZE} sub-rect of either the urban tile sheet or the road
 * sheet, drawn at its cell center; the drain batches them per sheet.
 *
 * <p>Migrated from {@code BattleRenderer.renderDoodads} (Story D) — the first
 * sheet-based pass routed through the {@link DrawList}, exercising the
 * {@code QuadBatch} sheet-grouping path in {@link BattleRenderer#drainLayer}.
 */
public final class DoodadRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public DoodadRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        SpriteAPI urban = sprites.tileSheet();
        if (urban == null) return;
        SpriteAPI road = sprites.roadSheet();

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (Doodad d : ctx.sim.getDoodads()) {
            SpriteAPI sheet;
            if (d.fromRoadSheet) {
                if (road == null) continue;
                sheet = road;
            } else {
                sheet = urban;
            }
            TileManifest.TileFrame f = d.tile;
            int srcX = f.col * TileManifest.TILE_SIZE;
            int srcY = f.row * TileManifest.TILE_SIZE;
            float cx = cam.cellToScreenX(d.cellX + 0.5f);
            float cy = cam.cellToScreenY(d.cellY + 0.5f);
            out.add(RenderLayer.DOODADS, new DrawCommand.SheetQuad(
                    sheet,
                    srcX, srcY, TileManifest.TILE_SIZE, TileManifest.TILE_SIZE,
                    cx, cy, cellPx, cellPx,
                    1f, 1f, 1f, alphaMult));
        }
    }
}
