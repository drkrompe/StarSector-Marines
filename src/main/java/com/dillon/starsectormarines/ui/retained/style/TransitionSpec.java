package com.dillon.starsectormarines.ui.retained.style;

/** One entry in a CSS {@code transition} list. */
public record TransitionSpec(StyleProperty property, float durationSeconds,
                             Easing easing, float delaySeconds) {

    public TransitionSpec {
        if (!property.animatable()) {
            throw new IllegalArgumentException(property.cssName() + " is not animatable");
        }
        requireSeconds("duration", durationSeconds);
        requireSeconds("delay", delaySeconds);
    }

    private static void requireSeconds(String label, float seconds) {
        if (!Float.isFinite(seconds) || seconds < 0f) {
            throw new IllegalArgumentException("Transition " + label
                    + " must be finite and non-negative: " + seconds);
        }
    }
}
