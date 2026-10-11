package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Emits the {@link RenderLayer#DOODADS} layer — props (rocks, plants, debris,
 * parked road vehicles) painted above ground/decals/aircraft and below units. Each doodad uses
 * the canonical fixed-grid source rectangle and the placement's rotated world
 * footprint; the drain batches them per sheet. Off-camera footprints are skipped
 * against {@link VisibleCellRect}.
 *
 * <p>Legacy-only maps retain their established sheet grouping. Maps using added
 * catalog sheets preserve placement order: a room lays its deck covering before
 * its fixtures, and grouping an unknown sheet last could hide that machinery.
 * The drain batches adjacent runs on that path.
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
        BattleCamera cam = ctx.camera;
        VisibleCellRect view = cam.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                ctx.sim.getGrid().getWidth(), ctx.sim.getGrid().getHeight());
        collectPlacements(ctx.sim.getDoodads(), out, cam, view, ctx.alphaMult);
    }

    void collectPlacements(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                           VisibleCellRect view, float alphaMult) {
        boolean additionalSheet = false;
        for (Doodad d : placements) {
            if (!isLegacySheet(d.sheetPath)) { additionalSheet = true; break; }
        }
        if (!additionalSheet) {
            // Preserve the existing draw order and texture-bind count for shipped maps.
            emitLegacySheet(placements, out, cam, view, sprites.roadSheet(), TileManifest.ROAD_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.doodadSheet(), TileManifest.DOODAD_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.parkedVehicleSheet(), TileManifest.PARKED_VEHICLE_SHEET, alphaMult);
            emitLegacySheet(placements, out, cam, view, sprites.tileSheet(), TileManifest.SHEET, alphaMult);
            return;
        }
        for (Doodad d : placements) {
            if (!view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            SpriteAPI sheet = switch (d.sheetPath) {
                case TileManifest.SHEET -> sprites.tileSheet();
                case TileManifest.ROAD_SHEET -> sprites.roadSheet();
                case TileManifest.DOODAD_SHEET -> sprites.doodadSheet();
                case TileManifest.PARKED_VEHICLE_SHEET -> sprites.parkedVehicleSheet();
                default -> sprites.extraTerrainSheet(d.sheetPath);
            };
            if (sheet != null) emit(out, cam, sheet, d, cam.cellPxSize(), alphaMult);
        }
    }

    private static boolean isLegacySheet(String path) {
        return path.equals(TileManifest.SHEET) || path.equals(TileManifest.ROAD_SHEET)
                || path.equals(TileManifest.DOODAD_SHEET) || path.equals(TileManifest.PARKED_VEHICLE_SHEET);
    }

    private static void emitLegacySheet(Iterable<Doodad> placements, DrawList out, BattleCamera cam,
                                        VisibleCellRect view, SpriteAPI sheet, String path, float alpha) {
        if (sheet == null) return;
        for (Doodad d : placements) {
            if (path.equals(d.sheetPath)
                    && view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY))
                emit(out, cam, sheet, d, cam.cellPxSize(), alpha);
        }
    }

    private static void emit(DrawList out, BattleCamera cam, SpriteAPI sheet,
                             Doodad d, float cellPx, float alphaMult) {
        // Source rectangle is read at the sheet's own cell size, not the
        // game's. A sheet drawn finer than the grid keeps its detail; one drawn
        // at the grid behaves exactly as before. The quad then stretches that
        // rectangle over the prop's footprint in cells, so what an authored
        // footprint really says is how much deck the art is stretched across.
        // Rotation keeps that source span canonical and turns the destination
        // around the center of the already-rotated tactical footprint.
        TileManifest.TileFrame f = d.tile;
        int cell = d.sourceCellPx;
        int srcX = f.col * cell;
        int srcY = f.row * cell;
        int sourceWidth = cell * d.sourceCellsX;
        int sourceHeight = cell * d.sourceCellsY;
        float cx = cam.cellToScreenX(d.cellX + d.footprintCellsX * 0.5f);
        float cy = cam.cellToScreenY(d.cellY + d.footprintCellsY * 0.5f);
        if (d.mirrored) {
            // A vertical flip through a half turn is a horizontal mirror, which
            // is what a prop drawn from one side needs to face the other way.
            // Rotating it instead lights it from underneath.
            out.addSheetQuadFlippedV(RenderLayer.DOODADS, sheet,
                    srcX, srcY, sourceWidth, sourceHeight,
                    cx, cy, cellPx * d.sourceCellsX, cellPx * d.sourceCellsY,
                    d.quarterTurns * 90f + 180f,
                    1f, 1f, 1f, alphaMult);
            return;
        }
        out.addSheetQuad(RenderLayer.DOODADS, sheet,
                srcX, srcY, sourceWidth, sourceHeight,
                cx, cy, cellPx * d.sourceCellsX, cellPx * d.sourceCellsY,
                d.quarterTurns * 90f,
                1f, 1f, 1f, alphaMult);
    }
}
