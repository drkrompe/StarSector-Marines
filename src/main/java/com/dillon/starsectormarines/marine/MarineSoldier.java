package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;

import java.io.Serializable;
import java.util.UUID;

/** One named, persisted rank-and-file soldier and their allocated field kit. */
public final class MarineSoldier implements Serializable {

    private String id;
    private String name;
    private SoldierAptitude aptitude;
    private int experienceXp;
    private MarineSoldierStatus status;
    private float unavailableUntilDay;
    private MarineWeapon primary;
    private EquipmentGrade primaryGrade;
    private MarineSecondary secondary;
    private MarineArmorPattern armor;

    public MarineSoldier(String name, SoldierAptitude aptitude) {
        this(UUID.randomUUID().toString(), name, aptitude);
    }

    /** Explicit-id constructor keeps tests and import/migration tools deterministic. */
    public MarineSoldier(String id, String name, SoldierAptitude aptitude) {
        this.id = id;
        this.name = name;
        this.aptitude = aptitude != null ? aptitude : SoldierAptitude.STEADY;
        this.status = MarineSoldierStatus.ACTIVE;
        this.primary = MarineWeapon.PULSE_RIFLE;
        this.primaryGrade = EquipmentGrade.SERVICE;
        this.armor = MarineArmorPattern.ARMORLESS;
    }

    public String id() { return id; }
    public String name() { return name; }
    public SoldierAptitude aptitude() { return aptitude; }
    public int experienceXp() { return experienceXp; }
    public SoldierProfile profile() { return new SoldierProfile(aptitude, experienceXp); }
    public MarineSoldierStatus status() { return status; }
    public float unavailableUntilDay() { return unavailableUntilDay; }
    public MarineWeapon primary() { return primary; }
    public EquipmentGrade primaryGrade() { return primaryGrade; }
    public MarineSecondary secondary() { return secondary; }
    public MarineArmorPattern armor() { return armor; }

    public void addExperience(int amount) {
        experienceXp = Math.max(0, experienceXp + amount);
    }

    void setPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        primary = weapon != null ? weapon : MarineWeapon.PULSE_RIFLE;
        primaryGrade = grade != null ? grade : EquipmentGrade.SERVICE;
    }

    void setSecondary(MarineSecondary value) { secondary = value; }
    void setArmor(MarineArmorPattern value) {
        armor = value != null ? value : MarineArmorPattern.ARMORLESS;
    }
    void setStatus(MarineSoldierStatus value) {
        status = value != null ? value : MarineSoldierStatus.ACTIVE;
    }

    void setUnavailableUntilDay(float value) {
        unavailableUntilDay = Math.max(0f, value);
    }

    private Object readResolve() {
        if (id == null) id = UUID.randomUUID().toString();
        if (name == null) name = "Marine";
        if (aptitude == null) aptitude = SoldierAptitude.STEADY;
        if (status == null) status = MarineSoldierStatus.ACTIVE;
        if (primary == null) primary = MarineWeapon.PULSE_RIFLE;
        if (primaryGrade == null) primaryGrade = EquipmentGrade.SERVICE;
        if (armor == null) armor = MarineArmorPattern.ARMORLESS;
        experienceXp = Math.max(0, experienceXp);
        unavailableUntilDay = Math.max(0f, unavailableUntilDay);
        return this;
    }
}
