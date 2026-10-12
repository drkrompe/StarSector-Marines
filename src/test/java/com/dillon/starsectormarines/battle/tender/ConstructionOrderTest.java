package com.dillon.starsectormarines.battle.tender;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionOrderTest {

    private static final float DT = 1f / 30f;

    private static ConstructionOrder working() {
        ConstructionOrder order = new ConstructionOrder(BuildOption.VULCAN_TURRET, 10.5f, 10.5f);
        order.begin();
        order.startWork();
        return order;
    }

    @Test
    void rateIsLinearInBuildersAndCappedAtAFullCrew() {
        assertEquals(0f, ConstructionOrder.rate(0, 3), 1e-6f);
        assertEquals(1f / 3f, ConstructionOrder.rate(1, 3), 1e-6f);
        assertEquals(1f, ConstructionOrder.rate(3, 3), 1e-6f);
        assertEquals(1f, ConstructionOrder.rate(5, 3), 1e-6f);
        assertEquals(0f, ConstructionOrder.rate(2, 0), 1e-6f);
    }

    @Test
    void aFullCrewFinishesInTheAuthoredTime() {
        ConstructionOrder order = working();
        int ticks = 0;
        while (!order.work(DT, 3, 3, false)) ticks++;
        assertEquals(BuildOption.VULCAN_TURRET.buildSeconds(), ticks * DT, 2 * DT);
        assertEquals(ConstructionOrder.Phase.COMPLETE, order.phase());
        assertEquals(1f, order.fraction(), 1e-6f);
    }

    @Test
    void aThirdOfACrewTakesThreeTimesAsLong() {
        ConstructionOrder full = working();
        ConstructionOrder lone = working();
        int fullTicks = 0;
        int loneTicks = 0;
        while (!full.work(DT, 3, 3, false)) fullTicks++;
        while (!lone.work(DT, 1, 3, false)) loneTicks++;
        assertEquals(3f, (float) loneTicks / fullTicks, 0.05f);
    }

    @Test
    void aContestedSiteOrAnEmptyCrewMakesNoProgress() {
        ConstructionOrder order = working();
        for (int i = 0; i < 300; i++) assertFalse(order.work(DT, 3, 3, true));
        assertEquals(0f, order.fraction(), 1e-6f);
        assertTrue(order.contested());
        for (int i = 0; i < 300; i++) assertFalse(order.work(DT, 0, 3, false));
        assertEquals(0f, order.fraction(), 1e-6f);
        assertFalse(order.contested());
    }

    @Test
    void workHappensOnlyWhileWorking() {
        ConstructionOrder order = new ConstructionOrder(BuildOption.VULCAN_TURRET, 1.5f, 1.5f);
        assertFalse(order.work(100f, 3, 3, false));
        order.begin();
        assertEquals(ConstructionOrder.Phase.TRANSIT, order.phase());
        assertFalse(order.work(100f, 3, 3, false));
        assertEquals(0f, order.fraction(), 1e-6f);
    }

    @Test
    void aTerminalOrderStaysTerminal() {
        ConstructionOrder order = working();
        order.cancel();
        assertTrue(order.phase().terminal());
        order.fail("too late");
        assertEquals(ConstructionOrder.Phase.CANCELLED, order.phase());
        assertEquals("", order.failure());
        assertFalse(order.work(100f, 3, 3, false));
    }

    @Test
    void aFailureSaysWhy() {
        ConstructionOrder order = working();
        order.fail("no builders left");
        assertEquals(ConstructionOrder.Phase.FAILED, order.phase());
        assertEquals("no builders left", order.failure());
    }
}
