package com.dillon.starsectormarines.battle.evacuation;

/**
 * The numbers and the pure decisions of a Matriarch's run ({@code colony-breach-nouns.md}, Matriarch): she walks,
 * and breaks into a run at infantry that lets her come close on open ground. Every number is a starting value to
 * feel out in play.
 *
 * <p>A run is the Brute's charge in her shape: a {@linkplain #TELL tell} the player can read, a run, and a
 * cooldown. Unlike a charge it is not a committed line: she keeps hunting her quarry at run pace, so a body
 * walking away from her inside the band is caught, and one already out of it is not.
 */
final class MatriarchRunRules {

    /** Her run against her walk: about 3.4 cells a second for an adult, against a Marine's 2. */
    static final float PACE = 4.8f;
    /** Quarry whose surface is further than this from her centre is walked toward, not run at. */
    static final float BAND = 8f;
    /** Seconds she stands crown-down and bellows before she goes: the player's warning. */
    static final float TELL = 0.6f;
    /** Seconds a run lasts before she falls back to a walk. */
    static final float RUN = 5f;
    /** Seconds from the end of one run to the next. */
    static final float COOLDOWN = 12f;
    /**
     * Running, she closes to this before she reaps rather than to her walking reach: a reap she began at the edge
     * of her reach would land on a body that had walked out of it in the wind-up.
     */
    static final float STRIKE_GAP = 1.5f;

    private MatriarchRunRules() {}

    /**
     * Whether she breaks into a run at quarry {@code gap} cells from her (centre to its surface): a body on foot,
     * which could otherwise walk away from her, inside the band but past her reach, over open ground, and her last
     * run's cooldown spent. A hull or a mech is walked down: nothing heavier outpaces her walk by much.
     */
    static boolean shouldRun(float gap, float reach, boolean onFoot, boolean lineOpen, int now, int readyTick) {
        return onFoot && lineOpen && gap > reach && gap <= BAND && now >= readyTick;
    }

    /**
     * Whether a run that has lasted {@code seconds} is over: it ran its time, its quarry is gone or another, or
     * she has closed to strike.
     */
    static boolean runOver(float seconds, boolean sameQuarry, float gap) {
        return seconds >= RUN || !sameQuarry || gap <= STRIKE_GAP;
    }

    /** The reach at which she strikes: her walking reach, or closer while she runs. */
    static float strikeGap(boolean running, float reach) {
        return running ? Math.min(reach, STRIKE_GAP) : reach;
    }
}
