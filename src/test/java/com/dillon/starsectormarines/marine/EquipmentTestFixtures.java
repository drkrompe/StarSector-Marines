package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Acquired service equipment for tests of editing and fitting an established company. */
public final class EquipmentTestFixtures {
    private EquipmentTestFixtures() {}
    public static void issueLauncher(MarineSoldier soldier) {
        soldier.setSpecialEquipment(SpecialEquipmentRegistry.SRM_1_ID);
    }
    public static void learnServiceKit(MarineArmory armory) {
        for (String id : List.of(WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.PULSE_RIFLE_ID, WeaponRegistry.SMG_ID,
                WeaponRegistry.SQUAD_AUTOMATIC_ID, WeaponRegistry.DMR_ID)) {
            armory.unlockPrimary(id, EquipmentGrade.SERVICE);
        }
        armory.unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.SURPLUS);
        for (String id : List.of(SpecialEquipmentRegistry.SRM_1_ID,
                SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                SpecialEquipmentRegistry.SMOKE_GRENADE_ID, SpecialEquipmentRegistry.FRAG_GRENADE_ID,
                SpecialEquipmentRegistry.SATCHEL_CHARGE_ID)) {
            armory.unlockSecondary(id);
        }
        for (String id : List.of("armor.cordon-shell", "armor.lashplate-harness",
                MarineArmorPattern.CHARCOAL.id, MarineArmorPattern.ARMY_GREEN.id,
                "armor.riot-shell", "armor.packframe")) armory.unlockArmor(id);
        // An established company has bought the ordinary regular-infantry sheets, whole.
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            if (plan.armorClass() == ArmorClass.STANDARD && !plan.isMixedClass()) learnSheet(armory, plan.id());
        }
        // ...and every weapon card its service equipment already covers.
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            LoadoutCard card = new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrine.id());
            if (armory.ownedEquipmentTemplateIds().containsAll(card.templateIds())) armory.learnLoadoutCard(card);
        }
    }

    /**
     * What a new company holds, read from the data: the equipment of every card the Kit Ladder places on its start
     * station (the founding weapon card and tactic sheet among them).
     */
    public static Set<String> baselineTemplateIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (LoadoutCard card : MarineArmory.startCards()) ids.addAll(card.templateIds());
        return ids;
    }

    /** Learns a tactic sheet the way the game does: by holding its card. */
    public static void learnSheet(MarineArmory armory, String sheetId) {
        LoadoutCardLearning.learn(armory, new LoadoutCard(LoadoutCard.Kind.SHEET, sheetId).id());
    }
}
