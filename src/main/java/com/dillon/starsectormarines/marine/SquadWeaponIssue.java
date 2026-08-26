package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
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
    /** Legacy enum name written by saves predating stable primary ids. */
    private String primary;
    /** Authoritative persisted catalog identity. */
    private String primaryId;
    private EquipmentGrade grade;
    private String specialEquipmentId;

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

    public SquadWeaponIssue(String role, WeaponDef primary, EquipmentGrade grade,
                            SpecialEquipmentDef special) {
        this(role, primary != null ? primary.id : null, grade,
                special != null ? special.id() : null);
    }

    public String role() { return role; }
    public String primaryId() { return primaryId; }
    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }
    public EquipmentGrade grade() { return grade; }
    public String specialEquipmentId() { return specialEquipmentId; }
    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }

    private Object readResolve() {
        if (role == null || role.isBlank()) role = "Marine";
        String savedPrimaryId = primaryId != null ? primaryId : primary;
        if (primaryId == null) primaryId = WeaponRegistry.legacyMarinePrimaryId(primary);
        WeaponDef savedPrimary = WeaponRegistry.installed() != null
                ? WeaponRegistry.installed().get(primaryId) : null;
        if (savedPrimary == null || savedPrimary.mount != MountClass.MARINE_PRIMARY) {
            LOG.warn("Repairing doctrine billet '" + role + "' unresolved primary '"
                    + savedPrimaryId + "' to starter weapon '" + WeaponRegistry.STARTER_PRIMARY_ID + "'");
            primaryId = WeaponRegistry.STARTER_PRIMARY_ID;
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
        WeaponDef def = WeaponRegistry.require(id != null ? id : WeaponRegistry.STARTER_PRIMARY_ID);
        if (def.mount != MountClass.MARINE_PRIMARY) {
            throw new IllegalArgumentException("Weapon '" + def.id + "' is not a marine primary");
        }
        return def;
    }

}
