package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Kind;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerelictSecurityDirectorTest {

    @Test
    void aWalkerOffTheCellItIsToHoldWalksThereFirst() {
        Order egress = new Order(Kind.HOLD_EGRESS, 70, 28, -1L);
        assertTrue(DerelictSecurityDirector.walksTo(20.5f, 28.5f, egress));
        assertFalse(DerelictSecurityDirector.walksTo(66.5f, 28.5f, egress), "within the egress radius it holds");
        Order post = new Order(Kind.HOLD, 20, 28, -1L);
        assertTrue(DerelictSecurityDirector.walksTo(30.5f, 28.5f, post), "back to its post after a pin");
        assertFalse(DerelictSecurityDirector.walksTo(21.5f, 29.5f, post));
        assertFalse(DerelictSecurityDirector.walksTo(90.5f, 28.5f, new Order(Kind.CLOSE, 20, 28, 7L)));
    }
}
