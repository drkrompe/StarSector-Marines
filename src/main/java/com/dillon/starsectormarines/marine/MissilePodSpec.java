package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The missile pod's authored delivery: which micro-missile round the suit's
 * shoulder mount fires ({@code integral-armor-systems.md}).
 *
 * <p>The salvo shape — how many missiles launch together, their spread, range,
 * damage, and penetration — belongs entirely to the referenced
 * {@link WeaponDef}. Duplicating {@code projectilesPerShot}, damage, or range
 * here would create a second place those numbers could disagree, which
 * {@code moddable-weapons-nouns.md}'s "one weapon behavior has one
 * authoritative authored value" law forbids. This spec only names which
 * weapon the pod fires and how many salvos the suit carries; the latter lives
 * on {@link IntegralSystemDef#startingAmmo()} alongside every other
 * ammunition-gated system, not duplicated here.
 *
 * <p>The referenced weapon must sit in the marine-secondary mount family — the
 * same family the carried rocket launcher and frag grenade use. A shoulder pod
 * on a battlesuit is still a one-person infantry billet's ordnance; reaching
 * for a mech-mount or turret-mount definition here would smuggle in the mech
 * authority {@code integral-armor-systems.md} explicitly forbids.
 */
public record MissilePodSpec(String weaponId) implements Serializable {

    static MissilePodSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        String weaponId = json.optString("weaponId", null);
        if (weaponId == null || weaponId.trim().isEmpty()) {
            throw new JSONException("Missile pod '" + systemId + "' on armor '" + armorId
                    + "' must declare weaponId");
        }
        weaponId = weaponId.trim();
        WeaponRegistry registry = WeaponRegistry.installed();
        WeaponDef weapon = registry != null ? registry.get(weaponId) : null;
        if (weapon == null) {
            throw new JSONException("Missile pod '" + systemId + "' on armor '" + armorId
                    + "' references unknown weapon id '" + weaponId + "'");
        }
        if (weapon.mount != MountClass.MARINE_SECONDARY) {
            throw new JSONException("Missile pod '" + systemId + "' on armor '" + armorId
                    + "' references '" + weaponId + "', which is not a marine-secondary weapon."
                    + " A shoulder pod is infantry ordnance carried by the suit, never a mech"
                    + " mount (integral-armor-systems.md).");
        }
        return new MissilePodSpec(weaponId);
    }

    /** The authoritative micro-missile behavior this pod fires. */
    public WeaponDef weaponDef() {
        return WeaponRegistry.require(weaponId);
    }
}
