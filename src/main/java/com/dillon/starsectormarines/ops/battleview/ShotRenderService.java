package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.List;

/**
 * Emits the {@link RenderLayer#SHOTS} body strata — hitscan tracers ({@code LINE})
 * and projectile sprites ({@code SPRITE}) — driven by the {@link ShotFx} effect
 * composition rather than the old per-carrier {@code if turretKind … else if
 * marineWeapon …} cascade. The sweeps key on the shot's effects, never on who
 * fired it: a future arc-and-contrail marine grenade launcher flows through here
 * with no new branch.
 *
 * <p>Two sweeps in submission order: <strong>tracers</strong> then
 * <strong>sprites</strong> (matching the old {@code collectShots} after the
 * contrails). The contrail ribbon is still emitted as a {@code Custom} by
 * {@link BattleRenderer} (its lifecycle state lives there until F4) and is ordered
 * <em>before</em> this service in the registry, so the paint order stays
 * contrails → tracers → sprites.
 *
 * <p>Holds only immutable refs: {@link BattleSprites} (projectile sprites resolved
 * by path — carrier-agnostic) and {@link ImpactFx} (the engine/smoke trail spawn
 * sink, gated on the {@code engineTrail}/{@code smokeTrail} effects in the sprite
 * sweep). Per-frame state comes via the {@link RenderContext}.
 */
public final class ShotRenderService implements RenderSystem {

    /** Dual-use in BattleScreen (spawnImpactFx); kept independent here for zero back-dependency. */
    private static final Color MARINE_TRACER   = new Color(0xFF, 0xE0, 0x70);
    private static final Color DEFENDER_TRACER  = new Color(0xFF, 0x70, 0x40);
    /** Hitscan tracer line width in px (was {@code glLineWidth(2f)} in the old immediate-mode pass). */
    private static final float TRACER_WIDTH = 2f;

    private final BattleSprites sprites;
    private final ImpactFx impactFx;

    public ShotRenderService(BattleSprites sprites, ImpactFx impactFx) {
        this.sprites = sprites;
        this.impactFx = impactFx;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.SHOTS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        List<ShotEvent> shots = ctx.sim.getActiveShots();
        if (shots.isEmpty()) return;
        BattleCamera cam = ctx.camera;
        float alphaMult = ctx.alphaMult;

        // Tracer sweep: shots whose body is a hitscan line.
        for (ShotEvent s : shots) {
            if (!(ShotFx.of(s).body() instanceof ShotFx.Tracer tracer)) continue;
            float lifeT = Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            Color c = tracer.color() != null
                    ? tracer.color()
                    : (s.shooterFaction == Faction.MARINE ? MARINE_TRACER : DEFENDER_TRACER);
            out.addLine(RenderLayer.SHOTS,
                    cam.cellToScreenX(s.fromX), cam.cellToScreenY(s.fromY),
                    cam.cellToScreenX(s.toX),   cam.cellToScreenY(s.toY),
                    TRACER_WIDTH,
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, lifeT * alphaMult);
        }

        // Sprite sweep: shots whose body is a traveling projectile sprite.
        float cellPx = cam.cellPxSize();
        for (ShotEvent s : shots) {
            ShotFx fx = ShotFx.of(s);
            if (!(fx.body() instanceof ShotFx.Sprite sprite)) continue;
            ShuttleSpriteCache cache = sprites.projectileSprite(sprite.spritePath());
            if (cache == null) continue;

            float linearProgress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float progress = fx.boostRamp() ? Projectile.applyBoostCurve(linearProgress) : linearProgress;
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress;
            float bearing;
            float arcH = fx.arcHeight();
            if (arcH > 0f) {
                py += arcH * 4f * progress * (1f - progress);
                float tangentDy = (s.toY - s.fromY) + arcH * 4f * (1f - 2f * progress);
                bearing = bearingDeg(0f, 0f, s.toX - s.fromX, tangentDy);
            } else {
                bearing = bearingDeg(s.fromX, s.fromY, s.toX, s.toY);
            }
            float pxH = sprite.visualCells() * cellPx;
            float pxW = pxH * cache.aspect;
            out.addSprite(RenderLayer.SHOTS, cache.sprite,
                    cam.cellToScreenX(px), cam.cellToScreenY(py),
                    pxW, pxH, bearing, 1f, 1f, 1f, alphaMult);

            if ((fx.engineTrail() || fx.smokeTrail()) && progress > 0.02f && progress < 0.98f) {
                float headingRad = (float) Math.toRadians(bearing);
                float tailDx = -(float) Math.sin(headingRad) * 0.15f;
                float tailDy = -(float) Math.cos(headingRad) * 0.15f;
                if (fx.engineTrail()) impactFx.spawnEngineTrail(px + tailDx, py + tailDy, 0.18f);
                else                  impactFx.spawnSmokeTrail(px + tailDx, py + tailDy, 0.20f);
            }
        }
    }

    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }
}
