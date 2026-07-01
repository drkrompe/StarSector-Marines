package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;

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
    public RenderLayer layer() {
        return RenderLayer.CONVOY;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        long[] ids = ctx.sim.getConvoyVehicleIds();
        if (ids.length == 0) return;
        ConvoyService convoy = ctx.sim.convoy();

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (long id : ids) {
            Vehicle v = convoy.vehicle(id);
            if (v == null || !v.isVisible()) continue;
            VehicleType type = convoy.vehicleType(id);
            GroundBody body = convoy.body(id);
            UnitSpriteCache cache = sprites.convoySprites().get(type);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (type.spriteFrame < 0 || type.spriteFrame >= cache.frames.frames.length) continue;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[type.spriteFrame];

            float frameAspect = (float) f.w / (float) f.h;
            float drawLong = type.visualLengthCells * cellPx;
            float drawShort = drawLong / frameAspect;
            float chassisFacingDeg = body.facingDegrees + type.spriteFacingOffsetDeg;
            float cx = cam.cellToScreenX(body.x);
            float cy = cam.cellToScreenY(body.y);
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, drawLong, drawShort, chassisFacingDeg,
                    1f, 1f, 1f, alphaMult);

            if (type.turretFrame >= 0 && type.turretFrame < frames.frames.length) {
                SpriteSheetFrames.Frame tf = frames.frames[type.turretFrame];
                float turretAspect = (float) tf.w / (float) tf.h;
                float tDrawLong = type.turretVisualCells * cellPx;
                float tDrawShort = tDrawLong / turretAspect;

                // Gated on turretFrame (broader than hasTurretWeapon), so a decorative
                // turret variant may have no GROUND_TURRET state — draw it facing 0.
                GroundTurret turret = convoy.turret(id);
                float turretStateFacing = (turret != null) ? turret.facingDeg : 0f;
                float turretFacingDeg = turretStateFacing + type.turretSpriteFacingOffsetDeg;
                float cRad = (float) Math.toRadians(chassisFacingDeg);
                float cc = (float) Math.cos(cRad);
                float cs = (float) Math.sin(cRad);
                float mountWorldX = type.turretMountX * cc - type.turretMountY * cs;
                float mountWorldY = type.turretMountX * cs + type.turretMountY * cc;
                float tRad = (float) Math.toRadians(turretFacingDeg);
                float tc = (float) Math.cos(tRad);
                float ts = (float) Math.sin(tRad);
                float pivotWorldX = type.turretPivotX * tc - type.turretPivotY * ts;
                float pivotWorldY = type.turretPivotX * ts + type.turretPivotY * tc;
                float drawCellX = body.x + mountWorldX - pivotWorldX;
                float drawCellY = body.y + mountWorldY - pivotWorldY;

                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        tf.x, tf.y, tf.w, tf.h,
                        cam.cellToScreenX(drawCellX), cam.cellToScreenY(drawCellY),
                        tDrawLong, tDrawShort, turretFacingDeg,
                        1f, 1f, 1f, alphaMult);
            }
        }
    }
}
