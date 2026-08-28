package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — props (rocks, plants, debris,
 * parked road vehicles) painted above ground/decals/aircraft and below units. Each doodad uses
 * the full fixed-grid source rectangle and world rectangle declared by its
 * cell footprint; the drain batches them per sheet. Off-camera footprints are
 * skipped against {@link VisibleCellRect}.
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
        SpriteAPI parkedVehicles = sprites.parkedVehicleSheet();

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        VisibleCellRect view = cam.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                ctx.sim.getGrid().getWidth(), ctx.sim.getGrid().getHeight());

        emitSheet(ctx, out, cam, view, road, TileManifest.ROAD_SHEET, cellPx, alphaMult);
        emitSheet(ctx, out, cam, view, generated, TileManifest.DOODAD_SHEET, cellPx, alphaMult);
        emitSheet(ctx, out, cam, view, parkedVehicles, TileManifest.PARKED_VEHICLE_SHEET,
                cellPx, alphaMult);
        emitSheet(ctx, out, cam, view, urban, TileManifest.SHEET, cellPx, alphaMult);
    }

    private static void emitSheet(RenderContext ctx, DrawList out, BattleCamera cam,
                                  VisibleCellRect view, SpriteAPI sheet, String sheetPath,
                                  float cellPx, float alphaMult) {
        if (sheet == null) return;
        for (Doodad d : ctx.sim.getDoodads()) {
            if (!sheetPath.equals(d.sheetPath)) continue;
            if (!view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            emit(out, cam, sheet, d, cellPx, alphaMult);
        }
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult) {
        // Source rectangle is read at the sheet's own cell size, not the
        // game's. A sheet drawn finer than the grid keeps its detail; one drawn
        // at the grid behaves exactly as before. The quad then stretches that
        // rectangle over the prop's footprint in cells, so what an authored
        // footprint really says is how much deck the art is stretched across.
        TileManifest.TileFrame f = d.tile;
        int cell = d.sourceCellPx;
        int srcX = f.col * cell;
        int srcY = f.row * cell;
        int sourceWidth = cell * d.footprintCellsX;
        int sourceHeight = cell * d.footprintCellsY;
        float cx = cam.cellToScreenX(d.cellX + d.footprintCellsX * 0.5f);
        float cy = cam.cellToScreenY(d.cellY + d.footprintCellsY * 0.5f);
        out.addSheetQuad(RenderLayer.DOODADS, sheet,
                srcX, srcY, sourceWidth, sourceHeight,
                cx, cy, cellPx * d.footprintCellsX, cellPx * d.footprintCellsY,
                1f, 1f, 1f, alphaMult);
    }
}
