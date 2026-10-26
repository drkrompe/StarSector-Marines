package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.turret.TurretRole;
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
 * A build is one transaction against the fleet's hold, or it is nothing at
 * all; a fit moves parts between a boat and stores and never touches the hold.
 */
class BoatWorkshopTest {

    /** The Hermes is the company's boat: every upgrade goes on her, from a spare. */
    @Test
    void theHermesTakesEveryUpgradeFromASpare() {
        BoatDeck deck = new BoatDeck();
        deck.reconcile("second-wind", ShuttleType.HERMES, 1);
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(1_000));
        CampaignBoat boat = deck.flightReady().get(0);

        for (BoatFittingSlot slot : BoatFittingSlot.values()) {
            BoatFitting top = BoatFitting.catalog(slot).get(BoatFitting.catalog(slot).size() - 1);
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
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(1_000));
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
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(1_000));
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
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

        assertEquals(BoatWorkshop.Status.PART_FABRICATED, workshop.fabricatePart("reinforced_plating").status());
        assertEquals(90, hold.available(Commodities.SUPPLIES));
        assertEquals(80, hold.available(Commodities.METALS));
        assertEquals(100, hold.available(Commodities.HEAVY_MACHINERY),
                "a plating bill has no machinery line and must not invent one");
        assertEquals(1, deck.spares("reinforced_plating"));

        BoatWorkshop.Result result = workshop.fit(boatId, "reinforced_plating");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.REINFORCED_PLATING, deck.boatById(boatId).plating());
        assertEquals(0, deck.spares("reinforced_plating"));
        assertEquals(90, hold.available(Commodities.SUPPLIES), "fitting a spare spends nothing");
    }

    @Test
    void aFitWithNoSpareChangesNothing() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        String boatId = deck.flightReady().get(0).id();

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fit(boatId, "armoured_plating");

        assertEquals(BoatWorkshop.Status.NO_SPARE, result.status());
        assertFalse(result.succeeded());
        assertSame(BoatFitting.STANDARD_PLATING, deck.boatById(boatId).plating());
        assertEquals(100, hold.available(Commodities.SUPPLIES));
    }

    /** The stock part is a part like any other: a refit stores it, and putting it back takes it out again. */
    @Test
    void theStockPartComesOffIntoStoresAndGoesBackOnFromThere() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();
        workshop.fabricatePart("uprated_drive");
        workshop.fit(boatId, "uprated_drive");
        Map<String, Integer> afterUprating = Map.copyOf(hold.stock);
        assertEquals(1, deck.spares("standard_drive"), "the stock drive that came off is in stores");

        BoatWorkshop.Result result = workshop.fit(boatId, "standard_drive");

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertSame(BoatFitting.STANDARD_DRIVE, deck.boatById(boatId).drive());
        assertEquals(1, deck.spares("uprated_drive"));
        assertEquals(0, deck.spares("standard_drive"));
        assertEquals(afterUprating, hold.stock);
    }

    /** A part moves between boats through stores: off one, onto the other, nothing rebuilt. */
    @Test
    void aPartStrippedFromOneBoatFitsAnotherWithoutABill() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String first = deck.flightReady().get(0).id();
        String second = deck.flightReady().get(1).id();
        workshop.fabricatePart("tuned_drive");
        workshop.fit(first, "tuned_drive");
        Map<String, Integer> afterBuild = Map.copyOf(hold.stock);

        assertEquals(BoatWorkshop.Status.STRIPPED, workshop.strip(first, BoatFittingSlot.DRIVE).status());
        assertNull(deck.boatById(first).drive());
        assertEquals(BoatWorkshop.Status.NOTHING_TO_STRIP, workshop.strip(first, BoatFittingSlot.DRIVE).status());
        assertEquals(BoatWorkshop.Status.FITTED, workshop.fit(second, "tuned_drive").status());

        assertSame(BoatFitting.TUNED_DRIVE, deck.boatById(second).drive());
        assertEquals(0, deck.spares("tuned_drive"));
        assertEquals(afterBuild, hold.stock);
    }

    /** Pulling a required part grounds her, as a mech without its engine is held off the drop. */
    @Test
    void aBoatStrippedOfARequiredPartIsOffTheLiftUntilOneIsFitted() {
        BoatDeck deck = deck();
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(100));
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
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(100));
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
        TestResources hold = TestResources.stocked(4);

        BoatWorkshop.Result result = new BoatWorkshop(deck, hold).fabricatePart("armoured_plating");

        assertEquals(BoatWorkshop.Status.CANNOT_AFFORD, result.status());
        assertEquals(0, deck.spares("armoured_plating"));
        assertEquals(4, hold.available(Commodities.SUPPLIES));
        assertEquals(4, hold.available(Commodities.METALS));
        assertEquals(4, hold.available(Commodities.HEAVY_MACHINERY));
    }

    /** Refitting what is already there would be a spare spent for no work. */
    @Test
    void refittingWhatIsAlreadyInstalledIsRefusedWithoutTakingASpare() {
        BoatDeck deck = deck();
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(100));
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
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

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
        String lost = deck.flightReady().get(0).id();
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
        assertEquals(2, deck.flightReady().size());
    }

    @Test
    void aBillTheHoldCannotCoverBuildsNothing() {
        BoatDeck deck = deck();
        deck.lose(List.of(deck.flightReady().get(0).id()));
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
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();

        assertEquals(BoatWorkshop.Status.NO_SPARE, workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id()).status());
        workshop.fabricatePart(BoatGun.ARBALEST.id());
        BoatWorkshop.Result result = workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id());

        assertEquals(BoatWorkshop.Status.FITTED, result.status());
        assertEquals(90, hold.available(Commodities.SUPPLIES));
        assertEquals(95, hold.available(Commodities.HEAVY_MACHINERY));
        assertEquals(80, hold.available(Commodities.METALS));
        assertEquals(List.of(BoatGun.ARBALEST.structureId()),
                List.of(deck.boatById(boatId).freezeForDeployment().kitFor(TurretRole.A2G)));
        assertEquals(0, deck.boatById(boatId).freezeForDeployment().kitFor(TurretRole.AA).length,
                "a refit changes what an air-to-ground sortie mounts, not what other roles do");
    }

    /** The yard's gun is a gun like any other: it comes off into stores and goes back on from there. */
    @Test
    void theYardGunComesOffIntoStoresAndLeavesTheHardpointBare() {
        BoatDeck deck = deck();
        TestResources hold = TestResources.stocked(100);
        BoatWorkshop workshop = new BoatWorkshop(deck, hold);
        String boatId = deck.flightReady().get(0).id();
        workshop.fabricatePart(BoatGun.ARBALEST.id());
        workshop.fitGun(boatId, 0, BoatGun.ARBALEST.id());
        Map<String, Integer> afterRefit = Map.copyOf(hold.stock);
        assertEquals(1, deck.spares(BoatGun.HEAVY_MG.id()), "the yard gun that came off is in stores");

        assertEquals(BoatWorkshop.Status.FITTED, workshop.fitGun(boatId, 0, BoatGun.HEAVY_MG.id()).status());
        assertEquals(afterRefit, hold.stock);
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
        BoatWorkshop workshop = new BoatWorkshop(deck, TestResources.stocked(1_000));
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
