package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
                WeaponRegistry.require(WeaponRegistry.STARTER_PRIMARY_ID), EquipmentGrade.SERVICE).issueCost());
        assertEquals(new EquipmentTemplateCost(3, 2, 1, 0),
                EquipmentTemplateCatalog.special(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID)).issueCost());
        assertFalse(cards.stream().anyMatch(card -> card.id().contains("drone-pulse")));
        assertEquals("Integrated drone armament is not compatible with human infantry issue.",
                EquipmentTemplateCatalog.installed().nonPlayerReason(
                        EquipmentTemplateCard.Kind.PRIMARY, WeaponRegistry.DRONE_PULSE_ID));
    }

    @Test
    void authoredCatalogPassesTheNothingStrandedAssetAudit() {
        EquipmentTemplateCatalog.installed().validateCompleteness();

        assertEquals(5, EquipmentTemplateCatalog.playerPrimaryIds().size());
        assertEquals(5L * EquipmentGrade.values().length,
                EquipmentTemplateCatalog.all().stream()
                        .filter(card -> card.kind() == EquipmentTemplateCard.Kind.PRIMARY)
                        .count());
        assertEquals(MarineArmorCatalogRegistry.installed().size(),
                EquipmentTemplateCatalog.all().stream()
                        .filter(card -> card.kind() == EquipmentTemplateCard.Kind.ARMOR)
                        .count());
        assertEquals(SpecialEquipmentRegistry.installed().size(),
                EquipmentTemplateCatalog.all().stream()
                        .filter(card -> card.kind() == EquipmentTemplateCard.Kind.SPECIAL)
                        .count());
    }

    @Test
    void incompletePrimaryMatrixAndMixedNonPlayerClaimFailLoud() throws Exception {
        JSONObject incomplete = new JSONObject(Files.readString(Path.of(
                "mod", "data", "marines", "equipment-templates.template.json")));
        incomplete.getJSONArray("primaries").getJSONObject(0)
                .getJSONObject("grades").remove("masterwork");
        EquipmentTemplateCatalog missingGrade = new EquipmentTemplateCatalog();
        missingGrade.ingest(incomplete);

        IllegalStateException gradeFailure = assertThrows(
                IllegalStateException.class, missingGrade::validateCompleteness);
        assertTrue(gradeFailure.getMessage().contains("MASTERWORK"));

        EquipmentTemplateCatalog mixed = new EquipmentTemplateCatalog();
        mixed.ingest(new JSONObject(Files.readString(Path.of(
                "mod", "data", "marines", "equipment-templates.template.json"))));
        mixed.ingest(new JSONObject("""
                {"nonPlayerEquipment": [{
                  "kind": "primary",
                  "equipmentId": "weapon.field-rifle",
                  "reason": "Contradicts its collectible templates."
                }]}
                """));
        assertThrows(IllegalStateException.class, mixed::validateCompleteness);
    }

    @Test
    void newArmoryOwnsStarterCardsButAdvancedCardsRemainCollectible() {
        MarineArmory armory = new MarineArmory();

        assertTrue(armory.ownsPrimaryTemplate(
                WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID), EquipmentGrade.SERVICE));
        assertTrue(armory.ownsArmorTemplate(MarineArmorPattern.CHARCOAL));
        assertTrue(armory.ownsSpecialTemplate(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID)));
        assertFalse(armory.ownsPrimaryTemplate(
                WeaponRegistry.require(WeaponRegistry.DMR_ID), EquipmentGrade.MASTERWORK));
        assertFalse(armory.ownsArmorTemplate(MarineArmorPattern.RED_ELITE));
        assertFalse(armory.ownsSpecialTemplate(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID)));
    }

    @Test
    void playerAuthoredDefinitionCannotReferenceAnUnownedCard() {
        MarineArmory armory = new MarineArmory();
        List<SquadWeaponIssue> assault = SquadEquipmentDoctrines.weaponById(
                SquadEquipmentDoctrines.ASSAULT_WEAPONS).issues();

        assertThrows(IllegalArgumentException.class,
                () -> armory.createWeaponDoctrine("Premature Assault", assault));
        armory.unlockSecondary(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.FRAG_GRENADE_ID));
        assertEquals("Collected Assault",
                armory.createWeaponDoctrine("Collected Assault", assault).displayName());
    }
}
