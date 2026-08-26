package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.fs.starfarer.api.campaign.impl.items.BlueprintProviderItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTemplateLearningTest {

    @Test
    void learningMovesAnUnknownTemplateIntoTheArmory() {
        MarineArmory armory = new MarineArmory();
        String id = EquipmentTemplateCatalog.primaryId(
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK);

        assertEquals(EquipmentTemplateLearning.Result.LEARNED,
                EquipmentTemplateLearning.status(armory, id));
        assertEquals(EquipmentTemplateLearning.Result.LEARNED,
                EquipmentTemplateLearning.learn(armory, id));
        assertTrue(armory.ownsEquipmentTemplate(id));
        assertEquals(EquipmentTemplateLearning.Result.ALREADY_KNOWN,
                EquipmentTemplateLearning.learn(armory, id));
    }

    @Test
    void invalidAndUnavailableLearningAreNonDestructive() {
        MarineArmory armory = new MarineArmory();

        assertEquals(EquipmentTemplateLearning.Result.UNKNOWN_TEMPLATE,
                EquipmentTemplateLearning.learn(armory, "equipment-template:missing"));
        assertEquals(EquipmentTemplateLearning.Result.UNKNOWN_TEMPLATE,
                EquipmentTemplateLearning.learn(armory, null));
        assertEquals(EquipmentTemplateLearning.Result.ARMORY_UNAVAILABLE,
                EquipmentTemplateLearning.learn(null, EquipmentTemplateCatalog.armorId(
                        MarineArmorPattern.RED_ELITE)));
    }

    @Test
    void cargoItemIsRegisteredWithoutBecomingAVanillaBlueprintProvider() throws IOException {
        assertFalse(BlueprintProviderItem.class.isAssignableFrom(
                EquipmentTemplateCardItemPlugin.class));

        String csv = Files.readString(Path.of(
                "mod", "data", "campaign", "special_items.csv"));
        assertTrue(csv.contains(EquipmentTemplateCardItemPlugin.ITEM_ID));
        assertTrue(csv.contains(EquipmentTemplateCardItemPlugin.class.getName()));
    }

    @Test
    void rewardPayloadFactoryValidatesTheTemplateId() {
        String id = EquipmentTemplateCatalog.armorId(MarineArmorPattern.RED_ELITE);

        assertEquals(EquipmentTemplateCardItemPlugin.ITEM_ID,
                EquipmentTemplateCardItemPlugin.itemData(id).getId());
        assertEquals(id, EquipmentTemplateCardItemPlugin.itemData(id).getData());
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentTemplateCardItemPlugin.itemData("equipment-template:missing"));
    }
}
