package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Winning grants cards only through the collection safety net, and only whole cards, never single equipment. */
class VictoryCardGrantsTest {

    @Test void winningGrantsNothingButWholeCards() {
        MarineArmory armory = new MarineArmory();
        for (int win = 0; win < 60; win++) armory.recordVictory(win % 3 == 0);
        Set<String> carried = new HashSet<>();
        for (String id : armory.knownLoadoutCardIds()) carried.addAll(LoadoutCard.parse(id).templateIds());
        assertEquals(carried, armory.ownedEquipmentTemplateIds(),
                "every piece of equipment the company owns arrived on a card it holds");
        for (String id : armory.knownLoadoutCardIds()) {
            assertNotEquals(EquipmentAccessTier.RESTRICTED, LoadoutCard.parse(id).accessTier(), id);
        }
    }

    @Test void theSafetyNetHoldsItsFloorOfCards() {
        MarineArmory armory = new MarineArmory();
        Set<String> offerable = new HashSet<>();
        for (LoadoutCard card : LoadoutCards.all()) offerable.add(card.id());
        for (int win = 1; win <= 40; win++) {
            armory.recordVictory(false);
            long collected = armory.knownLoadoutCardIds().stream().filter(offerable::contains).count();
            assertTrue(collected >= EquipmentCollectionCurve.minimumCollectedAtVictories(win), "at " + win);
        }
    }

    @Test void cardsHeldInCargoCountWithoutBeingLearned() {
        MarineArmory armory = new MarineArmory();
        List<LoadoutCard> held = EquipmentCollectionCurve.FALLBACK_ORDER.subList(0, 3);
        Set<String> heldIds = new HashSet<>();
        for (LoadoutCard card : held) heldIds.add(card.id());
        armory.repairCollectionProgression(heldIds);
        for (int win = 0; win < 5; win++) armory.recordVictory(false, heldIds);
        for (LoadoutCard card : held) assertFalse(armory.knowsLoadoutCard(card.id()), card.id());
    }

    @Test void theFloorRisesAndStopsShortOfTheWholeCollection() {
        int previous = 0;
        for (int victories = 0; victories <= 100; victories++) {
            int target = EquipmentCollectionCurve.minimumCollectedAtVictories(victories);
            assertTrue(target >= previous);
            previous = target;
        }
        assertTrue(previous < LoadoutCards.all().size());
    }
}
