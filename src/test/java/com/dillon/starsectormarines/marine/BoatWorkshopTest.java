package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fit or a build is one transaction against the fleet's hold, or it is
 * nothing at all.
 */
class BoatWorkshopTest {

    @Test
    void fittingSpendsExactlyTheBillAndInstallsTheWork() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();

        BoatWorkshop.Result result = workshop.fit(boatId, "reinforced_plating");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.REINFORCED_PLATING, deck.boatById(boatId).plating());
        assertEquals(90, hold.available(Commodities.SUPPLIES));
        assertEquals(80, hold.available(Commodities.METALS));
        assertEquals(100, hold.available(Commodities.HEAVY_MACHINERY),
                "a plating bill has no machinery line and must not invent one");
    }

    /** The standard fit is what a boat is built with, so it costs the hold nothing. */
    @Test
    void goingBackToTheStandardFittingIsFreeAndStillAChange() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();
        workshop.fit(boatId, "uprated_drive");
        int suppliesAfterUprating = hold.available(Commodities.SUPPLIES);

        BoatWorkshop.Result result = workshop.fit(boatId, "standard_drive");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.STANDARD_DRIVE, deck.boatById(boatId).drive());
        assertEquals(suppliesAfterUprating, hold.available(Commodities.SUPPLIES));
    }

    @Test
    void anUnaffordableFitChangesNeitherTheBoatNorTheHold() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(4);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();

        BoatWorkshop.Result result = workshop.fit(boatId, "armoured_plating");

        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, result.status());
        assertFalse(result.succeeded());
        assertSame(BoatFitting.STANDARD_PLATING, deck.boatById(boatId).plating());
        assertEquals(4, hold.available(Commodities.SUPPLIES));
        assertEquals(4, hold.available(Commodities.METALS));
        assertEquals(4, hold.available(Commodities.HEAVY_MACHINERY));
    }

    /** Refitting what is already there would be a bill for no work. */
    @Test
    void refittingWhatIsAlreadyInstalledIsRefusedWithoutSpending() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();
        workshop.fit(boatId, "tuned_drive");
        int supplies = hold.available(Commodities.SUPPLIES);

        BoatWorkshop.Result result = workshop.fit(boatId, "tuned_drive");

        assertEquals(BoatWorkshop.Status.ALREADY_FITTED, result.status());
        assertTrue(result.succeeded(), "the boat is in the state that was asked for");
        assertEquals(supplies, hold.available(Commodities.SUPPLIES));
    }

    @Test
    void anUnknownBoatOrFittingIsRefusedBeforeAnythingIsSpent() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.airworthy().get(0).id();

        BoatWorkshop.Result noBoat = workshop.fit("boat_99", "reinforced_plating");
        BoatWorkshop.Result noFitting = workshop.fit(boatId, "ablative_hopes");

        assertEquals(BoatWorkshop.Status.UNKNOWN_BOAT, noBoat.status());
        assertNull(noBoat.boat());
        assertEquals(BoatWorkshop.Status.UNKNOWN_FITTING, noFitting.status());
        assertEquals(100, hold.available(Commodities.SUPPLIES));
    }

    @Test
    void fabricatingIntoAVacantBerthSpendsTheBillAndStandsANewBoat() {
        BoatDeck deck = deck();
        String lost = deck.airworthy().get(0).id();
        deck.lose(List.of(lost));
        TestResources hold = TestResources.stocked(200);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        FabricationCost bill = BoatFabricationCatalog.recipe(ShuttleType.AEROSHUTTLE).bill();

        BoatWorkshop.Result result = workshop.fabricate(0);

        assertEquals(BoatWorkshop.Status.FABRICATED, result.status());
        assertTrue(result.succeeded());
        assertEquals(ShuttleType.AEROSHUTTLE, result.boat().pattern());
        assertSame(BoatFitting.STANDARD_PLATING, result.boat().plating());
        assertSame(BoatFitting.STANDARD_DRIVE, result.boat().drive());
        assertNotEquals(lost, result.boat().id(), "a tail number is never reused");
        assertSame(result.boat(), deck.boats().get(0));
        assertEquals(List.of(), deck.vacantBerths());
        for (FabricationCost.Line line : bill.lines()) {
            assertEquals(200 - line.quantity(), hold.available(line.commodityId()));
        }
    }

    /** A berth with a boat in it is not somewhere to build a second one. */
    @Test
    void aBerthThatIsHeldIsRefusedWithoutSpending() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(200);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        CampaignBoat standing = deck.boats().get(0);

        BoatWorkshop.Result held = workshop.fabricate(0);
        BoatWorkshop.Result offTheEnd = workshop.fabricate(7);

        assertEquals(BoatWorkshop.Status.BERTH_OCCUPIED, held.status());
        assertSame(standing, held.boat());
        assertEquals(BoatWorkshop.Status.NO_SUCH_BERTH, offTheEnd.status());
        assertEquals(200, hold.available(Commodities.METALS));
        assertEquals(2, deck.airworthy().size());
    }

    @Test
    void aBillTheHoldCannotCoverBuildsNothing() {
        BoatDeck deck = deck();
        deck.lose(List.of(deck.airworthy().get(0).id()));
        TestResources hold = TestResources.stocked(10);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);

        BoatWorkshop.Result result = workshop.fabricate(0);

        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, result.status());
        assertFalse(result.succeeded());
        assertNull(deck.boats().get(0));
        assertEquals(10, hold.available(Commodities.METALS));
        assertEquals(10, hold.available(Commodities.HEAVY_MACHINERY));
        assertEquals(10, hold.available(Commodities.SUPPLIES));
    }

    /** A deck nobody has reconciled has no berths and nothing to build into them. */
    @Test
    void aDeckThatDoesNotKnowWhatSheCarriesRefusesToBuild() {
        BoatDeck deck = new BoatDeck();
        TestResources hold = TestResources.stocked(200);

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fabricate(0);

        assertEquals(BoatWorkshop.Status.NO_SUCH_BERTH, result.status());
        assertEquals(200, hold.available(Commodities.METALS));
    }

    private static BoatDeck deck() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("fleet-member-transport", ShuttleType.AEROSHUTTLE, 2);
        return deck;
    }

    static final class TestResources implements FabricationResources {
        private final Map<String, Integer> stock = new HashMap<>();

        static TestResources stocked(int quantity) {
            TestResources resources = new TestResources();
            resources.stock.put(Commodities.SUPPLIES, quantity);
            resources.stock.put(Commodities.HEAVY_MACHINERY, quantity);
            resources.stock.put(Commodities.METALS, quantity);
            resources.stock.put(Commodities.RARE_METALS, quantity);
            return resources;
        }

        @Override public int available(String commodityId) {
            return stock.getOrDefault(commodityId, 0);
        }

        @Override public String commodityName(String commodityId) {
            return commodityId.replace('_', ' ');
        }

        @Override public String commodityIcon(String commodityId) {
            return "graphics/icons/cargo/" + commodityId + ".png";
        }

        @Override public boolean spend(FabricationCost cost) {
            if (!canAfford(cost)) return false;
            for (FabricationCost.Line line : cost.lines()) {
                stock.merge(line.commodityId(), -line.quantity(), Integer::sum);
            }
            return true;
        }
    }
}
