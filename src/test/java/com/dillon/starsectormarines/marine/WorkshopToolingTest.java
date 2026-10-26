package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.ops.battleview.BoatDoll;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.ops.BoatDeckViewModel;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopToolingTest {
    @Test void suppliesAndMachineryRoundUpAndOtherMaterialsAndBaseRecipeStayUntouched() {
        var base = new FabricationCost(List.of(new FabricationCost.Line("supplies", 11),
                new FabricationCost.Line("metals", 17), new FabricationCost.Line("heavy_machinery", 11)));
        assertSame(base, WorkshopTooling.quote(base, false));
        var quoted = WorkshopTooling.quote(base, true);
        assertEquals(List.of(9, 17, 9), quoted.lines().stream().map(FabricationCost.Line::quantity).toList());
        assertEquals(11, base.lines().get(0).quantity());
        assertEquals(1, WorkshopTooling.quote(new FabricationCost(List.of(new FabricationCost.Line("supplies", 1))), true).lines().get(0).quantity());
    }
    @Test void displayedBoatBillAndActualDebitAgreeAtTheDiscountedAffordabilityBoundary() {
        var stock = new HashMap<>(Map.of("supplies", 8, "metals", 20));
        boolean[] equipped = {true};
        var resources = new FabricationResources() {
            public int available(String id) { return stock.getOrDefault(id, 0); }
            public String commodityName(String id) { return id; }
            public String commodityIcon(String id) { return ""; }
            public FabricationCost quote(FabricationCost base) { return WorkshopTooling.quote(base, equipped[0]); }
            public boolean spend(FabricationCost bill) {
                if (!canAfford(bill)) return false;
                bill.lines().forEach(l -> stock.compute(l.commodityId(), (k, n) -> n - l.quantity()));
                return true;
            }
        };
        var deck = new BoatDeck(); deck.reconcile("ship", ShuttleType.AEROSHUTTLE, 1);
        var model = new BoatDeckViewModel(new Reactor(), deck, resources, "Second Wind");
        model.selectBoatAction(0).run();
        var row = model.catalogRows().get().stream().filter(r -> r.id().endsWith("reinforced_plating")).findFirst().orElseThrow();
        assertTrue(row.materials().stream().anyMatch(r -> r.id().endsWith("supplies") && r.required() == 8 && r.shortfall() == 0));
        equipped[0] = false;
        // A stale favorable preview cannot underpay after moving hull.
        row.card().action().run();
        assertEquals(8, stock.get("supplies"));
        assertEquals(0, deck.spares(BoatFitting.REINFORCED_PLATING.id()));
        equipped[0] = true;
        row.card().action().run();
        assertEquals(0, stock.get("supplies"));
        assertEquals(0, stock.get("metals"));
        assertEquals(1, deck.spares(BoatFitting.REINFORCED_PLATING.id()));
        assertTrue(model.canDropOnSocket(row.kind(), row.itemId(), BoatDoll.slotKey(BoatFittingSlot.PLATING)));
        model.dropOnSocket(row.kind(), row.itemId(), BoatDoll.slotKey(BoatFittingSlot.PLATING));
        assertSame(BoatFitting.REINFORCED_PLATING, deck.boats().get(0).plating());
    }
}
