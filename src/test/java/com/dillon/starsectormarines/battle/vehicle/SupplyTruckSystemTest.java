package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a supply truck's crates can come off: halted, on the field, and whole. */
class SupplyTruckSystemTest {

    @Test
    void aHaltedTruckOnTheFieldIssues() {
        assertTrue(SupplyTruckSystem.issuing(VehicleState.DEPLOYED, 0f));
        assertTrue(SupplyTruckSystem.issuing(VehicleState.LANDED, 0f));
        assertTrue(SupplyTruckSystem.issuing(VehicleState.DEPLOYED, SupplyTruckSystem.PARKED_SPEED));
        assertTrue(SupplyTruckSystem.issuing(VehicleState.DEPLOYED, -SupplyTruckSystem.PARKED_SPEED),
                "reversing slowly is still halted");
    }

    @Test
    void aMovingTruckIssuesNothing() {
        assertFalse(SupplyTruckSystem.issuing(VehicleState.DEPLOYED, SupplyTruckSystem.PARKED_SPEED + .01f));
        assertFalse(SupplyTruckSystem.issuing(VehicleState.INCOMING, 2f));
        assertFalse(SupplyTruckSystem.issuing(VehicleState.DEPLOYED, -1f));
    }

    @Test
    void aWreckOrATruckNotYetOnTheFieldIssuesNothing() {
        assertFalse(SupplyTruckSystem.issuing(VehicleState.WRECKED, 0f));
        assertFalse(SupplyTruckSystem.issuing(VehicleState.GONE, 0f));
        assertFalse(SupplyTruckSystem.issuing(VehicleState.PENDING, 0f));
    }
}
