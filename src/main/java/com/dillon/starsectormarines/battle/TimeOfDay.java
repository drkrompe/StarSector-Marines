package com.dillon.starsectormarines.battle;

/**
 * Ambient lighting state for the 2D battlefield. Consumed by the lightmap
 * multiply pass: the lightmap FBO clears each frame to {@code (ambientR,
 * ambientG, ambientB, 1)}, dynamic lights additively brighten regions, and
 * the FBO is multiply-blended over the world layer so darker ambients
 * darken the whole scene.
 *
 * <p>V1 is a hard-coded preset chosen at battle start. The {@link
 * #evaluateAt(float)} signature is the seam for a future animated cycle
 * (e.g. dawn over a 20-minute night raid) — once the curve lands, the
 * call site in {@code BattleScreen.render} stays unchanged and a long
 * battle becomes a diegetic clock for triggering reinforcements when the
 * sun comes up.
 *
 * <p>{@link #bypass} = true short-circuits the multiply pass entirely.
 * {@link #DAY} sets it because multiplying a fully-rendered scene by
 * (1, 1, 1) is a no-op that still costs an FBO clear + blit.
 */
public final class TimeOfDay {

    public static final TimeOfDay DAY   = new TimeOfDay(1.00f, 1.00f, 1.00f, true);
    public static final TimeOfDay DUSK  = new TimeOfDay(0.55f, 0.45f, 0.60f, false);
    public static final TimeOfDay NIGHT = new TimeOfDay(0.18f, 0.20f, 0.32f, false);

    public final float ambientR;
    public final float ambientG;
    public final float ambientB;
    public final boolean bypass;

    private TimeOfDay(float r, float g, float b, boolean bypass) {
        this.ambientR = r;
        this.ambientG = g;
        this.ambientB = b;
        this.bypass = bypass;
    }

    /**
     * V1 stub: returns {@code this} regardless of the clock. V2 will
     * interpolate between presets along a battle-time curve and return a
     * fresh per-frame snapshot. Call sites should always evaluate rather
     * than read the field directly, so the v2 switch is a no-op for them.
     */
    public TimeOfDay evaluateAt(float battleTimeSeconds) {
        return this;
    }
}
