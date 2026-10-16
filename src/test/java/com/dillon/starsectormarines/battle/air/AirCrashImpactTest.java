package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirCrashImpactTest {
    @Test void crossingCapturesActualContactBeforeSkidAndHandsSameSnapshotToWreck() {
        var body = new AirBody();
        body.x = 10; body.y = 20; body.vx = 8; body.vy = -6;
        body.facingDegrees = 35; body.angVelDegPerSec = 20;
        var mission = new ShuttleMission(0, 0, 0, 0, 0, 0, 0, 0);
        float before = AirCrashMotion.FALL_SECONDS - .01f, dt = .04f, now = 12f;
        mission.crashSeconds = before;
        float airborne = AirCrashMotion.FALL_SECONDS - before;
        AirCrashMotion.fall(body, airborne);
        mission.crashSeconds += dt;
        mission.crashImpact = AirCrashImpact.captureContact(null, body, 42,
                before, mission.crashSeconds, now - dt + airborne);
        var impact = mission.crashImpact;
        assertNotNull(impact);
        assertEquals(11.97f, impact.atSeconds(), 1e-5);
        assertEquals(body.x, impact.x()); assertEquals(body.y, impact.y());
        assertEquals(8, impact.vx()); assertEquals(-6, impact.vy());
        assertEquals(body.facingDegrees, impact.facingDegrees());
        assertEquals(body.angVelDegPerSec, impact.angularVelocityDegrees());
        AirCrashMotion.skid(body, 2);
        assertNotEquals(body.x, impact.x());
        assertEquals(8, impact.vx(), "friction must not mutate the captured velocity");
        assertSame(impact, AirCrashImpact.captureContact(impact, body, 42, before, 5, 99));
        var wreck = new GroundWreck(body.x, body.y, body.facingDegrees, null, mission.crashImpact);
        assertSame(impact, wreck.crashImpact);
        assertEquals(.03f, wreck.crashImpact.age(now), 1e-5);
        assertEquals(0, impact.age(0));
        assertNull(new GroundWreck(0, 0, 0, null).crashImpact);
    }

    @Test void noContactBeforeTouchdownAndSeedIsStableWithoutRandomState() {
        var body = new AirBody(); body.x = 2; body.y = 3;
        assertNull(AirCrashImpact.captureContact(null, body, 42, 0, 1, 10));
        var first = AirCrashImpact.captureContact(null, body, 42, 1.5f, 1.7f, 10);
        var repeated = AirCrashImpact.captureContact(null, body, 42, 1.5f, 1.7f, 10);
        assertEquals(first, repeated);
        var otherHull = AirCrashImpact.captureContact(null, body, 43, 1.5f, 1.7f, 10);
        assertNotEquals(first.seed(), otherHull.seed());
        body.x += 1;
        var otherPoint = AirCrashImpact.captureContact(null, body, 42, 1.5f, 1.7f, 10);
        assertNotEquals(first.seed(), otherPoint.seed());
    }
}
