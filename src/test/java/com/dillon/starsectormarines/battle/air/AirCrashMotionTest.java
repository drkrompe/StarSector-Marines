package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirCrashMotionTest {
    @Test void fallingCarriesTheActualVelocityRatherThanTheNoseDirection() {
        var body = new AirBody();
        body.vx = 6f; body.vy = -8f; body.facingDegrees = 90f;
        AirCrashMotion.beginFall(body, .3f);
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

    @Test void hoverLossAcceleratesIntoASkidRatherThanReceivingAnInstantKick() {
        var body = new AirBody();
        AirCrashMotion.beginFall(body, .5f);
        assertEquals(0f, body.speed());
        AirCrashMotion.fall(body, .8f);
        assertEquals(4f, body.speed(), .001f);
        assertEquals(1.6f, body.x, .001f);
        AirCrashMotion.fall(body, .8f);
        assertEquals(8f, body.speed(), .001f);
        assertEquals(6.4f, body.x, .001f);
        assertTrue(AirCrashMotion.skid(body, 1f));
        assertEquals(10.4f, body.x, .001f);
        assertEquals(0f, body.y);
    }

    @Test void aSlowMovingCraftTipsAlongItsExistingMotion() {
        var body = new AirBody(); body.vy = -2f;
        AirCrashMotion.beginFall(body, .1f);
        AirCrashMotion.fall(body, AirCrashMotion.FALL_SECONDS);
        assertEquals(0f, body.vx, .001f);
        assertEquals(-8f, body.vy, .001f);
    }
}
