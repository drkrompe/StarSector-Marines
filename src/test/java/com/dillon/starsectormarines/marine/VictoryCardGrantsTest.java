package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Victory milestones and the collection safety net grant whole cards, never single equipment. */
class VictoryCardGrantsTest {

    @Test void eachMilestonePaysItsCardAtItsRung() {
        MarineArmory armory = new MarineArmory();
        armory.recordVictory(false);
        armory.recordVictory(false);
        assertTrue(armory.paidVictoryMilestoneIds().isEmpty());
        assertFalse(armory.knowsWeaponCard(SquadEquipmentDoctrines.CHECKPOINT_WEAPONS),
                "Checkpoint Issue is bought at the Relief market, never won");
        armory.recordVictory(false);
        assertTrue(armory.knowsArmorPlan(SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR));
        for (int win = 0; win < 2; win++) armory.recordVictory(false);
        assertFalse(armory.knowsWeaponCard(SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS),
                "the chase rung also wants a high-risk victory");
        armory.recordVictory(true);
        assertTrue(armory.knowsWeaponCard(SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS));
        assertEquals(VictoryMilestones.ladder().size(), armory.paidVictoryMilestoneIds().size());
    }

    @Test void aPaidMilestoneStaysPaidAcrossALoad() throws Exception {
        MarineArmory armory = new MarineArmory();
        for (int win = 0; win < 3; win++) armory.recordVictory(false);
        MarineArmory loaded = roundTrip(armory);
        assertEquals(armory.paidVictoryMilestoneIds(), loaded.paidVictoryMilestoneIds());
        assertEquals(armory.knownLoadoutCardIds(), loaded.knownLoadoutCardIds());
    }

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

    private static MarineArmory roundTrip(MarineArmory armory) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(armory); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MarineArmory) in.readObject();
        }
    }
}
