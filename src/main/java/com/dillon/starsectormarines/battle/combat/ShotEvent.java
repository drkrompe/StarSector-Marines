package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;

import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;

/**
 * Visual record of a single shot fired by a unit in {@link BattleSimulation}.
 * Emitted on every fire — hit or miss — so the renderer can draw the resolved
 * round body even when no damage lands. The endpoint is the physical stop
 * selected by the ballistic resolver (unit, cover, wall, or overshoot).
 * Lightweight {@code fromZ}/{@code toZ} offsets project into screen Y so a
 * miss can visibly fly high or low without introducing full 3D physics.
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
    public final float fromZ;
    public final float toX;
    public final float toY;
    public final float toZ;
    public final boolean hit;
    public final Faction shooterFaction;
    /** Non-null when the shooter is a turret — drives projectile sprite + fire sound. */
    public final TurretKind turretKind;
    /** Non-null when a marine fired their primary — drives tracer color + per-weapon fire sound. Mutually exclusive with {@link #turretKind} and {@link #marineSecondary}. */
    public final MarineWeapon marineWeapon;
    /** Non-null when a marine fired their secondary (rocket, etc.) — drives projectile sprite + impact recipe. Mutually exclusive with {@link #turretKind} and {@link #marineWeapon}. */
    public final MarineSecondary marineSecondary;
    /** Non-null when a mech fired one of its chassis weapons (chaingun, SRM pod, LRM). Drives projectile sprite + fire/impact sound + impact profile. Mutually exclusive with all the other source tags. */
    public final MechWeapon mechWeapon;
    /** Scales the morale drain this shot inflicts if it counts as a near-miss against a hostile squad. Sourced from the shooter's {@link UnitType#moraleImpact} at fire time. Defaults to 1.0 for shots emitted by paths that don't thread shooter type (detonations, legacy callers). */
    public final float moraleImpact;
    /**
     * True when the resolved round physically damaged ANY unit — the locked
     * target or an incidental contact — regardless of {@link #hit} (which
     * only means "hit the locked target," see the class doc). Near-miss
     * morale drain in {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem}
     * must skip such shots: a round that struck someone (even incidentally)
     * already drains morale through the hit path, and treating it as a
     * near-miss too would double-drain via a stale cooldown window. Defaults
     * false for callers that don't thread a resolved victim.
     */
    public final boolean struckUnit;
    /** Resolver stop kind for physical rounds; null for legacy/direct visual events. */
    public final BallisticResolver.StopKind stopKind;

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
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, mechWeapon, moraleImpact, false);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon,
                     float moraleImpact, boolean struckUnit) {
        this(fromX, fromY, 0f, toX, toY, 0f, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, mechWeapon,
                moraleImpact, struckUnit, null);
    }

    public ShotEvent(float fromX, float fromY, float fromZ,
                     float toX, float toY, float toZ,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon,
                     float moraleImpact, boolean struckUnit,
                     BallisticResolver.StopKind stopKind) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.hit = hit;
        this.shooterFaction = shooterFaction;
        this.lifetime = lifetime;
        this.lifetimeMax = lifetime;
        this.turretKind = turretKind;
        this.marineWeapon = marineWeapon;
        this.marineSecondary = marineSecondary;
        this.mechWeapon = mechWeapon;
        this.moraleImpact = moraleImpact;
        this.struckUnit = struckUnit;
        this.stopKind = stopKind;
    }

    /** Screen-space map Y after applying the lightweight elevation offset. */
    public float visualFromY() {
        return fromY + fromZ;
    }

    /** Screen-space map Y after applying the lightweight elevation offset. */
    public float visualToY() {
        return toY + toZ;
    }

    /** Whether expiry represents a physical impact rather than free-flight overshoot. */
    public boolean impacts() {
        return stopKind != BallisticResolver.StopKind.OVERSHOOT;
    }
}
