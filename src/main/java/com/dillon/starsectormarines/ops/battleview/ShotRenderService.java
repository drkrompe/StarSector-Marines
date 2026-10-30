package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.dillon.starsectormarines.battle.weapon.SoftLaunch;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.List;

/**
 * Emits the {@link RenderLayer#SHOTS} body strata — hitscan tracers ({@code LINE}),
 * traveling tinted bolts, and projectile sprites ({@code SPRITE}) — driven by
 * the {@link ShotFx} effect composition rather than the old per-carrier {@code if
 * carrier-type cascade. The sweeps key on the shot's
 * effects, never on who fired it: a future arc-and-trail marine grenade
 * launcher flows through here with no new branch.
 *
 * <p>Four sweeps in submission order: <strong>projectile tracer tails</strong>,
 * <strong>hitscan tracers</strong>, <strong>bolts</strong>, then
 * <strong>sprites</strong>. The contrail ribbon is
 * emitted before this service by {@link BattleRenderer}, so paint order stays
 * contrails → tracer tails → hitscan tracers → bolts → sprites.
 *
 * <p>Holds only the immutable sprite resolver. Particle trails advance separately
 * through {@link ProjectileTrailFxService}; collecting a frame never emits particles.
 */
public final class ShotRenderService implements RenderSystem {

    /** Hitscan tracer line width in px (was {@code glLineWidth(2f)} in the old immediate-mode pass). */
    private static final float TRACER_WIDTH = 2f;
    /** Fraction of the flight clock over which a bolt fades from transparent to its full tint. */
    static final float BOLT_FADE_IN_FRACTION = 0.10f;
    /** Opacity of the glow laid under a tracer round's streak, against its full-strength core. */
    private static final float TRACER_GLOW_ALPHA = 0.4f;

    /** Pure cell-space result for the traveling-bolt sweep, exposed package-locally for headless kinematics tests. */
    record BoltPose(float headX, float headY, float headZ,
                    float tailX, float tailY, float tailZ,
                    float visibleLength, float fadeIn) {}

    /** Pure cell-space result for a short line attached to a traveling projectile. */
    record TracerTailPose(float headX, float headY, float tailX, float tailY,
                          float visibleLength, float fadeIn) {}

