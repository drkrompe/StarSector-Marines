package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.panel.Ordnance;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;

/**
 * One piece of a marine's kit as the fitting rooms draw hardware: a 1x2 brick-stack, one
 * brick high and two across, in the colour of its {@link Kind}. A weapon is coloured by what
 * it throws (ballistic yellow, energy red-orange, missile green, as a mech hardpoint), read
 * off its own data by {@link Ordnance#of(WeaponDef)}, the reading the direct-control HUD
 * draws its load by, so a new or retuned weapon takes its colour from its authoring. A
 * secondary that throws nothing is utility teal, a suit is plating grey and its integral
 * system flux violet, as the Mech Lab colours a frame's own parts.
 *
 * @param icon the item's armoury art, or empty for none
 * @param kind what the piece is, which colours it
 * @param lie the angle in degrees above horizontal at which long art lies, turned to lie
 *            along the bricks; null for compact art, drawn as it stands
 */
public record KitBrick(String icon, Kind kind, Float lie) {
    /** A marine's kit mount, 1x2: one row of two bricks. */
    public static final int ROWS = 1, COLUMNS = 2;

    public enum Kind { BALLISTIC, ENERGY, MISSILE, UTILITY, ARMOR, SYSTEM }

    public KitBrick {
        icon = icon == null ? "" : icon;
        if (kind == null) throw new IllegalArgumentException("kind is required");
    }

    public static KitBrick weapon(WeaponDef weapon, String icon, float lie) {
        return new KitBrick(icon, of(Ordnance.of(weapon)), lie);
    }

    /** A secondary: its weapon's colour, or utility for a piece that fires nothing. */
    public static KitBrick secondary(SpecialEquipmentDef special) {
        if (special == null) return null;
        return new KitBrick(special.armoryIconPath(),
                special.weaponId() != null ? of(Ordnance.of(special.weaponDef())) : Kind.UTILITY,
                special.presentation().armoryIconLie());
    }

    public static KitBrick armor(MarineArmorCatalogDef armor) {
        return armor == null ? null : new KitBrick(armor.iconPath(), Kind.ARMOR, null);
    }

    /** The suit's integral system, or null for a suit that carries none. */
    public static KitBrick system(MarineArmorCatalogDef armor) {
        return armor == null || !IntegralSystemCopy.carried(armor) ? null
                : new KitBrick(IntegralSystemCopy.iconPath(armor), Kind.SYSTEM, null);
    }

    private static Kind of(Ordnance ordnance) {
        return switch (ordnance) {
            case BALLISTIC -> Kind.BALLISTIC;
            case ENERGY -> Kind.ENERGY;
            case MISSILE -> Kind.MISSILE;
        };
    }
}
