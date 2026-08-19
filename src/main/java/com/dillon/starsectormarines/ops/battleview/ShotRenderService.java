package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.fx.ImpactFx;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.List;

/**
 * Emits the {@link RenderLayer#SHOTS} body strata — hitscan tracers ({@code LINE}),
 * traveling tinted bolts, and projectile sprites ({@code SPRITE}) — driven by
 * the {@link ShotFx} effect composition rather than the old per-carrier {@code if
 * turretKind … else if marineWeapon …} cascade. The sweeps key on the shot's
 * effects, never on who fired it: a future arc-and-contrail marine grenade
 * launcher flows through here with no new branch.
 *
 * <p>Three sweeps in submission order: <strong>tracers</strong>,
 * <strong>bolts</strong>, then <strong>sprites</strong>. The contrail ribbon is
 * emitted before this service by {@link BattleRenderer}, so paint order stays
 * contrails → tracers → bolts → sprites.
 *
 * <p>Holds only immutable refs: {@link BattleSprites} (projectile sprites resolved
 * by path — carrier-agnostic) and {@link ImpactFx} (the engine/smoke trail spawn
 * sink, gated on the {@code engineTrail}/{@code smokeTrail} effects in the sprite
 * sweep). Per-frame state comes via the {@link RenderContext}.
 */
public final class ShotRenderService implements RenderSystem {

    /** Hitscan tracer line width in px (was {@code glLineWidth(2f)} in the old immediate-mode pass). */
    private static final float TRACER_WIDTH = 2f;
    /** Fraction of the flight clock over which a bolt fades from transparent to its full tint. */
    static final float BOLT_FADE_IN_FRACTION = 0.10f;

    /** Pure cell-space result for the traveling-bolt sweep, exposed package-locally for headless kinematics tests. */
    record BoltPose(float headX, float headY, float headZ,
                    float tailX, float tailY, float tailZ,
                    float visibleLength, float fadeIn) {}

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
                    : ShotFx.defaultTracerColor(s.shooterFaction);
            out.addLine(RenderLayer.SHOTS,
                    cam.cellToScreenX(s.fromX), cam.cellToScreenY(s.visualFromY()),
                    cam.cellToScreenX(s.toX),   cam.cellToScreenY(s.visualToY()),
                    TRACER_WIDTH,
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, lifeT * alphaMult);
        }

        // Bolt sweep: the configured family silhouette stretched head-to-tail
        // on the real flight clock, then tinted from the weapon declaration.
        float cellPx = cam.cellPxSize();
        for (ShotEvent s : shots) {
            if (!(ShotFx.of(s).body() instanceof ShotFx.Bolt bolt)) continue;
            ShuttleSpriteCache boltSprite = sprites.projectileSprite(bolt.spritePath());
            if (boltSprite == null) continue;
            BoltPose pose = boltPose(s, bolt);
            if (pose.visibleLength() <= 1e-6f || pose.fadeIn() <= 0f) continue;

            float centerX = (pose.headX() + pose.tailX()) * 0.5f;
            float projectedHeadY = pose.headY() + pose.headZ();
            float projectedTailY = pose.tailY() + pose.tailZ();
            float centerY = (projectedHeadY + projectedTailY) * 0.5f;
            float pxH = dist(pose.headX(), projectedHeadY, pose.tailX(), projectedTailY) * cellPx;
            float pxW = boltWidthCells(pose, bolt) * cellPx;
            Color color = bolt.color() != null ? bolt.color() : Color.WHITE;
            out.addSprite(RenderLayer.SHOTS, boltSprite.sprite,
                    cam.cellToScreenX(centerX), cam.cellToScreenY(centerY),
                    pxW, pxH, bearingDeg(pose.tailX(), projectedTailY,
                            pose.headX(), projectedHeadY),
                    color.getRed() / 255f, color.getGreen() / 255f,
                    color.getBlue() / 255f, pose.fadeIn() * alphaMult);
        }

        // Sprite sweep: shots whose body is a traveling projectile sprite.
        for (ShotEvent s : shots) {
            ShotFx fx = ShotFx.of(s);
            if (!(fx.body() instanceof ShotFx.Sprite sprite)) continue;
            ShuttleSpriteCache cache = sprites.projectileSprite(sprite.spritePath());
            if (cache == null) continue;

            float linearProgress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float progress = fx.boostRamp() ? Projectile.applyBoostCurve(linearProgress) : linearProgress;
            float px = s.fromX + (s.toX - s.fromX) * progress;
            float pz = s.fromZ + (s.toZ - s.fromZ) * progress;
            float py = s.fromY + (s.toY - s.fromY) * progress + pz;
            float bearing;
            float arcH = fx.arcHeight();
            if (arcH > 0f) {
                py += arcH * 4f * progress * (1f - progress);
                float tangentDy = (s.visualToY() - s.visualFromY())
                        + arcH * 4f * (1f - 2f * progress);
                bearing = bearingDeg(0f, 0f, s.toX - s.fromX, tangentDy);
            } else {
                bearing = bearingDeg(s.fromX, s.visualFromY(), s.toX, s.visualToY());
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

    static BoltPose boltPose(ShotEvent shot, ShotFx.Bolt bolt) {
        float progress = 1f - Math.max(0f, Math.min(1f,
                shot.lifetime / Math.max(0.001f, shot.lifetimeMax)));
        float dx = shot.toX - shot.fromX;
        float dy = shot.toY - shot.fromY;
        float shotLength = (float) Math.sqrt(dx * dx + dy * dy);
        float headX = shot.fromX + dx * progress;
        float headY = shot.fromY + dy * progress;
        float headZ = shot.fromZ + (shot.toZ - shot.fromZ) * progress;
        float visibleLength = Math.min(Math.max(0f, bolt.lengthCells()), progress * shotLength);
        float invLength = shotLength > 1e-6f ? 1f / shotLength : 0f;
        float tailProgress = Math.max(0f, progress - visibleLength * invLength);
        float tailX = shot.fromX + dx * tailProgress;
        float tailY = shot.fromY + dy * tailProgress;
        float tailZ = shot.fromZ + (shot.toZ - shot.fromZ) * tailProgress;
        float fadeIn = Math.min(1f, progress / BOLT_FADE_IN_FRACTION);
        return new BoltPose(headX, headY, headZ, tailX, tailY, tailZ, visibleLength, fadeIn);
    }

    /** Width grows with the emerging streak so a bolt never spawns as a sideways muzzle blob. */
    static float boltWidthCells(BoltPose pose, ShotFx.Bolt bolt) {
        if (bolt.lengthCells() <= 1e-6f) return 0f;
        float growth = Math.min(1f, pose.visibleLength() / bolt.lengthCells());
        return Math.max(0f, bolt.widthCells()) * growth;
    }

    private static float bearingDeg(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
