package com.dillon.starsectormarines.battle.combat;

import java.util.Random;

/**
 * Samples one committed aim result in the plane perpendicular to a shot's
 * ground direction. Lateral error becomes real XY divergence; elevation error
 * becomes the lightweight Z axis used by {@link BallisticResolver}.
 */
final class TargetPlaneAim {

    /** Extra clearance beyond the target silhouette for an authored miss. */
    static final float MISS_CLEARANCE_MIN = 0.20f;
    static final float MISS_CLEARANCE_MAX = 0.80f;
    private static final float AXIS_EPSILON = 1e-6f;

    record Sample(boolean onTarget, float lateral, float elevation) {}

    private TargetPlaneAim() {}

    static Sample sample(float finalAccuracy, float incomingAccuracyMult,
                         float effectiveSpread, float horizontalRadius,
                         float verticalHalfHeight, Random rng) {
        float chance = clamp01(finalAccuracy * incomingAccuracyMult);
        boolean onTarget = rng.nextFloat() < chance;
        float horizontal = Math.max(AXIS_EPSILON, horizontalRadius);
        float vertical = Math.max(AXIS_EPSILON, verticalHalfHeight);

        if (onTarget) {
            float jitter = ShotEndpoint.HIT_JITTER_BASELINE + Math.max(0f, effectiveSpread);
            float lateralMax = Math.min(horizontal * 0.90f, jitter);
            float elevationMax = Math.min(vertical * 0.90f, jitter);
            float lateral = signed(rng.nextFloat()) * lateralMax;
            float elevation = signed(rng.nextFloat()) * elevationMax;
            return new Sample(true, lateral, elevation);
        }

        float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
        float lateralDir = (float) Math.cos(angle);
        float elevationDir = (float) Math.sin(angle);
        float toHorizontalEdge = Math.abs(lateralDir) > AXIS_EPSILON
                ? horizontal / Math.abs(lateralDir)
                : Float.POSITIVE_INFINITY;
        float toVerticalEdge = Math.abs(elevationDir) > AXIS_EPSILON
                ? vertical / Math.abs(elevationDir)
                : Float.POSITIVE_INFINITY;
        float boundaryDistance = Math.min(toHorizontalEdge, toVerticalEdge);
        float clearance = MISS_CLEARANCE_MIN
                + rng.nextFloat() * (MISS_CLEARANCE_MAX - MISS_CLEARANCE_MIN)
                + Math.max(0f, effectiveSpread);
        float distance = boundaryDistance + clearance;
        return new Sample(false, lateralDir * distance, elevationDir * distance);
    }

    private static float signed(float unit) {
        return unit * 2f - 1f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
