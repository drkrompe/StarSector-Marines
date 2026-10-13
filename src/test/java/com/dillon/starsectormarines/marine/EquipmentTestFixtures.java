package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import java.util.List;

/** Acquired service equipment for tests of editing and fitting an established company. */
public final class EquipmentTestFixtures {
    private EquipmentTestFixtures() {}
    public static void issueLauncher(MarineSoldier soldier) {
        soldier.setSpecialEquipment(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID);
    }
    public static void learnServiceKit(MarineArmory armory) {
        for (String id : List.of(WeaponRegistry.PULSE_RIFLE_ID, WeaponRegistry.SMG_ID,
                WeaponRegistry.SQUAD_AUTOMATIC_ID, WeaponRegistry.DMR_ID)) {
            armory.unlockPrimary(id, EquipmentGrade.SERVICE);
        }
        armory.unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SURPLUS);
        for (String id : List.of(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID,
                SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                SpecialEquipmentRegistry.SMOKE_GRENADE_ID, SpecialEquipmentRegistry.SATCHEL_CHARGE_ID)) {
            armory.unlockSecondary(id);
        }
        for (String id : List.of("armor.cordon-shell", "armor.lashplate-harness",
                MarineArmorPattern.CHARCOAL.id, MarineArmorPattern.ARMY_GREEN.id,
                "armor.riot-shell", "armor.packframe")) armory.unlockArmor(id);
    }
}
