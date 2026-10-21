package com.dillon.starsectormarines.campaign.refit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipRefitsRefusalTest {
    private static final ShipCondition START = ShipCondition.battered();

    @Test void anEligibleOrderIsRefusedForEachMissingPrecondition() {
        var order = RefitOrder.STRUCTURE_RESTORED;
        assertNull(ShipRefits.refusal(order, START, true, true, true, true, order.price));
        assertNotNull(ShipRefits.refusal(order, START, false, true, true, true, order.price));
        assertNotNull(ShipRefits.refusal(order, START, true, false, true, true, order.price));
        assertNotNull(ShipRefits.refusal(order, START, true, true, false, true, order.price));
        assertNotNull(ShipRefits.refusal(order, START, true, true, true, false, order.price));
        assertNotNull(ShipRefits.refusal(order, START, true, true, true, true, order.price - 1));
        assertNotNull(ShipRefits.refusal(order, ShipCondition.NONE, true, true, true, true, order.price));
    }

    @Test void repeatsSkippedTiersAndLockedCoresAreRefused() {
        var done = START.with(RefitTrack.STRUCTURE, RefitBand.RESTORED);
        assertNotNull(ShipRefits.refusal(RefitOrder.STRUCTURE_RESTORED, done, true, true, true, true, 99_999));
        assertNotNull(ShipRefits.refusal(RefitOrder.MESS_GALLEY, START, true, true, true, true, 99_999));
        assertNotNull(ShipRefits.refusal(RefitOrder.LIFE_SUPPORT_RESTORED, START, true, true, true, true, 99_999));
        assertNull(ShipRefits.refusal(RefitOrder.LIFE_SUPPORT_RESTORED,
                START.with(RefitTrack.POWER, RefitBand.RESTORED), true, true, true, true, 99_999));
    }
}
