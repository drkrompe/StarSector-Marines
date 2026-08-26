package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.mech.MechWeapon;
import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

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
    /** Firing entity id, or {@code 0L} for anonymous/aerial legacy sources. */
    public final long shooterId;
    /** Non-null when the shooter is a turret — drives projectile sprite + fire sound. */
    public final TurretKind turretKind;
    /** Non-null when a marine fired their primary — drives tracer color + per-weapon fire sound. Mutually exclusive with {@link #turretKind} and {@link #marineSecondary}. */
    public final MarineWeapon marineWeapon;
    /** Authoritative primary definition, including contributed weapons without an enum handle. */
    public final WeaponDef primaryWeaponDef;
    /** Non-null when a marine fired their secondary (rocket, etc.) — drives projectile sprite + impact recipe. Mutually exclusive with {@link #turretKind} and {@link #marineWeapon}. */
    public final MarineSecondary marineSecondary;
    /** Authoritative special-equipment source, including contributed items. */
    public final SpecialEquipmentDef specialEquipmentDef;
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

    public ShotEvent(long shooterId, float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime) {
        this(fromX, fromY, 0f, toX, toY, 0f, hit, shooterFaction, lifetime,
                null, null, null, null, 1f, false, null, shooterId);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime, TurretKind turretKind) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime, turretKind, null, null, null, 1.0f);
    }

    public ShotEvent(long shooterId, float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind) {
        this(fromX, fromY, 0f, toX, toY, 0f, hit, shooterFaction, lifetime,
                turretKind, null, null, null, 1f, false, null, shooterId);
    }

    public ShotEvent(float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon, MarineSecondary marineSecondary) {
        this(fromX, fromY, toX, toY, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, null, 1.0f);
    }

    public ShotEvent(long shooterId, float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary) {
        this(fromX, fromY, 0f, toX, toY, 0f, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, null, 1f,
                false, null, shooterId);
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

    public ShotEvent(long shooterId, float fromX, float fromY, float toX, float toY,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon,
                     float moraleImpact) {
        this(fromX, fromY, 0f, toX, toY, 0f, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, mechWeapon,
                moraleImpact, false, null, shooterId);
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
        this(fromX, fromY, fromZ, toX, toY, toZ, hit, shooterFaction, lifetime,
                turretKind, marineWeapon, marineSecondary, mechWeapon,
                moraleImpact, struckUnit, stopKind, 0L);
    }

    public ShotEvent(float fromX, float fromY, float fromZ,
                     float toX, float toY, float toZ,
                     boolean hit, Faction shooterFaction, float lifetime,
                     TurretKind turretKind, MarineWeapon marineWeapon,
                     MarineSecondary marineSecondary, MechWeapon mechWeapon,
                     float moraleImpact, boolean struckUnit,
                     BallisticResolver.StopKind stopKind, long shooterId) {
        this(fromX, fromY, fromZ, toX, toY, toZ, hit, shooterFaction, lifetime,
                turretKind, marineWeapon,
                marineWeapon != null ? marineWeapon.def() : null,
                marineSecondary != null ? marineSecondary.specialDef() : null,
                mechWeapon, moraleImpact, struckUnit, stopKind, shooterId);
    }

    /** Creates a primary shot from an arbitrary catalog definition. */
    public static ShotEvent primary(float fromX, float fromY, float fromZ,
                                    float toX, float toY, float toZ,
                                    boolean hit, Faction shooterFaction, float lifetime,
                                    WeaponDef weapon, float moraleImpact, boolean struckUnit,
                                    BallisticResolver.StopKind stopKind, long shooterId) {
        MarineWeapon compatibility;
        try {
            compatibility = weapon != null ? MarineWeapon.fromId(weapon.id) : null;
        } catch (IllegalArgumentException ignored) {
            compatibility = null;
        }
        return new ShotEvent(fromX, fromY, fromZ, toX, toY, toZ, hit,
                shooterFaction, lifetime, null, compatibility, weapon, null, null,
                moraleImpact, struckUnit, stopKind, shooterId);
    }

    /** Creates a shot from arbitrary data-authored special equipment. */
    public static ShotEvent special(float fromX, float fromY, float fromZ,
                                    float toX, float toY, float toZ,
                                    boolean hit, Faction shooterFaction, float lifetime,
                                    SpecialEquipmentDef equipment, float moraleImpact,
                                    boolean struckUnit, BallisticResolver.StopKind stopKind,
                                    long shooterId) {
        return new ShotEvent(fromX, fromY, fromZ, toX, toY, toZ, hit,
                shooterFaction, lifetime, null, null, null, equipment, null,
                moraleImpact, struckUnit, stopKind, shooterId);
    }

    private ShotEvent(float fromX, float fromY, float fromZ,
                      float toX, float toY, float toZ,
                      boolean hit, Faction shooterFaction, float lifetime,
                      TurretKind turretKind, MarineWeapon marineWeapon,
                      WeaponDef primaryWeaponDef, SpecialEquipmentDef specialEquipmentDef,
                      MechWeapon mechWeapon, float moraleImpact, boolean struckUnit,
                      BallisticResolver.StopKind stopKind, long shooterId) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.fromZ = fromZ;
        this.toX = toX;
        this.toY = toY;
        this.toZ = toZ;
        this.hit = hit;
        this.shooterFaction = shooterFaction;
        this.shooterId = shooterId;
        this.lifetime = lifetime;
        this.lifetimeMax = lifetime;
        this.turretKind = turretKind;
        this.marineWeapon = marineWeapon;
        this.primaryWeaponDef = primaryWeaponDef;
        this.specialEquipmentDef = specialEquipmentDef;
        this.marineSecondary = specialEquipmentDef != null
                ? SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentDef.id()) : null;
        this.mechWeapon = mechWeapon;
        this.moraleImpact = moraleImpact;
        this.struckUnit = struckUnit;
        this.stopKind = stopKind;
    }

    /** Source link safe to expose to hearing; indirect launches stay anonymous. */
    public long audibleSourceUnitId() {
        return isIndirectFire()
                || specialEquipmentDef != null
                && specialEquipmentDef.activation() == SpecialActivation.DIRECT_PRECISION
                ? 0L : shooterId;
    }

    public boolean isIndirectFire() {
        if (specialEquipmentDef != null && (specialEquipmentDef.weaponDef().indirectFire
                || specialEquipmentDef.arcHeight() > 0f)) return true;
        if (mechWeapon != null && mechWeapon.arcHeight() > 0f) return true;
        return turretKind != null
                && (turretKind.indirectFire() || turretKind.arcHeight() > 0f);
    }

    /** Coarse ground-combat loudness used by the squad hearing model. */
    public float noiseMagnitude() {
        if (specialEquipmentDef != null) return 2.5f;
        if (mechWeapon != null) return Math.min(4f, 2f + mechWeapon.aoeRadius());
        if (turretKind != null) return Math.min(4f, 1.5f + turretKind.aoeRadius());
        if (primaryWeaponDef != null && primaryWeaponDef.impactProfile == ImpactProfile.KINETIC) {
            return 1.4f;
        }
        return 1f;
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

    /** Carrier-agnostic presentation class for muzzle, impact, light, decal, and audio recipes. */
    public ImpactProfile impactProfile() {
        if (turretKind != null) return turretKind.impactProfile();
        if (specialEquipmentDef != null) return specialEquipmentDef.impactProfile();
        if (primaryWeaponDef != null) return primaryWeaponDef.impactProfile;
        if (mechWeapon != null) return mechWeapon.impactProfile();
        return ImpactProfile.RIFLE;
    }
}
