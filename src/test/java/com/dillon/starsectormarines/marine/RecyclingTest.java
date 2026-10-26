package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.world.gen.ship.WorkshopState;
import com.dillon.starsectormarines.campaign.start.CompanyWorkshopRestoration;
import com.dillon.starsectormarines.ops.BoatDeckViewModel;
import com.dillon.starsectormarines.ops.fitting.FittingCard;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A spare breaks back down into a share of its bill, at a rate the machine shop sets, or not at all. */
class RecyclingTest {

    @Test void theMachineShopsStageSetsTheShareAndEachLineRoundsDown() {
        assertSame(Reclamation.NONE, CompanyWorkshopRestoration.reclamation(WorkshopState.BURIED));
        assertSame(Reclamation.NONE, CompanyWorkshopRestoration.reclamation(WorkshopState.CLEARED));
        assertSame(Reclamation.MACHINERY, CompanyWorkshopRestoration.reclamation(WorkshopState.REPAIRED));
        assertSame(Reclamation.TOOLED, CompanyWorkshopRestoration.reclamation(WorkshopState.EQUIPPED));
        assertSame(Reclamation.MACHINERY, CompanyWorkshopRestoration.reclamation(WorkshopState.NONE),
                "a hull outside the restoration chain runs its shop as built");

        var bill = cost(Commodities.METALS, 7, Commodities.SUPPLIES, 1);
        assertNull(Reclamation.NONE.recover(bill));
        assertEquals(List.of(new FabricationCost.Line(Commodities.METALS, 3)), Reclamation.MACHINERY.recover(bill).lines(),
                "a line that rounds to nothing is dropped");
        assertEquals(List.of(new FabricationCost.Line(Commodities.METALS, 5)), Reclamation.TOOLED.recover(bill).lines());
        assertNull(Reclamation.TOOLED.recover(cost(Commodities.SUPPLIES, 1)));
    }

    @Test void aRecycledSpareLeavesStoresOnlyOnceTheHoldHasItsMaterials() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        var hold = new Hold(Reclamation.MACHINERY);
        var workshop = new BoatWorkshop(deck, hold);
        String part = BoatFitting.REINFORCED_PLATING.id();

        assertEquals(BoatWorkshop.Status.NO_SPARE, workshop.recycle(part).status());
        assertEquals(BoatWorkshop.Status.NOT_FABRICABLE, workshop.recycle(BoatFitting.standard(BoatFittingSlot.PLATING).id()).status());
        deck.stow(part);

        hold.reclamation = Reclamation.NONE;
        assertEquals(BoatWorkshop.Status.WORKSHOP_IDLE, workshop.recycle(part).status());
        hold.reclamation = Reclamation.MACHINERY;
        hold.accepts = false;
        assertEquals(BoatWorkshop.Status.NO_HOLD, workshop.recycle(part).status());
        assertEquals(1, deck.spares(part));
        assertEquals(Map.of(), hold.stock);

        hold.accepts = true;
        assertEquals(BoatWorkshop.Status.RECYCLED, workshop.recycle(part).status());
        assertEquals(0, deck.spares(part));
        for (FabricationCost.Line line : BoatFitting.REINFORCED_PLATING.bill().lines()) {
            assertEquals(line.quantity() / 2, hold.stock.getOrDefault(line.commodityId(), 0), line.commodityId());
        }
    }

    @Test void recoveryIsAShareOfTodaysPriceSoItNeverBeatsARebuild() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        var hold = new Hold(Reclamation.TOOLED);
        hold.tooled = true;
        var workshop = new BoatWorkshop(deck, hold);
        FabricationCost price = hold.quote(BoatFitting.REINFORCED_PLATING.bill());
        FabricationCost back = workshop.recycleQuote(BoatFitting.REINFORCED_PLATING.id());
        for (FabricationCost.Line line : back.lines()) {
            int paid = price.lines().stream().filter(l -> l.commodityId().equals(line.commodityId()))
                    .mapToInt(FabricationCost.Line::quantity).sum();
            assertEquals(paid * 3 / 4, line.quantity(), line.commodityId());
        }
    }

    @Test void theMechLabRecyclesOnlyFreeSpares() {
        var bay = new MechBay();
        var hold = new Hold(Reclamation.MACHINERY);
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

        hold.reclamation = Reclamation.NONE;
        assertEquals(MechWorkshop.Status.WORKSHOP_IDLE, workshop.recycleWeapon(weapon).status());
        assertEquals(1, bay.availableWeapon(weapon.id));
    }

    @Test void theStoresCardsRecycleTakesASecondPressToCommit() {
        var deck = new BoatDeck();
        deck.reconcile("ship", ShuttleType.HERMES, 1);
        String part = BoatFitting.REINFORCED_PLATING.id();
        deck.stow(part);
        var hold = new Hold(Reclamation.MACHINERY);
        var model = new BoatDeckViewModel(new Reactor(), deck, hold, "Second Wind");
        model.selectBoatAction(0).run();

        card(model, part).recycle().action().run();
        assertEquals(1, deck.spares(part), "the first press only arms the card");
        assertEquals(true, card(model, part).recycle().armed());
        card(model, part).recycle().action().run();
        assertEquals(0, deck.spares(part));
        assertNull(card(model, part).recycle(), "a card with no spare offers no RECYCLE");

        deck.stow(part);
        hold.reclamation = Reclamation.NONE;
        var idle = new BoatDeckViewModel(new Reactor(), deck, hold, "Second Wind");
        idle.selectBoatAction(0).run();
        assertNull(card(idle, part).recycle(), "an idle machine shop offers no RECYCLE");
    }

    private static FittingCard card(BoatDeckViewModel model, String part) {
        return model.catalogRows().get().stream().filter(row -> part.equals(row.itemId())).findFirst().orElseThrow().card();
    }

    private static FabricationCost cost(Object... pairs) {
        var lines = new ArrayList<FabricationCost.Line>();
        for (int i = 0; i < pairs.length; i += 2) lines.add(new FabricationCost.Line((String) pairs[i], (Integer) pairs[i + 1]));
        return new FabricationCost(lines);
    }

    /** An empty hold that takes whatever comes back, and a machine shop the test sets. */
    private static final class Hold implements FabricationResources {
        final Map<String, Integer> stock = new HashMap<>();
        Reclamation reclamation;
        boolean accepts = true;
        boolean tooled;

        Hold(Reclamation reclamation) { this.reclamation = reclamation; }

        @Override public int available(String id) { return stock.getOrDefault(id, 0); }
        @Override public String commodityName(String id) { return id; }
        @Override public String commodityIcon(String id) { return ""; }
        @Override public boolean spend(FabricationCost cost) { return false; }
        @Override public FabricationCost quote(FabricationCost base) { return WorkshopTooling.quote(base, tooled); }
        @Override public Reclamation reclamation() { return reclamation; }
        @Override public boolean credit(FabricationCost salvage) {
            if (!accepts) return false;
            salvage.lines().forEach(line -> stock.merge(line.commodityId(), line.quantity(), Integer::sum));
            return true;
        }
    }
}
