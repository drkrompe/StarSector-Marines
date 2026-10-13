package com.dillon.starsectormarines.battle.air;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunshipIngressTest {
    @Test void waypointHandoffPreservesTheFlyingBody() {
        var route = new GunshipIngress(List.of(new GunshipIngress.Point(10, 0), new GunshipIngress.Point(40, 0)));
        var body = new AirBody();
        body.x = 9f;
        body.vx = 2f;
        body.facingDegrees = -90f;
        assertTrue(route.steer(body, ShuttleType.MUDSKIPPER, 1f / 30f));
        assertEquals(1, route.waypoint());
        assertTrue(body.x > 9f && body.x < 9.2f, "handoff steers from the current pose, never snaps to a waypoint");
        assertTrue(body.vx >= 2f, "handoff keeps forward momentum");
    }

    @Test void completedApproachHandsBackWithoutMovingTheBodyAgain() {
        var route = new GunshipIngress(List.of(new GunshipIngress.Point(10, 0)));
        var body = new AirBody();
        body.x = 9f;
        body.vx = 2f;
        assertFalse(route.steer(body, ShuttleType.MUDSKIPPER, 1f / 30f));
        assertTrue(route.complete());
        assertFalse(route.steer(body, ShuttleType.MUDSKIPPER, 1f / 30f));
        assertEquals(9f, body.x);
        assertEquals(2f, body.vx);
    }
}
