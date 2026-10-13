package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

/** Existing equipment artwork shared by company readouts. */
final class EquipmentIcons {
    private EquipmentIcons() { }
    static String primary(WeaponDef primary, EquipmentGrade grade) {
        LayeredWeaponFamily family = LayeredWeaponFamily.fromPrimary(primary);
        String resource = switch (family) {
            case RIFLE -> grade == EquipmentGrade.SURPLUS ? "grades/surplus/rifle" : "rifle";
            case DMR -> grade == EquipmentGrade.MASTERWORK ? "grades/masterwork/dmr" : "dmr";
            case LASER_GUN -> "laser-gun";
            case SMG -> "smg";
        };
        return "graphics/battle/marine-modular-topdown/variants/weapons/" + resource + ".png";
    }
}
