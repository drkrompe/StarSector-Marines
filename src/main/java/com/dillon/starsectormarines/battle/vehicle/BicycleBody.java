package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * Kinematic bicycle model — front wheel steers, rear "wheel" follows.
 * Position is the geometric center of the vehicle; steering angle δ is
 * carried as state and slewed toward a pure-pursuit-derived desired angle
 * each tick so steering reads smoothly instead of snapping. This is the
 * standard 2D car-like vehicle model used in robotics and racing-game AI.
 *
 * <p>Dynamics per tick:
 * <ul>
 *   <li>Heading error α to carrot → desired steering
 *       {@code δ_d = atan2(2·L·sin(α), L_d)}, clamped to {@code maxSteering}.
 *       This is the pure-pursuit steering law: a bicycle with steering δ
 *       traces an arc through the carrot ahead of it.</li>
 *   <li>Slew current steering δ toward δ_d at {@code steeringSlewDegPerSec}
 *       — driver wheel-spin limit.</li>
 *   <li>Speed ramps toward {@code targetSpeed} at {@code accel} (or
 *       {@code brakingAccel} when slowing).</li>
 *   <li>Heading update: {@code θ' = (v/L)·tan(δ)}. Positive δ rotates CCW,
 *       matching our facing convention (0° = +Y, positive CCW).</li>
 *   <li>Position integrated along the new facing.</li>
 * </ul>
 *
 * <p>The minimum turn radius is set entirely by
 * {@code L / tan(maxSteering)} — speed does not change it. Slowing down does
 * <em>not</em> let the truck make a tighter turn; this is the source of the
 * "trucks have to commit to corners" feel. Pure pursuit prevents the
 * orbit-around-waypoint failure mode of "rotate-then-thrust" controllers
 * because the carrot keeps moving forward along the polyline, so the body
 * never tries to circle a stationary point.
 */
public class BicycleBody extends GroundBody {

    private final float wheelbaseCells;
    private final float maxSteeringRad;
    private final float steeringSlewRadPerSec;
    private final float accel;
    private final float brakingAccel;
    private final float maxSpeed;

    /** Current steering angle, radians; positive = left turn. Slewed toward desired each tick. */
    private float steeringRad;

    public BicycleBody(float wheelbaseCells,
                       float maxSteeringDeg, float steeringSlewDegPerSec,
                       float accel, float brakingAccel, float maxSpeed) {
        this.wheelbaseCells = wheelbaseCells;
        this.maxSteeringRad = (float) Math.toRadians(maxSteeringDeg);
        this.steeringSlewRadPerSec = (float) Math.toRadians(steeringSlewDegPerSec);
        this.accel = accel;
        this.brakingAccel = brakingAccel;
        this.maxSpeed = maxSpeed;
    }

    @Override
    protected void onTeleport() {
        steeringRad = 0f;
    }

    @Override
    public void tick(float carrotX, float carrotY, float targetSpeed, float dt) {
        float dx = carrotX - x;
        float dy = carrotY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        boolean reversing = targetSpeed < 0f;

        // Pure-pursuit steering law. α is the carrot's bearing relative to
        // current facing, wrapped to (-180°, 180°]; α>0 means carrot is to
        // the left (CCW), so desired steering is positive (left turn).
        //
        // In reverse the heading equation θ'=(v/L)·tan(δ) inverts the
        // heading response (negative v flips the sign), so we negate the
        // desired steering — this swings the heading TOWARD the carrot
        // while backing away from it, producing 3-point turn behavior.
        float desiredSteering;
        if (dist > 1e-4f) {
            float carrotFacingDeg = AirBody.facingToward(dx, dy);
            float alphaDeg = ((carrotFacingDeg - facingDegrees + 540f) % 360f) - 180f;
            float alphaRad = (float) Math.toRadians(alphaDeg);
            float numer = 2f * wheelbaseCells * (float) Math.sin(alphaRad);
            desiredSteering = (float) Math.atan2(numer, dist);
            if (reversing) desiredSteering = -desiredSteering;
            if (desiredSteering >  maxSteeringRad) desiredSteering =  maxSteeringRad;
            if (desiredSteering < -maxSteeringRad) desiredSteering = -maxSteeringRad;
        } else {
            desiredSteering = 0f;
        }

        // Slew steering toward desired — bounded driver wheel-spin rate.
        float steeringStep = steeringSlewRadPerSec * dt;
        float dSteer = desiredSteering - steeringRad;
        if (dSteer >  steeringStep) dSteer =  steeringStep;
        if (dSteer < -steeringStep) dSteer = -steeringStep;
        steeringRad += dSteer;

        // Adjust speed toward targetSpeed. Reverse is capped at half forward max.
        float maxReverse = maxSpeed * 0.5f;
        float clampedTarget = Math.max(-maxReverse, Math.min(maxSpeed, targetSpeed));
        float dv = clampedTarget - speed;
        float speedStep = (dv >= 0f) ? accel * dt : brakingAccel * dt;
        if (dv >  speedStep) dv =  speedStep;
        if (dv < -speedStep) dv = -speedStep;
        speed += dv;
        if (speed >  maxSpeed) speed = maxSpeed;
        if (speed < -maxReverse) speed = -maxReverse;

        // Bicycle heading update: θ' = (v / L) · tan(δ). Our convention
        // (0°=+Y, positive CCW) shares CCW direction with the math frame, so
        // positive steering increases facingDegrees. With negative v (reverse),
        // the heading change inverts naturally — no special-casing needed.
        float headingDotRad = (speed / wheelbaseCells) * (float) Math.tan(steeringRad);
        facingDegrees += (float) Math.toDegrees(headingDotRad) * dt;
        // Keep facing in a sane range so floats don't wander after many ticks.
        if (facingDegrees >  360f) facingDegrees -= 360f;
        if (facingDegrees < -360f) facingDegrees += 360f;

        // Integrate position along the new facing.
        float rad = (float) Math.toRadians(facingDegrees);
        float fx = -(float) Math.sin(rad);
        float fy =  (float) Math.cos(rad);
        x += fx * speed * dt;
        y += fy * speed * dt;
    }

    /** Read-only access to current steering angle (radians). Useful for debug overlays. */
    public float getSteeringRad() { return steeringRad; }

    public float getWheelbaseCells() { return wheelbaseCells; }
    public float getMaxSteeringRad() { return maxSteeringRad; }

    /**
     * Minimum turn radius for this bicycle, cells. Derived from the bicycle
     * model: {@code R_min = L / tan(δ_max)}. Used by the Reeds-Shepp solver
     * as its unit-radius scale factor when computing docking paths.
     */
    public float minTurnRadiusCells() {
        return wheelbaseCells / (float) Math.tan(maxSteeringRad);
    }
}
