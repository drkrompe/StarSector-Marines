package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.panel.Ordnance;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * One piece of a marine's carried kit as the fitting rooms draw hardware: a 1x2 brick-stack
 * standing upright, coloured by what it throws (ballistic yellow, energy red-orange, missile
 * green, as a mech hardpoint), with a piece that throws nothing in utility teal. The colour
 * is read off the weapon's own data by {@link Ordnance#of(WeaponDef)}, the reading the
 * direct-control HUD draws its load by, so a new or retuned weapon takes its colour from its
 * authoring.
 *
 * @param icon the item's armoury art, or empty for none
 * @param ordnance what the item throws, or null for a piece that is not a weapon
 * @param lie the angle in degrees above horizontal at which long art lies, turned upright on
 *            the brick; null for compact art, drawn as it stands
 */
public record KitBrick(String icon, Ordnance ordnance, Float lie) {
    /** A marine's carried kit mount: one brick across, two down. */
    public static final int COLUMNS = 1, ROWS = 2;

    public KitBrick {
        icon = icon == null ? "" : icon;
    }

    public static KitBrick weapon(WeaponDef weapon, String icon, float lie) {
        return new KitBrick(icon, Ordnance.of(weapon), lie);
    }

    /** A secondary: its weapon's colour, or utility for a piece that fires nothing. */
    public static KitBrick secondary(SpecialEquipmentDef special) {
        if (special == null) return null;
        return new KitBrick(special.armoryIconPath(),
                special.weaponId() != null ? Ordnance.of(special.weaponDef()) : null,
                special.presentation().armoryIconLie());
    }
}
