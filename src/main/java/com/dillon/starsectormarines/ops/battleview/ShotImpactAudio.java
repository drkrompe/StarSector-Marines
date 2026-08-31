package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.turret.TurretImpactAudio;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/** Shared authored impact-cue selection for both ground-battle presentation hosts. */
public final class ShotImpactAudio {

    private ShotImpactAudio() {}

    public static Cue resolve(ShotEvent shot, String explosiveFallback) {
        if (shot == null || !shot.impacts()) return null;
        if (shot.turretStructureDef != null) {
            TurretImpactAudio.Cue cue = TurretImpactAudio.resolve(
                    shot.turretStructureDef, explosiveFallback);
            return cue != null ? new Cue(cue.soundId(), cue.volume()) : null;
        }

        WeaponDef weapon;
        float volume;
        boolean allowExplosiveFallback;
        if (shot.specialEquipmentDef != null) {
            weapon = shot.specialEquipmentDef.weaponDef();
            volume = 0.70f;
            allowExplosiveFallback = false;
        } else if (shot.primaryWeaponDef != null) {
            weapon = shot.primaryWeaponDef;
            volume = 0.55f;
            allowExplosiveFallback = false;
        } else if (shot.mechWeaponDef != null) {
            weapon = shot.mechWeaponDef;
            volume = weapon.fx.hasHeavyImpact() ? 0.86f : 0.65f;
            allowExplosiveFallback = true;
        } else {
            return null;
        }

        String soundId = weapon.impactSoundId;
        if (soundId == null && allowExplosiveFallback && weapon.fx.hasExplosiveImpact()) {
            soundId = explosiveFallback;
        }
        return soundId != null ? new Cue(soundId, volume) : null;
    }

    public record Cue(String soundId, float volume) {
        public Cue {
            if (soundId == null || soundId.isBlank()) {
                throw new IllegalArgumentException("impact sound id is required");
            }
        }
    }
}
