package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
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
    private MarineSoldierStatus status;
    private float unavailableUntilDay;
    /** Legacy enum name written by saves predating stable primary ids. */
    private String primary;
    /** Authoritative persisted primary catalog identity. */
    private String primaryId;
    private EquipmentGrade primaryGrade;
    /** Legacy enum name written by saves predating stable special ids. */
    private String secondary;
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
        this.primaryId = WeaponRegistry.STARTER_PRIMARY_ID;
        this.primaryGrade = EquipmentGrade.SERVICE;
        this.armorId = MarineArmorPattern.ARMORLESS.id;
        this.career = new SoldierCareer();
    }

    public String id() { return id; }
    public String name() { return name; }
    public SoldierAptitude aptitude() { return aptitude; }
    public EnlistedRank enlistedRank() { return enlistedRank; }
    /**
     * The battle-ready profile this marine deploys with: persisted aptitude, and
     * the experience band issued with their armour. Not a stored number — a
     * rank-and-file marine has no personal ladder, so asking what they are
     * worth means reading what they are wearing ({@code progression-nouns.md}).
     */
    public SoldierProfile profile() { return SquadExperienceStandard.profileFor(this); }
    public MarineSoldierStatus status() { return status; }
    public float unavailableUntilDay() { return unavailableUntilDay; }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade primaryGrade() { return primaryGrade; }
    public String specialEquipmentId() { return specialEquipmentId; }
    public SpecialEquipmentDef specialEquipmentDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }
    public MarineArmorPattern armor() {
        MarineArmorPattern resolved = armorHandle(armorId);
        return resolved != null ? resolved : armor;
    }
    public String armorId() { return armorId; }
    public MarineArmorCatalogDef armorDef() { return MarineArmorCatalogRegistry.require(armorId); }

    /** Lifetime service record — missions, rounds, damage, kills. Never null. */
    public SoldierCareer career() { return career; }

    void setPrimary(String weaponId, EquipmentGrade grade) {
        WeaponDef def = WeaponRegistry.require(
                weaponId != null ? weaponId : WeaponRegistry.STARTER_PRIMARY_ID);
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

    void setSpecialEquipment(String value) {
        specialEquipmentId = value != null ? SpecialEquipmentRegistry.require(value).id() : null;
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
        String savedPrimaryId = primaryId != null ? primaryId : primary;
        if (primaryId == null) primaryId = WeaponRegistry.legacyMarinePrimaryId(primary);
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing marine '" + id + "' unresolved primary '" + savedPrimaryId
                    + "' to starter weapon '" + WeaponRegistry.STARTER_PRIMARY_ID + "'");
            primaryId = WeaponRegistry.STARTER_PRIMARY_ID;
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
        String savedSpecialEquipmentId = specialEquipmentId != null ? specialEquipmentId : secondary;
        if (specialEquipmentId == null) specialEquipmentId = SpecialEquipmentRegistry.legacyId(secondary);
        if (savedSpecialEquipmentId != null
                && SpecialEquipmentRegistry.get(specialEquipmentId) == null) {
            LOG.warn("Clearing marine '" + id + "' unresolved special equipment '"
                    + savedSpecialEquipmentId + "'");
            specialEquipmentId = null;
        }
        secondary = null;
        unavailableUntilDay = Math.max(0f, unavailableUntilDay);
        return this;
    }

    private static MarineArmorPattern armorHandle(String id) {
        try { return MarineArmorPattern.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
