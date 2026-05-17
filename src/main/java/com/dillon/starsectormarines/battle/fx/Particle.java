package com.dillon.starsectormarines.battle.fx;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * One animated FX particle. Plain data — {@link ImpactFx} owns the list, ticks
 * lifetime + position + radius, and renders. Spawn helpers on the engine fill
 * in the recipe (smoke rises and billows, sparks parked and brief, fire bursts
 * grow and fade additive).
 *
 * <p>Mirrors the per-particle data structure inside
 * {@code com.dillon.starsectormarines.battle.flyby.FlybyOverlay.Particle} —
 * the two systems share assets and rendering math but live in separate
 * particle lists so flyby FX and ground-combat impact FX can evolve
 * independently without one regressing the other.
 */
public final class Particle {

    public float x, y;
    /** Velocity in cells/sec. Smoke rises, sparks stay parked. */
    public float vx, vy;
    public float lifetimeRemaining;
    public float lifetimeMax;
    public float radiusCells;
    /** Per-second growth of {@link #radiusCells}. Smoke billows; sparks don't grow. */
    public float radiusGrowthPerSec;
    public Color color;
    /** Sprite to draw. Required — no null fallback in the ground-combat engine (caller chose to spawn this particle, so it should provide art). */
    public SpriteAPI sprite;
    /** Render rotation in degrees, applied at draw time. Randomized at spawn for sprites where rotation invariance reads better. */
    public float angleDeg;
    /** Flipbook frame index of the first frame this particle plays. */
    public int firstFrame;
    /** Frame count for the flipbook. 0 = render the whole sprite (no slicing). */
    public int frameCount;
    /** True = additive blend (hot fire / sparks). False = normal alpha (smoke / dust that should occlude rather than glow). */
    public boolean additive;
}
