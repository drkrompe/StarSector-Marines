package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/**
 * What a weapon throws, as the direct-control HUD draws its load: cartridges and belts, a rack of
 * missiles, or a capacitor that charges between shots.
 */
public enum Ordnance {
    BALLISTIC, MISSILE, ENERGY;

    /**
     * Read off how the weapon is drawn, since the catalog keeps no separate family: a missile flies a
     * missile sprite or trails a contrail, an energy weapon draws a beam, is held as a laser, or is authored
     * {@code render.energy}.
     */
    public static Ordnance of(WeaponDef weapon) {
        if (weapon == null) return BALLISTIC;
        if (weapon.energy || weapon.beamStyle != WeaponDef.BeamStyle.DEFAULT
                || weapon.heldSpriteFamily == LayeredWeaponFamily.LASER_GUN) return ENERGY;
        boolean missileSprite = weapon.projectileSpritePath != null
                && weapon.projectileSpritePath.contains("/missiles/");
        if (missileSprite || weapon.contrailProfile != null || weapon.boostRamp) return MISSILE;
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
