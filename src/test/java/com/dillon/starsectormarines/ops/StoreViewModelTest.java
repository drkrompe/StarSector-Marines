package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.BoatParts;
import com.dillon.starsectormarines.marine.EquipmentStore;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.LoadoutCards;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MechBay;
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
        var first = LoadoutCards.all().get(0).id();
        var second = LoadoutCards.all().get(1).id();
        List<EquipmentStore.Offer> shelf = List.of(offer(first), offer(second));
        StoreViewModel model = new StoreViewModel(new Reactor(), () -> shelf, () -> new EquipmentStore.Holdings(armory, new MechBay(), null), purse(credits), () -> "Jangala");

        assertEquals(2, model.rows().get().size());
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

        assertEquals(3, model.rows().get().size(), "everything is shown to begin with");
        assertTrue(model.categoryRows().get().get(0).label().endsWith(" 3"));

        model.selectFilter("SECTION:MARINE");
        assertEquals(2, model.rows().get().size());

        model.selectFilter("SLOT:ARMOR_LOADOUT");
        assertEquals(1, model.rows().get().size());
        assertTrue(model.rows().get().get(0).classes().contains("selected"),
                "the selection moves to what is still shown");

        model.selectFilter("SECTION:MECHS");
        assertTrue(model.rows().get().isEmpty());
        assertEquals(EquipmentStore.Sale.UNAVAILABLE, model.buySelected(), "nothing is selected in an empty category");

        model.selectFilter("SECTION:BOATS");
        assertEquals(1, model.rows().get().size());
        assertEquals(EquipmentStore.Sale.SOLD, model.buySelected());
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
