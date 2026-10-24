package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplyDeliveryTest {

    @Test
    void arrivalDeliversOnlyWithTruckAndRecipientStillThere() {
        assertEquals(State.DELIVERED, SupplyDelivery.rule(State.ON_ROAD, true, true, true));
        assertEquals(State.LOST, SupplyDelivery.rule(State.ON_ROAD, false, true, true));
        assertEquals(State.LOST, SupplyDelivery.rule(State.ON_ROAD, true, false, false));
        assertEquals(State.ON_ROAD, SupplyDelivery.rule(State.ON_ROAD, true, true, false));
    }

    @Test
    void waitingAndFinishedDeliveriesDoNotMove() {
        assertEquals(State.WAITING, SupplyDelivery.rule(State.WAITING, false, false, false));
        assertEquals(State.DELIVERED, SupplyDelivery.rule(State.DELIVERED, false, false, false));
        assertEquals(State.LOST, SupplyDelivery.rule(State.LOST, true, true, true));
    }
}
