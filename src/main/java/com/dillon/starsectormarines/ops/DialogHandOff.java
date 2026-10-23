package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import org.apache.log4j.Logger;

/**
 * Opens the next full-canvas screen of a chained flow over the same port dialog,
 * a few frames after the previous one was dismissed.
 *
 * <p>The opening is one walk from the Jangala arrival through the yard into
 * Marine operations, and each of those is a custom visual dialog over the planet
 * menu. Opening the next one from inside the last one's dismissal callback would
 * ask the engine to show a dialog while it is still tearing one down, so the
 * step is taken on a later frame instead. The port menu stays hidden meanwhile,
 * so the player never sees it flash between two screens.
 *
 * <p>If the step cannot be taken (the dialog is gone, or the step throws), the
 * fallback runs instead, which puts the port menu back so the player is never
 * left in a dialog with nothing on it.
 */
public final class DialogHandOff implements EveryFrameScript {
    private static final int SETTLE_FRAMES = 2;
    private static final Logger LOG = Global.getLogger(DialogHandOff.class);

    private final InteractionDialogAPI dialog;
    private final Runnable step;
    private final Runnable fallback;
    private int frames;
    private boolean done;

    private DialogHandOff(InteractionDialogAPI dialog, Runnable step, Runnable fallback) {
        this.dialog = dialog;
        this.step = step;
        this.fallback = fallback;
    }

    public static void next(InteractionDialogAPI dialog, Runnable step, Runnable fallback) {
        if (Global.getSector() == null) {
            fallback.run();
            return;
        }
        Global.getSector().addTransientScript(new DialogHandOff(dialog, step, fallback));
    }

    @Override public boolean isDone() { return done; }
    @Override public boolean runWhilePaused() { return true; }

    @Override public void advance(float amount) {
        if (done || ++frames < SETTLE_FRAMES) return;
        done = true;
        if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != dialog) return;
        try {
            step.run();
        } catch (RuntimeException failure) {
            LOG.warn("Chained screen could not open; returning to port", failure);
            fallback.run();
        }
    }
}
