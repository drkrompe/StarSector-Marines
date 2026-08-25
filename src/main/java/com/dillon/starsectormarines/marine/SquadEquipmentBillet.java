package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

/** Exact resolved issue for one of a squad's twelve current billets. */
public record SquadEquipmentBillet(
        String role, MarineWeapon primary, EquipmentGrade grade,
        String specialEquipmentId, MarineArmorPattern armor) {

    public MarineSecondary special() {
        return SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
    }
}
