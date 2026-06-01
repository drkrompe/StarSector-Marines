package com.dillon.starsectormarines.battle.air;

/**
 * Kinematic state of one airborne vehicle — position, velocity, acceleration,
 * facing, and angular velocity. <b>Pure data</b>: the Transform + Motion
 * component of an air entity, with no behavior. The steering that integrates it
 * lives in {@link AirSteeringSystem}; the realized {@code ax/ay} and
 * {@code angVelDegPerSec} are written there each tick.
 *
 * <p>Shared by shuttles, drones, and (planned) fighters; all compose an instance
 * rather than duplicating the kinematics. The body owns its primitive state
 * directly so callers can read {@link #x}/{@link #y}/{@link #facingDegrees}
 * for rendering without going through accessors.
 *
 * <p>Facing convention: 0° points sprite-north (+Y), positive rotates
 * counterclockwise (Starsector sprite-angle convention). Velocity is in the
 * same Y-up cell-coord space, cells/sec.
 */
public class AirBody {

    /** Position, cells. */
    public float x, y;

    /** Velocity, cells/sec. */
    public float vx, vy;

    /**
     * Linear acceleration over the last tick, cells/sec² — the realized
     * {@code Δv/dt} written by {@link AirSteeringSystem}. This is the "intended
     * velocity change" the engine-FX read uses to bloom the thrusters actually
     * doing the pushing (see {@code ThrusterDemand}); zero between ticks / after
     * a {@link #teleport}.
     */
    public float ax, ay;

    /** Facing — 0° = +Y, positive CCW. Matches Starsector sprite convention. */
    public float facingDegrees;

    /** Realized heading change rate over the last tick, deg/sec (shortest-arc Δfacing/dt). Written by {@link AirSteeringSystem}; drives the rotational term of the engine-FX thruster weighting. */
    public float angVelDegPerSec;

    /** Distance to ({@code gx, gy}), cells. */
    public float distanceTo(float gx, float gy) {
        float dx = gx - x, dy = gy - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Current speed, cells/sec. */
    public float speed() {
        return (float) Math.sqrt(vx * vx + vy * vy);
    }

    /** Snaps the body to ({@code px, py}) with zero velocity and the given facing. Use at lifecycle boundaries (touchdown, respawn). */
    public void teleport(float px, float py, float facingDeg) {
        x = px; y = py;
        vx = 0f; vy = 0f;
        ax = 0f; ay = 0f;
        facingDegrees = facingDeg;
        angVelDegPerSec = 0f;
    }

    /**
     * Sprite-angle for an arbitrary direction vector — 0° = +Y, positive CCW.
     * Math: angle of the vector minus 90° because Starsector's sprite default
     * is north-facing while atan2's 0° is east. Caller checks for zero-length
     * before calling.
     */
    public static float facingToward(float dx, float dy) {
        float mathDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        return mathDeg - 90f;
    }
}
