package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — point overlays (rocks, plants,
 * debris) painted above ground/decals/vehicles and below units. Each doodad uses
 * the full fixed-grid source rectangle and world rectangle declared by its
 * cell footprint; the drain batches them per sheet.
 *
 * <p>Emitted in two passes — road-sheet doodads first, then urban — so each sheet
 * forms one contiguous run for the strict-painter drain (one batch flush per
 * sheet). Road-under-urban matches the original {@code renderDoodads} flush order;
 * doodads do not overlap across sheets, so the order is not load-bearing.
 */
public final class DoodadRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public DoodadRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        SpriteAPI urban = sprites.tileSheet();
        if (urban == null) return;
        SpriteAPI road = sprites.roadSheet();
        SpriteAPI generated = sprites.doodadSheet();

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        emitSheet(ctx, out, cam, road, TileManifest.ROAD_SHEET, cellPx, alphaMult);
        emitSheet(ctx, out, cam, generated, TileManifest.DOODAD_SHEET, cellPx, alphaMult);
        emitSheet(ctx, out, cam, urban, TileManifest.SHEET, cellPx, alphaMult);
    }

    private static void emitSheet(RenderContext ctx, DrawList out, BattleCamera cam,
                                  SpriteAPI sheet, String sheetPath,
                                  float cellPx, float alphaMult) {
        if (sheet == null) return;
        for (Doodad d : ctx.sim.getDoodads()) {
            if (sheetPath.equals(d.sheetPath)) {
                emit(out, cam, sheet, d, cellPx, alphaMult);
            }
        }
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult) {
        TileManifest.TileFrame f = d.tile;
        int srcX = f.col * TileManifest.TILE_SIZE;
        int srcY = f.row * TileManifest.TILE_SIZE;
        int sourceWidth = TileManifest.TILE_SIZE * d.footprintCellsX;
        int sourceHeight = TileManifest.TILE_SIZE * d.footprintCellsY;
        float cx = cam.cellToScreenX(d.cellX + d.footprintCellsX * 0.5f);
        float cy = cam.cellToScreenY(d.cellY + d.footprintCellsY * 0.5f);
        out.addSheetQuad(RenderLayer.DOODADS, sheet,
                srcX, srcY, sourceWidth, sourceHeight,
                cx, cy, cellPx * d.footprintCellsX, cellPx * d.footprintCellsY,
                1f, 1f, 1f, alphaMult);
    }
}
