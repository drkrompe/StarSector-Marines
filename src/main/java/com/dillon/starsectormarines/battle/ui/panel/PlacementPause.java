package com.dillon.starsectormarines.battle.ui.panel;

/**
 * Holds the battle paused while an armor-drop picker is open, and gives back the pause state
 * it found: a battle that was running resumes, one the player had already paused stays
 * paused.
 */
public final class PlacementPause {

    /** The battle clock as the panel sees it. */
    public interface Clock {
        boolean paused();

        void setPaused(boolean paused);
    }

    private final Clock clock;
    private boolean holding;
    private boolean wasPaused;

    public PlacementPause(Clock clock) {
        this.clock = clock;
    }

    /** Pauses the battle, remembering whether it already was; a second hold changes nothing. */
    public void hold() {
        if (holding || clock == null) return;
        holding = true;
        wasPaused = clock.paused();
        if (!wasPaused) clock.setPaused(true);
    }

    /** Ends a hold: resumes the battle only if the hold is what paused it. */
    public void release() {
        if (!holding) return;
        holding = false;
        if (!wasPaused && clock.paused()) clock.setPaused(false);
    }

    public boolean holding() {
        return holding;
    }
}
