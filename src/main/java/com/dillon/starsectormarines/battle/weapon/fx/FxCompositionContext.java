package com.dillon.starsectormarines.battle.weapon.fx;

/**
 * World-space inputs to deterministic effect composition. {@code seedTimeSeconds}
 * is the event's stable presentation timestamp, not the current render time.
 */
public record FxCompositionContext(
        float x,
        float y,
        float bearingDegrees,
        boolean wallImpact,
        float seedTimeSeconds) {

    public FxCompositionContext {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(bearingDegrees)
                || !Float.isFinite(seedTimeSeconds)) {
            throw new IllegalArgumentException("FX composition context values must be finite");
        }
    }
}
