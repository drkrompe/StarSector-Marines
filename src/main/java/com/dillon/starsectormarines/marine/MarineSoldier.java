package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.UUID;

/** One named, persisted rank-and-file soldier and their allocated field kit. */
public final class MarineSoldier implements Serializable {

    private static final Logger LOG = Global.getLogger(MarineSoldier.class);

    private String id;
    private String name;
    private SoldierAptitude aptitude;
    /** Follows the marine's billet; rewritten by the roster whenever leadership is re-derived. */
    private EnlistedRank enlistedRank;
    private int experienceXp;
    private MarineSoldierStatus status;
    private float unavailableUntilDay;
    /** Legacy save input and built-in compatibility handle. */
    private MarineWeapon primary;
    /** Authoritative persisted primary catalog identity. */
    private String primaryId;
    private EquipmentGrade primaryGrade;
    private MarineSecondary secondary;
    /** Stable special-equipment identity; {@link #secondary} is legacy save input only. */
    private String specialEquipmentId;
    /** Legacy save input and built-in compatibility handle. */
    private MarineArmorPattern armor;
    /** Authoritative persisted armor catalog identity. */
    private String armorId;
    /**
     * Lifetime service record. Never null after construction or
     * {@link #readResolve}; a save written before careers existed repairs to
     * a zeroed one.
     */
    private SoldierCareer career;

    public MarineSoldier(String name, SoldierAptitude aptitude) {
        this(UUID.randomUUID().toString(), name, aptitude);
    }

    /** Explicit-id constructor keeps tests and import/migration tools deterministic. */
    public MarineSoldier(String id, String name, SoldierAptitude aptitude) {
        this.id = id;
        this.name = name;
        this.aptitude = aptitude != null ? aptitude : SoldierAptitude.STEADY;
        this.enlistedRank = EnlistedRank.MARINE;
        this.status = MarineSoldierStatus.ACTIVE;
        this.primaryId = MarineWeapon.FIELD_RIFLE.id;
        this.primaryGrade = EquipmentGrade.SERVICE;
        this.armorId = MarineArmorPattern.ARMORLESS.id;
        this.career = new SoldierCareer();
    }

    public String id() { return id; }
    public String name() { return name; }
    public SoldierAptitude aptitude() { return aptitude; }
    public EnlistedRank enlistedRank() { return enlistedRank; }
    public int experienceXp() { return experienceXp; }
    public SoldierProfile profile() { return new SoldierProfile(aptitude, experienceXp); }
    public MarineSoldierStatus status() { return status; }
    public float unavailableUntilDay() { return unavailableUntilDay; }
    public MarineWeapon primary() {
        MarineWeapon resolved = primaryHandle(primaryId);
        return resolved != null ? resolved : primary;
    }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade primaryGrade() { return primaryGrade; }
    public MarineSecondary secondary() {
        MarineSecondary resolved = SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
        return resolved != null ? resolved : secondary;
    }
    public String specialEquipmentId() { return specialEquipmentId; }
    public MarineArmorPattern armor() {
        MarineArmorPattern resolved = armorHandle(armorId);
        return resolved != null ? resolved : armor;
    }
    public String armorId() { return armorId; }
    public MarineArmorCatalogDef armorDef() { return MarineArmorCatalogRegistry.require(armorId); }

    /** Lifetime service record — missions, rounds, damage, kills. Never null. */
    public SoldierCareer career() { return career; }

    public void addExperience(int amount) {
        experienceXp = Math.max(0, experienceXp + amount);
    }

    void setPrimary(MarineWeapon weapon, EquipmentGrade grade) {
        setPrimary(weapon != null ? weapon.id : MarineWeapon.FIELD_RIFLE.id, grade);
    }

    void setPrimary(String weaponId, EquipmentGrade grade) {
        WeaponDef def = WeaponRegistry.require(
                weaponId != null ? weaponId : MarineWeapon.FIELD_RIFLE.id);
        if (def.mount != MountClass.MARINE_PRIMARY) {
            throw new IllegalArgumentException("Weapon '" + def.id
                    + "' is not a marine primary");
        }
        primaryId = def.id;
        primary = null;
        primaryGrade = grade != null ? grade : EquipmentGrade.SERVICE;
    }

    void setEnlistedRank(EnlistedRank value) {
        enlistedRank = value != null ? value : EnlistedRank.MARINE;
    }

    void setSecondary(MarineSecondary value) {
        specialEquipmentId = value != null ? value.specialEquipmentId : null;
        secondary = null;
    }
    void setArmor(MarineArmorPattern value) {
        setArmor(value != null ? value.id : MarineArmorPattern.ARMORLESS.id);
    }
    void setArmor(String value) {
        armorId = MarineArmorCatalogRegistry.require(
                value != null ? value : MarineArmorPattern.ARMORLESS.id).id();
        armor = null;
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
        if (enlistedRank == null) enlistedRank = EnlistedRank.MARINE;
        if (status == null) status = MarineSoldierStatus.ACTIVE;
        if (primaryId == null && primary != null) primaryId = primary.id;
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing marine '" + id + "' unresolved primary '" + primaryId
                    + "' to starter weapon '" + MarineWeapon.FIELD_RIFLE.id + "'");
            primaryId = MarineWeapon.FIELD_RIFLE.id;
        }
        primary = null;
        if (primaryGrade == null) primaryGrade = EquipmentGrade.SERVICE;
        if (armorId == null && armor != null) armorId = armor.id;
        if (MarineArmorCatalogRegistry.installed() == null
                || MarineArmorCatalogRegistry.installed().get(armorId) == null) {
            LOG.warn("Repairing marine '" + id + "' unresolved armor '" + armorId
                    + "' to starter armor '" + MarineArmorPattern.ARMORLESS.id + "'");
            armorId = MarineArmorPattern.ARMORLESS.id;
        }
        armor = null;
        if (career == null) career = new SoldierCareer();
        if (specialEquipmentId == null && secondary != null) {
            specialEquipmentId = secondary.specialEquipmentId;
        }
        secondary = null;
        experienceXp = Math.max(0, experienceXp);
        unavailableUntilDay = Math.max(0f, unavailableUntilDay);
        return this;
    }

    private static MarineWeapon primaryHandle(String id) {
        try { return MarineWeapon.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static MarineArmorPattern armorHandle(String id) {
        try { return MarineArmorPattern.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
