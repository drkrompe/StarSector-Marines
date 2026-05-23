package com.dillon.starsectormarines.battle;

/**
 * Simulated in-flight projectile — owns position over time and the AoE
 * detonation that fires on arrival. Replaces the parallel
 * {@link ShotEvent} + {@link PendingDetonation} pair for slow-flight AoE
 * kinds: turret-class rockets / mortars (locust, grenade launcher with
 * {@code cellsPerSec > 0}) AND marine handheld rockets (whose flight
 * time is a per-weapon constant — same Projectile shape, different
 * spawner). Carries only the primitive visual params it needs
 * ({@link #hasBoostRamp}, {@link #arcHeight}) so the spawner can be
 * either weapon family without coupling Projectile to TurretKind.
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
    /** True for rocket-class projectiles whose visual position grows quadratically over the boost-ramp window (accelerate from rest, then cruise). False for chemical-charge shells (mortars / grenades) that exit at terminal velocity. */
    public final boolean hasBoostRamp;
    /** Parabolic arc apex layered on the lerp, in cells. {@code 0} = flat (direct-fire rocket / level missile); {@code > 0} = lobbed (mortar / arc shot). */
    public final float arcHeight;
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

    /**
     * Fraction of total flight time spent in the booster-ramp phase. Position
     * grows quadratically over this window (constant acceleration from 0
     * velocity), then transitions to linear travel at terminal velocity. The
     * end-of-boost and start-of-cruise velocities are matched for C1
     * continuity — no visible kink at the transition.
     */
    public static final float BOOST_FRACTION = 0.25f;
    /**
     * Distance fraction covered by the end of the boost phase, derived so the
     * velocity is C1 continuous at the boost/cruise junction:
     * {@code BOOST_FRACTION / (2 - BOOST_FRACTION) ≈ 0.143}. The rocket spends
     * 25% of its flight covering 14% of the distance (slow boost), then 75%
     * of its flight covering the remaining 86% at terminal velocity.
     */
    public static final float BOOST_DIST = BOOST_FRACTION / (2f - BOOST_FRACTION);
    /** Quadratic coefficient applied during boost: {@code BOOST_DIST / BOOST_FRACTION²}. */
    private static final float BOOST_QUAD_COEFF = BOOST_DIST / (BOOST_FRACTION * BOOST_FRACTION);
    /** Terminal velocity relative to the constant-velocity baseline. With BOOST_FRACTION=0.25, this is ~1.143 — the cruise leg covers ground 14% faster than a same-flight-time constant-velocity rocket would. */
    private static final float TERMINAL_VEL = (1f - BOOST_DIST) / (1f - BOOST_FRACTION);

    /**
     * Boost-then-cruise curve: maps a linear time fraction {@code t ∈ [0,1]}
     * to a position fraction. Quadratic ease-in over {@link #BOOST_FRACTION},
     * then linear at terminal velocity. Reads as "booster ignites, rocket
     * accelerates from the launch tube, then sustains terminal velocity."
     * Static so the renderer can apply the same curve when computing the
     * projectile sprite's screen-space lerp position.
     */
    public static float applyBoostCurve(float linearT) {
        if (linearT <= 0f) return 0f;
        if (linearT >= 1f) return 1f;
        if (linearT < BOOST_FRACTION) {
            return BOOST_QUAD_COEFF * linearT * linearT;
        }
        return BOOST_DIST + (linearT - BOOST_FRACTION) * TERMINAL_VEL;
    }

    public Projectile(float fromX, float fromY, float toX, float toY,
                      boolean hasBoostRamp, float arcHeight,
                      Faction shooterFaction, boolean aerialDelivery,
                      float totalFlightTime, PendingDetonation onArrival) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.hasBoostRamp = hasBoostRamp;
        this.arcHeight = arcHeight;
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

    /** Current world-space X (cells). Rocket-class projectiles use the boost curve so they start slow and accelerate to terminal velocity; chemical-charge shells (no boost ramp) travel at constant speed. */
    public float currentX() {
        float raw = progress();
        float p = hasBoostRamp ? applyBoostCurve(raw) : raw;
        return fromX + (toX - fromX) * p;
    }

    /** Current world-space Y (cells). Parabolic arc layered on top of the (possibly curved) lerp. Arc peak follows the curved progress so a rocket's arc apex shifts toward late-flight (where it actually is) rather than time-midpoint. */
    public float currentY() {
        float raw = progress();
        float p = hasBoostRamp ? applyBoostCurve(raw) : raw;
        float y = fromY + (toY - fromY) * p;
        if (arcHeight > 0f) {
            y += arcHeight * 4f * p * (1f - p);
        }
        return y;
    }

    public boolean isExpired() {
        return remainingTime <= 0f;
    }
}
