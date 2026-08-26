package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic safety-net breadth for a company that misses ordinary card sources. */
final class EquipmentCollectionCurve {

    static final int FIVE_VICTORY_TARGET = 19;
    static final int FIFTEEN_VICTORY_TARGET = 23;
    static final int THIRTY_VICTORY_TARGET = 27;
    static final int FORTY_VICTORY_TARGET = 28;

    private static final List<String> FALLBACK_ORDER = List.of(
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MILSPEC),
            EquipmentTemplateCatalog.armorId(MarineArmorPattern.BLUE_SCOUT),
            EquipmentTemplateCatalog.armorId(MarineArmorPattern.OUTLAW),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MASTERWORK),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.SMG_ID, EquipmentGrade.MASTERWORK),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MASTERWORK),
            EquipmentTemplateCatalog.armorId(MarineArmorPattern.RED_ELITE),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MASTERWORK),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.DMR_ID, EquipmentGrade.MASTERWORK),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.DMR_ID, EquipmentGrade.SURPLUS),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.SURPLUS),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.SMG_ID, EquipmentGrade.SURPLUS),
            EquipmentTemplateCatalog.primaryId(
                    WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SURPLUS));

    private EquipmentCollectionCurve() {}

    static int minimumCollectedAtVictories(int victories) {
        int wins = Math.max(0, victories);
        if (wins < 2) return 14;
        if (wins == 2) return 16;
        if (wins == 3) return 17;
        if (wins < 5) return FIVE_VICTORY_TARGET;
        if (wins <= 15) {
            return FIVE_VICTORY_TARGET
                    + (wins - 5) * (FIFTEEN_VICTORY_TARGET - FIVE_VICTORY_TARGET) / 10;
        }
        if (wins <= 30) {
            return FIFTEEN_VICTORY_TARGET
                    + (wins - 15) * (THIRTY_VICTORY_TARGET - FIFTEEN_VICTORY_TARGET) / 15;
        }
        if (wins <= 40) {
            return THIRTY_VICTORY_TARGET
                    + (wins - 30) * (FORTY_VICTORY_TARGET - THIRTY_VICTORY_TARGET) / 10;
        }
        return FORTY_VICTORY_TARGET;
    }

    static List<String> repair(
            MarineArmory armory, int victories, Set<String> acquiredOrCarriedTemplateIds) {
        if (armory == null) return List.of();
        int target = minimumCollectedAtVictories(victories);
        Set<String> effectiveCollection = new HashSet<>();
        Set<String> external = acquiredOrCarriedTemplateIds != null
                ? acquiredOrCarriedTemplateIds : Set.of();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (armory.ownsEquipmentTemplate(card.id()) || external.contains(card.id())) {
                effectiveCollection.add(card.id());
            }
        }
        int collected = effectiveCollection.size();
        if (collected >= target) return List.of();

        List<String> granted = new ArrayList<>();
        for (String templateId : FALLBACK_ORDER) {
            if (collected >= target) break;
            if (effectiveCollection.contains(templateId)
                    || !EquipmentTemplateCatalog.contains(templateId)
                    || !armory.acquireEquipmentTemplate(templateId)) {
                continue;
            }
            granted.add(templateId);
            effectiveCollection.add(templateId);
            collected++;
        }
        if (collected < target) {
            throw new IllegalStateException("Equipment collection curve cannot reach its "
                    + target + "-card target at " + victories + " victories");
        }
        return List.copyOf(granted);
    }
}
