package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipThrustTest {

    @Test
    void aStoppedShipIdlesAtTheHoverGlow() {
        assertEquals(WarshipThrust.IDLE_LEVEL, WarshipThrust.level(0f, 0f), 1e-6f);
    }

    @Test
    void accelerationBurnsHarderThanCruisingAndBrakingDimsBelowIdle() {
        float cruise = WarshipThrust.level(1f, 0f);
        float accelerating = WarshipThrust.level(0.5f, 1f);
        float braking = WarshipThrust.level(0.5f, -1f);

        assertEquals(1f, accelerating, 1e-6f);
        assertEquals(WarshipThrust.CRUISE_LEVEL, cruise, 1e-6f);
        assertTrue(cruise > WarshipThrust.IDLE_LEVEL);
        assertEquals(WarshipThrust.BRAKE_LEVEL, braking, 1e-6f);
        assertTrue(braking < WarshipThrust.IDLE_LEVEL);
    }

    @Test
    void levelRisesContinuouslyWithSpeedAndThrust() {
        float previous = -1f;
        for (int i = 0; i <= 20; i++) {
            float v = WarshipThrust.level(i / 20f, 0f);
            assertTrue(v >= previous);
            previous = v;
        }
        previous = -1f;
        for (int i = -10; i <= 10; i++) {
            float v = WarshipThrust.level(0.6f, i / 10f);
            assertTrue(v >= previous);
            previous = v;
        }
    }

    @Test
    void inputsOutsideTheirRangesAreClamped() {
        assertEquals(WarshipThrust.level(1f, 1f), WarshipThrust.level(5f, 9f), 1e-6f);
        assertEquals(WarshipThrust.level(0f, -1f), WarshipThrust.level(-3f, -9f), 1e-6f);
    }

    @Test
    void thrustFractionIsTheSpeedGapAgainstHalfASecondOfAcceleration() {
        // Accelerating at 0.4 cells/s^2: half a second is 0.2 cells/s of gap.
        assertEquals(1f, WarshipThrust.thrustFraction(0f, 1f, 0.4f, false), 1e-6f);
        assertEquals(0.5f, WarshipThrust.thrustFraction(0.9f, 1f, 0.4f, false), 1e-5f);
        assertEquals(0f, WarshipThrust.thrustFraction(1f, 1f, 0.4f, false), 1e-6f);
        assertEquals(-1f, WarshipThrust.thrustFraction(1f, 0f, 0.4f, false), 1e-6f);
    }

    @Test
    void followingTheBrakingCurveIsBrakingEvenWhenTheGapIsNil() {
        assertEquals(-1f, WarshipThrust.thrustFraction(0.7f, 0.7f, 0.4f, true), 1e-6f);
    }

    @Test
    void anAftNozzleWorksHardestWhenThePushIsAheadAndLeastWhenItIsBehind() {
        // Hull facing 0 points along +y; an aft nozzle's flame heading is 180.
        float ahead = WarshipThrust.slotShare(0f, 1f, 180f);
        float across = WarshipThrust.slotShare(1f, 0f, 180f);
        float behind = WarshipThrust.slotShare(0f, -1f, 180f);

        assertEquals(1f, ahead, 1e-5f);
        assertEquals(WarshipThrust.OFF_AXIS_SHARE, across, 1e-5f);
        assertEquals(WarshipThrust.OFF_AXIS_SHARE, behind, 1e-5f);
    }

    @Test
    void theHeadingIsInTheWorldFrameSoATurnedHullKeepsItsAftNozzlesOnTheRightPush() {
        // Hull turned a quarter turn counter-clockwise points along -x (the hull
        // convention is (-sin, cos)), so its aft flame heads 270 and pushes toward -x.
        assertEquals(1f, WarshipThrust.slotShare(-1f, 0f, 90f + 180f), 1e-5f);
        assertEquals(WarshipThrust.OFF_AXIS_SHARE, WarshipThrust.slotShare(0f, 1f, 90f + 180f), 1e-5f);
    }

    @Test
    void withNoPushDirectionEveryNozzleBurnsAlike() {
        assertEquals(1f, WarshipThrust.slotShare(0f, 0f, 37f), 1e-6f);
    }
}
