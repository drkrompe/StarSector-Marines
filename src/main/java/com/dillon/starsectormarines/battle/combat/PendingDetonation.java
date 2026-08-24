package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;


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

    /**
     * Entity credited with everything this detonation does, or
     * {@link com.dillon.starsectormarines.battle.sim.CombatTelemetryService#NO_ATTACKER}
     * for a source with no entity behind it (an orbital barrage). Telemetry
     * attribution only: the splash is identical either way, and a shooter
     * already dead when the shell lands is simply not credited.
     */
    public final long shooterId;
    public final float endpointX;
    public final float endpointY;
    /** Sim-seconds until detonation. Decremented per tick by {@code Detonations.tick}. */
    public float remainingTime;
    /** Splash radius in cells. Every unit within this distance of the endpoint with line of sight to it takes damage (cover-reduced). */
    public final float aoeRadius;
    /** Damage applied to each unit in the splash, before cover reduction. */
    public final float damage;
    /** Efficiency input against actor armor. */
    public final float penetration;
    /**
     * Optional actor physically contacted by the incoming round. {@code 0}
     * means the detonation has only its area payload. A live contacted actor
     * receives {@link #directDamage} with {@link #directPenetration} before
     * area damage is gathered, and is excluded from that area payload so the
     * direct value remains the authored total for a contact hit.
     */
    public final long directTargetId;
    /**
     * Damage carried by the physical contact, before ordinary target-side
     * resolution. The firing procedure owns any source-side multiplier: a
     * friendly direct hit therefore carries its already-reduced value here.
     */
    public final float directDamage;
    /** Efficiency input against actor armor for the physical contact. */
    public final float directPenetration;
    /** Wall HP knocked off the endpoint cell on detonation. 0 = no structural damage. */
    public final int wallDamage;
    /** Faction of the firing unit. Currently unused (FF on); captured for future per-side filters. */
    public final Faction shooterFaction;
    /**
     * True when the projectile is conceptually delivered from above (LRM /
     * mortar arcs, shuttle / fighter weapons). Used by the AoE pipeline as
     * the discriminator for the roof-shield rule: aerial deliveries are
     * intercepted by intact roofs; ground deliveries (rocket through a
     * doorway, SRM line-of-sight rocket) explode at the endpoint and damage
     * the inside of a roofed building normally.
     */
    public final boolean aerialDelivery;
    /**
     * Radius (in cells) over which {@link #wallDamage} is applied. {@code 0}
     * = endpoint-only (existing rocket / LRM behavior); {@code > 0} = damage
     * every wall cell within radius of the endpoint (fighter missile / heavy
     * blast behavior — flattens whole wall sections in one detonation).
     */
    public final float wallDamageRadius;
    /**
     * When {@code true}, every wall collapse caused by this detonation emits
     * a dust-burst FX event via {@code WeaponSimContext.spawnDustBurst}. Used
     * by the heavy-blast variants (fighter missile) where each collapse
     * should read as a chunky structural breach; cheaper rockets leave it
     * off so their endpoint-wall hits are quiet.
     */
    public final boolean spawnDustOnWallBreak;
    /**
     * When {@code true}, units of {@link #shooterFaction} are skipped in the
     * splash damage loop — friendly fire OFF for this detonation. Used by
     * called-in air support (flyby missiles) where killing your own marines
     * with the strike you ordered reads as a game bug, not a player skill
     * issue. Ground rockets / mech weapons leave it {@code false} (FF on by
     * default — players deciding to fire those have aim control).
     */
    public final boolean friendlyFireImmune;
    /** Whether the impact leaves a short-lived fire-and-smoke plume instead of smoke alone. */
    public final boolean burningPlume;

    /**
     * Compact constructor — defaults the heavy-blast knobs off. Used by mech
     * LRM / SRM, marine rocket, and turret detonations whose wall damage
     * stays at the endpoint cell and which still apply friendly fire.
     */
    public PendingDetonation(long shooterId,
                             float endpointX, float endpointY, float remainingTime,
                             float aoeRadius, float damage, float penetration,
                             int wallDamage, Faction shooterFaction,
                             boolean aerialDelivery) {
        this(shooterId, endpointX, endpointY, remainingTime, aoeRadius, damage, penetration,
                wallDamage, shooterFaction, aerialDelivery,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false, /*friendlyFireImmune*/ false,
                /*directTargetId*/ 0L, /*directDamage*/ 0f, /*directPenetration*/ 0f,
                /*burningPlume*/ false);
    }

    public PendingDetonation(long shooterId,
                             float endpointX, float endpointY, float remainingTime,
                             float aoeRadius, float damage, float penetration,
                             int wallDamage, Faction shooterFaction,
                             boolean aerialDelivery,
                             float wallDamageRadius,
                             boolean spawnDustOnWallBreak,
                             boolean friendlyFireImmune) {
        this(shooterId, endpointX, endpointY, remainingTime, aoeRadius, damage, penetration,
                wallDamage, shooterFaction, aerialDelivery,
                wallDamageRadius, spawnDustOnWallBreak, friendlyFireImmune,
                /*directTargetId*/ 0L, /*directDamage*/ 0f, /*directPenetration*/ 0f,
                /*burningPlume*/ false);
    }

    public PendingDetonation(long shooterId,
                             float endpointX, float endpointY, float remainingTime,
                             float aoeRadius, float damage, float penetration,
                             int wallDamage, Faction shooterFaction,
                             boolean aerialDelivery,
                             float wallDamageRadius,
                             boolean spawnDustOnWallBreak,
                             boolean friendlyFireImmune,
                             long directTargetId,
                             float directDamage,
                             float directPenetration,
                             boolean burningPlume) {
        this.shooterId     = shooterId;
        this.endpointX     = endpointX;
        this.endpointY     = endpointY;
        this.remainingTime = remainingTime;
        this.aoeRadius     = aoeRadius;
        this.damage        = damage;
        this.penetration   = penetration;
        this.directTargetId = directTargetId;
        this.directDamage = directDamage;
        this.directPenetration = directPenetration;
        this.wallDamage    = wallDamage;
        this.shooterFaction = shooterFaction;
        this.aerialDelivery = aerialDelivery;
        this.wallDamageRadius = wallDamageRadius;
        this.spawnDustOnWallBreak = spawnDustOnWallBreak;
        this.friendlyFireImmune = friendlyFireImmune;
        this.burningPlume = burningPlume;
    }
}
