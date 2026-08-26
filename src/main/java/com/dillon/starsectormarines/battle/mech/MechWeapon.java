package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.awt.Color;

/**
 * Id-only compatibility handles for the shipped mech weapon families.
 * Projectile behavior and presentation live in the installed weapon catalog;
 * {@link MechWeaponComponent} retains rack size, ammunition, hardpoint family,
 * and mount appearance.
 *
 * <p>The constants survive temporarily while mech tactics and shot events use
 * enum identity. W4 replaces those compatibility boundaries with stable ids.
 */
public enum MechWeapon {

    /** Close-band sustained saturation weapon. */
    CHAINGUN("weapon.mech-chaingun"),
    /** Direct-fire support cannon. */
    LINEAR_CANNON("weapon.mech-linear-cannon"),
    /** Precise anti-armor cannon with a compact blast. */
    HEAVY_CANNON("weapon.mech-heavy-cannon"),
    /** Mid-close finite rocket salvo. */
    SRM_POD("weapon.mech-srm-pod"),
    /** Long-range indirect artillery salvo. */
    LRM_ARTILLERY("weapon.mech-lrm-artillery");

    /** Stable registry id carried by mount definitions and future saves. */
    public final String id;

    MechWeapon(String id) {
        this.id = id;
    }

    public static MechWeapon fromId(String id) {
        for (MechWeapon weapon : values()) {
            if (weapon.id.equals(id)) return weapon;
        }
        throw new IllegalArgumentException("Unknown mech weapon id '" + id + "'");
    }

    public WeaponDef def() { return WeaponRegistry.require(id); }

    public String displayName() { return def().displayName; }
    public String fireSoundId() { return def().fireSoundId; }
    public Color tracerColor() { return def().tracerColor; }
    public float range() { return def().range; }
    public float damage() { return def().damage; }
    public float accuracy() { return def().accuracy; }
    public float cooldown() { return def().cooldown; }
    public float penetration() { return def().penetration; }
    public ImpactProfile impactProfile() { return def().impactProfile; }
    public int burstCount() { return def().burstCount; }
    public float burstSpacing() { return def().burstSpacing; }
    public String projectileSpritePath() { return def().projectileSpritePath; }
    public float projectileVisualCells() { return def().projectileVisualCells; }
    public float flightSec() { return def().flightSec; }
    public float arcHeight() { return def().arcHeight; }
    public float hitSpread() { return def().hitSpread; }
    public boolean engineTrail() { return def().engineTrail; }
    public float aoeRadius() { return def().aoeRadius; }
    public int wallDamage() { return def().wallDamage; }
    public float wallDamageRadius() { return def().wallDamageRadius; }
    public float roundVelocity() { return def().roundVelocity; }
    public boolean indirectFire() { return def().indirectFire; }
    public float noLosAccuracyMult() { return def().noLosAccuracyMult; }
    public boolean interceptableProjectile() { return def().interceptableProjectile; }
    public boolean boostRamp() { return def().boostRamp; }
}
