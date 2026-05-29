package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * Emits the {@link RenderLayer#CONVOY} layer — ground convoy vehicles (supply
 * trucks and their optional turret), just under the shuttle layer. Each chassis
 * is one rotated {@code SHEET_QUAD} at the vehicle body center; vehicles that
 * mount a turret emit a second rotated quad at the mount/pivot-resolved position.
 *
 * <p>Rotated sheet sub-rects: unlike VEHICLES (axis-aligned parked vehicles),
 * convoy trucks face along their heading, so this uses the rotated
 * {@code addSheetQuad} overload (drain → {@code QuadBatch.appendRotated}). The
 * chassis/turret kinematics are a faithful port of the former inline
 * {@code BattleRenderer.renderConvoyVehicles}; the batch path drops that pass's
 * per-frame mutation of the shared sheet sprite (sub-rect, color, angle) and its
 * end-of-pass {@code setAngle(0)} reset.
 *
 * <p>The debug overlays the old method dispatched (Reeds-Shepp docking paths,
 * selected-vehicle debug) are own-GL line passes and stay inline in
 * {@code BattleRenderer.renderWorld} after this layer drains.
 */
public final class ConvoyRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public ConvoyRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<Vehicle> convoy = ctx.sim.getConvoyVehicles();
        if (convoy.isEmpty()) return;

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (Vehicle v : convoy) {
            if (!v.isVisible()) continue;
            UnitSpriteCache cache = sprites.convoySprites().get(v.type);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (v.type.spriteFrame < 0 || v.type.spriteFrame >= cache.frames.frames.length) continue;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[v.type.spriteFrame];

            float frameAspect = (float) f.w / (float) f.h;
            float drawLong = v.type.visualLengthCells * cellPx;
            float drawShort = drawLong / frameAspect;
            float chassisFacingDeg = v.body.facingDegrees + v.type.spriteFacingOffsetDeg;
            float cx = cam.cellToScreenX(v.body.x);
            float cy = cam.cellToScreenY(v.body.y);
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, drawLong, drawShort, chassisFacingDeg,
                    1f, 1f, 1f, alphaMult);

            if (v.type.turretFrame >= 0 && v.type.turretFrame < frames.frames.length) {
                SpriteSheetFrames.Frame tf = frames.frames[v.type.turretFrame];
                float turretAspect = (float) tf.w / (float) tf.h;
                float tDrawLong = v.type.turretVisualCells * cellPx;
                float tDrawShort = tDrawLong / turretAspect;

                float turretFacingDeg = v.turretFacingDeg + v.type.turretSpriteFacingOffsetDeg;
                float cRad = (float) Math.toRadians(chassisFacingDeg);
                float cc = (float) Math.cos(cRad);
                float cs = (float) Math.sin(cRad);
                float mountWorldX = v.type.turretMountX * cc - v.type.turretMountY * cs;
                float mountWorldY = v.type.turretMountX * cs + v.type.turretMountY * cc;
                float tRad = (float) Math.toRadians(turretFacingDeg);
                float tc = (float) Math.cos(tRad);
                float ts = (float) Math.sin(tRad);
                float pivotWorldX = v.type.turretPivotX * tc - v.type.turretPivotY * ts;
                float pivotWorldY = v.type.turretPivotX * ts + v.type.turretPivotY * tc;
                float drawCellX = v.body.x + mountWorldX - pivotWorldX;
                float drawCellY = v.body.y + mountWorldY - pivotWorldY;

                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        tf.x, tf.y, tf.w, tf.h,
                        cam.cellToScreenX(drawCellX), cam.cellToScreenY(drawCellY),
                        tDrawLong, tDrawShort, turretFacingDeg,
                        1f, 1f, 1f, alphaMult);
            }
        }
    }
}
