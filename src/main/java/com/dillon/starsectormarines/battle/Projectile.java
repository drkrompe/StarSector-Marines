package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Simulated in-flight projectile — owns position over time and the AoE
 * detonation that fires on arrival. Replaces the parallel
 * {@link ShotEvent} + {@link PendingDetonation} pair for slow-flight AoE
 * kinds (turret-class rockets / mortars with {@code cellsPerSec > 0}).
 *
 * <h2>Why a separate entity</h2>
 * The legacy renderer-side lerp computed projectile position as
 * {@code lerp(from, to, lifetime/lifetimeMax)} with {@code lifetimeMax = kind.flightSec}
 * — which made velocity scale with distance (a 10-cell close shot and a
 * 100-cell long shot both took the same time, so the long one read as 10×
 * faster). With a real entity the velocity is per-kind ({@link TurretKind#cellsPerSec})
 * and flight time = {@code dist / cellsPerSec}, so closer shots arrive
 * sooner. Sets the foundation for point defense — a turret aiming at an
 * incoming projectile can flip {@link #intercepted} and the sim removes the
 * projectile without detonating.
 *
 * <h2>No hit/miss</h2>
 * AoE kinds don't roll hit/miss. {@link #toX}/{@link #toY} is the scatter
 * endpoint (target cell plus accuracy-scaled offset). The AoE radius
 * decides who gets hurt — whatever's in radius at detonation takes damage,
 * regardless of whether it was the locked target. Cleaner and matches real
 * artillery: rounds don't "miss," they just land somewhere and what's in
 * the splash decides the outcome.
 *
 * <h2>Lifecycle</h2>
 * Born in {@code BattleSimulation.fireShotFrom} when the firing kind has
 * {@code cellsPerSec > 0}. Advances every tick via
 * {@code BattleSimulation.advanceProjectiles}. On expiration, fires
 * {@link #onArrival} through {@code Detonations.detonateNow} and is removed.
 * Renderer reads {@link #currentX()}/{@link #currentY()} each frame.
 */
public final class Projectile {

    public final float fromX;
    public final float fromY;
    /** Scatter endpoint — where the projectile detonates on arrival. Computed at fire time from target cell + accuracy-scaled scatter. */
    public final float toX;
    public final float toY;
    public final TurretKind kind;
    public final Faction shooterFaction;
    /** True when delivered from above (arc shots, shuttle mounts). Threaded into {@link #onArrival} for the roof-shield rule + the renderer's flight FX. */
    public final boolean aerialDelivery;
    /** Total flight time at construction — {@code dist / kind.cellsPerSec()}. Used by the renderer to compute {@link #progress()}. */
    public final float totalFlightTime;
    /** Sim-seconds until arrival. Decremented per tick by {@code advanceProjectiles}; arrival fires {@link #onArrival}. */
    public float remainingTime;
    /** AoE damage payload fired when the projectile arrives. Owned by the projectile — when point defense cancels via {@link #intercepted}, this payload is dropped automatically (no parallel queue to clean up). */
    public final PendingDetonation onArrival;
    /** When set, the projectile is removed on the next tick without detonating. Reserved for the point-defense intercept path — not yet wired. */
    public boolean intercepted;

    public Projectile(float fromX, float fromY, float toX, float toY,
                      TurretKind kind, Faction shooterFaction, boolean aerialDelivery,
                      float totalFlightTime, PendingDetonation onArrival) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.kind = kind;
        this.shooterFaction = shooterFaction;
        this.aerialDelivery = aerialDelivery;
        this.totalFlightTime = totalFlightTime;
        this.remainingTime = totalFlightTime;
        this.onArrival = onArrival;
        this.intercepted = false;
    }

    /** 0 at launch, 1 at arrival. Clamped at both ends. */
    public float progress() {
        if (totalFlightTime <= 0.0001f) return 1f;
        float p = 1f - remainingTime / totalFlightTime;
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }

    /** Current world-space X (cells). Straight-line lerp from→to. */
    public float currentX() {
        return fromX + (toX - fromX) * progress();
    }

    /** Current world-space Y (cells). Adds the kind's parabolic arc on top of the straight-line lerp — same visual as the legacy renderer's arc math. */
    public float currentY() {
        float p = progress();
        float y = fromY + (toY - fromY) * p;
        if (kind != null && kind.arcHeight > 0f) {
            y += kind.arcHeight * 4f * p * (1f - p);
        }
        return y;
    }

    public boolean isExpired() {
        return remainingTime <= 0f;
    }
}
