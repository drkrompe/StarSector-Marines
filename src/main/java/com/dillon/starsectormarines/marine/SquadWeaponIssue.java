package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.io.Serializable;

/** One billet's weapon-side issue inside a squad doctrine. */
public final class SquadWeaponIssue implements Serializable {

    private final String role;
    private final MarineWeapon primary;
    private final EquipmentGrade grade;
    private final String specialEquipmentId;

    public SquadWeaponIssue(String role, MarineWeapon primary, EquipmentGrade grade,
                            MarineSecondary special) {
        this(role, primary, grade, special != null ? special.specialEquipmentId : null);
    }

    public SquadWeaponIssue(String role, MarineWeapon primary, EquipmentGrade grade,
                            String specialEquipmentId) {
        this.role = role != null && !role.isBlank() ? role.trim() : "Marine";
        this.primary = primary != null ? primary : MarineWeapon.FIELD_RIFLE;
        this.grade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.specialEquipmentId = SpecialEquipmentRegistry.get(specialEquipmentId) != null
                ? specialEquipmentId : null;
    }

    public String role() { return role; }
    public MarineWeapon primary() { return primary; }
    public EquipmentGrade grade() { return grade; }
    public String specialEquipmentId() { return specialEquipmentId; }
    public MarineSecondary special() {
        return SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
    }
}
