package com.dillon.starsectormarines.battle.air;

/**
 * Locomotion for an aircraft that is on its wheels. The ground counterpart of
 * {@link AirSteeringSystem}, and a genuinely different model rather than the
 * flying one held down to walking pace.
 *
 * <p>An aircraft in the air redirects momentum by pointing its nose at
 * something and waiting for the sideways component to bleed off. An aircraft on
 * its gear cannot do that at all: it goes where the wheels are pointed, it
 * steers through an arc the undercarriage sets rather than through a heading
 * rate, and it can stop. Driving the ground phases with the flight steering and
 * a decorated handling profile got the speed right and every other part of it
 * wrong — the craft crabbed sideways across the apron and settled onto its
 * waypoints like something hovering.
 *
 * <p>Three rules make up the model:
 *
 * <ul>
 *   <li><b>It rolls along its own heading.</b> Velocity is composed from the
 *       facing and one scalar speed, so lateral drift is not damped out — it
 *       never exists. The body's {@code vx}/{@code vy} are outputs of this
 *       system rather than state it carries forward, which is what lets a
 *       caller clamp a blocked axis and have the craft simply scrub off the
 *       speed it lost against the wall.</li>
 *   <li><b>It steers through an arc.</b> The nosewheel is deflected toward a
 *       pure-pursuit curvature — the arc through a carrot ahead of it on the
 *       route — bounded by {@link GroundHandling#minTurnRadiusCells()} and
 *       slewed rather than snapped, so the turn reads as something being
 *       steered. Pure pursuit is the same carrot law the ground vehicles use
 *       ({@link com.dillon.starsectormarines.battle.vehicle.PurePursuit}) and
 *       for the same reason: a controller that aims at a fixed waypoint orbits
 *       it, and a carrot that keeps sliding forward along the route cannot.</li>
 *   <li><b>It slows for its corners.</b> Desired speed falls away the further
 *       off the nose the carrot is, so a craft that wants a tight corner gives
 *       up speed for it rather than taking it wide, and one running straight
 *       goes at whatever the caller allowed. Held against the bounded arc, that
 *       is what a taxiway looks like: quick down the straights, slow round the
 *       hangars.</li>
 * </ul>
 *
 * <p>Below {@link GroundHandling#pivotSpeedCells()} a craft that is pointed far
 * enough away from where it is going swings its nose round <em>on the spot</em>
 * at {@link GroundHandling#pivotRateDegPerSec()}. A rolling model on its own
 * cannot turn at a standstill, and an aircraft plainly can — differential brake
 * and nosewheel, or a tug. It is also the whole of lining up: a craft that
 * reaches a threshold pointing across the strip stops, swings round until it is
 * within {@link GroundHandling#pivotErrorDeg()} of the roll direction, and only
 * then opens the throttle. Steering onto the centreline while accelerating
 * along it is exactly what threw aircraft off the side of the runway.
 *
 * <p>Stateless, in the shape the rest of the battle systems take. The one piece
 * of state the model needs is the current nosewheel deflection, which is passed
 * in and returned rather than held here; its home is the craft's
 * {@link ShuttleMission}.
 */
public final class GroundDriveSystem {

    private GroundDriveSystem() {}

    /**
     * How far off the nose a carrot has to be before the craft stops driving at
     * it, in degrees.
     *
     * <p>Not a gate but a taper: desired speed falls off linearly to nothing as
     * the carrot swings out to this bearing, so an aircraft slows into its
     * corners and creeps round the sharp ones. Wider than a right angle because
     * a route round a hangar turns ninety degrees at every corner and an
     * aircraft that stopped dead at each of them would never get anywhere.
     */
    private static final float TURN_AWAY_DEG = 100f;

