package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;
import com.dillon.starsectormarines.battle.world.gen.ship.HullRole;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SecondhandWingsTest {
    private BoatDeck wrecks() {
        BoatDeck deck = new BoatDeck();
        deck.requireCommissioning();
        deck.registerFoundingWrecks("second-wind");
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        return deck;
    }
    @Test void originalWrecksAreRepairedOnceAndOnlyInWorkingBerths() throws Exception {
        BoatDeck deck = wrecks();
        assertTrue(deck.wreckInBerth(0));
        assertFalse(deck.wreckInBerth(1));
        assertTrue(deck.airworthy().isEmpty());
        var first = deck.fabricate(0);
        deck = saved(deck);
        deck.lose(List.of(first.id()));
        deck.registerFoundingWrecks("second-wind");
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertFalse(deck.wreckInBerth(0), "A combat loss cannot resurrect the original wreck");
        assertTrue(deck.wreckInBerth(1));
        deck.fabricate(1);
        assertFalse(saved(deck).wreckInBerth(1));
    }
    @Test void failedRepairDoesNotConsumeTheWreck() {
        var deck = wrecks();
        var workshop = new BoatWorkshop(deck, new FabricationResources() {
            public int available(String id) { return 0; }
            public boolean spend(FabricationCost bill) { return false; }
        });
        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, workshop.fabricate(0).status());
        assertTrue(deck.wreckInBerth(0));
    }
    @Test void migrationPreservesBoatsAndDoesNotTurnOldLossesIntoWrecks() {
        var deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        deck.lose(List.of(deck.airworthy().get(0).id()));
        deck.registerFoundingWrecks("second-wind");
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertFalse(deck.wreckInBerth(0));
        assertTrue(deck.wreckInBerth(1), "The formerly sealed berth still has its original wreck");
        var untouchedLegacy = new BoatDeck();
        untouchedLegacy.registerFoundingWrecks("second-wind");
        untouchedLegacy.reconcile("second-wind", ShuttleType.HERMES, 2);
        untouchedLegacy.lose(untouchedLegacy.airworthy().stream().map(CampaignBoat::id).toList());
        assertFalse(untouchedLegacy.wreckInBerth(0));
        assertFalse(untouchedLegacy.wreckInBerth(1));
    }
    @Test void purchaseRechecksTradeAndPaymentAndCannotBeClaimedTwice() throws Exception {
        var deck = wrecks();
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        var hermes = deck.fabricate(0);
        var review = SecondhandWings.review(deck);
        AtomicInteger credits = new AtomicInteger(9000);
        hermes.install(BoatFitting.ARMOURED_PLATING);
        assertNotNull(SecondhandWings.purchase(deck, review, null, n -> { credits.addAndGet(-n); return true; }));
        assertEquals(9000, credits.get());
        review = SecondhandWings.review(deck);
        assertNotNull(SecondhandWings.purchase(deck, review, null, n -> false));
        assertSame(hermes, deck.airworthy().get(0));
        assertNull(SecondhandWings.purchase(deck, review, null, n -> { credits.addAndGet(-n); return true; }));
        assertEquals(4000, credits.get());
        assertEquals(12, deck.airworthy().stream().mapToInt(b -> b.freezeForDeployment().pattern().capacity).sum());
        assertTrue(deck.airworthy().stream().allMatch(b -> b.plating() == BoatFitting.STANDARD_PLATING));
        deck = saved(deck);
        assertNotNull(SecondhandWings.purchase(deck, SecondhandWings.review(deck), null, n -> fail("Paid twice")));
        deck.lose(List.of(deck.airworthy().get(0).id()));
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertEquals(1, deck.airworthy().size());
        assertEquals(ShuttleType.AEROSHUTTLE, deck.pattern());
        assertFalse(deck.wreckInBerth(0));
    }
    @Test void restorationAndStoryRemainSeparatePrerequisites() {
        var deck = wrecks();
        assertTrue(SecondhandWings.refusal(deck, true, true, false, true, true, 9000).contains("Restore"));
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertTrue(SecondhandWings.refusal(deck, false, true, true, true, true, 9000).contains("Counterattack"));
        assertNotNull(SecondhandWings.refusal(deck, true, false, true, true, true, 9000));
        assertNotNull(SecondhandWings.refusal(deck, true, true, true, false, true, 9000));
        assertNotNull(SecondhandWings.refusal(deck, true, true, true, true, false, 9000));
        assertNotNull(SecondhandWings.refusal(deck, true, true, true, true, true, 4999));
        assertNull(SecondhandWings.refusal(deck, true, true, true, true, true, 5000));
    }
    @Test void conversionBelongsToOneHullAndEveryHullProjectionRetainsIt() {
        var hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75, .5f)
                .converted().withBoatBay(BoatBayState.RESTORED);
        var upgraded = hull.withAeroshuttleConversion(true).withWorkshop(WorkshopState.REPAIRED)
                .withBoatBay(BoatBayState.RESTORED).converted();
        assertEquals(hull.deckPlan().rooms(), upgraded.deckPlan().rooms());
        assertEquals(ShuttleType.AEROSHUTTLE, upgraded.deckPlan().boats());
        assertEquals(List.of(ShuttleType.AEROSHUTTLE, ShuttleType.AEROSHUTTLE), ShipsBoats.aboard(upgraded));
        var deck = wrecks(); deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertNull(SecondhandWings.purchase(deck, SecondhandWings.review(deck), null, n -> true));
        var names = deck.airworthy().stream().map(CampaignBoat::id).toList();
        deck.reconcile("transport", ShuttleType.AEROSHUTTLE, 6);
        assertEquals(names, deck.airworthy().stream().map(CampaignBoat::id).toList());
        assertFalse(deck.hasAeroshuttleConversion("transport"));
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        assertEquals(names, deck.airworthy().stream().map(CampaignBoat::id).toList());
    }
    private BoatDeck saved(BoatDeck deck) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(deck); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (BoatDeck) in.readObject(); }
    }
}
