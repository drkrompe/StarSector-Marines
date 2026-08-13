package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

/** Named whole-fireteam issue patterns. Application is atomic through MarineRoster. */
public enum SquadEquipmentPreset {
    LINE("Line", MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE, MarineArmorPattern.CHARCOAL),
    RECON("Recon", MarineWeapon.SMG, EquipmentGrade.SERVICE, MarineArmorPattern.ARMY_GREEN),
    MARKSMAN("Marksman", MarineWeapon.DMR, EquipmentGrade.SERVICE, MarineArmorPattern.CHARCOAL),
    FIELD("Field", MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE, MarineArmorPattern.ARMORLESS);

    public final String displayName;
    public final MarineWeapon primary;
    public final EquipmentGrade grade;
    public final MarineArmorPattern armor;

    SquadEquipmentPreset(String displayName, MarineWeapon primary,
                         EquipmentGrade grade, MarineArmorPattern armor) {
        this.displayName = displayName;
        this.primary = primary;
        this.grade = grade;
        this.armor = armor;
    }
}
