package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build is one transaction against the fleet's hold, or it is nothing at
 * all; a fit moves parts between a boat and stores and never touches the hold.
 */
class BoatWorkshopTest {

    /** The Hermes is the company's boat: every upgrade goes on her, from a spare. */
    @Test
    void theHermesTakesEveryUpgradeFromASpare() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);

        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting top = BoatFitting.catalog(slot).get(BoatFitting.catalog(slot).size() - 1);
            if (top.standard()) continue;
            workshop.fabricatePart(top.id());
            assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), top.id()).status(), top.id());
            workshop.strip(boat.id(), slot);
        }
    }

    /** Her three turret slots: a heavy MG from the yard and two bare ones, each taking any family of gun. */
    @Test
    void theHermesFillsHerBareTurretsWithAnyFamilyAndStripsThemBare() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);
        assertSame(BoatGun.HEAVY_MG, boat.gunOn(0));
        assertNull(boat.gunOn(1));
        assertNull(boat.gunOn(2));
        assertEquals(BoatWorkshop.Status.NOTHING_TO_STRIP, workshop.stripGun(boat.id(), 1).status());

        workshop.fabricatePart(BoatGun.SRM_POD.id());
        workshop.fabricatePart(BoatGun.PULSE_LASER.id());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fitGun(boat.id(), 1, BoatGun.SRM_POD.id()).status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fitGun(boat.id(), 2, BoatGun.PULSE_LASER.id()).status());
        assertEquals(BoatGun.SRM_POD.weight() + BoatGun.PULSE_LASER.weight(), BoatFrame.weight(boat));

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.stripGun(boat.id(), 1).status());
        assertNull(boat.gunOn(1), "a stripped turret goes back bare");
        assertEquals(1, deck.spares(BoatGun.SRM_POD.id()));
        assertEquals(BoatWorkshop.Status.FITTED, workshop.moveGun(boat.id(), 2, 1).status());
        assertSame(BoatGun.PULSE_LASER, boat.gunOn(1));
        assertNull(boat.gunOn(2), "a gun moved off a bare slot leaves it bare");
        assertEquals(BoatWorkshop.Status.UNKNOWN_HARDPOINT, workshop.stripGun(boat.id(), 3).status());
    }

    /** A refit that takes her over the frame's rating is refused; one that lightens her never is. */
    @Test
    void aFitOverTheFrameRatingIsRefusedAndKeepsItsSpare() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);
        for (String part : List.of("heavy_lift_frame", "uprated_drive", "armoured_plating")) workshop.fabricatePart(part);
        workshop.fit(boat.id(), "heavy_lift_frame");
        workshop.fit(boat.id(), "uprated_drive");
        assertEquals(9, BoatFrame.weight(boat));

        assertEquals(BoatWorkshop.Status.OVER_FRAME, workshop.fit(boat.id(), "armoured_plating").status());
        assertEquals(1, deck.spares("armoured_plating"));
        assertSame(BoatFitting.STANDARD_PLATING, boat.plating());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), "standard_drive").status(),
                "taking weight off is always allowed");
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), "armoured_plating").status());
    }

    @Test
    void buildingSpendsExactlyTheBillAndFittingTakesTheSpare() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();
        long before = hold.credits;

        assertEquals(BoatWorkshop.Status.PART_FABRICATED, workshop.fabricatePart("reinforced_plating").status());
        assertEquals(before - BoatFitting.REINFORCED_PLATING.bill().credits(), hold.credits);
        assertEquals(1, deck.spares("reinforced_plating"));
        long afterBuild = hold.credits;

        BoatWorkshop.Result result = workshop.fit(boatId, "reinforced_plating");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.REINFORCED_PLATING, deck.boatById(boatId).plating());
        assertEquals(0, deck.spares("reinforced_plating"));
        assertEquals(afterBuild, hold.credits, "fitting a spare spends nothing");
    }

    @Test
    void aFitWithNoSpareChangesNothing() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        String boatId = deck.flightReady().get(0).id();

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fit(boatId, "armoured_plating");

        assertEquals(BoatWorkshop.Status.NO_SPARE, result.status());
        assertFalse(result.succeeded());
        assertSame(BoatFitting.STANDARD_PLATING, deck.boatById(boatId).plating());
        assertEquals(0, hold.spends);
    }

    /** The stock part is a part like any other: a refit stores it, and putting it back takes it out again. */
    @Test
    void theStockPartComesOffIntoStoresAndGoesBackOnFromThere() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();
        workshop.fabricatePart("uprated_drive");
        workshop.fit(boatId, "uprated_drive");
        long afterUprating = hold.credits;
        assertEquals(1, deck.spares("standard_drive"), "the stock drive that came off is in stores");

        BoatWorkshop.Result result = workshop.fit(boatId, "standard_drive");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.STANDARD_DRIVE, deck.boatById(boatId).drive());
        assertEquals(1, deck.spares("uprated_drive"));
        assertEquals(0, deck.spares("standard_drive"));
        assertEquals(afterUprating, hold.credits);
    }

    /** A part moves between boats through stores: off one, onto the other, nothing rebuilt. */
    @Test
    void aPartStrippedFromOneBoatFitsAnotherWithoutABill() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String first = deck.flightReady().get(0).id();
        String second = deck.flightReady().get(1).id();
        workshop.fabricatePart("tuned_drive");
        workshop.fit(first, "tuned_drive");
        long afterBuild = hold.credits;

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(first, BoatFittingSlot.DRIVE).status());
        assertNull(deck.boatById(first).drive());
        assertEquals(BoatWorkshop.Status.NOTHING_TO_STRIP, workshop.strip(first, BoatFittingSlot.DRIVE).status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(second, "tuned_drive").status());

        assertSame(BoatFitting.TUNED_DRIVE, deck.boatById(second).drive());
        assertEquals(0, deck.spares("tuned_drive"));
        assertEquals(afterBuild, hold.credits);
    }

    /** Pulling a required part grounds her, as a mech without its engine is held off the drop. */
    @Test
    void aBoatStrippedOfARequiredPartIsOffTheLiftUntilOneIsFitted() {
        BoatDeck deck = deck();
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(boat.id(), BoatFittingSlot.EMITTER).status());
        assertEquals(List.of(), boat.missingParts(), "the emitter is an option: she flies unshielded");
        assertNull(boat.freezeForDeployment().shieldSpec());

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(boat.id(), BoatFittingSlot.DRIVE).status());
        assertEquals(List.of(BoatFittingSlot.DRIVE), boat.missingParts());
        assertFalse(deck.flightReady().contains(boat));
        assertEquals(1, deck.spares("standard_drive"));

        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), "standard_drive").status());
        assertTrue(deck.flightReady().contains(boat));
    }

    /** Plating is an option: stripped, she still flies, her hull whole but with no armour over it. */
    @Test
    void aBoatStrippedOfHerPlatingFliesWithNoArmorOverHerHull() {
        BoatDeck deck = deck();
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);
        float stock = boat.freezeForDeployment().maxHp();

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(boat.id(), BoatFittingSlot.PLATING).status());

        assertEquals(List.of(), boat.missingParts());
        assertTrue(deck.flightReady().contains(boat));
        assertNull(boat.freezeForDeployment().plating());
        assertEquals(stock, boat.freezeForDeployment().maxHp(), 1e-3f, "plating is armour, not hull");
        assertEquals(0f, boat.freezeForDeployment().armor(), 1e-3f);
    }

    @Test
    void anUnaffordableBuildChangesNeitherTheStoresNorTheHold() {
        BoatDeck deck = deck();
        TestPurse hold = new TestPurse(4);

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fabricatePart("armoured_plating");

        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, result.status());
        assertEquals(0, deck.spares("armoured_plating"));
        assertEquals(4, hold.credits);
        assertEquals(0, hold.spends);
    }

    /** Refitting what is already there would be a spare spent for no work. */
    @Test
    void refittingWhatIsAlreadyInstalledIsRefusedWithoutTakingASpare() {
        BoatDeck deck = deck();
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        String boatId = deck.flightReady().get(0).id();
        workshop.fabricatePart("tuned_drive");
        workshop.fabricatePart("tuned_drive");
        workshop.fit(boatId, "tuned_drive");

        BoatWorkshop.Result result = workshop.fit(boatId, "tuned_drive");

        assertEquals(BoatWorkshop.Status.ALREADY_FITTED, result.status());
        assertTrue(result.succeeded(), "the boat is in the state that was asked for");
        assertEquals(1, deck.spares("tuned_drive"));
    }

    @Test
    void anUnknownBoatOrFittingIsRefusedBeforeAnythingIsSpent() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

        BoatWorkshop.Result noBoat = workshop.fit("boat_99", "reinforced_plating");
        BoatWorkshop.Result noFitting = workshop.fit(boatId, "ablative_hopes");

        assertEquals(BoatWorkshop.Status.UNKNOWN_BOAT, noBoat.status());
        assertNull(noBoat.boat());
        assertEquals(BoatWorkshop.Status.UNKNOWN_FITTING, noFitting.status());
        assertEquals(0, hold.spends);
    }

    @Test
    void fabricatingIntoAVacantBerthSpendsTheBillAndStandsANewBoat() {
        BoatDeck deck = deck();
        String lost = deck.flightReady().get(0).id();
        deck.lose(List.of(lost));
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        FabricationCost bill = BoatFabricationCatalog.recipe(ShuttleType.AEROSHUTTLE).bill();
        long before = hold.credits;

        BoatWorkshop.Result result = workshop.fabricate(0);

        assertEquals(BoatWorkshop.Status.FABRICATED, result.status());
        assertTrue(result.succeeded());
        assertEquals(ShuttleType.AEROSHUTTLE, result.boat().pattern());
        assertSame(BoatFitting.STANDARD_PLATING, result.boat().plating());
        assertSame(BoatFitting.STANDARD_DRIVE, result.boat().drive());
        assertNotEquals(lost, result.boat().id(), "a tail number is never reused");
        assertSame(result.boat(), deck.boats().get(0));
        assertEquals(List.of(), deck.vacantBerths());
        assertEquals(before - bill.credits(), hold.credits);
    }

    /** A berth with a boat in it is not somewhere to build a second one. */
    @Test
    void aBerthThatIsHeldIsRefusedWithoutSpending() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        CampaignBoat standing = deck.boats().get(0);

        BoatWorkshop.Result held = workshop.fabricate(0);
        BoatWorkshop.Result offTheEnd = workshop.fabricate(7);

        assertEquals(BoatWorkshop.Status.BERTH_OCCUPIED, held.status());
        assertSame(standing, held.boat());
        assertEquals(BoatWorkshop.Status.NO_SUCH_BERTH, offTheEnd.status());
        assertEquals(0, hold.spends);
        assertEquals(2, deck.flightReady().size());
    }

    @Test
    void aBillTheHoldCannotCoverBuildsNothing() {
        BoatDeck deck = deck();
        deck.lose(List.of(deck.flightReady().get(0).id()));
        TestPurse hold = new TestPurse(10);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);

        BoatWorkshop.Result result = workshop.fabricate(0);

        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, result.status());
        assertFalse(result.succeeded());
        assertNull(deck.boats().get(0));
        assertEquals(10, hold.credits);
        assertEquals(0, hold.spends);
    }

    /** A deck nobody has reconciled has no berths and nothing to build into them. */
    @Test
    void aDeckThatDoesNotKnowWhatSheCarriesRefusesToBuild() {
        BoatDeck deck = new BoatDeck();
        TestPurse hold = TestPurse.rich();

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fabricate(0);

        assertEquals(BoatWorkshop.Status.NO_SUCH_BERTH, result.status());
        assertEquals(0, hold.spends);
    }

    /** A hardpoint nobody refitted flies the sortie kit's gun, which is what every old save carries. */
    @Test
    void anUnrefittedHardpointCarriesTheSortieKitsGun() {
        CampaignBoat boat = deck().flightReady().get(0);
        assertSame(BoatGun.HEAVY_MG, boat.gunOn(0));
        assertNull(boat.gunOn(1), "an Aeroshuttle has one hardpoint");
        assertEquals(List.of(BoatGun.HEAVY_MG.structureId()), boat.freezeForDeployment().guns());
    }

    @Test
    void aBuiltDoorGunArmsTheFrozenBoat() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

        assertEquals(BoatWorkshop.Status.NO_SPARE, workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id()).status());
        long before = hold.credits;
        workshop.fabricatePart(BoatGun.ARBALEST.id());
        BoatWorkshop.Result result = workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id());

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertEquals(before - BoatGun.ARBALEST.bill().credits(), hold.credits);
        assertEquals(List.of(BoatGun.ARBALEST.structureId()),
                List.of(deck.boatById(boatId).freezeForDeployment().kitFor(TurretRole.A2G)));
        assertEquals(0, deck.boatById(boatId).freezeForDeployment().kitFor(TurretRole.AA).length,
                "a refit changes what an air-to-ground sortie mounts, not what other roles do");
    }

    /** The yard's gun is a gun like any other: it comes off into stores and goes back on from there. */
    @Test
    void theYardGunComesOffIntoStoresAndLeavesTheHardpointBare() {
        BoatDeck deck = deck();
        TestPurse hold = TestPurse.rich();
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();
        workshop.fabricatePart(BoatGun.ARBALEST.id());
        workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id());
        long afterRefit = hold.credits;
        assertEquals(1, deck.spares(BoatGun.HEAVY_MG.id()), "the yard gun that came off is in stores");

        assertEquals(BoatWorkshop.Status.FITTED, workshop.fitGun(boatId, 0, BoatGun.HEAVY_MG.id()).status());
        assertEquals(afterRefit, hold.credits);
        assertEquals(1, deck.spares(BoatGun.ARBALEST.id()));
        assertEquals(0, deck.spares(BoatGun.HEAVY_MG.id()));

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.stripGun(boatId, 0).status());
        assertNull(deck.boatById(boatId).gunOn(0));
        assertEquals(1, deck.spares(BoatGun.HEAVY_MG.id()));
        assertNull(deck.boatById(boatId).freezeForDeployment().kitFor(TurretRole.A2G)[0], "a bare hardpoint flies no gun");
        assertEquals(BoatWorkshop.Status.UNKNOWN_HARDPOINT,
                workshop.fitGun(boatId, 3, BoatGun.HEAVY_MG.id()).status());
    }

    /** A gun moved between hardpoints leaves its own bare and stores what it displaced; the yard's gun moves too. */
    @Test
    void movingAGunBetweenHardpointsStoresWhatItDisplaced() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("valkyrie", ShuttleType.VALKYRIE, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, TestPurse.rich());
        CampaignBoat boat = deck.flightReady().get(0);
        BoatGun yard = BoatGun.standardFor(ShuttleType.VALKYRIE, 0);
        workshop.fabricatePart(BoatGun.HEPHAESTUS.id());
        workshop.fabricatePart(BoatGun.GRENADE_LAUNCHER.id());
        workshop.fitGun(boat.id(), 0, BoatGun.HEPHAESTUS.id());
        workshop.fitGun(boat.id(), 1, BoatGun.GRENADE_LAUNCHER.id());

        assertEquals(BoatWorkshop.Status.FITTED, workshop.moveGun(boat.id(), 0, 1).status());

        assertNull(boat.gunOn(0));
        assertSame(BoatGun.HEPHAESTUS, boat.gunOn(1));
        assertEquals(1, deck.spares(BoatGun.GRENADE_LAUNCHER.id()));
        assertEquals(0, deck.spares(BoatGun.HEPHAESTUS.id()));
        assertEquals(BoatWorkshop.Status.NOTHING_TO_STRIP, workshop.moveGun(boat.id(), 0, 1).status(),
                "a bare hardpoint has nothing to move");

        workshop.fitGun(boat.id(), 0, yard.id());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.moveGun(boat.id(), 0, 1).status());
        assertSame(yard, boat.gunOn(1));
    }

    /** Buying needs a port: away from one every purchase is refused unspent, while work on what is aboard goes on. */
    @Test
    void awayFromAPortNothingIsBoughtButFittingStrippingMovingAndRecyclingStillWork() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        TestPurse purse = TestPurse.rich().reclaiming(Reclamation.FULL);
        BoatWorkshop workshop = new BoatWorkshop(deck, purse);
        CampaignBoat boat = deck.flightReady().get(0);
        String part = BoatFitting.TUNED_DRIVE.id();
        workshop.fabricatePart(part);
        workshop.fabricatePart(BoatGun.SRM_POD.id());
        deck.settleRecovery("battle", Map.of(boat.id(), 1f), List.of(), 0f);
        BoatWorkshop.RepairQuote reviewed = workshop.repairQuote(boat.id(), 0f);
        deck.lose(List.of(deck.flightReady().get(0).id()));
        int vacant = deck.vacantBerths().get(0);
        long before = purse.credits;
        int spends = purse.spends;

        purse.away();
        assertEquals(BoatWorkshop.Status.NO_MARKET, workshop.fabricatePart(part).status());
        assertEquals(BoatWorkshop.Status.NO_MARKET, workshop.fabricate(vacant).status());
        assertEquals(BoatWorkshop.Status.NO_MARKET, workshop.expedite(reviewed, 0f).status());
        assertEquals(before, purse.credits);
        assertEquals(spends, purse.spends);
        assertNull(deck.boats().get(vacant));
        assertFalse(boat.airworthy(0f));

        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(boat.id(), part).status());
        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(boat.id(), BoatFittingSlot.DRIVE).status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fitGun(boat.id(), 1, BoatGun.SRM_POD.id()).status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.moveGun(boat.id(), 1, 2).status());
        assertEquals(BoatWorkshop.Status.RECYCLED, workshop.recycle(part).status());
        assertEquals(before + purse.refunded, purse.credits);
    }
    private static BoatDeck deck() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("fleet-member-transport", ShuttleType.AEROSHUTTLE, 2);
        return deck;
    }
}



