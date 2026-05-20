package com.dillon.starsectormarines.battle.fx;

import com.dillon.starsectormarines.battle.MechWeapon;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.render2d.LightAccumulator;
import com.dillon.starsectormarines.render2d.LightKernel;

import java.awt.Color;

/**
 * Single source of truth for weapon-fire lighting — every muzzle flash,
 * HE bloom, and line-tracer beam path stamps through this helper so the
 * kernel choice, tuning constants, and color palette live in one place.
 *
 * <p>Pair with {@link ImpactFx} (particle FX), {@link ImpactDecals} (decals),
 * and the {@link LightAccumulator} that owns the lightmap. Engine glows
 * live in {@link com.dillon.starsectormarines.battle.air.engine.EngineFxRenderer}
 * since they're per-slot transforms, but the brightness constants
 * ({@link #ENGINE_RGB_SCALE}) live here so playtest tuning happens at one
 * filename.
 */
public final class WeaponLights {

    // ===== Muzzle flash radii (cells) =====
    private static final float MARINE_MUZZLE_RADIUS  = 0.55f;
    private static final float MECH_MUZZLE_RADIUS    = 0.90f;
    private static final float TURRET_MUZZLE_RADIUS  = 1.10f;
    private static final float FIGHTER_MUZZLE_RADIUS = 1.20f;

    // ===== Muzzle flash lifetimes (sim-seconds) =====
    private static final float MARINE_MUZZLE_LIFETIME  = 0.05f;
    private static final float MECH_MUZZLE_LIFETIME    = 0.08f;
    private static final float TURRET_MUZZLE_LIFETIME  = 0.07f;
    private static final float FIGHTER_MUZZLE_LIFETIME = 0.07f;

    // ===== HE impact bloom =====
    private static final float HE_RADIUS   = 3.5f;
    private static final float HE_LIFETIME = 0.45f;

    // ===== Laser / line-tracer path =====
    /** Distance between stamped kernels along a line-tracer's path. */
    private static final float LASER_STEP_CELLS = 0.7f;
    /** Per-stamp halo radius — small so the line reads as a beam, not a swath. */
    private static final float LASER_RADIUS_CELLS = 0.40f;
    /** Stamp lifetime — one frame's worth at ~20fps, so the path fades with the tracer. */
    private static final float LASER_LIFETIME = 0.05f;
    /** Per-stamp intensity — dialed below 1 so colored beams tint the lightmap rather than washing it. */
    private static final float LASER_INTENSITY = 0.55f;
    /** Cap on stamps per beam — guards against insanely long beams creating thousands of lights. */
    private static final int LASER_MAX_STAMPS = 48;

    // ===== Engine glow (tuning shared with EngineFxRenderer.emitLights) =====
    /**
     * Multiplier applied to engine flame RGB before lighting. Without this,
     * a MIDLINE shuttle's flame color of (1.0, 0.57, 0.29) saturates white
     * on the red channel at full throttle and the engine reads as a solid
     * bright disc. 0.5 keeps the warm hue while leaving headroom for
     * overlapping HE bursts to stack visibly.
     */
    public static final float ENGINE_RGB_SCALE = 0.45f;
    /** Engine light intensity floor at zero throttle — idle valks still get a faint halo. */
    public static final float ENGINE_INTENSITY_FLOOR = 0.40f;
    /** Engine light intensity ceiling at full throttle. */
    public static final float ENGINE_INTENSITY_CEIL = 0.85f;

    // ===== Palette =====
    /** Warm gunmetal pop for kinetic muzzle flashes (most weapons). */
    private static final float MUZZLE_R = 1.0f, MUZZLE_G = 0.85f, MUZZLE_B = 0.45f;
    /** Orange HE bloom. */
    private static final float HE_R = 1.0f, HE_G = 0.60f, HE_B = 0.25f;

    private WeaponLights() {}

    // ------------------------------------------------------------------
    // Muzzle flashes
    // ------------------------------------------------------------------

    public static void marineMuzzleFlash(LightAccumulator lights, float x, float y) {
        if (lights == null) return;
        lights.addTransient(x, y, MARINE_MUZZLE_RADIUS,
                LightKernel.MUZZLE_FLASH,
                MUZZLE_R, MUZZLE_G, MUZZLE_B, 1.0f, MARINE_MUZZLE_LIFETIME);
    }

