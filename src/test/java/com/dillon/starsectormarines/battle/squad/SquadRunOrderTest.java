package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Run is a pace on a standing order: toggled while one stands, and gone with it or a fresh one. */
class SquadRunOrderTest {
    private static final int SQUAD = 3;
    private final SquadMoveOrderService orders = new SquadMoveOrderService();
    private final ActiveMoveOrder order = new ActiveMoveOrder(5, 5, 5, 5);

    @Test
    void aSquadWithNoOrderCannotRun() {
        assertFalse(orders.toggleRun(SQUAD));
        assertFalse(orders.isRunning(SQUAD));
    }

    @Test
    void togglesBetweenRunAndWalkWhileTheOrderStands() {
        orders.activate(SQUAD, order);
        assertTrue(orders.toggleRun(SQUAD));
        assertTrue(orders.isRunning(SQUAD));
        assertFalse(orders.toggleRun(SQUAD));
        assertFalse(orders.isRunning(SQUAD));
    }

    @Test
    void aFreshOrderStartsAtAWalk() {
        orders.activate(SQUAD, order);
        orders.toggleRun(SQUAD);
        orders.requestMove(SQUAD, 9, 9);
        assertFalse(orders.isRunning(SQUAD));
    }

    @Test
    void theRunEndsWithItsOrder() {
        orders.activate(SQUAD, order);
        orders.toggleRun(SQUAD);
        orders.release(SQUAD, order);
        orders.activate(SQUAD, new ActiveMoveOrder(7, 7, 7, 7));
        assertFalse(orders.isRunning(SQUAD));
    }

    @Test
    void aRefreshedOrderKeepsItsPace() {
        orders.activate(SQUAD, order);
        orders.toggleRun(SQUAD);
        orders.activate(SQUAD, new ActiveMoveOrder(5, 5, 6, 6));
        assertTrue(orders.isRunning(SQUAD));
    }
}
