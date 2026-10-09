package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.ship.BayAperture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipBayTransitTest {
    private static final AirHandling HULL = new AirHandling() {
        public float maxSpeed() { return 12f; }
        public float accel() { return 1.5f; }
        public float brakingAccel() { return 2f; }
        public float maxTurnRateDegPerSec() { return 45f; }
        public float lateralDriftDamping() { return 2f; }
        public float stationDamping() { return 3f; }
    };

    @Test void everyBankHasAContinuousStoppedRoundTripThroughTheOpening() {
        for (int left : new int[]{3, 16}) {
            for (int bottom : new int[]{4, 12, 20}) {
                for (int rotation = 0; rotation < 4; rotation++) {
                    Gantry source = Gantry.covering(left, bottom, 5, 7,
                            left == 3 ? Gantry.Facing.EAST : Gantry.Facing.WEST, Gantry.Holds.BOAT);
                    Gantry berth = rotate(source, rotation);
                    float[] opening = rotate(12f, -0.5f, rotation);
                    int[] out = rotate(0, -1, rotation);
                    ShipBayRoute route = ShipBayRoute.from(berth,
                            new BayAperture(1, opening[0], opening[1], out[0], out[1], 8), 16f);
                    AirBody body = new AirBody();
                    body.teleport(berth.worldCenterX(), berth.worldCenterY(), berth.facing.degrees());
                    fly(new ShipBayTransit(route, false), body);
                    assertEquals(route.outside().x(), body.x, 0.0001f);
                    assertEquals(route.outside().y(), body.y, 0.0001f);
                    fly(new ShipBayTransit(route, true), body);
                    assertEquals(berth.worldCenterX(), body.x, 0.0001f);
                    assertEquals(berth.worldCenterY(), body.y, 0.0001f);
                    assertEquals(0f, body.speed(), 0.0001f);
                    assertEquals(0f, angleDelta(body.facingDegrees, berth.facing.degrees()), 0.0001f);
                }
            }
        }
    }

    private static void fly(ShipBayTransit transit, AirBody body) {
        float previousAltitude = transit.returning ? 1f : 0f;
        for (int tick = 0; tick < 4500; tick++) {
            float oldX = body.x, oldY = body.y, heading = body.facingDegrees;
            int segment = transit.segment;
            boolean complete = transit.advance(body, HULL, 1f / 30f);
            assertTrue(body.distanceTo(oldX, oldY) <= 2f / 30f + 0.0001f, "position jump");
            assertTrue(Math.abs(body.x - oldX) < 0.0001f || Math.abs(body.y - oldY) < 0.0001f,
                    "diagonal shortcut across a bank");
            assertTrue(body.speed() <= 2.0001f);
            assertTrue(Math.hypot(body.ax, body.ay) <= HULL.accel() + 0.002f, "unbounded thrust/brake");
            assertTrue(Math.abs(angleDelta(body.facingDegrees, heading)) <= 45f / 30f + 0.001f);
            if (segment != transit.segment) assertEquals(0f, body.speed(), 0.0001f, "corner without stopping");
            float altitude = transit.altitude(body);
            assertTrue(Math.abs(altitude - previousAltitude) < 0.02f, "altitude discontinuity");
            previousAltitude = altitude;
            if (complete) return;
        }
        fail("bay transit never completed");
    }

    @Test void singleRankClearsItsNoseBeforeAligningWithTheDoor() {
        Gantry berth = Gantry.covering(4, 5, 7, 5, Gantry.Facing.SOUTH, Gantry.Holds.BOAT);
        ShipBayRoute route = ShipBayRoute.from(berth, new BayAperture(1, 14, -0.5f, 0, -1, 8), 16);
        assertEquals(berth.worldCenterX(), route.points().get(1).x());
        assertEquals(route.points().get(1).y(), route.points().get(2).y());
        assertEquals(14f, route.points().get(2).x());
    }

    private static Gantry rotate(Gantry source, int turns) {
        int[] a = rotate(source.left(), source.bottom(), turns);
        int[] b = rotate(source.right() + 1, source.top() + 1, turns);
        int[] heading = rotate(source.facing.dx, source.facing.dy, turns);
        return Gantry.covering(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]),
                Gantry.Facing.of(heading[0], heading[1]), Gantry.Holds.BOAT);
    }
    private static int[] rotate(int x, int y, int turns) {
        for (int i = 0; i < turns; i++) { int old = x; x = -y; y = old; }
        return new int[]{x, y};
    }
    private static float[] rotate(float x, float y, int turns) {
        for (int i = 0; i < turns; i++) { float old = x; x = -y; y = old; }
        return new float[]{x, y};
    }
    private static float angleDelta(float a, float b) {
        float delta = (a - b) % 360f;
        return delta > 180 ? delta - 360 : delta < -180 ? delta + 360 : delta;
    }
}
