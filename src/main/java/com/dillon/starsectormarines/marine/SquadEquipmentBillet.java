package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * Exact resolved issue for one of a squad's twelve current billets. {@code beltEquipmentId} is the thrown item in
 * the seat's second special slot, or null.
 */
public record SquadEquipmentBillet(
        String role, String primaryId, EquipmentGrade grade,
        String specialEquipmentId, String beltEquipmentId, String armorId) {

    public SquadEquipmentBillet(String role, String primaryId, EquipmentGrade grade,
                                String specialEquipmentId, String armorId) {
        this(role, primaryId, grade, specialEquipmentId, null, armorId);
    }

    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }

    public MarineArmorPattern armor() {
        try { return MarineArmorPattern.fromId(armorId); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public MarineArmorCatalogDef armorDef() {
        return MarineArmorCatalogRegistry.require(armorId);
    }

    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }
}
