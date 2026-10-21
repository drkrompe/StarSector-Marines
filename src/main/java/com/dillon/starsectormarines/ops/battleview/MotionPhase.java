package com.dillon.starsectormarines.ops.battleview;

/**
 * How far one piece of presentation machinery has run, in running seconds, on the render's animation clock.
 *
 * <p>Phase only advances while the machine runs, at the share of full speed it is running at, so a fan whose power
 * dies runs down where it stands rather than snapping to rest, and picks up from there. The clock is the frame's
 * {@link RenderContext#animationTimeSeconds}; a step is clamped so a camera that comes back to a prop after a long
 * look elsewhere does not spin it a hundred turns in one frame, and a second pass over the same frame advances
 * nothing. Shared by the fort's roof rotors and the deck's moving props.
 */
final class MotionPhase {
    private static final float MAX_STEP_SECONDS = 0.25f;

    private float phase;
    private float rate = Float.NaN;
    private float last = Float.NaN;

    MotionPhase(float startPhase) {
        this.phase = startPhase;
    }

    /**
     * Advance to {@code now}, running toward {@code target} (a share of full speed) and easing there over
     * {@code easeSeconds}; zero responds at once. The first observation starts the machine already at its target.
     *
     * @return the phase after this step
     */
    float advance(float now, float target, float easeSeconds) {
        float dt = Float.isNaN(last) || !Float.isFinite(now) ? 0f : Math.max(0f, Math.min(MAX_STEP_SECONDS, now - last));
        if (Float.isFinite(now)) last = now;
        if (Float.isNaN(rate) || easeSeconds <= 0f) rate = target;
        else {
            float step = dt / easeSeconds;
            rate = rate < target ? Math.min(target, rate + step) : Math.max(target, rate - step);
        }
        phase += rate * dt;
        return phase;
    }

    float phase() { return phase; }
    float rate() { return Float.isNaN(rate) ? 0f : rate; }
}
