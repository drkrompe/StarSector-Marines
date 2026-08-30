package com.dillon.starsectormarines.battle.air;

/**
 * A craft's handling while it is still on its wheels: the same aircraft, held
 * to taxi speed and turning like something being steered rather than flown.
 *
 * <p>A decorator rather than a second set of authored numbers. Which hull this
 * is remains the authority for how it moves — a bus taxis like a bus — and the
 * ground merely puts a ceiling on it. Authoring a separate taxi profile per
 * hull would be a second place for the same fact to live, and
 * {@code air-nouns.md} keeps hull-derived handling as the one source.
 *
 * <p>Turning is <em>faster</em> on the ground, not slower, which looks wrong
 * written down and is right: an aircraft on its wheels pivots about its gear at
 * walking pace, while the same craft in the air is fighting its own momentum
 * through the turn. Without it a taxiing craft swings wide of a taxiway it is
 * supposed to be following.
 */
public final class GroundHandling implements AirHandling {

    /**
     * Taxi speed, in cells per second.
     *
     * <p>Slow enough that crossing a base is a journey somebody could walk out
     * and interrupt — which is the entire point of making an aircraft roll
     * instead of lifting off its stand — and fast enough that it is not a
     * stationary target the whole time.
     */
    public static final float TAXI_SPEED = 2.4f;

    /** How much quicker a craft turns on its wheels than in the air. */
    private static final float GROUND_TURN_MULTIPLIER = 3f;

    private final AirHandling flight;
    private final float maxSpeed;

    private GroundHandling(AirHandling flight, float maxSpeed) {
        this.flight = flight;
        this.maxSpeed = maxSpeed;
    }

    /** This craft taxiing. */
    public static GroundHandling taxiing(AirHandling flight) {
        return new GroundHandling(flight, TAXI_SPEED);
    }

    /**
     * This craft on its takeoff roll: its own full speed, but still steering
     * like something on the ground, so it tracks the centreline instead of
     * drifting off the side of the strip.
     */
    public static GroundHandling rolling(AirHandling flight) {
        return new GroundHandling(flight, flight.maxSpeed());
    }

    @Override public float maxSpeed() { return maxSpeed; }
    @Override public float accel() { return flight.accel(); }
    @Override public float brakingAccel() { return flight.brakingAccel(); }

    @Override
    public float maxTurnRateDegPerSec() {
        return flight.maxTurnRateDegPerSec() * GROUND_TURN_MULTIPLIER;
    }

    /**
     * No sideways drift at all. A wheeled aircraft goes where it is pointed;
     * the lateral slip an airframe carries through a turn is exactly the thing
     * that would take it off the taxiway.
     */
    @Override public float lateralDriftDamping() { return 1f; }

    @Override public float stationDamping() { return flight.stationDamping(); }
}
