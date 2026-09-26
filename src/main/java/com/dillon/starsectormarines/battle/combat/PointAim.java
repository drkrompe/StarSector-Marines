package com.dillon.starsectormarines.battle.combat;

import java.util.Random;

/**
 * Target-free angular error for a manually aimed ground round. Accuracy changes
 * the size of a uniform error disk, never an invisible hit/miss result. Its
 * radius is measured at weapon range, so moving the cursor closer along the
 * same bearing cannot amplify or eliminate the supplied dispersion.
 */
final class PointAim {

    /** Retains the small organic variance of ordinary accurate direct fire. */
    static final float BASE_ERROR_CELLS = 0.20f;
    /** Additional error at weapon range for an accuracy of zero. */
    static final float ACCURACY_ERROR_CELLS = 2f;

    record Sample(float lateralSlope, float elevationSlope) { }

    private PointAim() { }

    static Sample sample(float accuracy, float effectiveSpread,
                         float maximumTargetingRange, Random rng) {
        if (!Float.isFinite(accuracy) || !Float.isFinite(effectiveSpread)
                || !(maximumTargetingRange > 0f) || !Float.isFinite(maximumTargetingRange)) {
            throw new IllegalArgumentException("Point aim parameters must be finite with positive range");
        }
        float clampedAccuracy = Math.max(0f, Math.min(1f, accuracy));
        float errorRadius = BASE_ERROR_CELLS + Math.max(0f, effectiveSpread)
                + ACCURACY_ERROR_CELLS * (1f - clampedAccuracy);
        float slope = errorRadius / maximumTargetingRange;
        float radius = (float) Math.sqrt(rng.nextFloat()) * slope;
        float angle = rng.nextFloat() * (float) (Math.PI * 2.0);
        return new Sample(radius * (float) Math.cos(angle),
                radius * (float) Math.sin(angle));
    }
}
