package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirCrashMotionTest {
    @Test void fallingCarriesTheActualVelocityRatherThanTheNoseDirection() {
        var body = new AirBody();
        body.vx = 6f; body.vy = -8f; body.facingDegrees = 90f;
        AirCrashMotion.fall(body, AirCrashMotion.FALL_SECONDS);
        assertEquals(9.6f, body.x, .001f);
        assertEquals(-12.8f, body.y, .001f);
        assertEquals(10f, body.speed(), .001f);
        assertEquals(0f, AirCrashMotion.altitude(AirCrashMotion.FALL_SECONDS, 1f));
    }

    @Test void impactFrictionStopsWithoutReversingAndDoesNotDependOnStepSize() {
        var whole = new AirBody(); whole.vx = 8f;
        var stepped = new AirBody(); stepped.vx = 8f;
        assertTrue(AirCrashMotion.skid(whole, 2f));
        for (int i = 0; i < 60; i++) AirCrashMotion.skid(stepped, 1f / 30f);
        assertEquals(4f, whole.x, .001f);
        assertEquals(whole.x, stepped.x, .001f);
        assertEquals(0f, whole.speed());
        assertEquals(0f, stepped.speed());
    }

    @Test void aStationaryCraftDoesNotAcquireInventedForwardMomentum() {
        var body = new AirBody();
        AirCrashMotion.fall(body, AirCrashMotion.FALL_SECONDS);
        assertTrue(AirCrashMotion.skid(body, 1f));
        assertEquals(0f, body.x);
        assertEquals(0f, body.y);
    }
}
