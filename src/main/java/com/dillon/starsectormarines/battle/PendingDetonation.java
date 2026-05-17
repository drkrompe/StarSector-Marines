package com.dillon.starsectormarines.battle;

/**
 * A rocket / missile in flight, scheduled to detonate at a specific cell after
 * its flight time elapses. Queued by the sim at fire time and drained by the
 * per-tick {@code Detonations.tick} pass. The
 * physics-based replacement for "apply damage at fire time" — a rocket fired
 * now damages whatever is *actually at the impact endpoint when it arrives*,
 * not whatever was the locked target when the launch animation played.
 *
 * <p>{@link #shooterFaction} is captured for future targeting filters (e.g.
 * a friendly-fire toggle, or weapons that explicitly ignore allies). For now
 * friendly fire is ON unconditionally — the sim doesn't read it.
 *
 * <p>Lifetime matches the projectile's visible flight time so detonation
 * aligns with the impact FX dispatched on {@code shotsExpiredThisFrame}.
 * The two systems are paired but separate: ShotEvent owns visuals,
 * PendingDetonation owns damage.
 */
public final class PendingDetonation {

    public final float endpointX;
    public final float endpointY;
    /** Sim-seconds until detonation. Decremented per tick by {@code Detonations.tick}. */
    public float remainingTime;
    /** Splash radius in cells. Every unit within this distance of the endpoint with line of sight to it takes damage (cover-reduced). */
    public final float aoeRadius;
    /** Damage applied to each unit in the splash, before cover reduction. */
    public final float damage;
    /** Damage multiplier vs {@link MapTurret} targets — rockets shred turrets. */
    public final float vsTurretMult;
    /** Wall HP knocked off the endpoint cell on detonation. 0 = no structural damage. */
    public final int wallDamage;
    /** Faction of the firing unit. Currently unused (FF on); captured for future per-side filters. */
    public final Faction shooterFaction;

    public PendingDetonation(float endpointX, float endpointY, float remainingTime,
                             float aoeRadius, float damage, float vsTurretMult,
                             int wallDamage, Faction shooterFaction) {
        this.endpointX     = endpointX;
        this.endpointY     = endpointY;
        this.remainingTime = remainingTime;
        this.aoeRadius     = aoeRadius;
        this.damage        = damage;
        this.vsTurretMult  = vsTurretMult;
        this.wallDamage    = wallDamage;
        this.shooterFaction = shooterFaction;
    }
}
