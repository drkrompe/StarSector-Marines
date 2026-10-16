package com.dillon.starsectormarines.cinematic;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;

/**
 * Notices the docking dialog at Jangala and starts the arrival over it. Waits a frame for the dialog
 * to finish populating, and does nothing once the arrival has played, so it can stay installed.
 */
public final class ArrivalCinematicPresenter implements EveryFrameScript {
    private static final int SETTLE_FRAMES = 3;
    private int seenFrames;
    private boolean started;

    public static void install() {
        var sector = Global.getSector();
        if (sector == null) return;
        sector.removeTransientScriptsOfClass(ArrivalCinematicPresenter.class);
        sector.addTransientScript(new ArrivalCinematicPresenter());
    }

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return true; }

    @Override public void advance(float amount) {
        var sector = Global.getSector();
        if (sector == null || Global.getCurrentState() != GameState.CAMPAIGN) return;
        var ui = sector.getCampaignUI();
        var dialog = ui == null ? null : ui.getCurrentInteractionDialog();
        if (dialog == null || !ArrivalCinematic.due(dialog)) {
            seenFrames = 0;
            started = false;
            return;
        }
        if (started || ++seenFrames < SETTLE_FRAMES) return;
        // A refused display leaves the arrival unplayed; the yard flow carries on as normal.
        started = true;
        ArrivalCinematic.play(dialog, () -> { });
    }
}