    private final BattleSprites sprites;
    public ShotRenderService(BattleSprites sprites) {
        this.sprites = sprites;
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

        // Projectile-bound tail sweep: short, world-sized streaks beneath the
        // shell sprite, grown out of the muzzle on the real flight clock.
        for (ShotEvent s : shots) {
            ShotFx fx = ShotFx.of(s);
            ShotFx.TracerTail tracer = fx.tracerTail();
            if (tracer == null) continue;
            TracerTailPose pose = tracerTailPose(s, fx, tracer);
            if (pose.visibleLength() <= 1e-6f || pose.fadeIn() <= 0f) continue;
            Color c = tracer.color() != null ? tracer.color() : Color.WHITE;
            if (tracer.glowWidthPx() > 0f) {
                out.addLine(RenderLayer.SHOTS,
                        cam.cellToScreenX(pose.tailX()), cam.cellToScreenY(pose.tailY()),
                        cam.cellToScreenX(pose.headX()), cam.cellToScreenY(pose.headY()),
                        tracer.glowWidthPx(),
                        c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                        TRACER_GLOW_ALPHA * pose.fadeIn() * alphaMult);
            }
            out.addLine(RenderLayer.SHOTS,
                    cam.cellToScreenX(pose.tailX()), cam.cellToScreenY(pose.tailY()),
                    cam.cellToScreenX(pose.headX()), cam.cellToScreenY(pose.headY()),
                    TRACER_WIDTH,
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                    pose.fadeIn() * alphaMult);
        }

        // Hitscan tracer sweep: shots whose body is a full path line.
        for (ShotEvent s : shots) {
            if (!(ShotFx.of(s).body() instanceof ShotFx.Tracer tracer)) continue;
            if (tracer.style().lifetimeSec() > 0f) continue;
            float lifeT = Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
            float pulse = tracerPulse(s, tracer);
            Color c = tracer.color() != null
                    ? tracer.color()
                    : ShotFx.defaultTracerColor(s.shooterFaction);
            WeaponDef.BeamStyle style = tracer.style();
            if (style.glowColor() != null) {
                Color glow = style.glowColor();
                out.addLine(RenderLayer.SHOTS,
                        cam.cellToScreenX(s.fromX), cam.cellToScreenY(s.visualFromY()),
                        cam.cellToScreenX(s.toX),   cam.cellToScreenY(s.visualToY()),
                        style.glowWidthPx(),
                        glow.getRed() / 255f, glow.getGreen() / 255f, glow.getBlue() / 255f,
                        lifeT * (0.18f + 0.42f * pulse) * alphaMult);
            }
            out.addLine(RenderLayer.SHOTS,
                    cam.cellToScreenX(s.fromX), cam.cellToScreenY(s.visualFromY()),
                    cam.cellToScreenX(s.toX),   cam.cellToScreenY(s.visualToY()),
                    style.coreWidthPx(),
                    c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                    lifeT * (0.75f + 0.25f * pulse) * alphaMult);
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

        // Ground shadows under lobbed rounds whose weapon casts one, under the rounds themselves.
        sprites.ensureShadowSprite();
        SpriteAPI shadow = sprites.shadowBlobSprite();
        for (ShotEvent s : shots) {
            WeaponDef weapon = s.primaryWeaponDef != null ? s.primaryWeaponDef : s.mechWeaponDef;
            if (shadow == null || weapon == null || !weapon.groundShadow()) continue;
            ShotFx fx = ShotFx.of(s);
            if (!(fx.body() instanceof ShotFx.Sprite sprite)) continue;
            SpriteFlight flight = spriteFlight(s, fx);
            float height = arcHeight(s, fx) > 0f ? 4f * flight.progress() * (1f - flight.progress()) : 0f;
            float gx = s.fromX + (s.toX - s.fromX) * flight.progress();
            float gy = s.fromY + (s.toY - s.fromY) * flight.progress();
            float size = sprite.visualCells() * cellPx * (SHADOW_LANDED - (SHADOW_LANDED - SHADOW_ALOFT) * height);
            out.addSprite(RenderLayer.SHOTS, shadow, cam.cellToScreenX(gx), cam.cellToScreenY(gy), size, size, 0f,
                    0f, 0f, 0f, (SHADOW_ALPHA_LANDED - (SHADOW_ALPHA_LANDED - SHADOW_ALPHA_ALOFT) * height) * alphaMult);
        }

        // Sprite sweep: shots whose body is a traveling projectile sprite.
        for (ShotEvent s : shots) {
            ShotFx fx = ShotFx.of(s);
            if (!(fx.body() instanceof ShotFx.Sprite sprite)) continue;
            ShuttleSpriteCache cache = sprites.projectileSprite(sprite.spritePath());
            if (cache == null) continue;

            SpriteFlight flight = spriteFlight(s, fx);
            float pxH = sprite.visualCells() * cellPx;
            float pxW = pxH * cache.aspect;
            Color tint = sprite.tint() != null ? sprite.tint() : Color.WHITE;
            out.addSprite(RenderLayer.SHOTS, cache.sprite,
                    cam.cellToScreenX(flight.x()), cam.cellToScreenY(flight.y()),
                    pxW, pxH, flight.bearing(),
                    tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f, alphaMult);
        }
    }

    /** A ground shadow's width against the round's size: at the top of its arc, and as it lands. */
    private static final float SHADOW_ALOFT = 0.9f, SHADOW_LANDED = 2.2f;
    /** A ground shadow's darkness at the top of its arc and as it lands: it gathers as the round comes down. */
    private static final float SHADOW_ALPHA_ALOFT = 0.12f, SHADOW_ALPHA_LANDED = 0.55f;

    /** Where a sprite-bodied shot is in the air right now, in projected cell space, and which way it points. */
    public record SpriteFlight(float x, float y, float bearing, float progress) {}

    /** The in-flight pose of a projectile-sprite shot: straight, boosted, arced, soft-launched or guided. */
    static SpriteFlight spriteFlight(ShotEvent s, ShotFx fx) {
        float linearProgress = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
        if (fx.softLaunch() != null && s.guidedFlight == null) {
            return softLaunchFlight(s, fx, linearProgress);
        }
        float progress = fx.boostRamp() ? Projectile.applyBoostCurve(linearProgress) : linearProgress;
        float px = s.fromX + (s.toX - s.fromX) * progress;
        float pz = s.fromZ + (s.toZ - s.fromZ) * progress;
        float py = s.fromY + (s.toY - s.fromY) * progress + pz;
        float bearing;
        float arcH = arcHeight(s, fx);
        if (arcH > 0f) {
            py += arcH * 4f * progress * (1f - progress);
            float tangentDy = (s.visualToY() - s.visualFromY())
                    + arcH * 4f * (1f - 2f * progress);
            bearing = bearingDeg(0f, 0f, s.toX - s.fromX, tangentDy);
        } else {
            bearing = bearingDeg(s.fromX, s.visualFromY(), s.toX, s.visualToY());
        }
        if (s.guidedFlight != null) {
            px = s.guidedFlight.x;
            py = s.guidedFlight.y + s.guidedFlight.z;
            bearing = s.guidedFlight.visualBearingDegrees();
        }
        return new SpriteFlight(px, py, bearing, progress);
    }

    /**
     * A two-stage missile's pose: kicked out and climbing through its coast, then levelling onto its
     * target under power. The bearing is read off the flown path a moment ahead rather than off the
     * straight line, so the missile points up the screen as it climbs and swings onto the target as
     * the motor takes over.
     */
    private static SpriteFlight softLaunchFlight(ShotEvent s, ShotFx fx, float t) {
        float[] here = softLaunchPoint(s, fx, t);
        float ahead = Math.min(1f, t + 0.02f);
        float[] next = ahead > t ? softLaunchPoint(s, fx, ahead) : here;
        float bearing = next[0] != here[0] || next[1] != here[1]
                ? bearingDeg(here[0], here[1], next[0], next[1])
                : bearingDeg(s.fromX, s.visualFromY(), s.toX, s.visualToY());
        return new SpriteFlight(here[0], here[1], bearing, here[2]);
    }

    private static float[] softLaunchPoint(ShotEvent s, ShotFx fx, float t) {
        SoftLaunch launch = fx.softLaunch();
        float distance = (float) Math.hypot(s.toX - s.fromX, s.toY - s.fromY);
        float progress = launch.progress(t, s.lifetimeMax, distance, fx.boostRamp());
        float x = s.fromX + (s.toX - s.fromX) * progress;
        float z = s.fromZ + (s.toZ - s.fromZ) * progress;
        float y = s.fromY + (s.toY - s.fromY) * progress + z;
        float arc = arcHeight(s, fx);
        if (arc > 0f) y += arc * 4f * progress * (1f - progress);
        y += launch.lift(t, s.lifetimeMax);
        return new float[] {x, y, progress};
    }

    /** Whether a soft-launched shot's motor is burning yet; true for every shot that does not soft-launch. */
    static boolean motorLit(ShotEvent s, ShotFx fx) {
        if (fx.softLaunch() == null || s.guidedFlight != null) return true;
        float t = 1f - Math.max(0f, Math.min(1f, s.lifetime / Math.max(0.001f, s.lifetimeMax)));
        return fx.softLaunch().ignited(t, s.lifetimeMax);
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

    static TracerTailPose tracerTailPose(ShotEvent shot, ShotFx fx,
                                         ShotFx.TracerTail tracer) {
        float linearProgress = flightProgress(shot);
        float headProgress = fx.boostRamp()
                ? Projectile.applyBoostCurve(linearProgress) : linearProgress;
        float dx = shot.toX - shot.fromX;
        float dy = shot.toY - shot.fromY;
        float shotLength = (float) Math.sqrt(dx * dx + dy * dy);
        float visibleLength = Math.min(Math.max(0f, tracer.lengthCells()),
                headProgress * shotLength);
        float invLength = shotLength > 1e-6f ? 1f / shotLength : 0f;
        float tailProgress = Math.max(0f, headProgress - visibleLength * invLength);
        float headX = projectileX(shot, headProgress);
        float headY = projectedProjectileY(shot, fx, headProgress);
        float tailX = projectileX(shot, tailProgress);
        float tailY = projectedProjectileY(shot, fx, tailProgress);
        float fadeIn = Math.min(1f, linearProgress / BOLT_FADE_IN_FRACTION);
        return new TracerTailPose(headX, headY, tailX, tailY,
                dist(headX, headY, tailX, tailY), fadeIn);
    }

    private static float flightProgress(ShotEvent shot) {
        return 1f - Math.max(0f, Math.min(1f,
                shot.lifetime / Math.max(0.001f, shot.lifetimeMax)));
    }

    private static float projectileX(ShotEvent shot, float progress) {
        return shot.fromX + (shot.toX - shot.fromX) * progress;
    }

    /** Projected cell-space ordinate on the same straight/arc flight used by sprite bodies. */
    private static float projectedProjectileY(ShotEvent shot, ShotFx fx, float progress) {
        float z = shot.fromZ + (shot.toZ - shot.fromZ) * progress;
        float y = shot.fromY + (shot.toY - shot.fromY) * progress + z;
        float arc = arcHeight(shot, fx);
        if (arc > 0f) {
            y += arc * 4f * progress * (1f - progress);
        }
        return y;
    }

    /** The drawn arc's height: the round's own when its throw set one, else its weapon's. */
    static float arcHeight(ShotEvent shot, ShotFx fx) {
        return Float.isFinite(shot.arcHeightOverride) ? shot.arcHeightOverride : fx.arcHeight();
    }

    /** Width grows with the emerging streak so a bolt never spawns as a sideways muzzle blob. */
    static float boltWidthCells(BoltPose pose, ShotFx.Bolt bolt) {
        if (bolt.lengthCells() <= 1e-6f) return 0f;
        float growth = Math.min(1f, pose.visibleLength() / bolt.lengthCells());
        return Math.max(0f, bolt.widthCells()) * growth;
    }

    /** Smooth brightness envelope for a beam's authored number of pulses over its visual life. */
    static float tracerPulse(ShotEvent shot, ShotFx.Tracer tracer) {
        float cycles = tracer.style().pulseCycles();
        if (cycles <= 0f) return 1f;
        float lifeT = Math.max(0f, Math.min(1f,
                shot.lifetime / Math.max(0.001f, shot.lifetimeMax)));
        return BeamFxService.pulse(cycles, lifeT);
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
