package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

/**
 * Delegate for the event popup's {@link InteractionDialogAPI#showCustomVisualDialog}.
 *
 * <p>Same variant choice as the Marine Ops takeover: {@code CustomVisualDialogDelegate}
 * does not force confirm/cancel buttons into the chrome, so the card owns all three of
 * its options and no stray Esc/G press can answer a campaign decision by accident.
 */
public final class PlayerEventDialogDelegate implements CustomVisualDialogDelegate {

    private final PlayerEventCard card;
    private final Runnable onDismissed;

    /** Supplied by the engine at {@link #init}; the card dismisses through it. */
    private Runnable dismissDialog;

    public PlayerEventDialogDelegate(PlayerEventNotice notice, int currentDay,
                                     Runnable onDismissed) {
        this.onDismissed = onDismissed;
        // dismissDialog is only known once init runs, so the card holds an indirection
        // rather than the callback itself.
        this.card = new PlayerEventCard(notice, currentDay, this::dismiss);
    }

    private void dismiss() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.dismissDialog = callbacks::dismissDialog;
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return card;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void advance(float amount) {}

    @Override
    public void reportDismissed(int option) {
        if (onDismissed != null) onDismissed.run();
    }
}
