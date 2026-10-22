package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadoutCardTest {

    @Test
    void cardIdsRoundTripAndRejectUnknownDefinitions() {
        LoadoutCard card = new LoadoutCard(LoadoutCard.Kind.WEAPON,
                SquadEquipmentDoctrines.ASSAULT_WEAPONS);
        assertEquals(card, LoadoutCard.parse(card.id()));
        assertNull(LoadoutCard.parse("loadout-card:weapon:no-such-loadout"));
        assertNull(LoadoutCard.parse("equipment-template:weapon.field-rifle:service"));
        assertNull(LoadoutCard.parse(null));
    }

    @Test
    void starterIssueIsNeverACard() {
        List<String> ids = LoadoutCards.all().stream().map(LoadoutCard::id).toList();
        assertFalse(ids.contains(new LoadoutCard(LoadoutCard.Kind.WEAPON,
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS).id()));
        assertFalse(ids.contains(new LoadoutCard(LoadoutCard.Kind.SHEET,
                SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR).id()));
        assertFalse(ids.isEmpty());
    }

    @Test
    void learningAWeaponLoadoutTeachesEveryWeaponItIssues() {
        MarineArmory armory = new MarineArmory();
        LoadoutCard card = new LoadoutCard(LoadoutCard.Kind.WEAPON,
                SquadEquipmentDoctrines.ASSAULT_WEAPONS);
        assertEquals(LoadoutCardLearning.Result.LEARNED, LoadoutCardLearning.status(armory, card.id()));
        assertFalse(armory.knowsWeaponCard(card.definitionId()));

        assertEquals(LoadoutCardLearning.Result.LEARNED, LoadoutCardLearning.learn(armory, card.id()));

        assertTrue(armory.knowsWeaponCard(card.definitionId()));
        for (String template : card.templateIds()) assertTrue(armory.ownsEquipmentTemplate(template), template);
        assertEquals(LoadoutCardLearning.Result.ALREADY_KNOWN,
                LoadoutCardLearning.learn(armory, card.id()));
    }

    @Test
    void learningATacticSheetTeachesTheSheetAndItsPatterns() {
        for (LoadoutCard card : LoadoutCards.all()) {
            if (card.kind() != LoadoutCard.Kind.SHEET) continue;
            MarineArmory armory = new MarineArmory();
            assertEquals(LoadoutCardLearning.Result.LEARNED,
                    LoadoutCardLearning.learn(armory, card.id()), card.id());
            assertTrue(armory.knowsArmorPlan(card.definitionId()), card.id());
            assertTrue(armory.canIssue(SquadEquipmentDoctrines.armorPlanById(card.definitionId())),
                    card.id() + " must be issuable the moment it is learned");
        }
    }

    @Test
    void everyCardTeachesSomethingAndHasAnAccessTier() {
        for (LoadoutCard card : LoadoutCards.all()) {
            assertFalse(card.templateIds().isEmpty(), card.id());
            assertNotNull(card.accessTier(), card.id());
            assertFalse(card.displayName().isBlank(), card.id());
        }
    }

    @Test
    void unavailableArmoryNeverConsumes() {
        assertEquals(LoadoutCardLearning.Result.ARMORY_UNAVAILABLE,
                LoadoutCardLearning.learn(null, "loadout-card:weapon:weapons:assault"));
        assertEquals(LoadoutCardLearning.Result.UNKNOWN_CARD,
                LoadoutCardLearning.learn(new MarineArmory(), "nonsense"));
    }
}
