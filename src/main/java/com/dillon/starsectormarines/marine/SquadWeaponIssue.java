package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.io.Serializable;

/** One billet's weapon-side issue inside a squad doctrine. */
public final class SquadWeaponIssue implements Serializable {

    private static final Logger LOG = Global.getLogger(SquadWeaponIssue.class);

    private String role;
    /** Legacy save input and built-in compatibility handle. */
    private MarineWeapon primary;
    /** Authoritative persisted catalog identity. */
    private String primaryId;
    private EquipmentGrade grade;
    private String specialEquipmentId;

    public SquadWeaponIssue(String role, MarineWeapon primary, EquipmentGrade grade,
                            MarineSecondary special) {
        this(role, primary, grade, special != null ? special.specialEquipmentId : null);
    }

    public SquadWeaponIssue(String role, MarineWeapon primary, EquipmentGrade grade,
                            String specialEquipmentId) {
        this(role, primary != null ? primary.id : MarineWeapon.FIELD_RIFLE.id,
                grade, specialEquipmentId);
    }

    public SquadWeaponIssue(String role, String primaryId, EquipmentGrade grade,
                            String specialEquipmentId) {
        this.role = role != null && !role.isBlank() ? role.trim() : "Marine";
        WeaponDef def = requirePrimary(primaryId);
        this.primaryId = def.id;
        this.primary = null;
        this.grade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.specialEquipmentId = SpecialEquipmentRegistry.get(specialEquipmentId) != null
                ? specialEquipmentId : null;
    }

    public String role() { return role; }
    public MarineWeapon primary() {
        MarineWeapon resolved = compatibilityHandle(primaryId);
        return resolved != null ? resolved : primary;
    }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade grade() { return grade; }
    public String specialEquipmentId() { return specialEquipmentId; }
    public MarineSecondary special() {
        return SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
    }
    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }

    private Object readResolve() {
        if (role == null || role.isBlank()) role = "Marine";
        if (primaryId == null && primary != null) primaryId = primary.id;
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing doctrine billet '" + role + "' unresolved primary '"
                    + primaryId + "' to starter weapon '" + MarineWeapon.FIELD_RIFLE.id + "'");
            primaryId = MarineWeapon.FIELD_RIFLE.id;
        }
        primary = null;
        if (grade == null) grade = EquipmentGrade.SERVICE;
        if (specialEquipmentId != null && SpecialEquipmentRegistry.get(specialEquipmentId) == null) {
            LOG.warn("Clearing doctrine billet '" + role + "' unresolved special equipment '"
                    + specialEquipmentId + "'");
            specialEquipmentId = null;
        }
        return this;
    }

    private static WeaponDef requirePrimary(String id) {
        WeaponDef def = WeaponRegistry.require(id != null ? id : MarineWeapon.FIELD_RIFLE.id);
        if (def.mount != MountClass.MARINE_PRIMARY) {
            throw new IllegalArgumentException("Weapon '" + def.id + "' is not a marine primary");
        }
        return def;
    }

    private static MarineWeapon compatibilityHandle(String id) {
        try {
            return MarineWeapon.fromId(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
