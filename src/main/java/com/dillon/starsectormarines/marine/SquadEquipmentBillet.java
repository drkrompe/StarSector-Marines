package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/** Exact resolved issue for one of a squad's twelve current billets. */
public record SquadEquipmentBillet(
        String role, String primaryId, EquipmentGrade grade,
        String specialEquipmentId, String armorId) {

    public SquadEquipmentBillet(String role, MarineWeapon primary, EquipmentGrade grade,
                                String specialEquipmentId, MarineArmorPattern armor) {
        this(role, primary != null ? primary.id : MarineWeapon.FIELD_RIFLE.id, grade,
                specialEquipmentId,
                armor != null ? armor.id : MarineArmorPattern.ARMORLESS.id);
    }

    public MarineWeapon primary() {
        try { return MarineWeapon.fromId(primaryId); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public WeaponDef primaryDef() { return WeaponRegistry.require(primaryId); }

    public MarineArmorPattern armor() {
        try { return MarineArmorPattern.fromId(armorId); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public MarineArmorCatalogDef armorDef() {
        return MarineArmorCatalogRegistry.require(armorId);
    }

    public MarineSecondary special() {
        return SpecialEquipmentRegistry.compatibilityHandle(specialEquipmentId);
    }
}
