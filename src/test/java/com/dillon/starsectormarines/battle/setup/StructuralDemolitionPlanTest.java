package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StructuralDemolitionPlanTest {
    @Test void sweepUsesActualDamageAndHoldsItsHeadingAcrossDisjointSectors() {
        var plan = new StructuralDemolitionPlan(new WarshipOrder.FireOnArea(5, 5, 10, true, true),
                List.of(new int[]{0,0}, new int[]{9,0}, new int[]{0,9}, new int[]{9,9}));
        var east = plan.advance((x,y) -> true);
        assertTrue(east.contains(9.5f, .5f));
        assertFalse(east.contains(.5f, .5f));
        assertFalse(east.contains(9.5f, 9.5f));
        assertFalse(east.bringGunsToBear());
        assertSame(east, plan.advance((x,y) -> true), "Elapsed time alone cannot advance demolition");
        var southEast = plan.advance((x,y) -> y > 0 || x == 0);
        assertTrue(southEast.contains(9.5f, 9.5f));
        assertEquals(2, plan.sectorNumber());
        var west = plan.advance((x,y) -> x == 0);
        assertTrue(west.contains(.5f, .5f));
        assertEquals(3, plan.sectorNumber());
        assertNull(plan.advance((x,y) -> false));
    }
}
