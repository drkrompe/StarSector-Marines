package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoatPartLearningTest {

    @Test
    void starterPartsAreKnownAndNeverSold() {
        BoatDeck deck = new BoatDeck();
        Set<String> starter = BoatParts.starterIds();

        assertTrue(starter.contains(BoatFitting.STANDARD_PLATING.id()));
        assertTrue(starter.stream().anyMatch(id -> BoatGun.findById(id) != null), "the yard guns are starter issue");
        for (String id : starter) assertTrue(deck.knowsPart(id), id);
        for (String id : BoatParts.offerable()) {
            assertFalse(starter.contains(id), id + " is starter issue and cannot be sold");
            assertFalse(deck.knowsPart(id), id);
            assertTrue(BoatParts.accessTier(id) != null, id);
        }
        assertFalse(BoatParts.offerable().isEmpty(), "the store has boat parts to sell");
    }

    @Test
    void learningATeachesTheDeckOnceAndIsPermanent() {
        BoatDeck deck = new BoatDeck();
        String part = BoatParts.offerable().get(0);

        assertEquals(BoatPartLearning.Result.LEARNED, BoatPartLearning.status(deck, part));
        assertEquals(BoatPartLearning.Result.LEARNED, BoatPartLearning.learn(deck, part));
        assertEquals(BoatPartLearning.Result.ALREADY_KNOWN, BoatPartLearning.learn(deck, part));
        assertTrue(deck.knowsPart(part));
        assertTrue(deck.knownPartIds().contains(part));
        assertEquals(BoatPartLearning.Result.UNKNOWN_PART, BoatPartLearning.status(deck, "not-a-part"));
        assertEquals(BoatPartLearning.Result.DECK_UNAVAILABLE, BoatPartLearning.status(null, part));
    }

    @Test
    void theWorkshopBuildsOnlyWhatTheCompanyHasLearned() {
        BoatDeck deck = new BoatDeck();
        long[] credits = {1_000_000};
        FabricationResources purse = new FabricationResources() {
            @Override public long credits() { return credits[0]; }
            @Override public boolean atMarket() { return true; }
            @Override public boolean spend(FabricationCost cost) {
                credits[0] -= cost.credits();
                return true;
            }
        };
        BoatWorkshop workshop = new BoatWorkshop(deck, purse);
        String part = BoatParts.offerable().get(0);

        assertEquals(BoatWorkshop.Status.NOT_LEARNED, workshop.fabricatePart(part).status());
        assertEquals(1_000_000, credits[0], "a refused build is not charged");
        assertEquals(0, deck.spares(part));

        BoatPartLearning.learn(deck, part);
        assertEquals(BoatWorkshop.Status.PART_FABRICATED, workshop.fabricatePart(part).status());
        assertEquals(1, deck.spares(part));

        assertEquals(BoatWorkshop.Status.PART_FABRICATED,
                workshop.fabricatePart(BoatFitting.STANDARD_PLATING.id()).status(),
                "a starter part needs no learning");
    }

    @Test
    void aSaveFromBeforeLearningKeepsEveryPartItAlreadyOwned() throws Exception {
        BoatDeck deck = new BoatDeck();
        String owned = BoatParts.offerable().get(0);
        String other = BoatParts.offerable().get(1);
        deck.stow(owned);
        Field learned = BoatDeck.class.getDeclaredField("learnedParts");
        learned.setAccessible(true);
        learned.set(deck, null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(deck);
        }
        BoatDeck loaded;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            loaded = (BoatDeck) in.readObject();
        }

        assertTrue(loaded.knowsPart(owned), "a spare in stores is never locked away");
        assertFalse(loaded.knowsPart(other));
    }
}
