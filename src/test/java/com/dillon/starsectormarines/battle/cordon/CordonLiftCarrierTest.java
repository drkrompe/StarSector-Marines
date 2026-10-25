package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which free carrier the company asks for a lift ({@link CordonCommand#nearest}). */
class CordonLiftCarrierTest {

    @Test
    void theNearestCarrierThatSeatsTheWholeSquad() {
        CordonLogistics.Carrier near = new CordonLogistics.Carrier(1L, 10, 0, 4);
        CordonLogistics.Carrier far = new CordonLogistics.Carrier(2L, 40, 0, 8);
        assertEquals(near, CordonCommand.nearest(List.of(far, near), 0, 0, 4));
        assertEquals(far, CordonCommand.nearest(List.of(far, near), 0, 0, 6), "a six-man squad does not fit four seats");
        assertNull(CordonCommand.nearest(List.of(near), 0, 0, 6), "no carrier seats it: it walks");
    }
}
