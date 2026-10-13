package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoatRecoveryTest {
    @Test void recoveryIsConcurrentBoundedAndSurvivesMovingAndReloading() throws Exception {
        BoatDeck deck = deck(2);
        String first = deck.boats().get(0).id(), second = deck.boats().get(1).id();
        deck.settleRecovery("battle", Map.of(first, .5f, second, 2f), List.of(), 10f);
        assertTrue(deck.airworthy(10.5f).isEmpty());
        assertEquals(1f, deck.boatById(first).repairDaysRemaining(10f));
        assertEquals(2f, deck.boatById(second).repairDaysRemaining(10f));
        deck = reload(deck);
        deck.reconcile("another", ShuttleType.AEROSHUTTLE, 2);
        assertNull(deck.settleRecovery("battle", Map.of(first, .5f, second, 2f), List.of(), 11f));
        assertEquals(List.of(first), deck.airworthy(11f).stream().map(CampaignBoat::id).toList());
        assertEquals(2, deck.airworthy(12f).size());
    }

    @Test void expediteChargesRemainingWorkOnceAndRetainsFittings() throws Exception {
        BoatDeck deck = deck(1); var stores = new Stores(1000); var yard = new BoatWorkshop(deck, stores);
        var boat = deck.boats().get(0);
        yard.fit(boat.id(), BoatFitting.TUNED_DRIVE.id());
        deck.settleRecovery("battle", Map.of(boat.id(), 1f), List.of(), 20f);
        var quote = yard.repairQuote(boat.id(), 20f);
        assertEquals(30, quantity(quote.bill(), "metals"));
        assertEquals(15, quantity(yard.repairQuote(boat.id(), 21f).bill(), "metals"));
        int before = stores.available("metals");
        assertEquals(BoatWorkshop.Status.REPAIRED, yard.expedite(quote, 21f).status());
        assertEquals(before - 15, stores.available("metals"));
        assertSame(BoatFitting.TUNED_DRIVE, boat.drive());
        assertTrue(boat.airworthy(21f));
        assertEquals(BoatWorkshop.Status.STALE_REPAIR, yard.expedite(quote, 21f).status());
        assertEquals(before - 15, stores.available("metals"));
        deck = reload(deck);
        deck.settleRecovery("battle", Map.of(boat.id(), 1f), List.of(), 30f);
        assertTrue(deck.boatById(boat.id()).airworthy(30f));
    }

    @Test void shortageChangesNothingAndWaitingNeedsNoResources() {
        BoatDeck deck = deck(1); var boat = deck.boats().get(0); var stores = new Stores(0);
        var yard = new BoatWorkshop(deck, stores);
        deck.settleRecovery("battle", Map.of(boat.id(), .75f), List.of(), 3f);
        var quote = yard.repairQuote(boat.id(), 3f);
        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, yard.expedite(quote, 3f).status());
        assertEquals(1.5f, boat.repairDaysRemaining(3f));
        assertTrue(stores.stock.isEmpty());
        assertTrue(boat.airworthy(4.5f));
        assertEquals(BoatWorkshop.Status.STALE_REPAIR, yard.expedite(quote, 4.5f).status());
    }

    @Test void destructionWinsAndReplacementNeverRecoversItsPredecessorsFittings() {
        BoatDeck deck = deck(1); var yard = new BoatWorkshop(deck, new Stores(1000));
        var boat = deck.boats().get(0);
        yard.fit(boat.id(), BoatFitting.TUNED_DRIVE.id());
        deck.settleRecovery("lost", Map.of(boat.id(), 1f), List.of(boat.id()), 0f);
        assertTrue(deck.airworthy(10f).isEmpty());
        var replacement = yard.fabricate(0).boat();
        assertNotEquals(boat.id(), replacement.id());
        assertSame(BoatFitting.STANDARD_DRIVE, replacement.drive());
        deck.settleRecovery("lost", Map.of(boat.id(), 1f), List.of(boat.id()), 11f);
        assertSame(replacement, deck.boats().get(0));
    }

    @Test void laterDamageInvalidatesAnEarlierQuoteAndOriginalWrecksNeverRecoverByWaiting() {
        BoatDeck deck = deck(1); var boat = deck.boats().get(0); var stores = new Stores(1000);
        var yard = new BoatWorkshop(deck, stores);
        deck.settleRecovery("one", Map.of(boat.id(), .5f), List.of(), 0f);
        var stale = yard.repairQuote(boat.id(), 0f);
        deck.settleRecovery("two", Map.of(boat.id(), .8f), List.of(), 2f);
        assertEquals(BoatWorkshop.Status.STALE_REPAIR, yard.expedite(stale, 2f).status());
        assertTrue(stores.stock.isEmpty());
        var wrecks = new BoatDeck(); wrecks.requireCommissioning();
        wrecks.registerFoundingWrecks("original"); wrecks.reconcile("original", ShuttleType.HERMES, 2);
        assertTrue(wrecks.airworthy(100f).isEmpty());
        assertTrue(wrecks.wreckInBerth(0)); assertTrue(wrecks.wreckInBerth(1));
    }

    private static BoatDeck deck(int count) { var deck = new BoatDeck(); deck.reconcile("ship", ShuttleType.AEROSHUTTLE, count); return deck; }
    private static int quantity(FabricationCost bill, String id) { return bill.lines().stream().filter(l -> l.commodityId().equals(id)).mapToInt(FabricationCost.Line::quantity).sum(); }
    private static BoatDeck reload(BoatDeck deck) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(deck); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (BoatDeck) in.readObject(); }
    }
    static final class Stores implements FabricationResources {
        final Map<String, Integer> stock = new HashMap<>(); final int initial;
        Stores(int initial) { this.initial = initial; }
        public int available(String id) { return stock.getOrDefault(id, initial); }
        public String commodityName(String id) { return id; }
        public String commodityIcon(String id) { return ""; }
        public boolean spend(FabricationCost cost) {
            if (!canAfford(cost)) return false;
            for (var line : cost.lines()) stock.put(line.commodityId(), available(line.commodityId()) - line.quantity());
            return true;
        }
    }
}
