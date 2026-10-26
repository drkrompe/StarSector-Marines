package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;
import com.dillon.starsectormarines.ops.BoatDeckViewModel;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A spare breaks back down into a share of its bill, at a rate the machine shop sets, or not at all. */
class RecyclingTest {

    @Test void theMachineShopsStageSetsTheShareAndItRoundsDown() {
        assertSame(Reclamation.NONE, CompanyWorkshopRestoration.reclamation(WorkshopState.BURIED));
        assertSame(Reclamation.NONE, CompanyWorkshopRestoration.reclamation(WorkshopState.CLEARED));
        assertSame(Reclamation.MACHINERY, CompanyWorkshopRestoration.reclamation(WorkshopState.REPAIRED));
        assertSame(Reclamation.TOOLED, CompanyWorkshopRestoration.reclamation(WorkshopState.EQUIPPED));
        assertSame(Reclamation.FULL, CompanyWorkshopRestoration.reclamation(WorkshopState.RECLAIMING));
        assertSame(Reclamation.MACHINERY, CompanyWorkshopRestoration.reclamation(WorkshopState.NONE),
                "a hull outside the restoration chain runs its shop as built");

        var bill = new FabricationCost(1001);
        assertNull(Reclamation.NONE.recover(bill));
        assertEquals(500, Reclamation.MACHINERY.recover(bill).credits());
        assertEquals(750, Reclamation.TOOLED.recover(bill).credits());
        assertEquals(1001, Reclamation.FULL.recover(bill).credits(), "the reclamation line returns the whole bill");
        assertNull(Reclamation.MACHINERY.recover(new FabricationCost(1)), "a share that rounds to nothing is dropped");
    }

    @Test void aHullIsItsBillLessTheStockPartsThatComeBackOnTheirOwn() {
        assertEquals(500, Reclamation.hull(new FabricationCost(1000),
                List.of(new FabricationCost(300), new FabricationCost(200))).credits());
        assertNull(Reclamation.hull(new FabricationCost(200), List.of(new FabricationCost(200))),
                "parts that use up the bill leave no hull, never a negative one");
    }

    @Test void aScrappedBoatGoesToStoresAndHerHullToThePurseWithoutPayingForHerPartsTwice() {
        var deck = boatDeck(ShuttleType.VALKYRIE);
        var purse = TestPurse.rich().reclaiming(Reclamation.FULL);
        var workshop = new BoatWorkshop(deck, purse);
        long start = purse.credits;
        assertEquals(BoatWorkshop.Status.FABRICATED, workshop.fabricate(0).status());
        CampaignBoat boat = deck.boats().get(0);

        purse.accepts = false;
        assertEquals(BoatWorkshop.Status.NO_HOLD, workshop.scrap(boat.id()).status());
        assertSame(boat, deck.boats().get(0), "a refused scrap leaves her standing");
        assertEquals(0, deck.spares(BoatFitting.STANDARD_PLATING.id()));

        purse.accepts = true;
        assertEquals(BoatWorkshop.Status.SCRAPPED, workshop.scrap(boat.id()).status());
        assertEquals(List.of(0), deck.vacantBerths());
        assertEquals(1, deck.spares(BoatFitting.STANDARD_PLATING.id()), "her stock plating is a spare now");
        sweep(deck, workshop);
        assertEquals(start, purse.credits, "at the full rate building and breaking her up is exactly free");
    }

    @Test void buildingABoatThenRecyclingHerPartsAndScrappingHerNeverTurnsAProfit() {
        for (ShuttleType pattern : List.of(ShuttleType.HERMES, ShuttleType.VALKYRIE)) {
            for (Reclamation level : Reclamation.values()) {
                for (boolean tooled : List.of(false, true)) {
                    var deck = boatDeck(pattern);
                    var purse = TestPurse.rich().reclaiming(level);
                    if (tooled) purse.quote = base -> WorkshopTooling.quote(base, true);
                    var workshop = new BoatWorkshop(deck, purse);
                    long start = purse.credits;
                    FabricationCost price = workshop.fabricationQuote(pattern);
                    String label = pattern + " " + level + (tooled ? " tooled" : "");
                    assertEquals(BoatWorkshop.Status.FABRICATED, workshop.fabricate(0).status(), label);
                    assertEquals(start - price.credits(), purse.credits, label);

                    assertEquals(BoatWorkshop.Status.SCRAPPED, workshop.scrap(deck.boats().get(0).id()).status(), label);
                    sweep(deck, workshop);

                    assertTrue(purse.refunded <= price.credits(), label + " paid back " + purse.refunded + " of " + price.credits());
                    assertTrue(purse.credits <= start, label);
                    if (level == Reclamation.FULL) assertEquals(price.credits(), purse.refunded, label + ": exactly free");
                }
            }
        }
    }

    @Test void aScrappedMachineReturnsTheShopsShareAndABuriedShopStillFreesThePad() {
        var bay = new MechBay();
        var purse = TestPurse.rich().reclaiming(Reclamation.FULL);
        var workshop = new MechWorkshop(bay, purse, () -> 4);
        long start = purse.credits;
        CampaignMech built = workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).mech();
        FabricationCost bill = MechFabricationCatalog.chassis(MechVariant.HOUND).cost();
        assertEquals(start - bill.credits(), purse.credits);
        assertEquals(MechWorkshop.Status.SCRAPPED, workshop.scrapMech(built.id()).status());
        // The frame's own parts are spares now; break those down too.
        for (MechWeaponComponent weapon : MechWeaponComponent.values()) {
            while (bay.availableWeapon(weapon.id) > 0 && workshop.recycleWeapon(weapon).succeeded()) { }
        }
        for (MechInternal internal : MechInternal.values()) {
            while (bay.availableInternal(internal.id) > 0 && workshop.recycleInternal(internal).succeeded()) { }
        }
        assertTrue(purse.credits <= start, "building and breaking up a chassis never turns a profit");
        assertTrue(purse.credits > start - bill.credits(), "the full rate gives something back");

