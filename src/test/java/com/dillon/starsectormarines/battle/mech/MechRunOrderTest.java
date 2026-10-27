package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mech's run is a pace on its order: toggled while one stands, and gone with it or a fresh one. */
class MechRunOrderTest {
    private static final long MECH = 42L;
    private final MechMoveOrderService orders = new MechMoveOrderService();
    private final ActiveOrder order = new ActiveOrder(5, 5, 5, 5);

    @Test
    void aMechWithNoOrderCannotRun() {
        assertFalse(orders.toggleRun(MECH));
        assertFalse(orders.isRunning(MECH));
    }

    @Test
    void togglesBetweenRunAndWalkWhileTheOrderStands() {
        orders.activate(MECH, order);
        assertTrue(orders.toggleRun(MECH));
        assertTrue(orders.isRunning(MECH));
        assertFalse(orders.toggleRun(MECH));
        assertFalse(orders.isRunning(MECH));
    }

    @Test
    void aFreshOrderStartsAtAWalk() {
        orders.activate(MECH, order);
        orders.toggleRun(MECH);
        orders.requestMove(MECH, 9, 9);
        assertFalse(orders.isRunning(MECH));
    }

    @Test
    void theRunEndsWithItsOrder() {
        orders.activate(MECH, order);
        orders.toggleRun(MECH);
        orders.release(MECH, order);
        orders.activate(MECH, new ActiveOrder(7, 7, 7, 7));
        assertFalse(orders.isRunning(MECH));
    }

    @Test
    void aReResolvedOrderKeepsItsPace() {
        orders.activate(MECH, order);
        orders.toggleRun(MECH);
        orders.activate(MECH, new ActiveOrder(5, 5, 6, 6));
        assertTrue(orders.isRunning(MECH));
    }
}
