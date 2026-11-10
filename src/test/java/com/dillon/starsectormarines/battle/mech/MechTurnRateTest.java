package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechTurnRateTest {

    @Test
    void aDerelictWalkerTurnsItsFeetRoundNoFasterThanItWalks() {
        for (MechVariant v : new MechVariant[] {MechVariant.SECURITY_ROUNDS, MechVariant.SECURITY_WALKER,
                MechVariant.SECURITY_HALL}) {
            float feet = DerelictLegRig.REST_CELLS * v.renderScale / MechVariant.SECURITY_WALKER.renderScale;
            float footSpeed = (float) Math.toRadians(MechLocomotion.turnRateDegrees(v)) * feet;
            assertEquals(v.moveSpeed, footSpeed, 1e-3f, v + "'s feet go round at its walking speed");
        }
        float rounds = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_ROUNDS);
        float walker = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_WALKER);
        float hall = MechLocomotion.turnRateDegrees(MechVariant.SECURITY_HALL);
        assertTrue(rounds > walker && walker > hall, rounds + " > " + walker + " > " + hall);
    }

    @Test
    void theCrawlerAndOtherMachinesKeepTheirRates() {
        assertEquals(MechLocomotion.LEGGED_TURN_AT_SCALE_ONE_DEGREES / MechVariant.CRAWLER.renderScale,
                MechLocomotion.turnRateDegrees(MechVariant.CRAWLER), 1e-3f);
        assertEquals(MechLocomotion.DEFAULT_TURN_RATE_DEGREES, MechLocomotion.turnRateDegrees(MechVariant.BULWARK));
    }
}
