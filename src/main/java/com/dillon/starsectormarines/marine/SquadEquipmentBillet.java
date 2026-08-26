package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/** Exact resolved issue for one of a squad's twelve current billets. */
public record SquadEquipmentBillet(
        String role, String primaryId, EquipmentGrade grade,
        String specialEquipmentId, String armorId) {

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
