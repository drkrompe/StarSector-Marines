package com.dillon.starsectormarines.battle.air;

/**
 * How {@link AirBody#tickToward} treats the goal point.
 *
 * <ul>
 *   <li>{@link #CRUISE} — fly at {@link AirHandling#maxSpeed()}. Doesn't try to
 *       stop at the goal; the caller transitions out when the body crosses the
 *       waypoint.</li>
 *   <li>{@link #BRAKE_TO_STATION} — approach the goal and decelerate so the
 *       body comes to rest on it. Desired forward speed is
 *       {@code min(maxSpeed, sqrt(2·brakingAccel·distance))}, which converges
 *       to zero as distance shrinks.</li>
 *   <li>{@link #STATION} — hold position at the goal. Same braking law, but
 *       lateral drift uses {@link AirHandling#stationDamping()} instead of
 *       {@link AirHandling#lateralDriftDamping()} so the body settles harder
 *       against ambient drift. Reserved for future hover-loiter behavior; not
 *       used by the current shuttle state machine.</li>
 * </ul>
 */
public enum SteeringMode {
    CRUISE, BRAKE_TO_STATION, STATION
}
