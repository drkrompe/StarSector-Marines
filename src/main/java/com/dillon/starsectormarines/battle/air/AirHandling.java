package com.dillon.starsectormarines.battle.air;

/**
 * Per-vehicle-type handling profile. Implementations live on the type enums
 * ({@code ShuttleType}, eventually {@code FighterType}) so an {@link AirBody}
 * holds a reference to its type's profile and looks up tunables each tick.
 *
 * <p>Units are cells-per-second and degrees-per-second so the numbers stay in
 * the same space as the rest of the battle sim. Lateral / station damping are
 * exponential decay rates (1/sec): a damping of {@code 2} means side velocity
 * halves about every 0.35s. Higher = harder snap to forward-only motion.
 */
public interface AirHandling {
    /** Hard cap on forward velocity along the body's facing, cells/sec. */
    float maxSpeed();

    /** Forward thrust cap, cells/sec². Applied when the body is below the desired forward speed. */
    float accel();

    /**
     * Forward braking cap, cells/sec². Used by {@link SteeringMode#BRAKE_TO_STATION}
     * to scale the approach taper, and as the upper bound on negative forward
     * thrust in all modes. A "bus" profile keeps this low so it can't stop on
     * a dime; a nimble profile makes it crisp.
     */
    float brakingAccel();

    /** Heading slew rate cap, degrees/sec. Bus profiles get low values so they pendulum into headings; nimble profiles snap. */
    float maxTurnRateDegPerSec();

    /**
     * Exponential decay rate (1/sec) applied to the velocity component
     * perpendicular to the body's facing during CRUISE / BRAKE_TO_STATION.
     * 0 = arcade slide (momentum is preserved in all directions); larger =
     * pure-forward bus where sideways momentum bleeds off quickly.
     */
    float lateralDriftDamping();

    /**
     * Side damping used by {@link SteeringMode#STATION}. Usually higher than
     * {@link #lateralDriftDamping()} so a hovering body settles against
     * ambient drift instead of orbiting its station point.
     */
    float stationDamping();
}
