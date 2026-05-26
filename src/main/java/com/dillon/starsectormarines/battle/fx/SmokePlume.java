package com.dillon.starsectormarines.battle.fx;

/**
 * Per-detonation smoke emitter parked at an HE impact site. Spawned by
 * {@link com.dillon.starsectormarines.battle.weapons.Detonations} whenever
 * a detonation with {@code aoeRadius > 0} resolves; the sim ages
 * {@link #remainingLifetime} and uses {@link #nextPuffTimer} to schedule
 * smoke-puff emissions the renderer drains into the impact FX engine.
 *
 * <p>Lighter-weight cousin to {@link SmokingWreck}: shorter lifetime, no
 * fire-burst phase, fractional cell positions (detonations don't snap to a
 * mount cell the way wrecks do). The shared {@code smokePuffsThisFrame}
 * pipeline carries both, so the renderer needs no new drain.
 *
 * <p>Plume radius scales with the remaining-lifetime fraction: the puff
 * cluster billows hard right after impact and tightens as the column rises
 * and dissipates.
 */
public final class SmokePlume {

    public final float x;
    public final float y;
    public final float totalLifetime;
    public float remainingLifetime;
    /** Sim-seconds until the next smoke puff. Reset on each emission with mild jitter. Starts at 0 so the plume emits immediately on its first tick. */
    public float nextPuffTimer;

    public SmokePlume(float x, float y, float totalLifetime) {
        this.x = x;
        this.y = y;
        this.totalLifetime = totalLifetime;
        this.remainingLifetime = totalLifetime;
        this.nextPuffTimer = 0f;
    }
}
