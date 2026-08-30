package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the {@link RenderLayer#SHUTTLES} layer — aircraft (shuttles) and their
 * mounted turrets, above the ground/unit/roof stack so they pierce the fog-roof.
 *
 * <p>Each shuttle emits, in submission (= paint) order: a {@code CUSTOM} engine-FX
 * pass (own-GL, drawn under the hull), a {@code SPRITE} hull (whole rotated
 * sprite), then per mounted turret an optional recoil-displaced barrel
 * {@code SPRITE} and the base {@code SPRITE}. Faithful port of the former inline
 * {@code BattleRenderer.renderShuttles}/{@code renderShuttleEngines}/
 * {@code renderShuttleTurrets}; whole-texture sprites use the non-batched
 * {@code SPRITE} command (which resets sprite angle after each draw, so the old
 * end-of-pass {@code setAngle(0)} reset loop is gone).
 *
 * <p>Engine FX manages its own GL bracket, so it rides the drain's {@code CUSTOM}
 * escape hatch rather than the textured-quad bracket.
 */
public final class ShuttleRenderSystem implements RenderSystem {

    private final BattleSprites sprites;

    public ShuttleRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.SHUTTLES;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        long[] airIds = ctx.sim.getAirEntityIds();
        if (airIds.length == 0) return;

        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (long id : airIds) {
            ShuttleMission mission = world.mission(id);
            if (mission == null || !mission.isOnMap()) continue;
            Airframe frame = world.airframe(id);
            ShuttleSpriteCache cache = sprites.airframeSprites().get(frame);
            if (cache == null) continue;
            AirBody body = world.kinematics(id);

            // Authored render-state is a world component now (read by id); the
            // scale + altitude offset are pure derivations of altitudeT/flightPhase.
            float altitudeT = world.altitudeT(id);
            float scaleMult = AirAppearance.scaleMult(altitudeT, world.flightPhase(id));
            float altOffset = AirAppearance.visualAltitudeOffsetCells(altitudeT);
            float engineFxIntensity = AirAppearance.thrusterPlume(mission.state, altitudeT);
            float[] thrusterGlow = ctx.sim.getThrusterGlow(id);

            // Engine FX (own GL) under the hull. The per-slot demand (smoothed
            // each sim tick by AirSystem's ThrusterFxSystem) blooms the thrusters
            // actually pushing / turning the hull and ramps instead of snapping.
            out.addCustom(RenderLayer.SHUTTLES, () -> EngineFxRenderer.draw(
                    EngineSlotResolver.resolve(frame),
                    body.x, body.y,
                    body.facingDegrees,
                    scaleMult,
                    altOffset,
                    engineFxIntensity,
                    alphaMult,
                    cam,
                    sprites.engineGlowSprite(), sprites.engineFlameSprite(),
                    thrusterGlow));

            // Hull. Length is derived from the hull's sprite pixel extent via
            // the one global pixel-density factor (HullFootprintResolver), not a
            // hand-authored per-type value.
            float hullLenCells = HullFootprintResolver.visualLengthCells(frame.renderHullId());
            float pxLen = hullLenCells * cellPx * scaleMult;
            float pxH = pxLen;
            float pxW = pxLen * cache.aspect;
            // Anchor the hull at its centre of gravity: offset the sprite's pixel
            // centre from body by the pivot (sprite-pixel-centre relative to the
            // authored `center`), rotated by facing and scaled by the altitude
            // zoom. This keeps `center` fixed at body so the hull rotates about
            // its CoG — and, since body == CoG, the center-relative turret and
            // engine slots land on their painted hardpoints.
            float[] pivot = HullPivotResolver.pivotOffset(frame.renderHullId());
            float rad = (float) Math.toRadians(body.facingDegrees);
            float pc = (float) Math.cos(rad);
            float psn = (float) Math.sin(rad);
            float pvx = pivot[0] * scaleMult;
            float pvy = pivot[1] * scaleMult;
            float cx = cam.cellToScreenX(body.x + (pvx * pc - pvy * psn));
            float cy = cam.cellToScreenY(body.y + (pvx * psn + pvy * pc) + altOffset);
            out.addSprite(RenderLayer.SHUTTLES, cache.sprite,
                    cx, cy, pxW, pxH, body.facingDegrees,
                    1f, 1f, 1f, alphaMult);

            emitTurrets(out, cam, cellPx, alphaMult, body, scaleMult, altOffset,
                    ctx.sim.getAirTurretMounts(id));

            // Durability, for as long as it is on the map — in the air as much
            // as on its wheels. An aircraft used to carry a bar only while it
            // was a parked grid unit, so the whole airborne part of its life,
            // which is the part somebody is shooting at, showed nothing.
            emitDurability(out, cx, cy, pxH, pxW, alphaMult,
                    world.airFaction(id), mission.hp, frame.maxHp());
        }
    }

    /**
     * Draws the aircraft's structure bar under its hull.
     *
     * <p>Sized and placed off the <em>drawn</em> hull rather than off the cell
     * grid, so the bar tracks the altitude zoom and the altitude Y-offset
     * instead of sitting under where the aircraft would be if it were on the
     * ground. An airframe with no stated maximum draws nothing, which is
     * {@link DurabilityBarDecor}'s own rule rather than a second one here.
     */
    private static void emitDurability(DrawList out, float cx, float cy,
                                       float pxH, float pxW, float alphaMult,
                                       Faction faction, float hp, float maxHp) {
        float barY = cy + pxH / 2f + BattleRenderer.HP_BAR_GAP;
        DurabilityBarDecor.emit(out, RenderLayer.SHUTTLES, Allegiance.of(faction),
                cx, barY, pxW, hp, maxHp, alphaMult);
    }

    private void emitTurrets(DrawList out, BattleCamera cam, float cellPx, float alphaMult,
                             AirBody body, float scaleMult, float altOffset, MountedTurret[] mounts) {
        if (mounts == null) return;
        float rad = (float) Math.toRadians(body.facingDegrees);
        float c = (float) Math.cos(rad);
        float si = (float) Math.sin(rad);
        for (MountedTurret mt : mounts) {
            ShuttleSpriteCache base = sprites.turretSprites().get(mt.mount.structureId);
            if (base == null) continue;
            // Same world-position helper the sim uses (so a round fires from
            // where the turret is drawn), with the render-only altitude zoom
            // (scaleMult) and the altitude Y-offset layered on top. The mount
            // offset is the real weapon-slot position; no per-ship factor.
            float screenX = cam.cellToScreenX(mt.worldX(body, c, si, scaleMult));
            float screenY = cam.cellToScreenY(mt.worldY(body, c, si, scaleMult) + altOffset);
            // Turret SIZE is intrinsic to the kind — an ARBALEST is the same
            // physical size on every hull, exactly like a ground MapTurret
            // (UnitRenderService draws it at visualCells flat). Only the altitude
            // visual zoom applies; the hull never scales turret size.
            float layerVisualCells = mt.mount.mountDef().visualCells * scaleMult;
            TurretLayerPose pose = TurretLayerPose.resolve(
                    screenX, screenY, mt.facingDegrees, layerVisualCells, cellPx,
                    mt.recoilTimer, BattleRenderer.RECOIL_DURATION,
                    BattleRenderer.RECOIL_DISTANCE_FRAC);

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(mt.mount.structureId);
            if (barrel != null) {
                emitTurretLayer(out, barrel, pose.facingDegrees(), pose.spriteHeightPx(),
                        pose.recoilCenterX(), pose.recoilCenterY(), alphaMult);
            }
            emitTurretLayer(out, base, pose.facingDegrees(), pose.spriteHeightPx(),
                    pose.baseCenterX(), pose.baseCenterY(), alphaMult);
        }
    }

    private static void emitTurretLayer(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float spriteHeightPx, float cx, float cy, float alphaMult) {
        float pxW = spriteHeightPx * cache.aspect;
        out.addSprite(RenderLayer.SHUTTLES, cache.sprite,
                cx, cy, pxW, spriteHeightPx, facingDegrees,
                1f, 1f, 1f, alphaMult);
    }
}
