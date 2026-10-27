package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/** Armory artwork for a primary weapon at its grade, shared by company readouts and armory cards. */
public final class EquipmentIcons {
    /** Every primary's armoury art lies on one diagonal, rising this many degrees to the muzzle. */
    public static final float PRIMARY_LIE = 32f;

    private EquipmentIcons() { }

    public static String primary(WeaponDef primary, EquipmentGrade grade) {
        LayeredWeaponFamily family = LayeredWeaponFamily.fromPrimary(primary);
        String resource = switch (family) {
            case RIFLE -> grade == EquipmentGrade.SURPLUS ? "weapon-rifle-surplus" : "weapon-rifle";
            case DMR -> grade == EquipmentGrade.MASTERWORK ? "weapon-dmr-masterwork" : "weapon-dmr";
            case LASER_GUN -> "weapon-laser-gun";
            case SMG -> "weapon-smg";
        };
        return "graphics/ui/armory/" + resource + ".png";
    }
}
