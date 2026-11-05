package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Buying and recycling a part is one rule in every room: each check in order, and a refusal moves nothing. */
class PartShopTest {

    private static final FabricationCost PRICE = FabricationCost.of(400);

    @Test
    void aBuyChecksPriceThenPatternThenPortThenPurse() {
        PartStores stores = new PartStores();
        TestPurse away = new TestPurse(0).away();
        assertEquals(PartShop.Outcome.NOT_FABRICABLE, PartShop.build(away, stores, "p", null, false));
        assertEquals(PartShop.Outcome.NOT_LEARNED, PartShop.build(away, stores, "p", PRICE, false));
        assertEquals(PartShop.Outcome.NO_MARKET, PartShop.build(away, stores, "p", PRICE, true));
        assertEquals(PartShop.Outcome.CANNOT_AFFORD, PartShop.build(new TestPurse(0), stores, "p", PRICE, true));
        assertEquals(0, stores.count("p"), "no refusal stows anything");

        TestPurse purse = new TestPurse(1000);
        assertEquals(PartShop.Outcome.BUILT, PartShop.build(purse, stores, "p", PRICE, true));
        assertEquals(1, stores.count("p"));
        assertEquals(600, purse.credits);
    }

    @Test
    void aRecycleNeedsASpareAWorkingShopAndAPurseBeforeTheSpareLeaves() {
        PartStores stores = new PartStores();
        TestPurse idle = TestPurse.rich();
        assertEquals(PartShop.Outcome.NO_SPARE, PartShop.recycle(idle, stores, "p", PRICE));
        stores.stow("p");
        assertEquals(PartShop.Outcome.WORKSHOP_IDLE, PartShop.recycle(idle, stores, "p", PRICE));
        TestPurse working = TestPurse.rich().reclaiming(Reclamation.FULL);
        assertEquals(PartShop.Outcome.NOTHING_TO_RECOVER, PartShop.recycle(working, stores, "p", null));
        working.accepts = false;
        assertEquals(PartShop.Outcome.NO_HOLD, PartShop.recycle(working, stores, "p", PRICE));
        assertEquals(1, stores.count("p"), "a refused recycle keeps its spare");

        working.accepts = true;
        assertEquals(PartShop.Outcome.RECYCLED, PartShop.recycle(working, stores, "p", PRICE));
        assertEquals(0, stores.count("p"));
    }
}
