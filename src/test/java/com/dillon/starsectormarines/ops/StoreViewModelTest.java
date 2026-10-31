package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatParts;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.StoreSlot;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MechBay;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreViewModelTest {

    @Test
    void aPurchaseStaysOnTheShelfMarkedAcquiredAndIsLearned() {
        MarineArmory armory = new MarineArmory();
        long[] credits = {20_000};
        List<EquipmentStore.Offer> shelf = List.of(offer(LoadoutCards.all().get(0).id()),
                offer(LoadoutCards.all().get(1).id()));
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> shelf, () -> new EquipmentStore.Holdings(armory, new MechBay(), null), purse(credits), () -> "Jangala");

        assertEquals(2, model.rows().get().size());
        String first = model.rows().get().get(0).offer().id();
        String second = model.rows().get().get(1).offer().id();
        assertFalse(model.rows().get().get(0).classes().contains("acquired"));

        assertEquals(EquipmentStore.Sale.SOLD, model.buySelected());

        assertTrue(armory.knowsLoadoutCard(first));
        assertEquals(13_500, credits[0]);
        assertEquals(2, model.rows().get().size(), "the shelf does not shift under the cursor");
        assertTrue(model.rows().get().get(0).classes().contains("acquired"));
        assertTrue(model.buyClasses().get().contains("current"), "an acquired item cannot be bought again");

        model.select("LOADOUT:" + second);
        assertFalse(model.rows().get().get(0).classes().contains("selected"));
        assertTrue(model.rows().get().get(1).classes().contains("selected"));
    }

    @Test
    void theCategoryListCountsTheShelfAndNarrowsItBySectionAndSlot() {
        String weapon = LoadoutCards.all().stream().filter(c -> c.kind() == LoadoutCard.Kind.WEAPON)
                .findFirst().orElseThrow().id();
        String sheet = LoadoutCards.all().stream().filter(c -> c.kind() == LoadoutCard.Kind.SHEET)
                .findFirst().orElseThrow().id();
        String boatPart = BoatParts.offerable().get(0);
        List<EquipmentStore.Offer> shelf = List.of(offer(weapon), offer(sheet),
                new EquipmentStore.Offer(EquipmentStore.Kind.BOAT_PART, boatPart,
                        EquipmentStore.Shelf.MARKET, 6500));
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> shelf,
                () -> new EquipmentStore.Holdings(new MarineArmory(), new MechBay(), new BoatDeck()),
                purse(new long[]{20_000}), () -> "Jangala");

        assertEquals(2, model.rows().get().size(), "the page opens on the first section with stock");
        assertEquals(StoreSlot.Section.values().length + StoreSlot.of(StoreSlot.Section.MARINE).size(),
                model.categoryRows().get().size(), "only the open section shows its slots");
        assertTrue(model.categoryRows().get().get(0).label().startsWith("- "), "the open section is marked open");
        assertTrue(model.categoryRows().get().get(0).label().endsWith(" 2"));

        model.selectFilter("SLOT:ARMOR_LOADOUT");
        assertEquals(1, model.rows().get().size());
        assertTrue(model.rows().get().get(0).classes().contains("selected"),
                "the selection moves to what is still shown");

        model.selectFilter("SECTION:MECHS");
        assertTrue(model.categoryRows().get().stream().noneMatch(row -> row.id().equals("store-cat-ARMOR_LOADOUT")),
                "opening one section collapses the others");
        assertTrue(model.rows().get().isEmpty());
        assertEquals(EquipmentStore.Sale.UNAVAILABLE, model.buySelected(), "nothing is selected in an empty category");

        model.selectFilter("SECTION:BOATS");
        assertEquals(1, model.rows().get().size());
        assertEquals(EquipmentStore.Sale.SOLD, model.buySelected());
    }

    @Test
    void theShelfReadsListedFirstThenBySlot() {
        String weapon = LoadoutCards.all().stream().filter(c -> c.kind() == LoadoutCard.Kind.WEAPON)
                .findFirst().orElseThrow().id();
        String sheet = LoadoutCards.all().stream().filter(c -> c.kind() == LoadoutCard.Kind.SHEET)
                .findFirst().orElseThrow().id();
        String listed = LoadoutCards.all().stream().filter(c -> c.kind() == LoadoutCard.Kind.SHEET)
                .skip(1).findFirst().orElseThrow().id();
        List<EquipmentStore.Offer> shelf = List.of(offer(sheet), offer(weapon),
                new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, listed, EquipmentStore.Shelf.LISTED, 6500));
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> shelf,
                () -> new EquipmentStore.Holdings(new MarineArmory(), new MechBay(), null), purse(new long[] {0}),
                () -> "Jangala");

        assertEquals(List.of(listed, weapon, sheet),
                model.rows().get().stream().map(row -> row.offer().id()).toList());
    }

    @Test
    void anUnaffordableSaleChargesNothingAndSaysSo() {
        MarineArmory armory = new MarineArmory();
        long[] credits = {100};
        var id = LoadoutCards.all().get(0).id();
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> List.of(offer(id)), () -> new EquipmentStore.Holdings(armory, new MechBay(), null), purse(credits), () -> "Jangala");

        assertTrue(model.buyClasses().get().contains("unaffordable"));
        assertEquals(EquipmentStore.Sale.CANNOT_AFFORD, model.buySelected());

        assertEquals(100, credits[0]);
        assertFalse(armory.knowsLoadoutCard(id));
        assertFalse(model.verdict().get().isEmpty());
    }

    @Test
    void theWaylinePathNotTakenIsShownAsItsCard() {
        String id = new LoadoutCard(LoadoutCard.Kind.WEAPON, FirstRefit.Path.STEADY_LINE.weapons).id();
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> List.of(offer(id)),
                () -> new EquipmentStore.Holdings(new MarineArmory(), new MechBay(), null), purse(new long[] {0}),
                () -> "Jangala");

        assertEquals(1, model.selectedCards().get().size());
        assertEquals("store-offer-flag", model.rows().get().get(0).flagClasses());
    }

    @Test
    void aMechWeaponReadsWhatItHitsForAndAShelfListsNoSlotNothingCanStock() {
        MechPattern weapon = MechPattern.offerable().stream()
                .filter(pattern -> pattern.kind() == MechPattern.Kind.WEAPON).findFirst().orElseThrow();
        StoreViewModel model = new StoreViewModel(new Reactor(),
                () -> List.of(new EquipmentStore.Offer(EquipmentStore.Kind.MECH_PATTERN, weapon.id(),
                        EquipmentStore.Shelf.MARKET, 6500)),
                () -> new EquipmentStore.Holdings(new MarineArmory(), new MechBay(), null), purse(new long[] {0}),
                () -> "Jangala");

        List<String> stats = model.specRows().get().stream().map(StoreViewModel.SpecRow::label).toList();
        assertTrue(stats.containsAll(List.of(Strings.get("gearStatDMG"), Strings.get("gearStatPEN"),
                Strings.get("gearStatRNG"))), stats.toString());
        assertFalse(model.specNotes().get().isEmpty(), "the weapon's own description is shown");

        model.selectFilter("SECTION:" + StoreSlot.Section.BOATS.name());
        boolean anyCabinPart = BoatParts.offerable().stream()
                .anyMatch(part -> new EquipmentStore.Offer(EquipmentStore.Kind.BOAT_PART, part,
                        EquipmentStore.Shelf.MARKET, 1).slot() == StoreSlot.BOAT_CABIN);
        assertEquals(anyCabinPart, model.categoryRows().get().stream()
                .anyMatch(row -> row.id().equals("store-cat-" + StoreSlot.BOAT_CABIN.name())));
    }

    private static EquipmentStore.Offer offer(String cardId) {
        return new EquipmentStore.Offer(EquipmentStore.Kind.LOADOUT, cardId, EquipmentStore.Shelf.MARKET, 6500);
    }

    private static FabricationResources purse(long[] credits) {
        return new FabricationResources() {
            @Override public long credits() { return credits[0]; }
            @Override public boolean atMarket() { return true; }
            @Override public boolean spend(FabricationCost cost) {
                if (credits[0] < cost.credits()) return false;
                credits[0] -= cost.credits();
                return true;
            }
        };
    }
}
