package com.dillon.starsectormarines.cinematic;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;

/**
 * Notices the docking dialog at Asharu once the recovery arc opens and plays the wreckfall recording
 * over it, then puts the port back so the player carries on to the Asharu dialog and its board.
 * Waits a few frames for the dialog to finish populating, and does nothing once the film has played,
 * so it can stay installed.
 */
public final class AsharuBattleCinematicPresenter implements EveryFrameScript {
    private static final int SETTLE_FRAMES = 3;
    private int seenFrames;
    /** The dialog already judged, so the due test runs once per docking. */
    private InteractionDialogAPI judged;
    /** The dialog settling now, whose frames {@link #seenFrames} counts. */
    private InteractionDialogAPI settling;

    public static void install() {
        var sector = Global.getSector();
        if (sector == null) return;
        CinematicFilm.clearShowing();
        sector.removeTransientScriptsOfClass(AsharuBattleCinematicPresenter.class);
        sector.addTransientScript(new AsharuBattleCinematicPresenter());
    }

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return true; }

    @Override public void advance(float amount) {
        var sector = Global.getSector();
        if (sector == null || Global.getCurrentState() != GameState.CAMPAIGN) return;
        var ui = sector.getCampaignUI();
        var dialog = ui == null ? null : ui.getCurrentInteractionDialog();
        if (dialog == null) {
            judged = settling = null;
            return;
        }
        if (dialog == judged) return;
        if (dialog != settling) {
            settling = dialog;
            seenFrames = 0;
        }
        // The due test reads the film's files, so it is asked once the dialog has settled, not every frame.
        if (CinematicFilm.anyShowing() || ++seenFrames < SETTLE_FRAMES) return;
        judged = dialog;
        if (!AsharuBattleCinematic.due(dialog)) return;
        // A refused display (no shot installed) leaves the dialog as it was.
        AsharuBattleCinematic.FILM.play(dialog, () -> showPort(dialog));
    }

    private static void showPort(InteractionDialogAPI dialog) {
        dialog.showTextPanel();
        dialog.showVisualPanel();
    }
}
