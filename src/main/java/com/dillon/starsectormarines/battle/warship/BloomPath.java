package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * The flight of one submunition from the split point to its aim point, as a
 * closed-form function of time: a quadratic curve that leaves the split along
 * its own outward bearing and bends onto the aim point, with a small weave
 * across it. Position, speed and heading are evaluated, never integrated, so
 * the renderer, point defence and the landing read one clock, and both ends are
 * exactly the committed ones (the weave is zero at both).
 *
 * <p>The warhead leaves quickly and is brought on faster toward the end, which
 * is the look of a sheaf blooming out and then diving: progress along the curve
 * is {@code 0.4 u + 0.6 u^2} in the share {@code u} of the flight elapsed.
 */
public final class BloomPath {

    private static final float EASE_LINEAR = 0.4f;
    private static final float EASE_QUADRATIC = 0.6f;
    private static final float DT = 0.02f;

    private final float fromX;
    private final float fromY;
    private final float controlX;
    private final float controlY;
    private final float toX;
    private final float toY;
    private final float seconds;
    private final float weaveAmplitude;
    private final float weaveHz;
    private final float weavePhase;
    private final float chordX;
    private final float chordY;

    private BloomPath(float fromX, float fromY, float controlX, float controlY, float toX, float toY,
                      float seconds, float weaveAmplitude, float weaveHz, float weavePhase) {
        this.fromX = fromX;
        this.fromY = fromY;
        this.controlX = controlX;
        this.controlY = controlY;
        this.toX = toX;
        this.toY = toY;
        this.seconds = Math.max(seconds, 0.05f);
        this.weaveAmplitude = weaveAmplitude;
        this.weaveHz = weaveHz;
        this.weavePhase = weavePhase;
        float dx = toX - fromX;
        float dy = toY - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        this.chordX = len > 1e-5f ? -dy / len : 0f;
        this.chordY = len > 1e-5f ? dx / len : 0f;
    }

    /**
     * A flight from {@code (fromX, fromY)} to {@code (toX, toY)} taking {@code seconds}.
     *
     * @param outwardRadians the bearing, in radians from the +X axis, the warhead leaves the split on
     * @param loop           how far out it goes before bending back, as a share of the chord; zero is a
     *                       straight line
     * @param weave          the weave amplitude as a share of the chord; zero for none
     */
    public static BloomPath plan(long seed, float fromX, float fromY, float toX, float toY, float seconds,
                                 float outwardRadians, float loop, float weave) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float chord = (float) Math.sqrt(dx * dx + dy * dy);
        float cx;
        float cy;
        if (loop <= 0f) {
            cx = (fromX + toX) * 0.5f;
            cy = (fromY + toY) * 0.5f;
        } else {
            float reach = chord * loop * 2f;
            cx = fromX + (float) Math.cos(outwardRadians) * reach;
            cy = fromY + (float) Math.sin(outwardRadians) * reach;
        }
        float amplitude = weave * chord;
        float hz = 0.9f + 0.9f * MissilePath.unit(seed, 11);
        float phase = MissilePath.unit(seed, 12) * 2f * (float) Math.PI;
        return new BloomPath(fromX, fromY, cx, cy, toX, toY, seconds, amplitude, hz, phase);
    }

    private float progress(float t) {
        float u = Math.max(0f, Math.min(1f, t / seconds));
        return EASE_LINEAR * u + EASE_QUADRATIC * u * u;
    }

    private float curveX(float s) {
        float q = 1f - s;
        return q * q * fromX + 2f * q * s * controlX + s * s * toX;
    }

    private float curveY(float s) {
        float q = 1f - s;
        return q * q * fromY + 2f * q * s * controlY + s * s * toY;
    }

    private float lateral(float t) {
        float u = Math.max(0f, Math.min(1f, t / seconds));
        return weaveAmplitude * (float) Math.sin(Math.PI * u)
                * (float) Math.sin(2.0 * Math.PI * weaveHz * t + weavePhase);
    }

    /** World X at time {@code t}. */
    public float x(float t) {
        return curveX(progress(t)) + chordX * lateral(t);
    }

    /** World Y at time {@code t}. */
    public float y(float t) {
        return curveY(progress(t)) + chordY * lateral(t);
    }

    /** Seconds from the split to the landing. */
    public float totalSeconds() {
        return seconds;
    }

    /** Where it lands, X. */
    public float aimX() {
        return toX;
    }

    /** Where it lands, Y. */
    public float aimY() {
        return toY;
    }

    /** Cells per second at time {@code t}, by a short central difference; zero outside the flight. */
    public float speed(float t) {
        if (t < 0f || t >= seconds) return 0f;
        float a = Math.max(0f, t - DT);
        float b = Math.min(seconds, t + DT);
        float dx = x(b) - x(a);
        float dy = y(b) - y(a);
        return (float) Math.sqrt(dx * dx + dy * dy) / Math.max(1e-4f, b - a);
    }

    /** The direction the nose points at time {@code t}: the path's tangent, in the hull-facing convention. */
    public float headingDegrees(float t) {
        float a = Math.max(0f, t - DT);
        float b = Math.min(seconds, t + DT);
        if (b - a < 1e-4f) a = Math.max(0f, b - DT);
        float dx = x(b) - x(a);
        float dy = y(b) - y(a);
        if (dx * dx + dy * dy < 1e-10f) return AirBody.facingToward(toX - fromX, toY - fromY);
        return AirBody.facingToward(dx, dy);
    }
}
