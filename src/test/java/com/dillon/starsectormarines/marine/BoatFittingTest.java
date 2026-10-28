package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoatFittingTest {

    @Test
    void aHoldLiftsWhatHerHullDoesOrMore() {
        assertEquals(LiftRating.LIGHT, BoatFitting.STANDARD_LIFT.liftOn(ShuttleType.HERMES),
                "a passenger hold keeps the Hermes' own light lift");
        assertEquals(LiftRating.HEAVY, BoatFitting.HEAVY_LIFT_FRAME.liftOn(ShuttleType.HERMES));
        assertEquals(LiftRating.NONE, BoatFitting.STANDARD_LIFT.liftOn(null));
    }
}
