package com.dillon.starsectormarines.ops.battleview;

/**
 * What the loading screen reads: how far a battle's loading is and which stage it is in.
 *
 * <p>A stage names a phase of the loader ("Loading sprites"), never what is being loaded: the
 * art a battle loads is what the player is about to face, and the owner does not want the
 * loading screen to give that away.
 */
public interface LoadingProgress {

    int stepsDone();

    int stepCount();

    /** The stage the next step belongs to, from the fixed set every battle uses. */
    String stage();

    /** Share done, 0 to 1; nothing to do is complete. */
    default float fraction() {
        int total = stepCount();
        return total <= 0 ? 1f : Math.min(1f, (float) stepsDone() / total);
    }
}
