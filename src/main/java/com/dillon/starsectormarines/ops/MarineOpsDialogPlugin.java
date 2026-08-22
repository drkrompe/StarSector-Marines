package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Opens the Marine Ops takeover as an interaction of its own, with no planet menu
 * behind it.
 *
 * <p>{@code MarineOpsCMD} reaches the same panel from inside the vanilla planet
 * dialog, where hiding the text/visual panels is a temporary swap and backing out
 * restores the menu. Here there is no menu: the whole interaction exists to host our
 * panel, so dismissing the panel must dismiss the interaction too.
 */
public final class MarineOpsDialogPlugin implements InteractionDialogPlugin {

    private static final Logger LOG = Global.getLogger(MarineOpsDialogPlugin.class);

    /** Fraction of the screen the takeover occupies — matches {@code MarineOpsCMD}. */
    private static final float WIDTH_FRACTION = 0.92f;
    private static final float HEIGHT_FRACTION = 0.88f;

    private final PlanetAPI planet;
    private final Consumer<MarineOpsContext> seed;

    private InteractionDialogAPI dialog;
    private boolean dismissRequested;

    public MarineOpsDialogPlugin(PlanetAPI planet, Consumer<MarineOpsContext> seed) {
        this.planet = planet;
        this.seed = seed;
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.hideTextPanel();
        dialog.hideVisualPanel();
        float w = Global.getSettings().getScreenWidth() * WIDTH_FRACTION;
        float h = Global.getSettings().getScreenHeight() * HEIGHT_FRACTION;
        dialog.showCustomVisualDialog(w, h,
                new MarineOpsDialogDelegate(planet, seed, this::requestDismiss));
        LOG.info("MarineOps: self-triggered dialog opened");
    }

    /**
     * Closes the hosting interaction. Tried immediately, and retried from
     * {@link #advance} if the engine rejects a dismissal made from inside the custom
     * panel's own teardown — a dialog left standing here has no options to click, so
     * failing quietly would strand the player.
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
            LOG.warn("MarineOps: deferred dialog dismissal after " + e);
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
