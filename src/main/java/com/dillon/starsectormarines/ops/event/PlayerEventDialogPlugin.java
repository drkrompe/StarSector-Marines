package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;

/**
 * Hosts the event card as an interaction of its own. Mirrors {@code MarineOpsCMD}'s
 * proven sequence — hide the vanilla text and visual panels, hand the canvas to a
 * custom visual dialog — but is opened by
 * {@link PlayerEventPresenter} rather than by a rule, so there is no menu underneath
 * and dismissing the card must dismiss the whole interaction.
 */
public final class PlayerEventDialogPlugin implements InteractionDialogPlugin {

    private static final Logger LOG = Global.getLogger(PlayerEventDialogPlugin.class);

    /** Modal card, not a takeover — an interruption should not swallow the screen. */
    private static final float WIDTH_FRACTION = 0.52f;
    private static final float HEIGHT_FRACTION = 0.42f;
    /** Floors so the card stays legible at Orbitron 20 on small windows. */
    private static final float MIN_WIDTH = 620f;
    private static final float MIN_HEIGHT = 340f;

    private final PlayerEventNotice notice;
    private final int currentDay;

    private InteractionDialogAPI dialog;
    private boolean dismissRequested;

    public PlayerEventDialogPlugin(PlayerEventNotice notice, int currentDay) {
        this.notice = notice;
        this.currentDay = currentDay;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        float screenW = Global.getSettings().getScreenWidth();
        float screenH = Global.getSettings().getScreenHeight();
        float w = Math.min(screenW, Math.max(MIN_WIDTH, screenW * WIDTH_FRACTION));
        float h = Math.min(screenH, Math.max(MIN_HEIGHT, screenH * HEIGHT_FRACTION));
        dialog.showCustomVisualDialog(w, h,
                new PlayerEventDialogDelegate(notice, currentDay, this::requestDismiss));
        LOG.info("PlayerEvent: presented " + notice);
    }

    /**
     * Closes the hosting interaction. Attempted immediately and retried from
     * {@link #advance} if the engine refuses a dismissal issued during the custom
     * panel's own teardown — this dialog has no options, so a failure here would trap
     * the player.
     */
    private void requestDismiss() {
        dismissRequested = true;
        closeIfRequested();
    }

    private void closeIfRequested() {
        if (!dismissRequested || dialog == null) return;
        try {
            dismissRequested = false;
            dialog.dismiss();
        } catch (RuntimeException e) {
            dismissRequested = true;
            LOG.warn("PlayerEvent: deferred dialog dismissal after " + e);
        }
    }

    @Override
    public void advance(float amount) {
        closeIfRequested();
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {}

    @Override
    public void optionMousedOver(String optionText, Object optionData) {}

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {}

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return Collections.emptyMap();
    }
}
