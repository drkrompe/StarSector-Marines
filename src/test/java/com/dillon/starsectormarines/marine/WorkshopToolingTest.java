package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.BoatDeckViewModel;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopToolingTest {
    @Test void toolingIsCheaperRoundsUpAndLeavesTheBaseBillAlone() {
        var base = new FabricationCost(1001);
        assertSame(base, WorkshopTooling.quote(base, false));
        var quoted = WorkshopTooling.quote(base, true);
        assertTrue(quoted.credits() < base.credits());
        assertEquals(851, quoted.credits());
        assertEquals(1001, base.credits());
        assertEquals(1, WorkshopTooling.quote(new FabricationCost(1), true).credits());
    }

    @Test void displayedBoatBillAndActualDebitAgreeAtTheDiscountedAffordabilityBoundary() {
        var row0 = new Object() { FabricationCost priced; };
        boolean[] equipped = {true};
        var purse = TestPurse.rich();
        purse.quote = base -> WorkshopTooling.quote(base, equipped[0]);
        var deck = new BoatDeck(); deck.reconcile("ship", ShuttleType.AEROSHUTTLE, 1);
        var model = new BoatDeckViewModel(new Reactor(), deck, purse, "Second Wind");
        model.selectBoatAction(0).run();
        var row = model.catalogRows().get().stream().filter(r -> r.id().endsWith("reinforced_plating")).findFirst().orElseThrow();
        long before = purse.credits;
        equipped[0] = false;
        // The preview was drawn with tooling; the debit is the live quote, never less.
        row.card().action().run();
        long undiscounted = before - purse.credits;
        assertEquals(1, deck.spares(BoatFitting.REINFORCED_PLATING.id()));
        purse.credits = before;
        equipped[0] = true;
        row.card().action().run();
        long discounted = before - purse.credits;
        assertTrue(discounted < undiscounted);
        assertNull(row0.priced);
    }
}