        purse.reclaiming(Reclamation.NONE);
        CampaignMech second = workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).mech();
        long before = purse.credits;
        assertNull(workshop.mechSalvage(second.id()));
        assertEquals(MechWorkshop.Status.SCRAPPED, workshop.scrapMech(second.id()).status());
        assertEquals(before, purse.credits);
        assertNull(bay.mechById(second.id()));
    }

    @Test void aRecycledSpareLeavesStoresOnlyOnceThePurseHasItsCredits() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        var purse = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        var workshop = new BoatWorkshop(deck, purse);
        String part = BoatFitting.REINFORCED_PLATING.id();

        assertEquals(BoatWorkshop.Status.NO_SPARE, workshop.recycle(part).status());
        deck.stow(part);

        purse.reclaiming(Reclamation.NONE);
        assertEquals(BoatWorkshop.Status.WORKSHOP_IDLE, workshop.recycle(part).status());
        purse.reclaiming(Reclamation.MACHINERY);
        purse.accepts = false;
        long before = purse.credits;
        assertEquals(BoatWorkshop.Status.NO_HOLD, workshop.recycle(part).status());
        assertEquals(1, deck.spares(part));
        assertEquals(before, purse.credits);

        purse.accepts = true;
        assertEquals(BoatWorkshop.Status.RECYCLED, workshop.recycle(part).status());
        assertEquals(0, deck.spares(part));
        assertEquals(before + BoatFitting.REINFORCED_PLATING.bill().credits() / 2, purse.credits);
    }

    @Test void recoveryIsAShareOfTodaysPriceSoItNeverBeatsARebuild() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        var purse = TestPurse.rich().reclaiming(Reclamation.TOOLED);
        purse.quote = base -> WorkshopTooling.quote(base, true);
        var workshop = new BoatWorkshop(deck, purse);
        FabricationCost price = purse.quote(BoatFitting.REINFORCED_PLATING.bill());
        FabricationCost back = workshop.recycleQuote(BoatFitting.REINFORCED_PLATING.id());
        assertEquals(price.credits() * 3 / 4, back.credits());
        assertTrue(back.credits() < price.credits());
    }
    @Test void theMechLabRecyclesOnlyFreeSpares() {
        var bay = new MechBay();
        var hold = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        var workshop = new MechWorkshop(bay, hold);
        MechWeaponComponent weapon = MechFabricationCatalog.weapons().get(0).component();
        MechInternal internal = MechFabricationCatalog.internals().get(0).internal();

        assertEquals(MechWorkshop.Status.NO_SPARE, workshop.recycleWeapon(weapon).status());
        bay.addWeapon(weapon.id, 2);
        bay.addInternal(internal.id, 1);
        assertEquals(MechWorkshop.Status.RECYCLED, workshop.recycleWeapon(weapon).status());
        assertEquals(1, bay.availableWeapon(weapon.id));
        assertEquals(MechWorkshop.Status.RECYCLED, workshop.recycleInternal(internal).status());
        assertEquals(0, bay.availableInternal(internal.id));
        assertEquals(MechWorkshop.Status.NO_SPARE, workshop.recycleInternal(internal).status());

        hold.reclaiming(Reclamation.NONE);
        assertEquals(MechWorkshop.Status.WORKSHOP_IDLE, workshop.recycleWeapon(weapon).status());
        assertEquals(1, bay.availableWeapon(weapon.id));
    }

    @Test void theStoresCardsRecycleTakesASecondPressToCommit() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        String part = BoatFitting.REINFORCED_PLATING.id();
        deck.stow(part);
        var hold = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        var model = new BoatDeckViewModel(new Reactor(), deck, hold, "Second Wind");
        model.selectBoatAction(0).run();

        card(model, part).recycle().action().run();
        assertEquals(1, deck.spares(part), "the first press only arms the card");
        assertEquals(true, card(model, part).recycle().armed());
        card(model, part).recycle().action().run();
        assertEquals(0, deck.spares(part));
        assertNull(card(model, part).recycle(), "a card with no spare offers no RECYCLE");

        deck.stow(part);
        hold.reclaiming(Reclamation.NONE);
        var idle = new BoatDeckViewModel(new Reactor(), deck, hold, "Second Wind");
        idle.selectBoatAction(0).run();
        assertNull(card(idle, part).recycle(), "an idle machine shop offers no RECYCLE");
    }

    private static FittingCard card(BoatDeckViewModel model, String part) {
        return model.catalogRows().get().stream().filter(row -> part.equals(row.itemId())).findFirst().orElseThrow().card();
    }

    /** Recycles every boat spare in stores. */
    private static void sweep(BoatDeck deck, BoatWorkshop workshop) {
        List<String> ids = new ArrayList<>();
        for (BoatFittingSlot slot : BoatFittingSlot.values()) BoatFitting.catalog(slot).forEach(f -> ids.add(f.id()));
        BoatGun.catalog().forEach(g -> ids.add(g.id()));
        for (String part : ids) {
            while (deck.spares(part) > 0 && workshop.recycle(part).succeeded()) { }
        }
    }

    private static BoatDeck boatDeck(ShuttleType pattern) {
        var deck = new BoatDeck();
        deck.reconcile("ship", pattern, 1);
        deck.lose(List.of(deck.boats().get(0).id()));
        return deck;
    }
}

