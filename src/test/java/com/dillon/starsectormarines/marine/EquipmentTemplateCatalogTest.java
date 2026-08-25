package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTemplateCatalogTest {

    @Test
    void catalogUsesStableUniqueIdsAndBaseCargoCosts() {
        List<EquipmentTemplateCard> cards = EquipmentTemplateCatalog.all();

        assertEquals(cards.size(), new HashSet<>(cards.stream()
                .map(EquipmentTemplateCard::id).toList()).size());
        assertTrue(cards.stream().allMatch(card -> card.id().startsWith("equipment-template:")));
        assertEquals(EquipmentTemplateCost.ZERO, EquipmentTemplateCatalog.primary(
                MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE).issueCost());
        assertEquals(new EquipmentTemplateCost(3, 2, 1, 0),
                EquipmentTemplateCatalog.special(MarineSecondary.ROCKET_LAUNCHER).issueCost());
        assertFalse(cards.stream().anyMatch(card -> card.id().contains("drone-pulse")));
    }

    @Test
    void newArmoryOwnsStarterCardsButAdvancedCardsRemainCollectible() {
        MarineArmory armory = new MarineArmory();

        assertTrue(armory.ownsPrimaryTemplate(
                MarineWeapon.PULSE_RIFLE, EquipmentGrade.SERVICE));
        assertTrue(armory.ownsArmorTemplate(MarineArmorPattern.CHARCOAL));
        assertTrue(armory.ownsSpecialTemplate(MarineSecondary.SATCHEL_CHARGE));
        assertFalse(armory.ownsPrimaryTemplate(
                MarineWeapon.DMR, EquipmentGrade.MASTERWORK));
        assertFalse(armory.ownsArmorTemplate(MarineArmorPattern.RED_ELITE));
        assertFalse(armory.ownsSpecialTemplate(MarineSecondary.FRAG_GRENADE));
    }

    @Test
    void playerAuthoredDefinitionCannotReferenceAnUnownedCard() {
        MarineArmory armory = new MarineArmory();
        List<SquadWeaponIssue> assault = SquadEquipmentDoctrines.weaponById(
                SquadEquipmentDoctrines.ASSAULT_WEAPONS).issues();

        assertThrows(IllegalArgumentException.class,
                () -> armory.createWeaponDoctrine("Premature Assault", assault));
        armory.unlockSecondary(MarineSecondary.FRAG_GRENADE);
        assertEquals("Collected Assault",
                armory.createWeaponDoctrine("Collected Assault", assault).displayName());
    }
}
