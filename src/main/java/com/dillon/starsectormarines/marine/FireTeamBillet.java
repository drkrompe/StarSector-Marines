package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
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
    /** Legacy save input and built-in compatibility handle. */
    private MarineWeapon primary;
    private String primaryId;
    private EquipmentGrade grade;
    private MarineSecondary secondary;
    /** Stable special-equipment identity; {@link #secondary} is legacy save input only. */
    private String specialEquipmentId;
    /** Legacy save input and built-in compatibility handle. */
    private MarineArmorPattern armor;
    private String armorId;

    public FireTeamBillet(String name, MarineWeapon primary, EquipmentGrade grade,
                          MarineSecondary secondary, MarineArmorPattern armor) {
        this(name, primary != null ? primary.id : MarineWeapon.FIELD_RIFLE.id, grade,
                secondary != null ? secondary.specialEquipmentId : null,
                armor != null ? armor.id : MarineArmorPattern.ARMORLESS.id);
    }

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

    public String name() { return name; }
    public MarineWeapon primary() {
        MarineWeapon resolved = primaryHandle(primaryId);
        return resolved != null ? resolved : primary;
    }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade grade() { return grade; }
    public MarineSecondary secondary() {
        MarineSecondary resolved = SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
        return resolved != null ? resolved : secondary;
    }
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
        if (primaryId == null && primary != null) primaryId = primary.id;
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing legacy fire-team billet '" + name + "' unresolved primary '"
                    + primaryId + "' to starter weapon '" + MarineWeapon.FIELD_RIFLE.id + "'");
            primaryId = MarineWeapon.FIELD_RIFLE.id;
        }
        primary = null;
        if (grade == null) grade = EquipmentGrade.SERVICE;
        if (specialEquipmentId == null && secondary != null) {
            specialEquipmentId = secondary.specialEquipmentId;
        }
        if (SpecialEquipmentRegistry.get(specialEquipmentId) == null) specialEquipmentId = null;
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

    private static MarineWeapon primaryHandle(String id) {
        try { return MarineWeapon.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static MarineArmorPattern armorHandle(String id) {
        try { return MarineArmorPattern.fromId(id); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
