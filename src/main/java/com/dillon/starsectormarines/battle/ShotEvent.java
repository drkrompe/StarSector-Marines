package com.dillon.starsectormarines.battle;

/**
 * Visual record of a single shot fired by a unit in {@link BattleSimulation}.
 * Emitted on every fire — hit or miss — so the renderer can draw a tracer
 * even when no damage lands. The endpoint is the target's cell on a hit and
 * a randomized near-miss offset on a miss, so the visual reads as a real
 * stray round rather than a dud.
 *
 * <p>{@link #turretKind} is the bridge between the sim's faction-only
 * abstraction and the renderer's per-weapon FX. When the shooter is a
 * {@link MapTurret}, the sim populates this so the renderer can substitute the
 * vanilla projectile sprite + per-kind fire sound for the marine line-tracer +
 * rifle SFX. Null for marine / militia / alien rifle fire.
 *
 * <p>Lifetime is in sim seconds, ticked by {@link BattleSimulation#advance}.
 * That keeps shots paused with the rest of the sim, but at 4× speed shots
 * flash by quickly — readability hasn't been a problem in playtest yet.
 */
public class ShotEvent {

    public final float fromX;
    public final float fromY;
    public final float toX;
    public final float toY;
    public final boolean hit;
    public final Faction shooterFaction;
    /** Non-null when the shooter is a turret — drives projectile sprite + fire sound. Null = unit rifle fire (rendered as a line, played as a rifle clip). */
    public final TurretKind turretKind;
    /** Non-null when a marine fired their primary — drives tracer color + per-weapon fire sound. Mutually exclusive with {@link #turretKind} and {@link #marineSecondary}. */
    public final MarineWeapon marineWeapon;
    /** Non-null when a marine fired their secondary (rocket, etc.) — drives projectile sprite + impact recipe. Mutually exclusive with {@link #turretKind} and {@link #marineWeapon}. */
    public final MarineSecondary marineSecondary;
    /** Non-null when a mech fired one of its chassis weapons (chaingun, SRM pod, LRM). Drives projectile sprite + fire/impact sound + impact profile. Mutually exclusive with all the other source tags. */
    public final MechWeapon mechWeapon;
    /** Scales the morale drain this shot inflicts if it counts as a near-miss against a hostile squad. Sourced from the shooter's {@link UnitType#moraleImpact} at fire time. Defaults to 1.0 for shots emitted by paths that don't thread shooter type (detonations, legacy callers). */
    public final float moraleImpact;

    public float lifetime;
    /** Initial lifetime — fixed at construction. Renderer uses this (not the global shot-lifetime constant) to compute fade-out alpha and projectile travel progress, so per-weapon flight times scale correctly. */
    public final float lifetimeMax;

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime, null, null, null, null, 1.0f);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime, TurretKind turretKind) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime, turretKind, null, null, null, 1.0f);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon, MarineSecondary marineSecondary) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, null, 1.0f);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, mechWeapon, 1.0f);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon,
                     float moraleImpact) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
        this.hit = hit;
        this.shooterFaction = shooterFaction;
        this.lifetime = lifetime;
        this.lifetimeMax = lifetime;
        this.turretKind = turretKind;
        this.marineWeapon = marineWeapon;
        this.marineSecondary = marineSecondary;
        this.mechWeapon = mechWeapon;
        this.moraleImpact = moraleImpact;
    }
}
