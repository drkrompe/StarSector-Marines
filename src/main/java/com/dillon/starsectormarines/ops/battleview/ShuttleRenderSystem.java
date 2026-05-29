package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer;
import com.dillon.starsectormarines.battle.air.engine.EngineSlotResolver;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

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
    public void collect(RenderContext ctx, DrawList out) {
        List<Shuttle> shuttles = ctx.sim.getShuttles();
        if (shuttles.isEmpty()) return;

        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;

        for (Shuttle s : shuttles) {
            if (!s.isVisible()) continue;
            ShuttleSpriteCache cache = sprites.shuttleSprites().get(s.type);
            if (cache == null) continue;

            float altOffset = s.visualAltitudeOffsetCells();

            // Engine FX (own GL) under the hull.
            out.addCustom(RenderLayer.SHUTTLES, () -> EngineFxRenderer.draw(
                    EngineSlotResolver.resolve(s.type),
                    s.body.x, s.body.y,
                    s.body.facingDegrees,
                    s.scaleMult,
                    altOffset,
                    s.engineFxIntensity(),
                    alphaMult,
                    cam,
                    sprites.engineGlowSprite(), sprites.engineFlameSprite()));

            // Hull.
            float pxLen = s.type.visualLengthCells * cellPx * s.scaleMult;
            float pxH = pxLen;
            float pxW = pxLen * cache.aspect;
            float cx = cam.cellToScreenX(s.body.x);
            float cy = cam.cellToScreenY(s.body.y + altOffset);
            out.addSprite(RenderLayer.SHUTTLES, cache.sprite,
                    cx, cy, pxW, pxH, s.body.facingDegrees,
                    1f, 1f, 1f, alphaMult);

            emitTurrets(out, cam, cellPx, alphaMult, s);
        }
    }

    private void emitTurrets(DrawList out, BattleCamera cam, float cellPx, float alphaMult, Shuttle s) {
        if (s.turrets.length == 0) return;
        float rad = (float) Math.toRadians(s.body.facingDegrees);
        float c = (float) Math.cos(rad);
        float si = (float) Math.sin(rad);
        float altOffset = s.visualAltitudeOffsetCells();
        for (MountedTurret mt : s.turrets) {
            ShuttleSpriteCache base = sprites.turretSprites().get(mt.mount.kind);
            if (base == null) continue;
            float lx = mt.mount.localOffsetX * s.scaleMult;
            float ly = mt.mount.localOffsetY * s.scaleMult;
            float worldOffsetX = lx * c - ly * si;
            float worldOffsetY = lx * si + ly * c;
            float screenX = cam.cellToScreenX(s.body.x + worldOffsetX);
            float screenY = cam.cellToScreenY(s.body.y + worldOffsetY + altOffset);
            float layerVisualCells = mt.mount.kind.visualCells * s.scaleMult * s.type.turretVisualScale;

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(mt.mount.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (mt.recoilTimer < BattleRenderer.RECOIL_DURATION) {
                    recoilT = 1f - mt.recoilTimer / BattleRenderer.RECOIL_DURATION;
                }
                float pushPx = recoilT * BattleRenderer.RECOIL_DISTANCE_FRAC * layerVisualCells * cellPx;
                double brad = Math.toRadians(mt.facingDegrees);
                float bx = (float) Math.sin(brad) * pushPx;
                float by = -(float) Math.cos(brad) * pushPx;
                emitTurretLayer(out, barrel, mt.facingDegrees, layerVisualCells, cellPx,
                        screenX + bx, screenY + by, alphaMult);
            }
            emitTurretLayer(out, base, mt.facingDegrees, layerVisualCells, cellPx,
                    screenX, screenY, alphaMult);
        }
    }

    private static void emitTurretLayer(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float visualCells, float cellPx, float cx, float cy, float alphaMult) {
        float pxH = visualCells * cellPx;
        float pxW = pxH * cache.aspect;
        out.addSprite(RenderLayer.SHUTTLES, cache.sprite,
                cx, cy, pxW, pxH, facingDegrees,
                1f, 1f, 1f, alphaMult);
    }
}
