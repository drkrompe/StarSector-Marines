package com.dillon.starsectormarines.battle.appearance;

/**
 * How a Rav body's spine gives through a turn, shared by every rig that bends one (the Runner, the Brute and the
 * Harrier): the back bows in proportion to how fast the body is turning, up to a limit, and takes the bend up over
 * a short lag, so a sharp turn throws a curve into the body and a straight run lets it out again.
 * Presentation only.
 */
public final class SpineBend {

    private SpineBend() {}

    /** Turn rate from one facing to the next, degrees a second, the short way round; positive turns left. */
    public static float yawRate(float fromDegrees, float toDegrees, float dt) {
        return (((toDegrees - fromDegrees) % 360f + 540f) % 360f - 180f) / dt;
    }

    /**
     * The bend after one step: eased toward {@code yawRate * perYawRate}, clamped to {@code max}, over
     * {@code seconds}. Positive bows the body into a left turn.
     */
    public static float ease(float bend, float yawRate, float perYawRate, float max, float seconds, float dt) {
        float target = Math.max(-max, Math.min(max, yawRate * perYawRate));
        return bend + (target - bend) * Math.min(1f, dt / seconds);
    }
}
