package com.dillon.starsectormarines.battle.story;

/** Finite depot defense clock. Capture is physical presence, never proximity to a road stage. */
public final class DepotHoldProgress {
    public static final float CAPTURE_SECONDS = 8f;
    public static final float FIRST_WAVE_SECONDS = 18f;
    public static final float SECOND_WAVE_SECONDS = 58f;
    private float capture, elapsed;
    private boolean committed, complete;
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
        boolean occupying = committed ? friendlyInside : companyInside;
        capture = occupying && !enemyInside ? Math.min(CAPTURE_SECONDS, capture + dt) : 0f;
        if (!committed && capture + .001f >= CAPTURE_SECONDS) {
            committed = true;
            return;
        }
        if (!committed) return;
        elapsed += dt;
        if (elapsed >= SECOND_WAVE_SECONDS) waves = 2;
        else if (elapsed >= FIRST_WAVE_SECONDS) waves = 1;
        // The sequence supplies pending arrivals as well as live opposition.
        complete = waves == 2 && remainingRaiders == 0 && capture + .001f >= CAPTURE_SECONDS;
    }
    public boolean committed() { return committed; }
    public boolean complete() { return complete; }
    public int waves() { return waves; }
    public float captureFraction() { return capture / CAPTURE_SECONDS; }
    public float elapsed() { return elapsed; }
    public float nextWaveSeconds() {
        return Math.max(0f, (waves == 0 ? FIRST_WAVE_SECONDS : SECOND_WAVE_SECONDS) - elapsed);
    }
}
