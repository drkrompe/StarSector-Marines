package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.weapon.ContrailProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * What a weapon throws, as the direct-control HUD draws its load: cartridges and belts, a rack of
 * missiles, or a capacitor that charges between shots.
 */
public enum Ordnance {
    BALLISTIC, MISSILE, ENERGY;

    /**
     * Read off how the weapon is drawn, since the catalog keeps no separate family: a missile flies a
     * {@code missile_} sprite, trails a contrail or boosts off the rail, an energy weapon draws a beam, is
     * held as a laser, or is authored {@code render.energy}. The base game keeps its shells beside its
     * missiles, so a sprite's folder says nothing; its name does.
     */
    public static Ordnance of(WeaponDef weapon) {
        if (weapon == null) return BALLISTIC;
        if (weapon.energy || weapon.beamStyle != WeaponDef.BeamStyle.DEFAULT
                || weapon.heldSpriteFamily == LayeredWeaponFamily.LASER_GUN) return ENERGY;
        String sprite = weapon.projectileSpritePath;
        boolean missileSprite = sprite != null && sprite.substring(sprite.lastIndexOf('/') + 1).startsWith("missile");
        if (missileSprite || weapon.contrailProfile != ContrailProfile.NONE || weapon.boostRamp) return MISSILE;
        return BALLISTIC;
    }

    /** A mech hardpoint says so outright. */
    public static Ordnance of(HardpointType hardpoint) {
        return switch (hardpoint) {
            case BALLISTIC -> BALLISTIC;
            case MISSILE -> MISSILE;
            case ENERGY -> ENERGY;
        };
    }
}
