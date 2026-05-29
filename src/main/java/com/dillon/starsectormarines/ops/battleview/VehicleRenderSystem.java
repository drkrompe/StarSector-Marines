package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.MapVehicle;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Emits the {@link RenderLayer#VEHICLES} layer — parked map vehicles (trucks,
 * wrecks) drawn as a single axis-aligned sheet sub-rect at the vehicle's
 * footprint center. Sits above ground/decals and below doodads/units; each
 * vehicle is one {@code SHEET_QUAD}, batched per sheet by the drain.
 *
 * <p>Unrotated — map vehicles sit cardinally on the grid (the frame index
 * encodes facing), so this uses the batched {@code append} path rather than
 * {@code appendRotated}. The frame is aspect-fit into the footprint box exactly
 * as the former inline {@code BattleRenderer.renderVehicles} did; the batch path
 * also drops the old per-frame {@code setColor}/{@code setTex*} mutation of the
 * shared sheet sprite (and its end-of-pass color reset).
 */
public final class VehicleRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public VehicleRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.VEHICLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<MapVehicle> vehicles = ctx.sim.getVehicles();
        if (vehicles.isEmpty()) return;

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (MapVehicle v : vehicles) {
            UnitSpriteCache cache = sprites.vehicleSheets().get(v.kind.sheet);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (v.kind.frameIndex >= cache.frames.frames.length) continue;
            SpriteSheetFrames.Frame f = cache.frames.frames[v.kind.frameIndex];

            float footW = v.kind.footprintCellsX * cellPx;
            float footH = v.kind.footprintCellsY * cellPx;
            float frameAspect = (float) f.w / (float) f.h;
            float footAspect = footW / footH;
            float drawW, drawH;
            if (frameAspect > footAspect) {
                drawW = footW;
                drawH = footW / frameAspect;
            } else {
                drawH = footH;
                drawW = footH * frameAspect;
            }
            float cx = cam.cellToScreenX(v.cellX + v.kind.footprintCellsX / 2f);
            float cy = cam.cellToScreenY(v.cellY + v.kind.footprintCellsY / 2f);
            out.addSheetQuad(RenderLayer.VEHICLES, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, drawW, drawH,
                    1f, 1f, 1f, alphaMult);
        }
    }
}
