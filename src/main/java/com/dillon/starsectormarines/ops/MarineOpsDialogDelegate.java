package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import org.apache.log4j.Logger;

/**
 * Delegate for {@link InteractionDialogAPI#showCustomVisualDialog}. Unlike the
 * {@code CustomDialogDelegate} variant, this one does NOT force confirm/cancel
 * buttons onto the dialog chrome — we own the dismiss action entirely via the
 * {@link DialogCallbacks#dismissDialog()} callback delivered to {@link #init}.
 *
 * <p>That property is the reason we use this variant: it means no accidental
 * keyboard shortcut (G/Esc) can yank the player out mid-action. The Back button
 * is a widget in {@link MarineOpsPanelPlugin}'s tree, fully under our control —
 * to gate exit during an in-progress mini-game, just no-op the onBack callback.
 *
 * <p>Holds a reference to the parent interaction dialog so {@link #reportDismissed}
 * can restore the text/visual panels {@code MarineOpsCMD} hid on open, keeping
 * the planet menu intact when the player backs out.
 */
public class MarineOpsDialogDelegate implements CustomVisualDialogDelegate {

    private static final Logger LOG = Global.getLogger(MarineOpsDialogDelegate.class);

    private final InteractionDialogAPI parent;
    private final MarineOpsPanelPlugin panel;

    public MarineOpsDialogDelegate(InteractionDialogAPI parent, PlanetAPI planet) {
        this.parent = parent;
        this.panel = new MarineOpsPanelPlugin(planet);
    }

    @Override
    public void init(CustomPanelAPI p, DialogCallbacks callbacks) {
        LOG.info("MarineOps: dialog created ("
                + p.getPosition().getWidth() + "x" + p.getPosition().getHeight() + ")");
        panel.setOnBack(callbacks::dismissDialog);
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return panel;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void reportDismissed(int option) {
        panel.dismiss();
        parent.showTextPanel();
        parent.showVisualPanel();
        LOG.info("MarineOps: dialog dismissed");
    }
}
