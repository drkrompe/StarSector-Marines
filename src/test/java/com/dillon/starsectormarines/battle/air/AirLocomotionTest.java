package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every phase says how the aircraft is being moved, and says exactly one thing.
 *
 * <p>The point of the derivation is that there is nothing to keep in step: a
 * phase added later cannot quietly acquire no locomotion, or two. So the test
 * is over the whole enum rather than over a case.
 */
class AirLocomotionTest {

    /**
     * A phase with no mode is a phase nothing knows how to move, and the
     * derivation is what several cross-cutting questions are now asked of — so
     * one that falls through is not a missing branch, it is an aircraft nobody
     * can shoot at.
     */
    @Test
    void everyPhaseNamesOneWayOfMoving() {
        for (ShuttleState state : ShuttleState.values()) {
            assertNotNull(AirLocomotion.of(state), state + " has no locomotion");
        }
    }

    /**
     * Being in the air is exactly the two flying modes.
     *
     * <p>Pinned against the list that used to be written out by hand, because
     * that list is what the derivation replaced and the replacement is only
     * worth anything if it says the same thing. The two additions are
     * deliberate: {@link ShuttleState#PAD_DESCENT} is a craft hovering over its
     * landing zone, which is in the air by any reading.
     */
    @Test
    void airborneIsTheFlyingPhasesAndNothingElse() {
        EnumSet<ShuttleState> airborne = EnumSet.noneOf(ShuttleState.class);
        for (ShuttleState state : ShuttleState.values()) {
            if (AirLocomotion.of(state).airborne()) airborne.add(state);
        }
        assertEquals(EnumSet.of(ShuttleState.INCOMING, ShuttleState.PAD_DESCENT,
                        ShuttleState.DEPARTING,
                        ShuttleState.RETURNING, ShuttleState.ATTACK_RUN,
                        ShuttleState.REPOSITION),
                airborne);
    }

    /**
     * The ground phases are the ones a wheeled model drives, plus the two that
     * are down and stationary.
     */
    @Test
    void theGroundPhasesAreTheOnesOnWheels() {
        EnumSet<ShuttleState> grounded = EnumSet.noneOf(ShuttleState.class);
        for (ShuttleState state : ShuttleState.values()) {
            if (AirLocomotion.of(state) == AirLocomotion.GROUNDED) grounded.add(state);
        }
        assertEquals(EnumSet.of(ShuttleState.LOADING, ShuttleState.TAXI_OUT,
                        ShuttleState.HOLDING_SHORT, ShuttleState.TAKEOFF_ROLL,
                        ShuttleState.LANDED, ShuttleState.LANDING_ROLL,
                        ShuttleState.TAXI_IN),
                grounded);
    }
}
