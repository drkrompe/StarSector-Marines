package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aircraft on its wheels, one property at a time.
 *
 * <p>Asked of the driver directly rather than of a battle. Every question here
 * is about the locomotion model — does it slide, does it square up, how tightly
 * can it corner — and each one has an answer a body and a handling profile
 * can give on their own.
 */
class GroundDriveSystemTest {

    private static final float DT = 1f / 30f;

    /** A middling aircraft: quick enough to be interesting, slow enough to watch. */
    private record TestFlight(float maxSpeed, float accel, float brakingAccel,
                              float maxTurnRateDegPerSec, float lateralDriftDamping,
                              float stationDamping) implements AirHandling {}

    private static AirHandling flight() {
        return new TestFlight(12f, 20f, 25f, 90f, 2.5f, 4f);
    }

    private static AirBody standing(float x, float y, float facingDeg) {
        AirBody body = new AirBody();
        body.teleport(x, y, facingDeg);
        return body;
    }

    /**
     * It goes where it is pointed and nowhere else.
     *
     * <p>The defining difference from the flight steering, which redirects a
     * body by pointing its nose and letting the sideways component of its
     * momentum bleed off. There is no sideways component here to bleed: every
     * step the craft takes is along its own heading, so it is asserted on the
     * step rather than on a damping rate.
     */
    @Test
    void itOnlyEverMovesAlongItsOwnHeading() {
        AirBody body = standing(10f, 10f, 0f);
        GroundHandling handling = GroundHandling.taxiing(flight());
        float steer = 0f;
        float worstSideways = 0f;
        for (int i = 0; i < 300; i++) {
            float wasX = body.x, wasY = body.y;
            // A carrot off to one side the whole way, so the craft is
            // continuously being asked to go somewhere it is not pointing.
            steer = GroundDriveSystem.drive(body, steer, 30f, 24f,
                    GroundHandling.TAXI_SPEED, handling, DT);
            float dx = body.x - wasX, dy = body.y - wasY;
            float rad = (float) Math.toRadians(body.facingDegrees);
            float fx = -(float) Math.sin(rad), fy = (float) Math.cos(rad);
            worstSideways = Math.max(worstSideways, Math.abs(dx * -fy + dy * fx));
        }
        assertTrue(worstSideways < 1e-4f,
                "slid " + worstSideways + " cells sideways in a single step");
    }

    /**
     * It squares up standing still before it rolls, however fast it arrived.
     *
     * <p>What lining up on a strip is made of. A craft that opens the throttle
     * and steers onto its line at the same time arrives at speed off the side
     * of it, which is the fault this exists to prevent.
     *
     * <p>Started at speed on purpose, because that is the case that broke. An
     * earlier version decided whether to square up from the speed the craft
     * happened to be doing rather than from how far off it was pointed, which
     * left a band where a craft most of a right angle out was still allowed a
     * trickle of throttle, never slowed enough to swing its nose, and drove off
     * in an enormous arc instead — twenty-five cells of open ground, sideways,
     * out of the threshold it was supposed to be lining up on.
     */
    @Test
    void itSquaresUpBeforeItRollsHoweverFastItArrived() {
        // Pointed due south with the carrot due north, and already rolling.
        AirBody body = standing(10f, 10f, 180f);
        GroundHandling handling = GroundHandling.rolling(flight());
        body.vy = -handling.maxSpeed();
        float steer = 0f;
        float travelledBeforeSquare = 0f;
        int squareAt = -1;
        for (int i = 0; i < 300; i++) {
            float wasX = body.x, wasY = body.y;
            steer = GroundDriveSystem.drive(body, steer, 10f, 400f,
                    handling.maxSpeed(), handling, DT);
            if (squareAt < 0) {
                travelledBeforeSquare += (float) Math.hypot(body.x - wasX, body.y - wasY);
                if (Math.abs(headingError(body, 10f, 400f)) <= handling.pivotErrorDeg()) squareAt = i;
            }
        }
        assertTrue(squareAt >= 0, "never got itself straight at all");
        // Only what it takes to stop: it is on the brakes from the first tick
        // and the swing itself covers no ground.
        assertTrue(travelledBeforeSquare < 3f,
                "covered " + travelledBeforeSquare + " cells before it was lined up");
        assertTrue(body.y > 60f, "squared up and then never went anywhere: y = " + body.y);
    }

    /**
     * It cannot corner tighter than its own turning circle.
     *
     * <p>The bound that makes it a wheeled thing rather than something that
     * redirects itself on the spot. Measured off the track it leaves — how far
     * it went against how far its nose came round — because that is what the
     * radius is, and a rate in degrees per second is not.
     */
    @Test
    void itCannotCornerTighterThanItsTurningCircle() {
        AirBody body = standing(10f, 10f, 0f);
        GroundHandling handling = GroundHandling.taxiing(flight());
        float steer = 0f;
        float tightest = Float.MAX_VALUE;
        for (int i = 0; i < 600; i++) {
            float wasX = body.x, wasY = body.y, wasFacing = body.facingDegrees;
            // A point close by and off to one side: as hard a turn as anything
            // on a taxiway ever asks for.
            steer = GroundDriveSystem.drive(body, steer, 13f, 10f,
                    GroundHandling.TAXI_SPEED, handling, DT);
            float step = (float) Math.hypot(body.x - wasX, body.y - wasY);
            float swung = (float) Math.abs(Math.toRadians(wrap(body.facingDegrees - wasFacing)));
            if (step > 1e-4f && swung > 1e-6f) tightest = Math.min(tightest, step / swung);
        }
        assertTrue(tightest < Float.MAX_VALUE, "never turned while moving, so nothing was measured");
        assertTrue(tightest > handling.minTurnRadiusCells() * 0.95f,
                "cornered on a " + tightest + "-cell radius, inside its "
                        + handling.minTurnRadiusCells() + "-cell circle");
    }

    /**
     * It gives up speed for a corner rather than taking the corner wide.
     *
     * <p>Which of the two an aircraft does is the whole difference between a
     * taxiway and a racetrack, and with the arc already bounded by the gear
     * there is only one way left to make a tight corner: go round it slowly.
     * So how fast the craft wants to go falls away with how far off its nose it
     * is being asked to go — full speed down a straight, walking pace round a
     * hangar.
     */
    @Test
    void aTightCornerCostsItSpeed() {
        GroundHandling handling = GroundHandling.taxiing(flight());
        assertTrue(settledSpeed(handling, /*carrotX*/ 10f, /*carrotY*/ 200f)
                        > GroundHandling.TAXI_SPEED - 0.05f,
                "would not reach taxi speed on a straight run");
        float roundABend = settledSpeed(handling, 14f, 13f);
        assertTrue(roundABend < GroundHandling.TAXI_SPEED - 0.2f,
                "held full taxi speed round a bend at " + roundABend);
    }

    /** Speed after a long run at one carrot, from a standing start pointed at it. */
    private static float settledSpeed(GroundHandling handling, float carrotX, float carrotY) {
        AirBody body = standing(10f, 10f, 0f);
        float steer = 0f;
        for (int i = 0; i < 60; i++) {
            steer = GroundDriveSystem.drive(body, steer, carrotX, carrotY,
                    handling.maxSpeed(), handling, DT);
        }
        return body.speed();
    }

    /** Signed shortest arc from the body's heading to the bearing of a point. */
    private static float headingError(AirBody body, float gx, float gy) {
        return wrap(AirBody.facingToward(gx - body.x, gy - body.y) - body.facingDegrees);
    }

    /** Shortest signed arc equivalent to {@code deg}. */
    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }
}
