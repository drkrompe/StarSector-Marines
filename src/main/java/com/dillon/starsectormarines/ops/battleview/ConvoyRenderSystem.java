package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

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
 * <p>A vehicle is a unit with a hull: it carries the same structure and armor
 * capacities as anything else that can be shot, so it wears the same ownership-coded
 * {@link DurabilityBarDecor} gauge, emitted last in the layer so bars paint over
 * every chassis. A wreck is exempt — its bar would read empty forever, and the
 * registered wreck art already says what happened to it.
 *
 * <p>The debug overlays the old method dispatched (Reeds-Shepp docking paths,
 * selected-vehicle debug) are own-GL line passes and stay inline in
 * {@code BattleRenderer.renderWorld} after this layer drains.
 */
public final class ConvoyRenderSystem implements RenderSystem {

    /** How far a hover hull's shadow lies from it, down and to the right of a light from the upper left, in cells. */
    static final float HOVER_SHADOW_CELLS = .35f;

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
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible()) continue;
            VehicleType type = convoy.vehicleType(id);
            GroundBody body = convoy.body(id);
            boolean wrecked = v.state == VehicleState.WRECKED;
            UnitSpriteCache cache = sprites.convoySprites().get(type);
            if (cache == null || cache.sheet == null || cache.frames == null) continue;
            if (type.spriteFrame < 0 || type.spriteFrame >= cache.frames.frames.length) continue;
            SpriteSheetFrames frames = cache.frames;
            SpriteSheetFrames.Frame f = frames.frames[wrecked ? type.wreckHullFrame() : type.spriteFrame];

            float frameAspect = (float) f.w / (float) f.h;
            float drawLong = type.visualLengthCells * cellPx;
            float drawShort = drawLong / frameAspect;
            float chassisFacingDeg = body.facingDegrees + type.spriteFacingOffsetDeg;
            float cx = cam.cellToScreenX(body.x);
            float cy = cam.cellToScreenY(body.y);
            if (type.isHover() && !wrecked) {
                // A hover hull rides clear of the ground, so its shadow lies apart from it, away from the light.
                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        f.x, f.y, f.w, f.h,
                        cam.cellToScreenX(body.x + HOVER_SHADOW_CELLS), cam.cellToScreenY(body.y - HOVER_SHADOW_CELLS),
                        drawLong * 1.02f, drawShort * 1.02f, chassisFacingDeg,
                        0f, 0f, 0f, alphaMult * .38f);
            }
            float[] tint = type.hullTint();
            out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, drawLong, drawShort, chassisFacingDeg,
                    tint[0], tint[1], tint[2], alphaMult);

            if (type.hasAnimatedTreads() && !wrecked) VehicleTreadRender.collect(type, cache, body, cam, alphaMult, out);
            if (!wrecked && type.fluxVentAftCells() > 0f) {
                collectFluxHaze(ctx, type, convoy.turret(id), body, cam, cellPx, alphaMult, out);
            }

            if (type.turretArtFromMount()) {
                collectMountTurret(type, convoy.turret(id), body, wrecked, cam, cellPx, alphaMult, out);
            } else if (type.turretFrame >= 0 && type.turretFrame < frames.frames.length) {
                // Gated on turretFrame (broader than hasTurretWeapon), so a decorative
                // turret variant may have no GROUND_TURRET state — draw it facing 0.
                GroundTurret turret = convoy.turret(id);
                int turretFrame = wrecked ? type.wreckTurretFrame() : type.turretFrame;
                if (!wrecked && type.turretFiringFrame() >= 0 && turret != null && engaged(turret)) {
                    turretFrame = type.turretFiringFrame();
                }
                SpriteSheetFrames.Frame tf = frames.frames[turretFrame];
                float turretAspect = (float) tf.w / (float) tf.h;
                float tDrawLong = type.turretVisualCells * cellPx;
                float tDrawShort = tDrawLong / turretAspect;

                float turretStateFacing = (turret != null) ? turret.facingDeg : 0f;
                float turretFacingDeg = turretStateFacing + type.turretSpriteFacingOffsetDeg;
                float cRad = (float) Math.toRadians(body.facingDegrees);
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

                if (!wrecked && type.turretRecoilCells() > 0f) {
                    float recoil = 0f;
                    for (var shot : ctx.sim.getActiveShots()) if (shot.shooterId == id) {
                        float age = shot.lifetimeMax - shot.lifetime;
                        recoil = Math.max(recoil, type.turretRecoilCells() * Math.max(0f, 1f - age / .22f));
                    }
                    float bearing = (float) Math.toRadians(turretStateFacing);
                    drawCellX += (float) Math.sin(bearing) * recoil;
                    drawCellY -= (float) Math.cos(bearing) * recoil;
                }
                out.addSheetQuad(RenderLayer.CONVOY, cache.sheet,
                        tf.x, tf.y, tf.w, tf.h,
                        cam.cellToScreenX(drawCellX), cam.cellToScreenY(drawCellY),
                        tDrawLong, tDrawShort, turretFacingDeg,
                        1f, 1f, 1f, alphaMult);
            }

        }

        if (ctx.hostProfile.unitDecorationsVisible()) {
            sweepDurabilityBars(ids, convoy, cam, cellPx, alphaMult, out);
        }
    }

    /** The haze over a hull's vents at full flux, and the vent plume of an overloaded gun: purple, then lavender. */
    private static final float[] FLUX_HAZE = {0.58f, 0.30f, 1.00f};
    private static final float[] FLUX_VENT = {0.82f, 0.68f, 1.00f};
    /** Wisps rising off the vents; each rides its own phase of the drift. */
    private static final int FLUX_WISPS = 4;

    /**
     * The gun's flux made visible, as a starship shows it: a purple haze over the hull's vents that thickens as flux
     * builds ({@code TurretMountDef#flux}), with wisps drifting aft off the vents, and a bright lavender plume while
     * an overloaded gun vents. Drawn from simulation time alone, so a review frame shows it as the screen does.
     */
    private void collectFluxHaze(RenderContext ctx, VehicleType type, GroundTurret turret, GroundBody body,
                                 BattleCamera cam, float cellPx, float alphaMult, DrawList out) {
        if (turret == null) return;
        StructureDef structure = type.turretStructure();
        if (structure == null || structure.mount.flux == null) return;
        float level = Math.min(1f, turret.flux / structure.mount.flux.capacity());
        if (level <= 0.02f) return;
        sprites.ensureEngineFxSprites();
        SpriteAPI glow = sprites.engineGlowSprite();
        if (glow == null) return;
        boolean venting = turret.overloaded;
        float[] colour = venting ? FLUX_VENT : FLUX_HAZE;
        float strength = venting ? 0.55f + 0.45f * level : level * level;
        float t = ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT;
        // Hull-local +Y is the nose (the turret mount's own transform); aft is -Y.
        float rad = (float) Math.toRadians(body.facingDegrees);
        float fwdX = -(float) Math.sin(rad);
        float fwdY = (float) Math.cos(rad);
        float sideX = (float) Math.cos(rad);
        float sideY = (float) Math.sin(rad);
        float ventX = body.x - fwdX * type.fluxVentAftCells();
        float ventY = body.y - fwdY * type.fluxVentAftCells();
        float span = type.fluxVentHalfSpanCells();
        for (int i = -1; i <= 1; i++) {
            float flicker = 0.85f + 0.15f * (float) Math.sin(t * 9f + i * 2.1f);
            float size = (venting ? 1.5f : 1.05f) * cellPx;
            out.addAdditiveSprite(RenderLayer.CONVOY, glow,
                    cam.cellToScreenX(ventX + sideX * span * 0.7f * i), cam.cellToScreenY(ventY + sideY * span * 0.7f * i),
                    size, size, 0f, colour[0], colour[1], colour[2], 0.5f * strength * flicker * alphaMult);
        }
        float drift = venting ? 1.6f : 0.55f;
        for (int w = 0; w < FLUX_WISPS; w++) {
            float phase = (t * drift + w / (float) FLUX_WISPS) % 1f;
            float lateral = span * (float) Math.sin(w * 2.4f + t * 0.7f) * 0.8f;
            float aft = (venting ? 0.4f : 0.2f) + phase * (venting ? 1.8f : 0.9f);
            float wx = ventX - fwdX * aft + sideX * lateral;
            float wy = ventY - fwdY * aft + sideY * lateral;
            float size = cellPx * (0.7f + phase * (venting ? 1.6f : 0.9f));
            float fade = (1f - phase) * Math.min(1f, phase * 4f);
            out.addAdditiveSprite(RenderLayer.CONVOY, glow, cam.cellToScreenX(wx), cam.cellToScreenY(wy),
                    size, size, 0f, colour[0], colour[1], colour[2], 0.4f * strength * fade * alphaMult);
        }
    }

    /** A gun with a target, ground or air, or a burst under way: what a firing frame is drawn for. */
    private static boolean engaged(GroundTurret turret) {
        return turret.targetId != 0L || turret.airTargetId != 0L || turret.burstRemaining > 0;
    }

    /**
     * Durability bars for every live convoy vehicle, run <b>last</b> in the layer
     * so a bar is never painted over by a neighbouring chassis — the same
     * per-stratum sweep the UNITS layer uses rather than per-entity decoration.
     *
     * <p>Vehicles are always authored with both capacities, so this always emits the
     * armored two-row bar. Placement follows the shared rule: the gauge spans the
     * body it belongs to and sits that body's half-extent above its center. A hull
     * is measured by its longest side so the bar clears the sprite at every
     * heading instead of sinking into it broadside-on.
     */
    private static void sweepDurabilityBars(long[] ids, ConvoyService convoy,
                                            BattleCamera cam, float cellPx,
                                            float alphaMult, DrawList out) {
        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible() || v.state == VehicleState.WRECKED) continue;
            VehicleType type = convoy.vehicleType(id);
            GroundBody body = convoy.body(id);
            float bodyPx = Math.max(type.visualLengthCells, type.visualWidthCells) * cellPx;
            float cx = cam.cellToScreenX(body.x);
            float barY = cam.cellToScreenY(body.y) + bodyPx / 2f + BattleRenderer.HP_BAR_GAP;
            DurabilityBarDecor.emit(out, RenderLayer.CONVOY,
                    Allegiance.of(convoy.faction(id)), cx, barY, bodyPx,
                    convoy.structure(id), convoy.maxStructure(id),
                    convoy.armor(id), convoy.maxArmor(id), alphaMult);
        }
    }

    /**
     * A roof turret drawn from its catalog mount's own sprite, the way an aircraft's hull
     * turrets are: at the mount's intrinsic size, base over barrel, darkened on a wreck.
     */
    private void collectMountTurret(VehicleType type, GroundTurret turret, GroundBody body, boolean wrecked,
                                    BattleCamera cam, float cellPx, float alphaMult, DrawList out) {
        StructureDef structure = type.turretStructure();
        if (structure == null) return;
        ShuttleSpriteCache base = sprites.turretSprites().get(structure.id);
        if (base == null) return;
        ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(structure.id);
        float facing = turret != null ? turret.facingDeg : body.facingDegrees;
        float cRad = (float) Math.toRadians(body.facingDegrees);
        float cc = (float) Math.cos(cRad);
        float cs = (float) Math.sin(cRad);
        float mountX = body.x + type.turretMountX * cc - type.turretMountY * cs;
        float mountY = body.y + type.turretMountX * cs + type.turretMountY * cc;
        float heightPx = structure.mount.visualCells * cellPx;
        float shade = wrecked ? .35f : 1f;
        float sx = cam.cellToScreenX(mountX);
        float sy = cam.cellToScreenY(mountY);
        if (barrel != null) {
            out.addSprite(RenderLayer.CONVOY, barrel.sprite, sx, sy, heightPx * barrel.aspect, heightPx,
                    facing, shade, shade, shade, alphaMult);
        }
        out.addSprite(RenderLayer.CONVOY, base.sprite, sx, sy, heightPx * base.aspect, heightPx,
                facing, shade, shade, shade, alphaMult);
    }
}