    /**
     * Rolls {@code body} one tick toward the carrot at ({@code carrotX},
     * {@code carrotY}), at no more than {@code targetSpeed} cells/sec.
     *
     * <p>The carrot is a look-ahead point on the route rather than the
     * destination, and {@code targetSpeed} is the caller's cap — normally the
     * profile's own maximum, tapered by the caller as it brakes into the end of
     * its leg. The same contract
     * {@link com.dillon.starsectormarines.battle.vehicle.GroundBody#tick} takes,
     * for the same reason: how fast to go on this leg is the leg's business and
     * how to get there is the driver's.
     *
     * @param steer nosewheel deflection carried from the previous tick, as a
     *              fraction of full lock in {@code [-1, 1]}
     * @return the deflection to carry into the next tick
     */
    public static float drive(AirBody body, float steer,
                              float carrotX, float carrotY,
                              float targetSpeed, GroundHandling handling, float dt) {
        float vx0 = body.vx, vy0 = body.vy, facing0 = body.facingDegrees;

        float dx = carrotX - body.x;
        float dy = carrotY - body.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        // Where the carrot sits relative to the nose. Positive is to the left,
        // matching the facing convention (0° = +Y, positive CCW).
        float alphaDeg = 0f;
        if (dist > 1e-4f) {
            alphaDeg = wrap180(AirBody.facingToward(dx, dy) - body.facingDegrees);
        }

        // Pure-pursuit curvature: the arc from here through the carrot. Behind
        // the wingline there is no such arc worth having, so the wheel goes to
        // full lock the right way round and the pivot below does the rest.
        float maxCurvature = 1f / handling.minTurnRadiusCells();
        float commanded;
        if (dist <= 1e-4f) {
            commanded = 0f;
        } else if (Math.abs(alphaDeg) > 90f) {
            commanded = Math.signum(alphaDeg) * maxCurvature;
        } else {
            commanded = (float) (2.0 * Math.sin(Math.toRadians(alphaDeg)) / dist);
        }
        commanded = clamp(commanded, -maxCurvature, maxCurvature);

        steer = slew(steer, commanded / maxCurvature, handling.steerSlewPerSec() * dt);
        float curvature = steer * maxCurvature;

        // Too far off to drive at all: brake, and swing the nose round once it
        // is slow enough. Whether the craft is squaring up is decided by the
        // heading error and nothing else. Gating it on the speed the craft
        // happens to be doing leaves a band in which a craft pointed most of a
        // right angle away is still permitted a trickle of throttle, never
        // slows to the pivot, and drives off in an enormous arc instead — a
        // fighter left the threshold on that band and crossed twenty-five cells
        // of open ground sideways before its wheel could bring it round.
        boolean squaringUp = Math.abs(alphaDeg) > handling.pivotErrorDeg();

        // How fast it may go: the caller's cap, tapered by how far off the nose
        // it is being asked to go. An aircraft gives up speed for a corner
        // rather than taking the corner wide.
        float desired = squaringUp ? 0f
                : Math.max(0f, targetSpeed)
                        * Math.max(0f, 1f - Math.abs(alphaDeg) / TURN_AWAY_DEG);

        float speed = body.speed();
        float dv = desired - speed;
        float maxStep = (dv >= 0f) ? handling.accel() * dt : handling.brakingAccel() * dt;
        speed += clamp(dv, -maxStep, maxStep);
        speed = clamp(speed, 0f, handling.maxSpeed());

        if (squaringUp && speed <= handling.pivotSpeedCells()) {
            // Round on the brakes. Nothing rolls while it swings, which is what
            // makes lining up a thing that finishes before the roll starts.
            body.facingDegrees += clamp(alphaDeg, -handling.pivotRateDegPerSec() * dt,
                    handling.pivotRateDegPerSec() * dt);
            speed = 0f;
            steer = 0f;
        } else {
            body.facingDegrees += (float) Math.toDegrees(speed * curvature) * dt;
        }
        body.facingDegrees = wrap180(body.facingDegrees);

        // Where it points and nowhere else.
        float rad = (float) Math.toRadians(body.facingDegrees);
        body.vx = -(float) Math.sin(rad) * speed;
        body.vy = (float) Math.cos(rad) * speed;
        body.x += body.vx * dt;
        body.y += body.vy * dt;

        if (dt > 1e-6f) {
            body.ax = (body.vx - vx0) / dt;
            body.ay = (body.vy - vy0) / dt;
            body.angVelDegPerSec = wrap180(body.facingDegrees - facing0) / dt;
        }
        return steer;
    }

    /**
     * The shortest signed arc equivalent to {@code deg}, in {@code (-180, 180]}.
     *
     * <p>Written out rather than as the {@code (deg + 540) % 360 - 180} idiom
     * the flight steering uses, because that idiom is only correct for inputs
     * above {@code -540}: Java's float remainder keeps the sign of its left
     * operand, so a heading that has wound past a full turn comes back out
     * unwrapped. An aircraft on the ground swings its nose round on the spot
     * for as long as it takes and does exactly that winding — the first version
     * of this driver spun one in place beside its own runway for the rest of
     * the battle.
     */
    private static float wrap180(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d <= -180f) d += 360f;
        return d;
    }

    private static float slew(float from, float to, float step) {
        return from + clamp(to - from, -step, step);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
