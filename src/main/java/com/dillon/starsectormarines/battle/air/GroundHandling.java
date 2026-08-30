package com.dillon.starsectormarines.battle.air;

/**
 * A craft's handling while it is still on its wheels — what
 * {@link GroundDriveSystem} drives it with.
 *
 * <p>Deliberately <em>not</em> an {@link AirHandling}. It used to be one: a
 * decorator that capped the flight profile's speed and multiplied its turn rate
 * so the flying steering could be pointed at a taxiway. That produced an
 * aircraft that drifted sideways across the apron and settled onto its
 * waypoints, because the thing being decorated was still a model of something
 * flying. Ground locomotion has quantities flight has no word for — how tight
 * an arc the gear can be steered round, how much sideways load the wheels will
 * take — and no use for the ones flight cares about, so it is its own profile.
 *
 * <p>What it still borrows is the hull. Which aircraft this is remains the
 * authority for how much power it has and how hard it stops — a bus taxis like
 * a bus — and the ground puts a ceiling on the rest. Authoring a separate taxi
 * profile per hull would be a second place for the same fact to live, and
 * {@code air-nouns.md} keeps hull-derived handling as the one source.
 */
public final class GroundHandling {

    /**
     * Taxi speed, in cells per second.
     *
     * <p>Slow enough that crossing a base is a journey somebody could walk out
     * and interrupt — which is the entire point of making an aircraft roll
     * instead of lifting off its stand — and fast enough that it is not a
     * stationary target the whole time.
     */
    public static final float TAXI_SPEED = 2.4f;

    /**
     * Taxi power and braking, cells/sec².
     *
     * <p>Capped rather than taken from the hull: a fighter's flight
     * acceleration would put it at taxi speed inside a tenth of a second, which
     * reads as a jump rather than as something rolling away from its stand.
     * Roughly a second to walking pace, and rather harder onto the brakes,
     * because an aircraft stops with wheelbrakes and starts with engines.
     */
    private static final float TAXI_ACCEL = 2.5f;
    private static final float TAXI_BRAKING = 3.5f;

    /**
     * Tightest arc the undercarriage will be steered round, cells.
     *
     * <p>Gear geometry rather than agility, which is why it is one number and
     * not scaled off the hull's flight turn rate: a nimble interceptor and a
     * sluggish bomber taxi round much the same corner, and the flight rate is
     * an angular rate rather than a radius — carried across it makes every
     * aircraft pivot on the spot, which is the reading that started this. Sized
     * against the maps: a couple of cells turns an aircraft between hangars
     * without letting it corner like something on rails.
     */
    private static final float MIN_TURN_RADIUS_CELLS = 2.0f;

    /**
     * Sideways load the wheels will take through a turn, cells/sec².
     *
     * <p>The single constant that makes a turn tighten as the craft slows. At
     * the minimum radius it holds an aircraft to about two cells a second, so a
     * sharp corner costs a little speed; at rolling speed it permits almost no
     * curvature at all, which is what keeps a takeoff roll straight without
     * anybody pinning the heading to the strip.
     */
    private static final float LATERAL_ACCEL = 2.0f;

    /** How fast the nosewheel swings, in fractions of full lock per second. */
    private static final float STEER_SLEW_PER_SEC = 2.5f;

    /** Below this speed, in cells/sec, a large heading error is turned out on the spot instead of driven round. */
    private static final float PIVOT_SPEED_CELLS = 0.35f;

    /**
     * How fast an aircraft swings its nose round standing still, deg/sec.
     *
     * <p>Brakes and nosewheel, not a hull turn rate. Slow enough to read as a
     * ground manoeuvre — a half turn takes a couple of seconds, every one of
     * them spent stationary in the open — and quick enough that lining up on a
     * strip is not most of the sortie.
     */
    private static final float PIVOT_RATE_DEG_PER_SEC = 70f;

    /**
     * How far off a taxiing craft will start rolling, degrees.
     *
     * <p>Generous, because a taxi route corners constantly and a craft that
     * stopped to square up at each one would crawl. What it prevents is the
     * standing start pointed the wrong way.
     */
    private static final float TAXI_PIVOT_ERROR_DEG = 40f;

    /**
     * How straight a craft has to be before it rolls, degrees.
     *
     * <p>Tight, because this one is lining up. Reaching a threshold and
     * reaching it pointed down the strip are different things, and the
     * difference is the whole of the complaint: a craft that opened the
     * throttle and steered onto the centreline at the same time arrived at
     * rotation speed somewhere off the side of it.
     */
    private static final float ROLL_PIVOT_ERROR_DEG = 5f;

    private final float maxSpeed;
    private final float accel;
    private final float brakingAccel;
    private final float pivotErrorDeg;

    private GroundHandling(float maxSpeed, float accel, float brakingAccel, float pivotErrorDeg) {
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.brakingAccel = brakingAccel;
        this.pivotErrorDeg = pivotErrorDeg;
    }

    /** This craft taxiing: walking pace, and squaring up only for the turns worth stopping for. */
    public static GroundHandling taxiing(AirHandling flight) {
        return new GroundHandling(TAXI_SPEED,
                Math.min(TAXI_ACCEL, flight.accel()),
                Math.min(TAXI_BRAKING, flight.brakingAccel()),
                TAXI_PIVOT_ERROR_DEG);
    }

    /**
     * This craft on the strip: its own full power, and lined up before any of
     * it is used. Both rolls take it — a takeoff builds speed along the
     * centreline and a landing bleeds it off along the same line.
     */
    public static GroundHandling rolling(AirHandling flight) {
        return new GroundHandling(flight.maxSpeed(), flight.accel(), flight.brakingAccel(),
                ROLL_PIVOT_ERROR_DEG);
    }

    /** Hard cap on rolling speed, cells/sec. */
    public float maxSpeed() { return maxSpeed; }

    /** Power available to build speed, cells/sec². */
    public float accel() { return accel; }

    /** Braking, cells/sec². */
    public float brakingAccel() { return brakingAccel; }

    /** Tightest arc the gear will be steered round, cells. */
    public float minTurnRadiusCells() { return MIN_TURN_RADIUS_CELLS; }

    /** Sideways load the wheels will take, cells/sec² — what makes a tight turn a slow one. */
    public float lateralAccel() { return LATERAL_ACCEL; }

    /** Nosewheel slew, fractions of full lock per second. */
    public float steerSlewPerSec() { return STEER_SLEW_PER_SEC; }

    /** Speed below which a large heading error is turned out on the spot, cells/sec. */
    public float pivotSpeedCells() { return PIVOT_SPEED_CELLS; }

    /** How fast the nose comes round standing still, deg/sec. */
    public float pivotRateDegPerSec() { return PIVOT_RATE_DEG_PER_SEC; }

    /** How far off where it is going this craft will move without squaring up first, degrees. */
    public float pivotErrorDeg() { return pivotErrorDeg; }
}
