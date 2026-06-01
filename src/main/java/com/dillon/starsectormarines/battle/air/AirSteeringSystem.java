package com.dillon.starsectormarines.battle.air;

/**
 * Stateless steering integrator for air craft — the behavior half that used to
 * live on {@link AirBody} as {@code tickToward}. Reads an {@link AirBody}'s
 * kinematic state plus an {@link AirHandling} profile and a goal point under a
 * {@link SteeringMode}, and writes the body's new position / velocity / facing
 * for the tick. {@code AirBody} is now pure data (a Transform + Motion
 * component); this is the system over it.
 *
 * <p>The "boat" feel comes from how forward thrust is constrained: it acts
 * along the current facing, not directly toward the goal. Sideways velocity
 * relative to facing bleeds off via {@link AirHandling#lateralDriftDamping()}
 * (or {@link AirHandling#stationDamping()} in STATION). A bus profile (low
 * turn rate, high lateral damping) has to swing its nose around to redirect
 * momentum; a nimble profile carves tight corners.
 */
public final class AirSteeringSystem {

    private AirSteeringSystem() {}

    /**
     * Advances {@code body} one tick toward ({@code gx, gy}) under {@code mode},
     * governed by {@code handling}. Mutates the body's kinematic fields and
     * records the realized acceleration / turn rate (the FX read in
     * {@link com.dillon.starsectormarines.battle.air.engine.ThrusterDemand}
     * consumes them).
     *
     * <p>Algorithm per tick:
     * <ol>
     *   <li>Rotate {@code facingDegrees} toward the goal direction, rate-limited
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
    public static void steer(AirBody body, float gx, float gy,
                             SteeringMode mode, AirHandling handling, float dt) {
        // Pre-step snapshot so we can report the realized accel / turn rate the
        // engine-FX thruster weighting reads.
        float vx0 = body.vx, vy0 = body.vy, facing0 = body.facingDegrees;

        float dx = gx - body.x;
        float dy = gy - body.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float goalFacing = body.facingDegrees;
        if (dist > 1e-4f) {
            goalFacing = AirBody.facingToward(dx, dy);
            float maxTurn = handling.maxTurnRateDegPerSec() * dt;
            body.facingDegrees = approachAngle(body.facingDegrees, goalFacing, maxTurn);
        }

        float rad = (float) Math.toRadians(body.facingDegrees);
        float fx = -(float) Math.sin(rad);
        float fy =  (float) Math.cos(rad);
        float sx = -fy;
        float sy =  fx;

        float fwdVel  = body.vx * fx + body.vy * fy;
        float sideVel = body.vx * sx + body.vy * sy;

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
        float headingErrDeg = Math.abs(((goalFacing - body.facingDegrees + 540f) % 360f) - 180f);
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

        body.vx = fx * fwdVel + sx * sideVel;
        body.vy = fy * fwdVel + sy * sideVel;

        body.x += body.vx * dt;
        body.y += body.vy * dt;

        // Realized dynamics for the engine-FX read. Guard tiny dt to avoid a
        // divide blow-up; the facing delta walks the shortest arc through ±180.
        if (dt > 1e-6f) {
            body.ax = (body.vx - vx0) / dt;
            body.ay = (body.vy - vy0) / dt;
            float dFacing = ((body.facingDegrees - facing0 + 540f) % 360f) - 180f;
            body.angVelDegPerSec = dFacing / dt;
        }
    }

    /**
     * Rotates {@code from} toward {@code to} by at most {@code maxStep}
     * degrees, choosing the shortest arc through the ±180° wrap.
     */
    private static float approachAngle(float from, float to, float maxStep) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        if (delta >  maxStep) delta =  maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return from + delta;
    }
}
