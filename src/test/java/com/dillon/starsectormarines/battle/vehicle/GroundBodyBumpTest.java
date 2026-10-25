package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When a stopped move is a bump the impact sound hears. */
class GroundBodyBumpTest {

    @Test
    void aStoppedMoveBumpsOnlyAtSpeedAndRemembersHowHard() {
        GroundBody body = VehicleType.BREAKTHROUGH_TANK.createBody();
        body.recordBlocked(GroundBody.BUMP_MIN_SPEED * .5f);
        assertEquals(0, body.bumps, "leaning on a wall from a standstill is not a bump");
        body.recordBlocked(-2f);
        assertEquals(1, body.bumps, "reversing into something is");
        assertEquals(2f, body.lastBumpSpeed);
        body.recordBlocked(Float.NaN);
        assertEquals(1, body.bumps);
    }
}
