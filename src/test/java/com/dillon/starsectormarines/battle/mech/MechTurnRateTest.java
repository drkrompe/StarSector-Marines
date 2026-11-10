package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechTurnRateTest {

    @Test
    void aLeggedMachineTurnsMoreSlowlyTheLargerItIsAndOtherMachinesKeepTheStockRate() {
        float rounds = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_ROUNDS);
        float walker = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_WALKER);
        float hall = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_HALL);
        assertTrue(rounds > walker && walker > hall, rounds + " > " + walker + " > " + hall);
        assertTrue(MechLocomotion.turnRateDegrees(MechVariant.CRAWLER) <= MechLocomotion.DEFAULT_TURN_RATE_DEGREES);
        assertEquals(MechLocomotion.DEFAULT_TURN_RATE_DEGREES, MechLocomotion.turnRateDegrees(MechVariant.BULWARK));
    }
}
