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

    @Test
    void truckIsSetDownFacingAlongItsWaysFirstLegNotTheDestination() {
        // At the east edge of a street running north, the redoubt far to the east: the crow's heading faces a wall.
        float[][] way = {{239.5f, 240.3f, 240.5f, 244f, 380f}, {229.5f, 226.3f, 225.5f, 221.5f, 177f}};
        float facing = SupplyDelivery.facing(way, 239.5f, 229.5f, 464.5f, 174.5f);
        // Forward is (-sin, cos): the first point at least FACING_LEAD off is (240.5, 225.5), north and a little east.
        double rad = Math.toRadians(facing);
        assertEquals(1f / (float) Math.hypot(1, 4), (float) -Math.sin(rad), 1e-4f);
        assertEquals(-4f / (float) Math.hypot(1, 4), (float) Math.cos(rad), 1e-4f);
    }

    @Test
    void truckWithNoWayOrAShortOneFacesItsEnd() {
        assertEquals(-90f, SupplyDelivery.facing(null, 10f, 10f, 20f, 10f), 1e-4f);
        // Every point nearer than the lead: the way's end.
        assertEquals(0f, SupplyDelivery.facing(new float[][]{{10f, 10f}, {10f, 12f}}, 10f, 10f, 30f, 10f), 1e-4f);
    }
}
