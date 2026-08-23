package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.io.Serializable;

/** One equipment position in a reusable four-billet fire-team template. */
public final class FireTeamBillet implements Serializable {

    private String name;
    private MarineWeapon primary;
    private EquipmentGrade grade;
    private MarineSecondary secondary;
    private MarineArmorPattern armor;

    public FireTeamBillet(String name, MarineWeapon primary, EquipmentGrade grade,
                          MarineSecondary secondary, MarineArmorPattern armor) {
        this.name = name != null && !name.isBlank() ? name.trim() : "Marine";
        this.primary = primary != null ? primary : MarineWeapon.FIELD_RIFLE;
        this.grade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.secondary = secondary;
        this.armor = armor != null ? armor : MarineArmorPattern.ARMORLESS;
    }

    public String name() { return name; }
    public MarineWeapon primary() { return primary; }
    public EquipmentGrade grade() { return grade; }
    public MarineSecondary secondary() { return secondary; }
    public MarineArmorPattern armor() { return armor; }

    private Object readResolve() {
        if (name == null || name.isBlank()) name = "Marine";
        if (primary == null) primary = MarineWeapon.FIELD_RIFLE;
        if (grade == null) grade = EquipmentGrade.SERVICE;
        if (armor == null) armor = MarineArmorPattern.ARMORLESS;
        return this;
    }
}