    public static void mechMuzzleFlash(LightAccumulator lights, float x, float y) {
        if (lights == null) return;
        lights.addTransient(x, y, MECH_MUZZLE_RADIUS,
                LightKernel.MUZZLE_FLASH,
                MUZZLE_R, MUZZLE_G, MUZZLE_B, 1.0f, MECH_MUZZLE_LIFETIME);
    }

    public static void turretMuzzleFlash(LightAccumulator lights, float x, float y) {
        if (lights == null) return;
        lights.addTransient(x, y, TURRET_MUZZLE_RADIUS,
                LightKernel.MUZZLE_FLASH,
                MUZZLE_R, MUZZLE_G, MUZZLE_B, 1.0f, TURRET_MUZZLE_LIFETIME);
    }

    public static void fighterMuzzleFlash(LightAccumulator lights, float x, float y) {
        if (lights == null) return;
        lights.addTransient(x, y, FIGHTER_MUZZLE_RADIUS,
                LightKernel.MUZZLE_FLASH,
                MUZZLE_R, MUZZLE_G, MUZZLE_B, 1.0f, FIGHTER_MUZZLE_LIFETIME);
    }

    /**
     * Pick the right muzzle flash for a {@link ShotEvent}'s source and emit
     * at its {@code fromX/fromY}. Caller passes {@code hasProjectile} (the
     * existing {@code hasProjectileSprite} check) so we can route generic
     * line tracers from non-marine infantry (militia / alien / defender)
     * through the small-arms flash as well.
     *
     * <p>Skips rocket / grenade launches ({@code marineSecondary} ≠ null
     * with a projectile) — those weapons have their own launch FX and
     * adding a light at the launcher would double up on the burst.
     */
    public static void shotMuzzleFlash(LightAccumulator lights, ShotEvent s, boolean hasProjectile) {
        if (lights == null || s == null) return;
        if (s.turretKind != null) {
            turretMuzzleFlash(lights, s.fromX, s.fromY);
            return;
        }
        if (s.mechWeapon == MechWeapon.CHAINGUN) {
            mechMuzzleFlash(lights, s.fromX, s.fromY);
            return;
        }
        if (s.marineWeapon != null) {
            marineMuzzleFlash(lights, s.fromX, s.fromY);
            return;
        }
        // No marineWeapon, no turret, no chaingun → enemy infantry or
        // similar generic line-tracer source. Only emit when there's no
        // projectile in flight (so rocket secondaries don't double-flash).
        if (!hasProjectile && s.marineSecondary == null) {
            marineMuzzleFlash(lights, s.fromX, s.fromY);
        }
    }

    // ------------------------------------------------------------------
    // HE impact bloom
    // ------------------------------------------------------------------

    /**
     * Warm orange bloom at an impact point — only fires for HE profiles.
     * Bullet impacts and chips skip this; the line-tracer path already
     * lights the impact zone.
     */
    public static void impactBurst(LightAccumulator lights, ImpactProfile profile, float x, float y) {
        if (lights == null || profile != ImpactProfile.HE) return;
        lights.addTransient(x, y, HE_RADIUS,
                LightKernel.HE_BURST,
                HE_R, HE_G, HE_B, 1.0f, HE_LIFETIME);
    }

    // ------------------------------------------------------------------
    // Line-tracer beam path
    // ------------------------------------------------------------------

    /**
     * Stamp a chain of small lights along a line-tracer's path so the
     * multiply pass doesn't darken the beam. The stamps inherit the
     * tracer color so a green pulse-rifle beam reads green and a blue
     * railgun reads blue.
     *
     * <p>Caller-side filter: invoke for every line-tracer shot fired
     * this frame (one stamp set per shot). The transient lifetime is
     * short enough that the path fades with the tracer's visible
     * lifetime rather than persisting.
     */
    public static void laserPath(LightAccumulator lights,
                                 float x0, float y0, float x1, float y1,
                                 Color tracerColor) {
        if (lights == null || tracerColor == null) return;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;

        float r = tracerColor.getRed()   / 255f;
        float g = tracerColor.getGreen() / 255f;
        float b = tracerColor.getBlue()  / 255f;

        int n = Math.max(2, Math.min(LASER_MAX_STAMPS,
                (int) Math.ceil(len / LASER_STEP_CELLS)));
        float invDenom = 1f / (n - 1);
        for (int i = 0; i < n; i++) {
            float t = i * invDenom;
            lights.addTransient(x0 + dx * t, y0 + dy * t,
                    LASER_RADIUS_CELLS,
                    LightKernel.MUZZLE_FLASH,
                    r, g, b, LASER_INTENSITY, LASER_LIFETIME);
        }
    }
}
