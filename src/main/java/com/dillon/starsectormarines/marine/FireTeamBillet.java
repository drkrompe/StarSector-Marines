package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.io.Serializable;

/** One equipment position in a reusable four-billet fire-team template. */
public final class FireTeamBillet implements Serializable {

    private static final Logger LOG = Global.getLogger(FireTeamBillet.class);

    private String name;
    /** Legacy enum name written by saves predating stable primary ids. */
    private String primary;
    private String primaryId;
    private EquipmentGrade grade;
    /** Legacy enum name written by saves predating stable special ids. */
    private String secondary;
    /** Stable special-equipment identity; {@link #secondary} is legacy save input only. */
    private String specialEquipmentId;
    /** Legacy save input and built-in compatibility handle. */
    private MarineArmorPattern armor;
    private String armorId;

    public FireTeamBillet(String name, String primaryId, EquipmentGrade grade,
                          String specialEquipmentId, String armorId) {
        this.name = name != null && !name.isBlank() ? name.trim() : "Marine";
        WeaponDef primaryDef = WeaponRegistry.require(primaryId);
        if (primaryDef.mount != MountClass.MARINE_PRIMARY) {
            throw new IllegalArgumentException("Weapon '" + primaryDef.id
                    + "' is not a marine primary");
        }
        this.primaryId = primaryDef.id;
        this.primary = null;
        this.grade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.specialEquipmentId = SpecialEquipmentRegistry.get(specialEquipmentId) != null
                ? specialEquipmentId : null;
        this.armorId = MarineArmorCatalogRegistry.require(armorId).id();
        this.armor = null;
    }

    public FireTeamBillet(String name, WeaponDef primary, EquipmentGrade grade,
                          SpecialEquipmentDef special, MarineArmorPattern armor) {
        this(name, primary != null ? primary.id : null, grade,
                special != null ? special.id() : null,
                armor != null ? armor.id : MarineArmorPattern.ARMORLESS.id);
    }

    public String name() { return name; }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade grade() { return grade; }
    public String specialEquipmentId() { return specialEquipmentId; }
    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }
    public MarineArmorPattern armor() {
        MarineArmorPattern resolved = armorHandle(armorId);
        return resolved != null ? resolved : armor;
    }
    public String armorId() { return armorId; }
    public MarineArmorCatalogDef armorDef() { return MarineArmorCatalogRegistry.require(armorId); }

    private Object readResolve() {
        if (name == null || name.isBlank()) name = "Marine";
        String savedPrimaryId = primaryId != null ? primaryId : primary;
        if (primaryId == null) primaryId = WeaponRegistry.legacyMarinePrimaryId(primary);
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing legacy fire-team billet '" + name + "' unresolved primary '"
                    + savedPrimaryId + "' to starter weapon '" + WeaponRegistry.STARTER_PRIMARY_ID + "'");
            primaryId = WeaponRegistry.STARTER_PRIMARY_ID;
        }
        primary = null;
        if (grade == null) grade = EquipmentGrade.SERVICE;
        String savedSpecialEquipmentId = specialEquipmentId != null ? specialEquipmentId : secondary;
        if (specialEquipmentId == null) specialEquipmentId = SpecialEquipmentRegistry.legacyId(secondary);
        if (savedSpecialEquipmentId != null
                && SpecialEquipmentRegistry.get(specialEquipmentId) == null) {
            LOG.warn("Clearing legacy fire-team billet '" + name
                    + "' unresolved special equipment '" + savedSpecialEquipmentId + "'");
            specialEquipmentId = null;
        }
        secondary = null;
        if (armorId == null && armor != null) armorId = armor.id;
        if (MarineArmorCatalogRegistry.installed() == null
                || MarineArmorCatalogRegistry.installed().get(armorId) == null) {
            LOG.warn("Repairing legacy fire-team billet '" + name + "' unresolved armor '"
                    + armorId + "' to starter armor '" + MarineArmorPattern.ARMORLESS.id + "'");
            armorId = MarineArmorPattern.ARMORLESS.id;
        }
        armor = null;
        return this;
    }

    private static MarineArmorPattern armorHandle(String id) {
        try { return MarineArmorPattern.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
