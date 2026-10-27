package com.dillon.starsectormarines.battle.story;

/**
 * Finite depot defense clock. Capture is physical presence, never proximity to a road stage.
 *
 * <p>The road attacks answer the company's first step into dispatch, not a finished capture: the first wave is
 * committed on that tick and the final one {@link #SECOND_WAVE_SECONDS} later, so the company takes the warehouse with
 * the raiders already on the roads. Militia inside alone do not start it.
 */
public final class DepotHoldProgress {
    public static final float CAPTURE_SECONDS = 8f;
    /** Seconds from the company's first entry to the first road wave. */
    public static final float FIRST_WAVE_SECONDS = 0f;
    /** Seconds from the company's first entry to the final road wave. */
    public static final float SECOND_WAVE_SECONDS = 40f;
    private float capture, elapsed;
    private boolean alerted, committed, complete;
    private int waves;
    private boolean companyInside, friendlyInside, enemyInside;
    public boolean companyInside() { return companyInside; }
    public boolean friendlyInside() { return friendlyInside; }
    public boolean enemyInside() { return enemyInside; }

    public void advance(float dt, boolean companyInside, boolean friendlyInside,
                        boolean enemyInside, int remainingRaiders) {
        if (complete) return;
        this.companyInside = companyInside;
        this.friendlyInside = friendlyInside;
        this.enemyInside = enemyInside;
        alerted |= companyInside;
        if (alerted) {
            if (waves > 0) elapsed += dt;
            if (elapsed >= SECOND_WAVE_SECONDS) waves = 2;
            else if (elapsed >= FIRST_WAVE_SECONDS) waves = Math.max(waves, 1);
        }
        boolean occupying = committed ? friendlyInside : companyInside;
        capture = occupying && !enemyInside ? Math.min(CAPTURE_SECONDS, capture + dt) : 0f;
        if (!committed && capture + .001f >= CAPTURE_SECONDS) {
            committed = true;
            return;
        }
        if (!committed) return;
        // The sequence supplies pending arrivals as well as live opposition.
        complete = waves == 2 && remainingRaiders == 0 && capture + .001f >= CAPTURE_SECONDS;
    }
    /** Whether the company has set foot in dispatch, which commits the road attacks. */
    public boolean alerted() { return alerted; }
    public boolean committed() { return committed; }
    public boolean complete() { return complete; }
    public int waves() { return waves; }
    public float captureFraction() { return capture / CAPTURE_SECONDS; }
    /** Seconds since the first road wave was committed. */
    public float elapsed() { return elapsed; }
    public float nextWaveSeconds() {
        return Math.max(0f, (waves == 0 ? FIRST_WAVE_SECONDS : SECOND_WAVE_SECONDS) - elapsed);
    }
}
