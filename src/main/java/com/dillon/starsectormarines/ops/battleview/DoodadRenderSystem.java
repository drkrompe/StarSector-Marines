package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — point overlays (rocks, plants,
 * debris) painted above ground/decals/vehicles and below units. Each doodad is a
 * full {@code TILE_SIZE} sub-rect of either the urban tile sheet or the road
 * sheet, drawn at its cell center; the drain batches them per sheet.
 *
 * <p>Emitted in two passes — road-sheet doodads first, then urban — so each sheet
 * forms one contiguous run for the strict-painter drain (one batch flush per
 * sheet). Road-under-urban matches the original {@code renderDoodads} flush order;
 * doodads are one-per-cell point overlays with no cross-sheet overlap, so the
 * order is not load-bearing.
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

        if (road != null) {
            for (Doodad d : ctx.sim.getDoodads()) {
                if (d.fromRoadSheet) emit(out, cam, road, d, cellPx, alphaMult);
            }
        }
        for (Doodad d : ctx.sim.getDoodads()) {
            if (!d.fromRoadSheet) emit(out, cam, urban, d, cellPx, alphaMult);
        }
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult) {
        TileManifest.TileFrame f = d.tile;
        int srcX = f.col * TileManifest.TILE_SIZE;
        int srcY = f.row * TileManifest.TILE_SIZE;
        float cx = cam.cellToScreenX(d.cellX + 0.5f);
        float cy = cam.cellToScreenY(d.cellY + 0.5f);
        out.addSheetQuad(RenderLayer.DOODADS, sheet,
                srcX, srcY, TileManifest.TILE_SIZE, TileManifest.TILE_SIZE,
                cx, cy, cellPx, cellPx,
                1f, 1f, 1f, alphaMult);
    }
}
