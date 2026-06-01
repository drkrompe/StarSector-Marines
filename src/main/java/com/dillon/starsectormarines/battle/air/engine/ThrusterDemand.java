package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirHandling;

/**
 * Per-thruster "how hard is this nozzle working" weighting for the engine-FX
 * pass. Reads an {@link AirBody}'s realized dynamics ({@code vx/vy}, the Δv/dt
 * acceleration {@code ax/ay}, and {@code angVelDegPerSec}) plus the entity's
 * {@link AirHandling} reference scalars, and returns a {@code [0,1]} demand per
 * engine slot. {@link EngineFxRenderer} multiplies each plume's intensity by
 * {@code FLOOR + (1-FLOOR)·demand}, so the thrusters actually pushing /
 * spinning the hull glow large and the idle ones shrink.
 *
 * <h2>Why this works geometrically</h2>
 * <p>Each {@link EngineSlotData} carries the direction its plume <em>exhausts</em>
 * ({@code angleDegrees}); the force the thruster exerts on the hull is the
 * opposite vector. Its {@code (localX, localY)} is — since the air stack anchors
 * every entity at its centroid of gravity
 * ({@code HullPivotResolver}) — the lever arm about the pivot. That's all a
 * dot-product (translation) and a cross-product (torque) need.
 *
 * <h2>The two terms</h2>
 * <ul>
 *   <li><b>Linear</b> — the thrust axis dotted with velocity (a coast-sustain so
 *       cruising mains stay lit) and with acceleration (the bloom when the pilot
 *       leans on the throttle), each scaled by its magnitude vs the handling
 *       reference. Braking flips the accel direction aft, so forward mains dim.</li>
 *   <li><b>Rotational</b> — the slot's torque {@code r × thrust} (z-component),
 *       normalized by lever arm to a tangential fraction in {@code [-1,1]}, gated
 *       on agreeing with the sign of the body's realized turn rate. In a bank the
 *       maneuvering side flares and its mirror partner goes quiet.</li>
 * </ul>
 * The two are combined with {@code max} (a thruster is "on" if it helps either
 * job) and clamped to {@code [0,1]}.
 *
 * <p>Pure — no {@code Global}, no rendering — so the offline {@code ThrusterDemandTest}
 * exercises the exact in-game math. The torque is computed in the slot-local
 * frame: both {@code r} and the thrust vector rotate identically into world, so
 * the cross-product's sign/magnitude is rotation-invariant and needs no facing.
 */
public final class ThrusterDemand {

    /** Coast-sustain weight: how much current velocity (vs accel) keeps aligned mains lit. */
    private static final float W_VEL = 0.6f;
    /** Acceleration-bloom weight: the "stand on the throttle" contribution. Dominant by design — this is the "intended velocity change" read. */
    private static final float W_ACCEL = 1.0f;

    private static final float[] EMPTY = new float[0];
    private static final float EPS = 1e-4f;

    private ThrusterDemand() {}

    /**
     * Returns a {@code [0,1]} demand per slot (array aligned with {@code slots}).
     * Empty array for a null/empty slot list. Never null.
     *
     * @param slots    the entity's resolved engine slots (local frame)
     * @param body     the live kinematic state (read-only)
     * @param handling reference scalars — {@code maxSpeed}, {@code accel},
     *                 {@code maxTurnRateDegPerSec} normalize the magnitudes
     */
    public static float[] compute(EngineSlotData[] slots, AirBody body, AirHandling handling) {
        if (slots == null || slots.length == 0 || body == null || handling == null) return EMPTY;

        float vx = body.vx, vy = body.vy;
        float ax = body.ax, ay = body.ay;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        float accelMag = (float) Math.sqrt(ax * ax + ay * ay);

        float maxSpeed = Math.max(EPS, handling.maxSpeed());
        float accelRef = Math.max(EPS, handling.accel());
        float turnRef = Math.max(EPS, handling.maxTurnRateDegPerSec());

        float speedMag01 = Math.min(1f, speed / maxSpeed);
        float accelMag01 = Math.min(1f, accelMag / accelRef);
        float vdx = speed > EPS ? vx / speed : 0f;
        float vdy = speed > EPS ? vy / speed : 0f;
        float adx = accelMag > EPS ? ax / accelMag : 0f;
        float ady = accelMag > EPS ? ay / accelMag : 0f;

        float angVel = body.angVelDegPerSec;
        float rotSign = Math.signum(angVel);
        float rotMag01 = Math.min(1f, Math.abs(angVel) / turnRef);

        float rad = (float) Math.toRadians(body.facingDegrees);
        float fcos = (float) Math.cos(rad);
        float fsin = (float) Math.sin(rad);

        float[] out = new float[slots.length];
        for (int i = 0; i < slots.length; i++) {
            EngineSlotData es = slots[i];

            // Thrust = -exhaust. Exhaust local dir for plume angle p is
            // (-sin p, cos p) (same as the renderer's plumeDX/DY); negate it.
            double plumeRad = Math.toRadians(es.angleDegrees);
            float tlx =  (float) Math.sin(plumeRad);
            float tly = -(float) Math.cos(plumeRad);

            // Linear term: rotate thrust into world (same R the renderer uses
            // for offsets), dot with velocity dir and accel dir.
            float twx = tlx * fcos - tly * fsin;
            float twy = tlx * fsin + tly * fcos;
            float velAlign = Math.max(0f, twx * vdx + twy * vdy);
            float accAlign = Math.max(0f, twx * adx + twy * ady);
            float linear = W_VEL * velAlign * speedMag01 + W_ACCEL * accAlign * accelMag01;

            // Rotational term: torque r × thrust (local frame), normalized by
            // lever to a tangential fraction, gated on matching the turn sign.
            float rotational = 0f;
            float lever = (float) Math.sqrt(es.localX * es.localX + es.localY * es.localY);
            if (lever > EPS && rotSign != 0f) {
                float torque = es.localX * tly - es.localY * tlx;
                float tangential = torque / lever;
                rotational = Math.max(0f, rotSign * tangential) * rotMag01;
            }

            out[i] = Math.min(1f, Math.max(linear, rotational));
        }
        return out;
    }
}
