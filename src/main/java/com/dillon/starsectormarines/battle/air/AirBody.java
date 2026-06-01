package com.dillon.starsectormarines.battle.air;

/**
 * Kinematic state of one airborne vehicle — position, velocity, facing, and
 * angular velocity — plus a steering tick that applies an {@link AirHandling}
 * profile toward a goal point under a {@link SteeringMode}.
 *
 * <p>Shared by shuttles and (planned) fighters; both compose an instance
 * rather than duplicating the kinematics. The body owns its primitive state
 * directly so callers can read {@link #x}/{@link #y}/{@link #facingDegrees}
 * for rendering without going through accessors.
 *
 * <p>Facing convention: 0° points sprite-north (+Y), positive rotates
 * counterclockwise (Starsector sprite-angle convention). Velocity is in the
 * same Y-up cell-coord space, cells/sec.
 *
 * <p>The "boat" feel comes from how forward thrust is constrained: it acts
 * along the current facing, not directly toward the goal. Sideways velocity
 * relative to facing bleeds off via {@link AirHandling#lateralDriftDamping()}
 * (or {@link AirHandling#stationDamping()} in STATION). A bus profile (low
 * turn rate, high lateral damping) has to swing its nose around to redirect
 * momentum; a nimble profile carves tight corners.
 */
public class AirBody {

    /** Position, cells. */
    public float x, y;

    /** Velocity, cells/sec. */
    public float vx, vy;

    /**
     * Linear acceleration over the last tick, cells/sec² — the realized
     * {@code Δv/dt} from {@link #tickToward}. This is the "intended velocity
     * change" the engine-FX read uses to bloom the thrusters actually doing the
     * pushing (see {@code ThrusterDemand}); zero between ticks / after a
     * {@link #teleport}.
     */
    public float ax, ay;

    /** Facing — 0° = +Y, positive CCW. Matches Starsector sprite convention. */
    public float facingDegrees;

    /** Realized heading change rate over the last tick, deg/sec (shortest-arc Δfacing/dt). Populated by {@link #tickToward}; drives the rotational term of the engine-FX thruster weighting. */
    public float angVelDegPerSec;

    /**
     * Advances kinematic state one tick toward ({@code gx, gy}) under
     * {@code mode}, governed by the body's {@code handling} profile.
     *
     * <p>Algorithm per tick:
     * <ol>
     *   <li>Rotate {@link #facingDegrees} toward the goal direction, rate-limited
     *       by {@link AirHandling#maxTurnRateDegPerSec()}.</li>
     *   <li>Decompose current velocity into forward/lateral components against
     *       the new facing.</li>
     *   <li>Pick desired forward speed: {@link AirHandling#maxSpeed()} in CRUISE;
     *       {@code min(maxSpeed, sqrt(2·brakingAccel·distance))} otherwise.</li>
     *   <li>Step forward velocity toward desired by at most {@code accel·dt}
     *       (or {@code brakingAccel·dt} when decelerating).</li>
     *   <li>Exponentially decay the lateral component with
     *       {@link AirHandling#lateralDriftDamping()} ({@link AirHandling#stationDamping()}
     *       in STATION).</li>
     *   <li>Recompose velocity and integrate position.</li>
     * </ol>
     */
    public void tickToward(float gx, float gy, SteeringMode mode, AirHandling handling, float dt) {
        // Pre-step snapshot so we can report the realized accel / turn rate the
        // engine-FX thruster weighting reads.
        float vx0 = vx, vy0 = vy, facing0 = facingDegrees;

        float dx = gx - x;
        float dy = gy - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float goalFacing = facingDegrees;
        if (dist > 1e-4f) {
            goalFacing = facingToward(dx, dy);
            float maxTurn = handling.maxTurnRateDegPerSec() * dt;
            facingDegrees = approachAngle(facingDegrees, goalFacing, maxTurn);
        }

        float rad = (float) Math.toRadians(facingDegrees);
        float fx = -(float) Math.sin(rad);
        float fy =  (float) Math.cos(rad);
        float sx = -fy;
        float sy =  fx;

        float fwdVel  = vx * fx + vy * fy;
        float sideVel = vx * sx + vy * sy;

        float desiredFwd;
        switch (mode) {
            case CRUISE:
                desiredFwd = handling.maxSpeed();
                break;
            case BRAKE_TO_STATION:
            case STATION:
            default:
                float taper = (float) Math.sqrt(2f * handling.brakingAccel() * Math.max(0f, dist));
                desiredFwd = Math.min(handling.maxSpeed(), taper);
                break;
        }

        // Heading gate — don't apply forward thrust if the goal is sideways or
        // behind us. Without this, a stationary body asked to "go to exit"
        // would first accelerate along its current (toward-LZ) facing before
        // swinging around, briefly drifting the wrong way. With the gate,
        // forward thrust ramps in only as the nose comes around toward the
        // goal — so buses get a clean "pause, swing, then go" startup that
        // matches how a heavy aircraft pivots before committing thrust.
        float headingErrDeg = Math.abs(((goalFacing - facingDegrees + 540f) % 360f) - 180f);
        float thrustGate = Math.max(0f, 1f - headingErrDeg / 90f);
        desiredFwd *= thrustGate;

        float dv = desiredFwd - fwdVel;
        float maxStep = (dv >= 0f) ? handling.accel() * dt : handling.brakingAccel() * dt;
        if (dv >  maxStep) dv =  maxStep;
        if (dv < -maxStep) dv = -maxStep;
        fwdVel += dv;

        if (fwdVel >  handling.maxSpeed()) fwdVel =  handling.maxSpeed();
        if (fwdVel < -handling.maxSpeed()) fwdVel = -handling.maxSpeed();

        float damp = (mode == SteeringMode.STATION)
                ? handling.stationDamping()
                : handling.lateralDriftDamping();
        sideVel *= (float) Math.exp(-damp * dt);

        vx = fx * fwdVel + sx * sideVel;
        vy = fy * fwdVel + sy * sideVel;

        x += vx * dt;
        y += vy * dt;

        // Realized dynamics for the engine-FX read. Guard tiny dt to avoid a
        // divide blow-up; the facing delta walks the shortest arc through ±180.
        if (dt > 1e-6f) {
            ax = (vx - vx0) / dt;
            ay = (vy - vy0) / dt;
            float dFacing = ((facingDegrees - facing0 + 540f) % 360f) - 180f;
            angVelDegPerSec = dFacing / dt;
        }
    }

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

    /**
     * Rotates {@code from} toward {@code to} by at most {@code maxStep}
     * degrees, choosing the shortest arc through the ±180° wrap.
     */
    public static float approachAngle(float from, float to, float maxStep) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        if (delta >  maxStep) delta =  maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return from + delta;
    }
}
