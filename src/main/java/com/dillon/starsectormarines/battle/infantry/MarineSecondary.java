package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.fx.ImpactProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.awt.Color;

/**
 * Transitional battle handle for weapon-like special equipment. Campaign
 * persistence uses {@link #specialEquipmentId}; projectile behavior resolves
 * through the referenced {@link WeaponDef}. Utility specials use the same
 * stable item registry later without becoming members of this weapon handle.
 */
public enum MarineSecondary {
    ROCKET_LAUNCHER(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
    ANTI_MATERIEL_RIFLE(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID);

    public final String specialEquipmentId;

    MarineSecondary(String specialEquipmentId) {
        this.specialEquipmentId = specialEquipmentId;
    }

    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.require(specialEquipmentId);
    }

    public WeaponDef def() {
        return WeaponRegistry.require(specialDef().weaponId());
    }

    public String displayName() { return specialDef().displayName(); }
    public SpecialActivation activation() { return specialDef().activation(); }
    public int startingAmmo() { return specialDef().startingAmmo(); }
    public String aimSpritePath() { return specialDef().aimSpritePath(); }
    public String armoryIconPath() { return specialDef().armoryIconPath(); }

    public String fireSoundId() { return def().fireSoundId; }
    public String impactSoundId() { return def().impactSoundId; }
    public String projectileSpritePath() { return def().projectileSpritePath; }
    public float projectileVisualCells() { return def().projectileVisualCells; }
    public Color tracerColor() { return def().tracerColor; }
    public ImpactProfile impactProfile() { return def().impactProfile; }
    public float range() { return def().range; }
    public float damage() { return def().damage; }
    public float accuracy() { return def().accuracy; }
    public float cooldown() { return def().cooldown; }
    public float penetration() { return def().penetration; }
    public float flightSec() { return def().flightSec; }
    public float aimDuration() { return def().aimDuration; }
    public float aoeRadius() { return def().aoeRadius; }
    public int wallDamage() { return def().wallDamage; }
    public float wallDamageRadius() { return def().wallDamageRadius; }
    public float roundVelocity() { return def().roundVelocity; }
}
